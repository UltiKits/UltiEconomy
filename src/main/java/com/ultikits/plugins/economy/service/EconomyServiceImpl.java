package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.entity.TreasuryEntity;
import com.ultikits.plugins.economy.model.CurrencyDefinition;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class EconomyServiceImpl implements EconomyService {

    private UltiToolsPlugin plugin;
    private DataOperator<PlayerAccountEntity> dataOperator;
    private EconomyConfig config;
    private DataOperator<CurrencyBalanceEntity> currencyDataOperator;
    private CurrencyManager currencyManager;
    private TaxService taxService;
    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");

    public EconomyServiceImpl(UltiToolsPlugin plugin) {
        this.plugin = plugin;
        this.dataOperator = plugin.getDataOperator(PlayerAccountEntity.class);
        this.config = plugin.getConfig(EconomyConfig.class);
        this.currencyDataOperator = plugin.getDataOperator(CurrencyBalanceEntity.class);
        this.currencyManager = ((UltiEconomy) plugin).getCurrencyManager();
        this.taxService = new TaxService(
                plugin.getConfig(EconomyConfig.class),
                plugin.getDataOperator(TreasuryEntity.class));
    }

    @SuppressWarnings("all")
    static EconomyServiceImpl createForTest(UltiToolsPlugin plugin,
                                            DataOperator<PlayerAccountEntity> dataOperator,
                                            EconomyConfig config,
                                            DataOperator<CurrencyBalanceEntity> currencyDataOperator,
                                            CurrencyManager currencyManager) {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            EconomyServiceImpl instance = (EconomyServiceImpl) unsafe.allocateInstance(EconomyServiceImpl.class);
            instance.plugin = plugin;
            instance.dataOperator = dataOperator;
            instance.config = config;
            instance.currencyDataOperator = currencyDataOperator;
            instance.currencyManager = currencyManager;
            // Field initializers don't run with allocateInstance
            java.lang.reflect.Field df = EconomyServiceImpl.class.getDeclaredField("decimalFormat");
            df.setAccessible(true);
            df.set(instance, new DecimalFormat("#,##0.00"));
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setTaxService(TaxService taxService) {
        this.taxService = taxService;
    }

    // --- Account-wallet methods ---
    // The primary currency's one and only wallet: the account row (table economy_accounts) that
    // Vault, /money, /pay, /bank and /eco use. The currency-aware methods below route the primary
    // currency's id here, so the primary currency is never kept anywhere else
    // (UltiKits/UltiEconomy#25).

    @Override
    public PlayerAccountEntity getAccount(UUID playerUuid) {
        List<PlayerAccountEntity> results = dataOperator.query()
                .where("uuid").eq(playerUuid.toString())
                .list();
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public PlayerAccountEntity getOrCreateAccount(UUID playerUuid, String playerName) {
        PlayerAccountEntity account = getAccount(playerUuid);
        if (account != null) {
            return account;
        }
        account = PlayerAccountEntity.builder()
                .uuid(playerUuid.toString())
                .playerName(playerName)
                .cash(config.getInitialCash())
                .bank(0.0)
                .build();
        dataOperator.insert(account);
        return account;
    }

    @Override
    public boolean hasAccount(UUID playerUuid) {
        return getAccount(playerUuid) != null;
    }

    @Override
    public double getCash(UUID playerUuid) {
        PlayerAccountEntity account = getAccount(playerUuid);
        return account != null ? account.getCash() : 0.0;
    }

    @Override
    public double getBank(UUID playerUuid) {
        PlayerAccountEntity account = getAccount(playerUuid);
        return account != null ? account.getBank() : 0.0;
    }

    @Override
    public double getTotalWealth(UUID playerUuid) {
        PlayerAccountEntity account = getAccount(playerUuid);
        return account != null ? account.getTotalWealth() : 0.0;
    }

    @Override
    public boolean setCash(UUID playerUuid, double amount) {
        if (amount < 0) {
            return false;
        }
        return changeAccount(playerUuid, getAccount(playerUuid), a -> {
            a.setCash(amount);
            return true;
        }) != null;
    }

    @Override
    public boolean setBank(UUID playerUuid, double amount) {
        if (amount < 0) {
            return false;
        }
        return changeAccount(playerUuid, getAccount(playerUuid), a -> {
            a.setBank(amount);
            return true;
        }) != null;
    }

    @Override
    public boolean addCash(UUID playerUuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        return changeAccount(playerUuid, getAccount(playerUuid), a -> {
            a.setCash(a.getCash() + amount);
            return true;
        }) != null;
    }

    @Override
    public boolean addBank(UUID playerUuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        return changeAccount(playerUuid, getAccount(playerUuid), a -> {
            a.setBank(a.getBank() + amount);
            return true;
        }) != null;
    }

    @Override
    public boolean takeCash(UUID playerUuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        return changeAccount(playerUuid, getAccount(playerUuid), a -> {
            if (a.getCash() < amount) {
                return false;
            }
            a.setCash(a.getCash() - amount);
            return true;
        }) != null;
    }

    @Override
    public boolean takeBank(UUID playerUuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        return changeAccount(playerUuid, getAccount(playerUuid), a -> {
            if (a.getBank() < amount) {
                return false;
            }
            a.setBank(a.getBank() - amount);
            return true;
        }) != null;
    }

    /**
     * The transaction tax on a transfer of {@code amount} by {@code payer}: none for a payer holding
     * {@code tax.transaction-tax.exempt-permission} (UltiKits/UltiEconomy#26). A transfer is started
     * by an online player ({@code /pay}), whose permissions are read here; a payer who is not
     * online cannot be checked and is taxed.
     */
    private double transactionTax(UUID payer, double amount) {
        if (taxService == null) {
            return 0.0;
        }
        String exemptPermission = config.getTransactionTaxExemptPermission();
        if (exemptPermission != null && !exemptPermission.isEmpty()) {
            // No server means no online player whose permission could be read (the service used
            // on its own, outside a running server): the payer is taxed.
            Player player = Bukkit.getServer() == null ? null : Bukkit.getPlayer(payer);
            if (player != null && player.hasPermission(exemptPermission)) {
                return 0.0;
            }
        }
        return taxService.calculateTransactionTax(amount);
    }

    @Override
    public boolean transfer(UUID from, UUID to, double amount) {
        return transferWithReceipt(from, to, amount).isSuccess();
    }

    @Override
    public TransferReceipt transferWithReceipt(UUID from, UUID to, double amount) {
        if (amount <= 0 || from.equals(to)) {
            return TransferReceipt.refused();
        }
        PlayerAccountEntity sender = getAccount(from);
        if (sender == null || sender.getCash() < amount) {
            return TransferReceipt.refused();
        }
        PlayerAccountEntity receiver = getAccount(to);
        if (receiver == null) {
            return TransferReceipt.refused();
        }
        double tax = transactionTax(from, amount);
        double received = amount - tax;
        // Each side is a conditional write that re-reads and retries when another server changed the
        // row (UltiKits/UltiEconomy#41); a receiver that cannot be credited gets the sender refunded.
        PlayerAccountEntity debited = changeAccount(from, sender, s -> {
            if (s.getCash() < amount) {
                return false;
            }
            s.setCash(s.getCash() - amount);
            return true;
        });
        if (debited == null) {
            return TransferReceipt.refused();
        }
        if (changeAccount(to, receiver, r -> {
            r.setCash(r.getCash() + received);
            return true;
        }) == null) {
            changeAccount(from, debited, s -> {
                s.setCash(s.getCash() + amount);
                return true;
            });
            return TransferReceipt.refused();
        }
        if (tax > 0 && taxService != null) {
            depositTax(tax, currencyManager.getPrimaryCurrency().getId());
        }
        return TransferReceipt.completed(received, tax);
    }

    @Override
    public boolean depositToBank(UUID playerUuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        if (amount < config.getMinDeposit()) {
            return false;
        }
        double maxBalance = config.getMaxBankBalance();
        return changeAccount(playerUuid, getAccount(playerUuid), a -> {
            if (a.getCash() < amount || (maxBalance > 0 && a.getBank() + amount > maxBalance)) {
                return false;
            }
            a.setCash(a.getCash() - amount);
            a.setBank(a.getBank() + amount);
            return true;
        }) != null;
    }

    @Override
    public boolean withdrawFromBank(UUID playerUuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        return changeAccount(playerUuid, getAccount(playerUuid), a -> {
            if (a.getBank() < amount) {
                return false;
            }
            a.setBank(a.getBank() - amount);
            a.setCash(a.getCash() + amount);
            return true;
        }) != null;
    }

    @Override
    public String formatAmount(double amount) {
        return config.getCurrencySymbol() + decimalFormat.format(amount);
    }

    // --- Currency-aware methods ---
    // A non-primary currency keeps its own row in currency_balances. The primary currency's id is
    // routed to the account-wallet methods above, whichever path names it (/money coins,
    // /pay ... coins, /eco ... coins, /note ... coins, %ultieconomy_coins_*%), so the primary
    // currency has exactly one wallet. Its limits come from config.yml (maintainer decision
    // 2026-09-24, UltiKits/UltiEconomy#25).

    /** Whether {@code currencyId} names the primary currency, whose one wallet is the account row. */
    private boolean isPrimary(String currencyId) {
        return currencyManager != null && currencyManager.getPrimaryCurrencyId().equals(currencyId);
    }

    /**
     * The primary currency's balance as a detached snapshot of the account row, or null when the
     * player has no account. Writing to the returned object changes nothing; use the service methods.
     */
    private static CurrencyBalanceEntity accountView(PlayerAccountEntity account, String currencyId) {
        if (account == null) {
            return null;
        }
        return CurrencyBalanceEntity.builder()
                .uuid(account.getUuid())
                .currencyId(currencyId)
                .cash(account.getCash())
                .bank(account.getBank())
                .build();
    }

    @Override
    public CurrencyBalanceEntity getBalance(UUID playerUuid, String currencyId) {
        if (isPrimary(currencyId)) {
            return accountView(getAccount(playerUuid), currencyId);
        }
        List<CurrencyBalanceEntity> results = currencyDataOperator.query()
                .where("uuid").eq(playerUuid.toString())
                .and("currency_id").eq(currencyId)
                .list();
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public CurrencyBalanceEntity getOrCreateBalance(UUID playerUuid, String playerName, String currencyId) {
        if (isPrimary(currencyId)) {
            // Never a second primary-currency row: the account, created with config.yml's initial-cash.
            return accountView(getOrCreateAccount(playerUuid, playerName), currencyId);
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        if (balance != null) {
            return balance;
        }
        double initialCash = 0.0;
        if (currencyManager != null) {
            CurrencyDefinition def = currencyManager.getCurrency(currencyId);
            if (def != null) {
                initialCash = def.getInitialCash();
            }
        }
        balance = CurrencyBalanceEntity.builder()
                .uuid(playerUuid.toString())
                .currencyId(currencyId)
                .cash(initialCash)
                .bank(0.0)
                .build();
        currencyDataOperator.insert(balance);
        return balance;
    }

    @Override
    public boolean hasBalance(UUID playerUuid, String currencyId) {
        if (isPrimary(currencyId)) {
            return hasAccount(playerUuid);
        }
        return getBalance(playerUuid, currencyId) != null;
    }

    @Override
    public double getCash(UUID playerUuid, String currencyId) {
        if (isPrimary(currencyId)) {
            return getCash(playerUuid);
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        return balance != null ? balance.getCash() : 0.0;
    }

    @Override
    public double getBank(UUID playerUuid, String currencyId) {
        if (isPrimary(currencyId)) {
            return getBank(playerUuid);
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        return balance != null ? balance.getBank() : 0.0;
    }

    @Override
    public double getTotalWealth(UUID playerUuid, String currencyId) {
        if (isPrimary(currencyId)) {
            return getTotalWealth(playerUuid);
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        return balance != null ? balance.getTotalWealth() : 0.0;
    }

    @Override
    public boolean setCash(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return setCash(playerUuid, amount);
        }
        if (amount < 0) {
            return false;
        }
        return changeBalance(playerUuid, currencyId, getBalance(playerUuid, currencyId), b -> {
            b.setCash(amount);
            return true;
        }) != null;
    }

    @Override
    public boolean setBank(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return setBank(playerUuid, amount);
        }
        if (amount < 0) {
            return false;
        }
        return changeBalance(playerUuid, currencyId, getBalance(playerUuid, currencyId), b -> {
            b.setBank(amount);
            return true;
        }) != null;
    }

    @Override
    public boolean addCash(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return addCash(playerUuid, amount);
        }
        if (amount <= 0) {
            return false;
        }
        return changeBalance(playerUuid, currencyId, getBalance(playerUuid, currencyId), b -> {
            b.setCash(b.getCash() + amount);
            return true;
        }) != null;
    }

    @Override
    public boolean addBank(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return addBank(playerUuid, amount);
        }
        if (amount <= 0) {
            return false;
        }
        return changeBalance(playerUuid, currencyId, getBalance(playerUuid, currencyId), b -> {
            b.setBank(b.getBank() + amount);
            return true;
        }) != null;
    }

    @Override
    public boolean takeCash(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return takeCash(playerUuid, amount);
        }
        if (amount <= 0) {
            return false;
        }
        return changeBalance(playerUuid, currencyId, getBalance(playerUuid, currencyId), b -> {
            if (b.getCash() < amount) {
                return false;
            }
            b.setCash(b.getCash() - amount);
            return true;
        }) != null;
    }

    @Override
    public boolean takeBank(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return takeBank(playerUuid, amount);
        }
        if (amount <= 0) {
            return false;
        }
        return changeBalance(playerUuid, currencyId, getBalance(playerUuid, currencyId), b -> {
            if (b.getBank() < amount) {
                return false;
            }
            b.setBank(b.getBank() - amount);
            return true;
        }) != null;
    }

    @Override
    public boolean transfer(UUID from, UUID to, double amount, String currencyId) {
        return transferWithReceipt(from, to, amount, currencyId).isSuccess();
    }

    @Override
    public TransferReceipt transferWithReceipt(UUID from, UUID to, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return transferWithReceipt(from, to, amount);
        }
        if (amount <= 0 || from.equals(to)) {
            return TransferReceipt.refused();
        }
        CurrencyBalanceEntity sender = getBalance(from, currencyId);
        if (sender == null || sender.getCash() < amount) {
            return TransferReceipt.refused();
        }
        CurrencyBalanceEntity receiver = getBalance(to, currencyId);
        if (receiver == null) {
            return TransferReceipt.refused();
        }
        double tax = transactionTax(from, amount);
        double received = amount - tax;
        // As the primary transfer: conditional writes that retry, the sender refunded on failure (#41).
        CurrencyBalanceEntity debited = changeBalance(from, currencyId, sender, s -> {
            if (s.getCash() < amount) {
                return false;
            }
            s.setCash(s.getCash() - amount);
            return true;
        });
        if (debited == null) {
            return TransferReceipt.refused();
        }
        if (changeBalance(to, currencyId, receiver, r -> {
            r.setCash(r.getCash() + received);
            return true;
        }) == null) {
            changeBalance(from, currencyId, debited, s -> {
                s.setCash(s.getCash() + amount);
                return true;
            });
            return TransferReceipt.refused();
        }
        if (tax > 0 && taxService != null) {
            depositTax(tax, currencyId);
        }
        return TransferReceipt.completed(received, tax);
    }

    @Override
    public boolean depositToBank(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            // config.yml governs the primary currency: bank.enabled here, bank.min-deposit and
            // bank.max-balance in depositToBank(UUID, double).
            return config.isBankEnabled() && depositToBank(playerUuid, amount);
        }
        if (amount <= 0) {
            return false;
        }
        CurrencyDefinition def = currencyManager != null ? currencyManager.getCurrency(currencyId) : null;
        if (def != null) {
            if (!def.isBankEnabled()) {
                return false;
            }
            if (amount < def.getMinDeposit()) {
                return false;
            }
        }
        double maxBalance = def != null ? def.getMaxBankBalance() : -1;
        return changeBalance(playerUuid, currencyId, getBalance(playerUuid, currencyId), b -> {
            if (b.getCash() < amount || (maxBalance > 0 && b.getBank() + amount > maxBalance)) {
                return false;
            }
            b.setCash(b.getCash() - amount);
            b.setBank(b.getBank() + amount);
            return true;
        }) != null;
    }

    @Override
    public boolean withdrawFromBank(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            // config.yml's bank.enabled governs the primary currency, as /withdraw <amount> applies it.
            return config.isBankEnabled() && withdrawFromBank(playerUuid, amount);
        }
        if (amount <= 0) {
            return false;
        }
        return changeBalance(playerUuid, currencyId, getBalance(playerUuid, currencyId), b -> {
            if (b.getBank() < amount) {
                return false;
            }
            b.setBank(b.getBank() - amount);
            b.setCash(b.getCash() + amount);
            return true;
        }) != null;
    }

    @Override
    public String formatAmount(double amount, String currencyId) {
        if (currencyManager != null) {
            CurrencyDefinition def = currencyManager.getCurrency(currencyId);
            if (def != null) {
                return def.getSymbol() + decimalFormat.format(amount);
            }
        }
        return decimalFormat.format(amount);
    }

    @Override
    public String getPrimaryCurrencyId() {
        return currencyManager != null ? currencyManager.getPrimaryCurrencyId() : "coins";
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    /** How many times one balance change is attempted when another writer keeps changing the row. */
    private static final int MAX_WRITE_ATTEMPTS = 3;

    /** One balance change, applied to a row as read; false refuses it (for example, not enough cash). */
    private interface Change<T> {
        boolean apply(T row);
    }

    /** Reads and puts back a row's two balances. */
    private interface Money<T> {
        double cash(T row);

        double bank(T row);

        void restore(T row, double cash, double bank);
    }

    private static final Money<PlayerAccountEntity> ACCOUNT_MONEY = new Money<PlayerAccountEntity>() {
        @Override
        public double cash(PlayerAccountEntity row) {
            return row.getCash();
        }

        @Override
        public double bank(PlayerAccountEntity row) {
            return row.getBank();
        }

        @Override
        public void restore(PlayerAccountEntity row, double cash, double bank) {
            row.setCash(cash);
            row.setBank(bank);
        }
    };

    private static final Money<CurrencyBalanceEntity> BALANCE_MONEY = new Money<CurrencyBalanceEntity>() {
        @Override
        public double cash(CurrencyBalanceEntity row) {
            return row.getCash();
        }

        @Override
        public double bank(CurrencyBalanceEntity row) {
            return row.getBank();
        }

        @Override
        public void restore(CurrencyBalanceEntity row, double cash, double bank) {
            row.setCash(cash);
            row.setBank(bank);
        }
    };

    /** {@link #change} on a player's account row. */
    private PlayerAccountEntity changeAccount(UUID playerUuid, PlayerAccountEntity read, Change<PlayerAccountEntity> change) {
        return change(read, () -> getAccount(playerUuid), dataOperator, ACCOUNT_MONEY, change, true);
    }

    /** {@link #change} on a player's non-primary currency row. */
    private CurrencyBalanceEntity changeBalance(UUID playerUuid, String currencyId, CurrencyBalanceEntity read,
                                                Change<CurrencyBalanceEntity> change) {
        return change(read, () -> getBalance(playerUuid, currencyId), currencyDataOperator, BALANCE_MONEY, change, false);
    }

    /**
     * Applies one balance change to a row and writes it so that it applies only while the stored row
     * still holds the cash and bank this change read ({@code DataOperator#updateIf},
     * UltiKits/UltiEconomy#41). Servers sharing one database used to write absolute values read
     * earlier, so a change another server made in between was overwritten -- money created or
     * destroyed. When the write does not apply, the row is read again and the change decided again (it
     * may now be refused, for example for lack of cash), at most {@link #MAX_WRITE_ATTEMPTS} times.
     *
     * <p>Failure is the path a failed write always took: the row object is given back the balances it
     * was read with, one line is logged with the reason, and {@code null} comes back -- for a row that
     * is gone on the re-read (another writer removed it, UltiKits/UltiEconomy#42), for a row that kept
     * changing on every attempt, and for an entity whose fields could not be read. Any other storage
     * failure propagates, as before. A change the row refuses (not enough cash, over the cap) logs
     * nothing.
     *
     * @param read   the row as the caller read it; null when the player has no such row
     * @param reread reads the row again, after a write that did not apply
     * @return the row as written, or null when nothing was written
     */
    private <T extends BaseDataEntity<String>> T change(T read, Supplier<T> reread, DataOperator<T> operator,
                                                       Money<T> money, Change<T> change, boolean accountRow) {
        T row = read;
        for (int attempt = 1; ; attempt++) {
            if (row == null) {
                if (attempt > 1) {
                    logWriteFailed(accountRow, plugin.i18n("economy.log.row_gone"));
                }
                return null;
            }
            double cash = money.cash(row);
            double bank = money.bank(row);
            if (!change.apply(row)) {
                money.restore(row, cash, bank);
                return null;
            }
            boolean written;
            try {
                written = operator.updateIf(row, where("cash", cash), where("bank", bank));
            } catch (DataAccessException e) {
                money.restore(row, cash, bank);
                // The framework wraps a failure to read the entity's fields in this exception; anything
                // else is a storage failure and propagates as before.
                if (!(e.getCause() instanceof IllegalAccessException)) {
                    throw e;
                }
                logWriteFailed(accountRow, e.getCause().getMessage());
                return null;
            }
            if (written) {
                return row;
            }
            money.restore(row, cash, bank);
            if (attempt >= MAX_WRITE_ATTEMPTS) {
                logWriteFailed(accountRow, plugin.i18n("economy.log.write_contended"));
                return null;
            }
            row = reread.get();
        }
    }

    private void logWriteFailed(boolean accountRow, String reason) {
        String line = accountRow ? plugin.i18n("economy.log.account_update_failed")
                : plugin.i18n("economy.log.balance_update_failed");
        plugin.getLogger().error(String.format(line, reason));
    }

    /**
     * Adds a transfer's tax to the treasury. The transfer itself is done by then; a tax the treasury
     * could not take (its row kept changing on every attempt, #41) is logged, not undone.
     */
    private void depositTax(double tax, String currencyId) {
        boolean stored;
        try {
            stored = taxService.depositToTreasury(tax, currencyId);
        } catch (IllegalAccessException e) {
            stored = false;
        }
        if (!stored && plugin.getLogger() != null) {
            plugin.getLogger().error(String.format(plugin.i18n("economy.log.treasury_write_failed"),
                    String.valueOf(tax), currencyId, plugin.i18n("economy.log.write_contended")));
        }
    }

    private static WhereCondition where(String column, double value) {
        return WhereCondition.builder().column(column).value(value).build();
    }
}
