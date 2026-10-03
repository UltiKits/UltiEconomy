package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.entity.WalletMergeClaimEntity;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.testsupport.InMemoryDataOperator;
import com.ultikits.plugins.economy.testsupport.Lockstep;
import com.ultikits.plugins.economy.testsupport.SteppedOperator;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * UltiKits/UltiEconomy#25, servers that share one database: when several servers upgraded to this
 * version start at the same moment, each runs the one-time wallet merge at load. Whatever order their
 * storage calls interleave in, every second wallet must be added to its account exactly once, and no
 * server may start (let players and Vault at the wallets) while another server's merge is unfinished.
 *
 * <p>Measured before {@link MergeClaim} existed, on the merge alone: 121 of 300 interleavings of two
 * servers and 145 of 300 of three went wrong -- an account created twice, an account credited twice,
 * rows left unmerged, a server starting before the merge was finished.
 *
 * <p>Each seed is one interleaving of the servers' storage calls ({@link Lockstep}); the test runs
 * several hundred of them over one shared, relational store (SQLite's or MySQL's shape: every write is
 * an auto-committed statement, durable and visible to the other servers when it returns).
 */
@DisplayName("UltiKits/UltiEconomy#25: several servers starting at once on one database merge each wallet once")
class PrimaryWalletMergeConcurrencyTest {

    /** Interleavings per server count; `-Dultieconomy.mergeSeeds=N` explores more. */
    private static final int SEEDS = Integer.getInteger("ultieconomy.mergeSeeds", 300);

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID NOOR = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID ZED = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    /** The accounts after the merge, whatever the interleaving: uuid -> "cash/bank" (one line per row). */
    private static final Map<String, List<String>> EXPECTED_ACCOUNTS = new TreeMap<>();

    static {
        EXPECTED_ACCOUNTS.put(STEVE.toString(), list("1500.0/150.0"));
        EXPECTED_ACCOUNTS.put(ALEX.toString(), list("7.5/0.0"));
        EXPECTED_ACCOUNTS.put(NOOR.toString(), list("14.0/6.0"));
        EXPECTED_ACCOUNTS.put(ZED.toString(), list("20.0/0.0"));
    }

    private static List<String> list(String... values) {
        List<String> out = new ArrayList<>();
        for (String v : values) {
            out.add(v);
        }
        return out;
    }

    /** The database every server shares. */
    private static final class SharedDatabase {
        final InMemoryDataOperator<PlayerAccountEntity> accounts =
                InMemoryDataOperator.relational("economy_accounts", PlayerAccountEntity.class, null);
        final InMemoryDataOperator<CurrencyBalanceEntity> balances =
                InMemoryDataOperator.relational("currency_balances", CurrencyBalanceEntity.class, null);
        final InMemoryDataOperator<WalletMergeClaimEntity> claims =
                InMemoryDataOperator.relational("economy_wallet_merge_claim", WalletMergeClaimEntity.class, null);

        /**
         * @param leftByStoppedServer whether a server that stopped in the middle of its merge left its
         *                            claim, and Steve's rows marked but not yet added, behind
         */
        SharedDatabase(boolean leftByStoppedServer) {
            accounts.seed(account(STEVE, "Steve", 500.0, 100.0));
            if (leftByStoppedServer) {
                WalletMergeClaimEntity claim = new WalletMergeClaimEntity("stopped-server", "7", "2026-09-25T08:00:00Z");
                claim.setId(MergeClaim.CLAIM_ID);
                claims.seed(claim);
                balances.seed(balance(STEVE, PrimaryWalletMerge.MARKER_PREFIX + "500.0/100.0:1000.0/50.0", 1000.0, 50.0));
            } else {
                balances.seed(balance(STEVE, "coins", 1000.0, 50.0));
            }
            // Alex has only a second wallet: the merge creates the account.
            balances.seed(balance(ALEX, "coins", 7.5, 0.0));
            // Noor has two second-wallet rows.
            accounts.seed(account(NOOR, "Noor", 10.0, 0.0));
            balances.seed(balance(NOOR, "coins", 1.0, 2.0));
            balances.seed(balance(NOOR, "coins", 3.0, 4.0));
            // Zed's other currency is not the merge's business.
            accounts.seed(account(ZED, "Zed", 20.0, 0.0));
            balances.seed(balance(ZED, "gems", 5.0, 5.0));
        }

