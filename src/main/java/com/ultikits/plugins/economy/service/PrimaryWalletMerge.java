package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.Cached;
import com.ultikits.ultitools.interfaces.DataOperator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Merges the primary currency's second wallet into the account wallet, once (UltiKits/UltiEconomy#25).
 *
 * <p>UltiEconomy 2.0.0 kept the primary currency in two wallets: the account row
 * ({@code economy_accounts}) that Vault, {@code /money}, {@code /pay} and {@code /bank} use, and a
 * {@code currency_balances} row that every path naming the currency used. The maintainer decided
 * (2026-09-24, confirmed 2026-09-25) that the account row is the only wallet, and that on the first
 * start after the upgrade each player's second-wallet cash and bank are added to it in full, logged
 * per player with a total. This class does that at load, before the module registers anything a
 * player or another plugin can reach.
 *
 * <p><b>Once, and safe against a crash between any two writes.</b> A player's merge is three steps,
 * each a single-row write:
 * <ol>
 *   <li>mark: every second-wallet row of the player gets a {@code currency_id} that records the
 *       account's balances before the merge and the amounts to add ({@link #MARKER_PREFIX});</li>
 *   <li>credit: if the account still holds the recorded "before" balances, it is set to "before plus
 *       the amounts"; if it already holds that, the credit happened in an earlier, interrupted start;
 *       anything else means the account changed since, and the row is left for an operator;</li>
 *   <li>remove the player's second-wallet rows; only marked rows are ever removed (a start that
 *       finds a player's marking interrupted finishes it first).</li>
 * </ol>
 * The credit writes absolute values computed from the marker, not an increment, so repeating it
 * changes nothing; a start first settles any marker an earlier start left. Every step is made durable
 * before the next one starts: on SQLite and MySQL each write is its own auto-committed statement,
 * durable when it returns; on the JSON backend, whose writes reach disk only when its cache is
 * flushed, this class flushes the markers before any credit and the credits before any removal. So
 * whatever point a crash interrupts, the next start ends with the same balances -- never more, never
 * less. (A crash while the JSON backend is rewriting one record's file can still leave that file
 * unreadable; that is the framework's file write, not something a module can make atomic.)
 *
 * <p>This class assumes it is the only writer while it runs. Servers that share one database run it
 * through {@link MergeClaim}, which lets one server at a time run it.
 */
public final class PrimaryWalletMerge {

    /**
     * {@code currency_id} prefix of a second-wallet row whose merge has started and not finished:
     * {@code <prefix><cash before>/<bank before>:<cash to add>/<bank to add>}, "before" being
     * {@code none} when the player had no account. No currency id can collide with it in practice:
     * it starts with a character no shipped or documented currency id uses.
     */
    static final String MARKER_PREFIX = "~merging-into-account:";

    private static final String NONE = "none";

    private final UltiToolsPlugin plugin;
    private final DataOperator<PlayerAccountEntity> accounts;
    private final DataOperator<CurrencyBalanceEntity> balances;
    private final String primaryId;
    private final Function<String, String> nameOf;

    private int merged;
    private BigDecimal cashAdded = BigDecimal.ZERO;
    private BigDecimal bankAdded = BigDecimal.ZERO;
    private int emptyRemoved;
    private final Set<String> unsettledPlayers = new HashSet<>();
    private Runnable heartbeat = () -> { };
    private Runnable beforeAccountWrite = () -> { };

    /**
     * @param plugin    the module, for its logger and language catalogue
     * @param accounts  the account wallets ({@code economy_accounts})
     * @param balances  the per-currency wallets ({@code currency_balances})
     * @param primaryId the primary currency's id
     * @param nameOf    the name to give an account created for a player who has only a second
     *                  wallet, from the player's UUID
     */
    public PrimaryWalletMerge(UltiToolsPlugin plugin,
                              DataOperator<PlayerAccountEntity> accounts,
                              DataOperator<CurrencyBalanceEntity> balances,
                              String primaryId,
                              Function<String, String> nameOf) {
        this.plugin = plugin;
        this.accounts = accounts;
        this.balances = balances;
        this.primaryId = primaryId;
        this.nameOf = nameOf;
    }

    /**
     * Makes the merge call {@code heartbeat} between its steps -- before each player and each row
     * removal -- so that {@link MergeClaim} can show the servers waiting for it that this one is still
     * merging, and {@code beforeAccountWrite} right before each write to an account, the one write a
     * second writer could not repeat harmlessly. Either stops the merge by throwing (another server has
     * taken the claim over), which ends it like a storage failure.
     */
    PrimaryWalletMerge withClaim(Runnable heartbeat, Runnable beforeAccountWrite) {
        this.heartbeat = heartbeat;
        this.beforeAccountWrite = beforeAccountWrite;
        return this;
    }

    /**
     * Whether anything is left to merge: a second-wallet row of the primary currency, or a row an
     * earlier start marked. False on a fresh install and on a database whose merge is finished.
     */
    public boolean pending() {
        for (CurrencyBalanceEntity row : balances.getAll()) {
            String id = row.getCurrencyId();
            if (primaryId.equals(id) || (id != null && id.startsWith(MARKER_PREFIX))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Settles any merge an earlier start left unfinished, merges every remaining second wallet, and
     * logs one line per merged player and one total line when there was anything to do.
     *
     * @return true when the merge finished (rows an operator must settle do not stop it); false when
     *         storage failed, which the caller must treat as "do not start": the next start resumes
     */
    public boolean run() {
        try {
            settleMarked();
            markSecondWallets();
            settleMarked();
        } catch (RuntimeException | IllegalAccessException e) {
            plugin.getLogger().error(String.format(
                    plugin.i18n("economy.log.wallet_merge.failed"), String.valueOf(e.getMessage())));
            return false;
        }
        if (merged > 0 || emptyRemoved > 0 || !unsettledPlayers.isEmpty()) {
            plugin.getLogger().info(String.format(plugin.i18n("economy.log.wallet_merge.total"),
                    merged, cashAdded.toPlainString(), bankAdded.toPlainString(), emptyRemoved,
                    unsettledPlayers.size()));
        }
        return true;
    }

    /** Step 1 for every player: record what the merge will do on each of their second-wallet rows. */
    private void markSecondWallets() throws IllegalAccessException {
        heartbeat.run();
        Map<String, List<CurrencyBalanceEntity>> byPlayer = group(balances.getAll(
                WhereCondition.builder().column("currency_id").value(primaryId).build()));
        if (byPlayer.isEmpty()) {
            return;
        }
        Map<String, PlayerAccountEntity> byUuid = accountsByUuid();
        boolean marked = false;
        List<CurrencyBalanceEntity> empty = new ArrayList<>();
        for (Map.Entry<String, List<CurrencyBalanceEntity>> player : byPlayer.entrySet()) {
            heartbeat.run();
            if (unsettledPlayers.contains(player.getKey())) {
                // An operator has to settle this player's earlier merge first.
                continue;
            }
            PlayerAccountEntity account = byUuid.get(player.getKey());
            String name = nameFor(player.getKey(), account);
            CurrencyBalanceEntity nonFinite = firstNonFinite(player.getValue());
            if (nonFinite != null) {
                // Not an amount of money: leave it for an operator rather than stop the module.
                unsettledPlayers.add(player.getKey());
                plugin.getLogger().warn(String.format(plugin.i18n("economy.log.wallet_merge.too_large"),
                        String.valueOf(nonFinite.getCash()), String.valueOf(nonFinite.getBank()), name));
                continue;
            }
            BigDecimal addCash = BigDecimal.ZERO;
            BigDecimal addBank = BigDecimal.ZERO;
            for (CurrencyBalanceEntity row : player.getValue()) {
                // A negative amount (no command of this module can produce one) is not taken from
                // the account: nobody's balance goes down.
                if (row.getCash() < 0) {
                    plugin.getLogger().warn(String.format(
                            plugin.i18n("economy.log.wallet_merge.negative_cash"), name, plain(row.getCash())));
                }
                if (row.getBank() < 0) {
                    plugin.getLogger().warn(String.format(
                            plugin.i18n("economy.log.wallet_merge.negative_bank"), name, plain(row.getBank())));
                }
                addCash = addCash.add(positivePart(row.getCash()));
                addBank = addBank.add(positivePart(row.getBank()));
            }
            if (addCash.signum() == 0 && addBank.signum() == 0) {
                // Nothing to move: removing the rows cannot lose money, so no marker is needed.
                empty.addAll(player.getValue());
                emptyRemoved += player.getValue().size();
                continue;
            }
            double beforeCash = account == null ? 0.0 : account.getCash();
            double beforeBank = account == null ? 0.0 : account.getBank();
            if (exactTarget(beforeCash, addCash) == null || exactTarget(beforeBank, addBank) == null) {
                // The account could not hold the result exactly: move nothing, keep the rows unmarked.
                unsettledPlayers.add(player.getKey());
                plugin.getLogger().warn(String.format(plugin.i18n("economy.log.wallet_merge.too_large"),
                        addCash.toPlainString(), addBank.toPlainString(), name));
                continue;
            }
            String marker = MARKER_PREFIX
                    + (account == null ? NONE : account.getCash() + "/" + account.getBank())
                    + ":" + encode(addCash) + "/" + encode(addBank);
            for (CurrencyBalanceEntity row : player.getValue()) {
                row.setCurrencyId(marker);
                balances.update(row);
                marked = true;
            }
        }
        if (marked) {
            // Every marker is on disk before any account is credited.
            flush(balances);
        }
        // Durable like every other removal, so a restart cannot bring the rows back.
        remove(empty);
    }

    /**
     * Steps 2 and 3 for every player with a marked row: credit the account once, then remove the
     * player's rows. Only marked rows are ever removed, so once removal has started every row the
     * player has left is marked, and an account holding the marker's target means "done".
     *
     * <p>A start interrupted while marking can leave a player with some rows marked and some not.
     * The marker records the sum of all the player's rows at marking time, and marking changes only
     * {@code currency_id}, so those unmarked rows are the ones whose amounts, added to the marked
     * rows', give the recorded sum. This start finishes that marking (durably) before it credits or
     * removes anything. Rows that do not add up are left for an operator.
     */
    private void settleMarked() throws IllegalAccessException {
        heartbeat.run();
        Map<String, List<CurrencyBalanceEntity>> markedByPlayer = new LinkedHashMap<>();
        Map<String, List<CurrencyBalanceEntity>> unmarkedByPlayer = new LinkedHashMap<>();
        for (CurrencyBalanceEntity row : balances.getAll()) {
            String id = row.getCurrencyId();
            if (id != null && id.startsWith(MARKER_PREFIX)) {
                add(markedByPlayer, row);
            } else if (primaryId.equals(id)) {
                add(unmarkedByPlayer, row);
            }
        }
        markedByPlayer.keySet().removeAll(unsettledPlayers);
        if (markedByPlayer.isEmpty()) {
            return;
        }
        Map<String, PlayerAccountEntity> byUuid = accountsByUuid();
        List<CurrencyBalanceEntity> markedDone = new ArrayList<>();
        boolean credited = false;
        boolean completedMarking = false;
        for (Map.Entry<String, List<CurrencyBalanceEntity>> player : markedByPlayer.entrySet()) {
            heartbeat.run();
            String uuid = player.getKey();
            List<CurrencyBalanceEntity> markedRows = player.getValue();
            List<CurrencyBalanceEntity> unmarkedRows = unmarkedByPlayer.containsKey(uuid)
                    ? unmarkedByPlayer.get(uuid) : new ArrayList<CurrencyBalanceEntity>();
            PlayerAccountEntity account = byUuid.get(uuid);
            Marker m = Marker.parse(markedRows.get(0).getCurrencyId());
            if (m == null || !sameMarker(markedRows)) {
                leaveUnsettled(uuid, account, markedRows.get(0));
                continue;
            }
            if (!unmarkedRows.isEmpty()) {
                if (!addsUpTo(m, markedRows, unmarkedRows)) {
                    leaveUnsettled(uuid, account, markedRows.get(0));
                    continue;
                }
                // Finish the interrupted marking, so that no row is ever removed unmarked.
                for (CurrencyBalanceEntity row : unmarkedRows) {
                    row.setCurrencyId(markedRows.get(0).getCurrencyId());
                    balances.update(row);
                    markedRows.add(row);
                }
                completedMarking = true;
            }
            Double exactCash = exactTarget(m.hasBefore ? m.cashBefore : 0.0, m.cashToAdd);
            Double exactBank = exactTarget(m.hasBefore ? m.bankBefore : 0.0, m.bankToAdd);
            if (exactCash == null || exactBank == null) {
                // Marking never records such a merge; a marker that leads here was not written by it.
                leaveUnsettled(uuid, account, markedRows.get(0));
                continue;
            }
            double targetCash = exactCash;
            double targetBank = exactBank;
            if (account != null && holds(account, targetCash, targetBank)) {
                // Credited by an earlier, interrupted start: only the removal is left.
                markedDone.addAll(markedRows);
                continue;
            }
            if (!m.hasBefore && account == null) {
                account = PlayerAccountEntity.builder()
                        .uuid(uuid)
                        .playerName(nameFor(uuid, null))
                        .cash(targetCash)
                        .bank(targetBank)
                        .build();
                beforeAccountWrite.run();
                accounts.insert(account);
            } else if (m.hasBefore && account != null && holds(account, m.cashBefore, m.bankBefore)) {
                account.setCash(targetCash);
                account.setBank(targetBank);
                beforeAccountWrite.run();
                accounts.update(account);
            } else {
                leaveUnsettled(uuid, account, markedRows.get(0));
                continue;
            }
            credited = true;
            markedDone.addAll(markedRows);
            merged++;
            cashAdded = cashAdded.add(m.cashToAdd);
            bankAdded = bankAdded.add(m.bankToAdd);
            plugin.getLogger().info(String.format(plugin.i18n("economy.log.wallet_merge.player"),
                    m.cashToAdd.toPlainString(), m.bankToAdd.toPlainString(), nameFor(uuid, account),
                    plain(targetCash), plain(targetBank)));
        }
        if (completedMarking) {
            // Every row is marked on disk before any row is removed.
            flush(balances);
        }
        if (credited) {
            // Every credit is on disk before any row is removed.
            flush(accounts);
        }
        remove(markedDone);
    }

    /** Removes {@code rows} and makes the removal durable before returning. */
    private void remove(List<CurrencyBalanceEntity> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (CurrencyBalanceEntity row : rows) {
            heartbeat.run();
            balances.delById(row.getId());
        }
        flush(balances);
        if (balances instanceof Cached) {
            ((Cached) balances).gc();
        }
    }

    private void leaveUnsettled(String uuid, PlayerAccountEntity account, CurrencyBalanceEntity row) {
        unsettledPlayers.add(uuid);
        Marker m = Marker.parse(row.getCurrencyId());
        plugin.getLogger().warn(String.format(plugin.i18n("economy.log.wallet_merge.unsettled"),
                nameFor(uuid, account),
                m != null ? m.cashToAdd.toPlainString() : plain(row.getCash()),
                m != null ? m.bankToAdd.toPlainString() : plain(row.getBank()),
                row.getCurrencyId()));
    }

    /**
     * The balance a merge stores: {@code before} plus {@code add}, added as the decimals they print
     * as (so 0.1 + 0.2 is 0.3), or null when no balance can hold that sum exactly -- it overflows, it
     * has more significant digits than a balance keeps, or {@code before} is not a number. One rule
     * instead of one check per way a sum can go wrong: the credit writes this absolute value, and
     * "the account already holds it" must mean "credited", which only an exact sum guarantees (a sum
     * that rounded back to {@code before} would read as done and lose the rows). Deterministic, so a
     * later start computes the same value from the same marker.
     */
    static Double exactTarget(double before, BigDecimal add) {
        if (!Double.isFinite(before)) {
            return null;
        }
        BigDecimal sum = BigDecimal.valueOf(before).add(add);
        double target = sum.doubleValue();
        if (!Double.isFinite(target) || BigDecimal.valueOf(target).compareTo(sum) != 0) {
            return null;
        }
        return target;
    }

    /** The first row holding an amount that is not a finite number, or null. */
    private static CurrencyBalanceEntity firstNonFinite(List<CurrencyBalanceEntity> rows) {
        for (CurrencyBalanceEntity row : rows) {
            if (!Double.isFinite(row.getCash()) || !Double.isFinite(row.getBank())) {
                return row;
            }
        }
        return null;
    }

    /** Whether every marked row carries the same marker, as one marking writes. */
    private static boolean sameMarker(List<CurrencyBalanceEntity> markedRows) {
        String first = markedRows.get(0).getCurrencyId();
        for (CurrencyBalanceEntity row : markedRows) {
            if (!first.equals(row.getCurrencyId())) {
                return false;
            }
        }
        return true;
    }

    /** Whether the marked and unmarked rows together hold exactly the sums the marker recorded. */
    private static boolean addsUpTo(Marker m, List<CurrencyBalanceEntity> markedRows,
                                    List<CurrencyBalanceEntity> unmarkedRows) {
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal bank = BigDecimal.ZERO;
        List<CurrencyBalanceEntity> all = new ArrayList<>(markedRows);
        all.addAll(unmarkedRows);
        for (CurrencyBalanceEntity row : all) {
            cash = cash.add(positivePart(row.getCash()));
            bank = bank.add(positivePart(row.getBank()));
        }
        return cash.compareTo(m.cashToAdd) == 0 && bank.compareTo(m.bankToAdd) == 0;
    }

    /** {@code amount} when it is above 0, as the exact decimal it prints as; otherwise 0. */
    private static BigDecimal positivePart(double amount) {
        return amount > 0 ? BigDecimal.valueOf(amount) : BigDecimal.ZERO;
    }

    private static Map<String, List<CurrencyBalanceEntity>> group(List<CurrencyBalanceEntity> rows) {
        Map<String, List<CurrencyBalanceEntity>> byPlayer = new LinkedHashMap<>();
        for (CurrencyBalanceEntity row : rows) {
            add(byPlayer, row);
        }
        return byPlayer;
    }

    private static void add(Map<String, List<CurrencyBalanceEntity>> byPlayer, CurrencyBalanceEntity row) {
        List<CurrencyBalanceEntity> list = byPlayer.get(row.getUuid());
        if (list == null) {
            list = new ArrayList<>();
            byPlayer.put(row.getUuid(), list);
        }
        list.add(row);
    }

    private Map<String, PlayerAccountEntity> accountsByUuid() {
        Map<String, PlayerAccountEntity> byUuid = new HashMap<>();
        for (PlayerAccountEntity account : accounts.getAll()) {
            if (!byUuid.containsKey(account.getUuid())) {
                byUuid.put(account.getUuid(), account);
            }
        }
        return byUuid;
    }

    private String nameFor(String uuid, PlayerAccountEntity account) {
        if (account != null && account.getPlayerName() != null) {
            return account.getPlayerName();
        }
        return nameOf.apply(uuid);
    }

    private static boolean holds(PlayerAccountEntity account, double cash, double bank) {
        return account.getCash() == cash && account.getBank() == bank;
    }

    /**
     * An amount as the marker stores it, short enough that a whole marker fits the
     * {@code VARCHAR(255)} column the framework gives {@code currency_id}: the plain decimal when it
     * is short (every realistic balance), else its scientific form, else -- only for an absurd sum
     * with hundreds of significant digits -- the nearest {@code double}, which then fails
     * {@link #addsUpTo} and leaves the player for an operator rather than moving a wrong amount.
     */
    private static String encode(BigDecimal amount) {
        String plain = amount.toPlainString();
        if (plain.length() <= 40) {
            return plain;
        }
        String scientific = amount.stripTrailingZeros().toString();
        return scientific.length() <= 40 ? scientific : Double.toString(amount.doubleValue());
    }

    private static String plain(double amount) {
        return BigDecimal.valueOf(amount).toPlainString();
    }

    private static void flush(DataOperator<?> operator) {
        if (operator instanceof Cached) {
            ((Cached) operator).flush();
        }
    }

    /** A parsed {@link #MARKER_PREFIX} currency id. */
    private static final class Marker {
        private boolean hasBefore;
        private double cashBefore;
        private double bankBefore;
        private BigDecimal cashToAdd;
        private BigDecimal bankToAdd;

        static Marker parse(String currencyId) {
            try {
                String[] parts = currencyId.substring(MARKER_PREFIX.length()).split(":");
                if (parts.length != 2) {
                    return null;
                }
                Marker m = new Marker();
                if (!NONE.equals(parts[0])) {
                    String[] before = parts[0].split("/");
                    if (before.length != 2) {
                        return null;
                    }
                    m.hasBefore = true;
                    m.cashBefore = Double.parseDouble(before[0]);
                    m.bankBefore = Double.parseDouble(before[1]);
                }
                String[] add = parts[1].split("/");
                if (add.length != 2) {
                    return null;
                }
                m.cashToAdd = new BigDecimal(add[0]);
                m.bankToAdd = new BigDecimal(add[1]);
                return m;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
