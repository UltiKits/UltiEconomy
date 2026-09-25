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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;

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

    /** What one server does at load, as {@code UltiEconomy.registerSelf} does it: merge under the claim. */
    private static boolean start(String server, SharedDatabase db, Lockstep lockstep) {
        UltiToolsPlugin plugin = plugin();
        PrimaryWalletMerge merge = new PrimaryWalletMerge(plugin,
                new SteppedOperator<>("economy_accounts", db.accounts, lockstep.stepper(server)),
                new SteppedOperator<>("currency_balances", db.balances, lockstep.stepper(server)),
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
                () -> new SteppedOperator<>("economy_wallet_merge_claim", db.claims, lockstep.stepper(server)),
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
            Map<String, Callable<?>> starts = new LinkedHashMap<>();
            for (int i = 0; i < servers; i++) {
                String server = String.valueOf((char) ('A' + i));
                starts.put(server, () -> {
                    boolean started = start(server, db, lockstep);
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

    private static double credit(String cashSlashBank) {
        String[] parts = cashSlashBank.split("/");
        return Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]);
    }

    private static void count(Map<String, Integer> kinds, String kind) {
        kinds.merge(kind, 1, Integer::sum);
    }
}