        private static PlayerAccountEntity account(UUID uuid, String name, double cash, double bank) {
            return PlayerAccountEntity.builder().uuid(uuid.toString()).playerName(name).cash(cash).bank(bank).build();
        }

        private static CurrencyBalanceEntity balance(UUID uuid, String currency, double cash, double bank) {
            return CurrencyBalanceEntity.builder().uuid(uuid.toString()).currencyId(currency).cash(cash).bank(bank).build();
        }

        Map<String, List<String>> accountsByUuid() {
            Map<String, List<String>> out = new TreeMap<>();
            for (PlayerAccountEntity a : accounts.getAll()) {
                out.computeIfAbsent(a.getUuid(), k -> new ArrayList<>()).add(a.getCash() + "/" + a.getBank());
            }
            return out;
        }

        /** Rows the merge still has to deal with: the primary currency's, or a merge marker. */
        List<String> pendingRows() {
            List<String> out = new ArrayList<>();
            for (CurrencyBalanceEntity b : balances.getAll()) {
                String id = b.getCurrencyId();
                if ("coins".equals(id) || (id != null && id.startsWith(PrimaryWalletMerge.MARKER_PREFIX))) {
                    out.add(b.getUuid().substring(b.getUuid().length() - 1) + " " + id + " " + b.getCash() + "/" + b.getBank());
                }
            }
            return out;
        }
    }

    private static UltiToolsPlugin plugin() {
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        lenient().when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
        return plugin;
    }

    /**
     * What one server does at load, as {@code UltiEconomy.registerSelf} does it: merge under the claim.
     * Also checks, at the moment each of the server's writes to the wallets happens, that the claim is
     * this server's -- mutual exclusion itself, not only its outcome -- and records any write that is not
     * in {@code notHolding}.
     */
    private static boolean start(String server, SharedDatabase db, Lockstep lockstep, List<String> notHolding) {
        UltiToolsPlugin plugin = plugin();
        String[] token = new String[1];
        Consumer<String> walletCalls = call -> {
            lockstep.stepper(server).accept(call);
            if (call.startsWith("insert") || call.startsWith("update") || call.startsWith("del")) {
                WalletMergeClaimEntity held = db.claims.getById(MergeClaim.CLAIM_ID);
                String holder = held == null ? null : held.getClaimOwner();
                if (holder == null || !holder.equals(token[0])) {
                    notHolding.add(server + " made \"" + call + "\" while the claim was " + holder);
                }
            }
        };
        PrimaryWalletMerge merge = new PrimaryWalletMerge(plugin,
                new SteppedOperator<>("economy_accounts", db.accounts, walletCalls),
                new SteppedOperator<>("currency_balances", db.balances, walletCalls),
                "coins", uuid -> "offline-" + uuid.substring(uuid.length() - 1));
        MergeClaim.Timing virtual = new MergeClaim.Timing() {
            @Override
            public long millis() {
                return lockstep.now(server);
            }

            @Override
            public void sleep(long millis) {
                lockstep.sleep(server, millis);
            }
        };
        return new MergeClaim(plugin,
                () -> new SteppedOperator<>("economy_wallet_merge_claim", db.claims, lockstep.stepper(server))
                        .onInsert(claim -> token[0] = claim.getClaimOwner()),
                virtual).runExclusively(merge);
    }

    @ParameterizedTest(name = "{0} servers")
    @ValueSource(ints = {2, 3})
    @DisplayName("every interleaving of the servers' storage calls adds each second wallet exactly once, and no server starts before the merge is done")
    void everyInterleavingMergesOnce(int servers) {
        everyInterleaving(servers, false);
    }

    @ParameterizedTest(name = "{0} servers")
    @ValueSource(ints = {2, 3})
    @DisplayName("after a server stopped in the middle of its merge, the servers starting next finish it once, whatever the interleaving")
    void everyInterleavingFinishesAStoppedServersMergeOnce(int servers) {
        everyInterleaving(servers, true);
    }

    @ParameterizedTest(name = "{0} servers")
    @ValueSource(ints = {2, 3})
    @DisplayName("on a slow database -- every storage call taking 2 s, so one merge takes minutes -- the waiting servers never take the claim from the server merging")
    void everyInterleavingOnASlowDatabase(int servers) {
        everyInterleaving(servers, false, 2_000L);
    }

    private void everyInterleaving(int servers, boolean leftByStoppedServer) {
        everyInterleaving(servers, leftByStoppedServer, Lockstep.CALL_COST_MS);
    }

    private void everyInterleaving(int servers, boolean leftByStoppedServer, long callCostMs) {
        List<String> failures = new ArrayList<>();
        Map<String, Integer> kinds = new TreeMap<>();
        for (long seed = 1; seed <= SEEDS; seed++) {
            SharedDatabase db = new SharedDatabase(leftByStoppedServer);
            Lockstep lockstep = new Lockstep(seed, callCostMs);
            Map<String, List<String>> startedWithPending = new TreeMap<>();
            List<String> notHolding = Collections.synchronizedList(new ArrayList<String>());
            Map<String, Callable<?>> starts = new LinkedHashMap<>();
            for (int i = 0; i < servers; i++) {
                String server = String.valueOf((char) ('A' + i));
                starts.put(server, () -> {
                    boolean started = start(server, db, lockstep, notHolding);
                    if (started) {
                        // What a player or Vault would see the moment this server goes on loading.
                        startedWithPending.put(server, db.pendingRows());
                    }
                    return started;
                });
            }

            Map<String, Object> results = lockstep.run(starts);

            List<String> wrong = new ArrayList<>();
            for (Map.Entry<String, Object> r : results.entrySet()) {
                if (!Boolean.TRUE.equals(r.getValue())) {
                    wrong.add("server " + r.getKey() + " returned " + r.getValue());
                    count(kinds, "a server did not start");
                }
            }
            for (Map.Entry<String, List<String>> s : startedWithPending.entrySet()) {
                if (!s.getValue().isEmpty()) {
                    wrong.add("server " + s.getKey() + " started while these rows were unmerged: " + s.getValue());
                    count(kinds, "a server started before the merge was finished");
                }
            }
            if (!notHolding.isEmpty()) {
                wrong.add("wallet writes by a server not holding the claim: " + notHolding);
                count(kinds, "a wallet write by a server not holding the claim");
            }
            Map<String, List<String>> accounts = db.accountsByUuid();
            if (!accounts.equals(EXPECTED_ACCOUNTS)) {
                wrong.add("accounts " + accounts + ", expected " + EXPECTED_ACCOUNTS);
                for (Map.Entry<String, List<String>> a : accounts.entrySet()) {
                    List<String> expected = EXPECTED_ACCOUNTS.get(a.getKey());
                    if (a.getValue().size() > 1) {
                        count(kinds, "an account created twice (its second wallet paid out twice)");
                    } else if (!a.getValue().equals(expected) && credit(a.getValue().get(0)) > credit(expected.get(0))) {
                        count(kinds, "an account credited more than once");
                    } else if (!a.getValue().equals(expected)) {
                        count(kinds, "an account short of its second wallet");
                    }
                }
            }
            if (!db.pendingRows().isEmpty()) {
                wrong.add("rows left " + db.pendingRows());
                count(kinds, "second-wallet rows left unmerged");
            }
            if (!db.claims.getAll().isEmpty()) {
                wrong.add("claim left " + db.claims.getAll());
                count(kinds, "a claim left behind");
            }
            if (db.balances.getAll().size() - db.pendingRows().size() != 1) {
                wrong.add("currency_balances holds " + db.balances.getAll().size() + " rows");
                count(kinds, "another currency's row changed");
            }
            if (!wrong.isEmpty()) {
                List<String> trace = lockstep.trace();
                failures.add("seed " + seed + ": " + wrong + "\n    last calls: "
                        + trace.subList(Math.max(0, trace.size() - 40), trace.size()));
            }
        }
        assertThat(failures.size())
                .as("%d of %d interleavings went wrong (%s); the first:%n%s", failures.size(), SEEDS, kinds,
                        String.join("\n", failures.subList(0, Math.min(2, failures.size()))))
                .isZero();
    }

    // ==================== UltiKits/UltiEconomy#39: a holder that stalls past the takeover ====================

    /**
     * The window {@link MergeClaim} cannot close on its own (UltiKits/UltiEconomy#39): holder A passes its
     * claim check right before an account write, then stalls for longer than the 30-second takeover. Server
     * B takes the claim over and finishes the merge, B's server starts, and a player spends there. Then A
     * resumes and makes the write it had prepared. The stall is driven through the merge's own claim seam
     * ({@code withClaim}: the check before each account write), not by sleeping, and every later check of
     * A's passes -- the worst case, a holder that never notices it lost the claim. Maintainer decision
     * 2026-09-29 (question 4, option 1): every account write is conditioned on the balance the merge read,
     * so A's stale write does not apply; A reads again and decides again.
     */
    @Test
    @DisplayName("UltiEconomy#39: a holder that stalls past the takeover and resumes after the new holder merged credits nothing a second time and undoes no later spend")
    void aStalledHolderResumingAfterTheTakeoverCreditsNothingTwice() {
        SharedDatabase db = new SharedDatabase(false);
        PrimaryWalletMerge b = merge(db);
        AtomicBoolean stalled = new AtomicBoolean();
        PrimaryWalletMerge a = merge(db).withClaim(() -> { }, () -> {
            if (stalled.compareAndSet(false, true)) {
                // A's check passed; A stalls past the takeover. B takes the claim over and merges everything.
                assertThat(b.run()).isTrue();
                // B's server starts and Steve spends 200 of his merged cash there.
                PlayerAccountEntity steve = db.accounts.getAll(where("uuid", STEVE.toString())).get(0);
                steve.setCash(steve.getCash() - 200.0);
                assertThat(db.accounts.updateCounted(steve)).isEqualTo(1);
            }
        });

        assertThat(a.run()).isTrue();

        assertThat(stalled).as("the stall happened, so the interleaving was exercised").isTrue();
        Map<String, List<String>> expected = new TreeMap<>(EXPECTED_ACCOUNTS);
        expected.put(STEVE.toString(), list("1300.0/150.0"));
        assertThat(db.accountsByUuid()).as("every account credited exactly once, Steve's later spend kept").isEqualTo(expected);
        assertThat(db.pendingRows()).isEmpty();
    }

    /**
     * The other interleaving the issue names: A resumes between B's first and second account write, runs
     * to its end while B is stopped there, and then B goes on. Whichever server's write reaches a row
     * first, the other's must not apply on top of it, and no account may be created twice.
     */
    @Test
    @Timeout(30)
    @DisplayName("UltiEconomy#39: a holder that resumes between the new holder's first and second account write leaves every account credited exactly once")
    void aStalledHolderResumingBetweenTheNewHoldersWritesCreditsEachAccountOnce() throws Exception {
        SharedDatabase db = new SharedDatabase(false);
        CountDownLatch aStalled = new CountDownLatch(1);
        CountDownLatch resumeA = new CountDownLatch(1);
        CountDownLatch aDone = new CountDownLatch(1);
        AtomicBoolean aHasStalled = new AtomicBoolean();
        AtomicInteger bChecks = new AtomicInteger();
        PrimaryWalletMerge a = merge(db).withClaim(() -> { }, () -> {
            if (aHasStalled.compareAndSet(false, true)) {
                aStalled.countDown();
                await(resumeA);
            }
        });
        PrimaryWalletMerge b = merge(db).withClaim(() -> { }, () -> {
            if (bChecks.incrementAndGet() == 2) {
                // B has made its first account write; A resumes now and runs to its end, then B goes on.
                resumeA.countDown();
                await(aDone);
            }
        });
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> aResult = threads.submit(() -> {
                try {
                    return a.run();
                } finally {
                    aDone.countDown();
                }
            });
            await(aStalled);
            Future<Boolean> bResult = threads.submit(b::run);

            assertThat(aResult.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(bResult.get(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            threads.shutdownNow();
        }

        assertThat(bChecks.get()).as("B reached its second account write, so the interleaving was exercised").isGreaterThanOrEqualTo(2);
        assertThat(db.accountsByUuid()).as("every account credited exactly once, none created twice").isEqualTo(EXPECTED_ACCOUNTS);
        assertThat(db.pendingRows()).isEmpty();
    }

    /** One server's merge over the shared database, with no claim (the tests drive the claim seam themselves). */
    private static PrimaryWalletMerge merge(SharedDatabase db) {
        return new PrimaryWalletMerge(plugin(), db.accounts, db.balances, "coins",
                uuid -> "offline-" + uuid.substring(uuid.length() - 1));
    }

    private static com.ultikits.ultitools.entities.WhereCondition where(String column, String value) {
        return com.ultikits.ultitools.entities.WhereCondition.builder().column(column).value(value).build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the interleaving did not arrive within 20 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static double credit(String cashSlashBank) {
        String[] parts = cashSlashBank.split("/");
        return Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]);
    }

    private static void count(Map<String, Integer> kinds, String kind) {
        kinds.merge(kind, 1, Integer::sum);
    }
}
