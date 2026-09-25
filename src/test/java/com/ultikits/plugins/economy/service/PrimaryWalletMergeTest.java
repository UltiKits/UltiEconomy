package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.testsupport.InMemoryDataOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.invocation.Invocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

/**
 * UltiKits/UltiEconomy#25, the one-time merge (maintainer decision 2026-09-24, confirmed 2026-09-25:
 * keep the account wallet, merge the second wallet into it in full): on the first start after the
 * upgrade, each player's primary-currency {@code currency_balances} row -- cash and bank -- is added
 * once to their account, logged per player with a total, and removed. Re-running it changes nothing,
 * and a crash between any two of its writes, on either kind of storage, leaves a state from which
 * the next start produces the same balances: never more, never less.
 */
@DisplayName("UltiKits/UltiEconomy#25: merging the second primary-currency wallet once")
class PrimaryWalletMergeTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID NOOR = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID ZED = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    private static PrimaryWalletMerge merge(EconomyTestWorld world) {
        return new PrimaryWalletMerge(world.plugin, world.accounts, world.balances,
                world.currencies.getPrimaryCurrencyId(), uuid -> "offline-" + uuid.substring(uuid.length() - 1));
    }

    /** Every line logged at {@code level} ("info", "warn", "error"), in order. */
    private static List<String> logged(EconomyTestWorld world, String level) {
        List<String> out = new ArrayList<>();
        for (Invocation i : mockingDetails(world.logger).getInvocations()) {
            if (i.getMethod().getName().equals(level)) {
                for (Object a : i.getArguments()) {
                    if (a instanceof String) {
                        out.add((String) a);
                    }
                }
            }
        }
        return out;
    }

    private static String en(String key, Object... args) {
        return String.format(CatalogueText.text("en", key), args);
    }

    /** uuid -> "cash/bank" of every account, sorted. */
    private static Map<String, String> accountsOf(EconomyTestWorld world) {
        Map<String, String> out = new TreeMap<>();
        for (PlayerAccountEntity a : world.accounts.getAll()) {
            out.put(a.getUuid(), a.getPlayerName() + " " + a.getCash() + "/" + a.getBank());
        }
        return out;
    }

    /** id -> every field of every currency_balances row, sorted. */
    private static Map<String, String> balancesOf(EconomyTestWorld world) {
        Map<String, String> out = new TreeMap<>();
        for (CurrencyBalanceEntity b : world.balances.getAll()) {
            out.put(b.getId(), b.getUuid() + " " + b.getCurrencyId() + " " + b.getCash() + "/" + b.getBank());
        }
        return out;
    }

    @Nested
    @DisplayName("what one start does")
    class OneStart {

        @Test
        @DisplayName("cash and bank of the second wallet are added to the account, the row is removed, and the player and the total are logged")
        void mergesCashAndBank() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 1000.0, 50.0);

            assertThat(merge(world).run()).isTrue();

            PlayerAccountEntity steve = world.account(STEVE);
            assertThat(steve.getCash()).isEqualTo(1500.0);
            assertThat(steve.getBank()).isEqualTo(150.0);
            assertThat(world.balanceRows("coins")).isEmpty();
            assertThat(world.balances.getAll()).isEmpty();
            assertThat(logged(world, "info")).containsExactly(
                    en("economy.log.wallet_merge.player", "1000.0", "50.0", "Steve", "1500.0", "150.0"),
                    en("economy.log.wallet_merge.total", 1, "1000.0", "50.0", 0, 0));
            assertThat(logged(world, "warn")).isEmpty();
        }

        @Test
        @DisplayName("the arithmetic on a sample: 1000.95 and 1588 merged, an untouched starting grant merged, nobody's balance falls")
        void sampleArithmetic() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 1200.0, 400.0);
            world.seedAccount(ALEX, "Alex", 0.5, 0.0);
            world.seedAccount(NOOR, "Noor", 1000.0, 0.0);
            world.seedBalance(STEVE, "coins", 1588.0, 0.0);
            world.seedBalance(ALEX, "coins", 1000.95, 0.0);
            world.seedBalance(NOOR, "coins", 1000.0, 0.0);

            assertThat(merge(world).run()).isTrue();

            assertThat(world.account(STEVE).getCash()).isEqualTo(2788.0);
            assertThat(world.account(STEVE).getBank()).isEqualTo(400.0);
            assertThat(world.account(ALEX).getCash()).isEqualTo(0.5 + 1000.95);
            assertThat(world.account(NOOR).getCash()).isEqualTo(2000.0);
            assertThat(logged(world, "info")).last().isEqualTo(
                    en("economy.log.wallet_merge.total", 3, "3588.95", "0", 0, 0));
        }

        @Test
        @DisplayName("a player with only an account: nothing changes and nothing is logged")
        void accountOnly() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);

            assertThat(merge(world).run()).isTrue();

            assertThat(accountsOf(world)).containsOnly(
                    org.assertj.core.api.Assertions.entry(STEVE.toString(), "Steve 500.0/100.0"));
            assertThat(logged(world, "info")).isEmpty();
        }

        @Test
        @DisplayName("a player with only the second wallet gets an account holding exactly that money, named from the server")
        void secondWalletOnly() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedBalance(ZED, "coins", 300.0, 20.0);

            assertThat(merge(world).run()).isTrue();

            PlayerAccountEntity zed = world.account(ZED);
            assertThat(zed).isNotNull();
            assertThat(zed.getPlayerName()).isEqualTo("offline-d");
            assertThat(zed.getCash()).isEqualTo(300.0);
            assertThat(zed.getBank()).isEqualTo(20.0);
            assertThat(world.balances.getAll()).isEmpty();
        }

        @Test
        @DisplayName("an empty second wallet is removed without touching the account")
        void zeroSecondWallet() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 0.0, 0.0);

            assertThat(merge(world).run()).isTrue();

            assertThat(world.account(STEVE).getCash()).isEqualTo(500.0);
            assertThat(world.account(STEVE).getBank()).isEqualTo(100.0);
            assertThat(world.balances.getAll()).isEmpty();
            assertThat(logged(world, "info")).containsExactly(
                    en("economy.log.wallet_merge.total", 0, "0", "0", 1, 0));
        }

        @Test
        @DisplayName("a negative amount in the second wallet is not taken from the account, and is logged")
        void negativeSecondWallet() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", -50.0, 30.0);

            assertThat(merge(world).run()).isTrue();

            assertThat(world.account(STEVE).getCash()).isEqualTo(500.0);
            assertThat(world.account(STEVE).getBank()).isEqualTo(130.0);
            assertThat(world.balances.getAll()).isEmpty();
            assertThat(logged(world, "warn")).containsExactly(
                    en("economy.log.wallet_merge.negative_cash", "Steve", "-50.0"));
        }

        @Test
        @DisplayName("a negative bank balance in the second wallet is not taken either, and is logged")
        void negativeSecondWalletBank() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 0.0, -30.0);

            assertThat(merge(world).run()).isTrue();

            assertThat(world.account(STEVE).getCash()).isEqualTo(500.0);
            assertThat(world.account(STEVE).getBank()).isEqualTo(100.0);
            assertThat(world.balances.getAll()).isEmpty();
            assertThat(logged(world, "warn")).containsExactly(
                    en("economy.log.wallet_merge.negative_bank", "Steve", "-30.0"));
        }

        @Test
        @DisplayName("two second-wallet rows for one player are added once each, in one account write")
        void duplicateRows() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 10.0, 0.0);
            world.seedBalance(STEVE, "coins", 1000.0, 0.0);
            world.seedBalance(STEVE, "coins", 5.0, 7.0);

            assertThat(merge(world).run()).isTrue();

            assertThat(world.account(STEVE).getCash()).isEqualTo(1015.0);
            assertThat(world.account(STEVE).getBank()).isEqualTo(7.0);
            assertThat(world.balances.getAll()).isEmpty();
        }

        @Test
        @DisplayName("rows of every other currency are left exactly as they were")
        void otherCurrenciesUntouched() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 10.0, 0.0);
            world.seedBalance(STEVE, "coins", 1000.0, 0.0);
            world.seedBalance(STEVE, "gems", 3617.0, 350.0);
            world.seedBalance(ALEX, "gems", 1.0, 0.0);
            Map<String, String> gemsBefore = new TreeMap<>(balancesOf(world));
            gemsBefore.values().removeIf(v -> !v.contains(" gems "));

            assertThat(merge(world).run()).isTrue();

            assertThat(balancesOf(world)).isEqualTo(gemsBefore);
        }
    }

    @Nested
    @DisplayName("a second start")
    class SecondStart {

        @Test
        @DisplayName("finds nothing to merge: no balance moves and no merge line is logged")
        void secondStartIsANoOp() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 1000.0, 50.0);
            world.seedBalance(STEVE, "gems", 4.0, 0.0);
            assertThat(merge(world).run()).isTrue();
            Map<String, String> accounts = accountsOf(world);
            Map<String, String> balances = balancesOf(world);
            int linesAfterFirst = logged(world, "info").size();

            assertThat(merge(world).run()).isTrue();

            assertThat(accountsOf(world)).isEqualTo(accounts);
            assertThat(balancesOf(world)).isEqualTo(balances);
            assertThat(logged(world, "info")).hasSize(linesAfterFirst);
        }
    }

    @Nested
    @DisplayName("a crash between any two writes")
    class Crash {

        /**
         * Four players covering every case: both wallets, three duplicate rows (so a resumed merge has
         * more than one unmarked row left; gate-1 WR-01), an empty wallet, only a second wallet.
         */
        private void seed(EconomyTestWorld world) {
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 1000.0, 50.0);
            world.seedAccount(ALEX, "Alex", 10.0, 0.0);
            world.seedBalance(ALEX, "coins", 1000.0, 0.0);
            world.seedBalance(ALEX, "coins", 5.0, 7.0);
            world.seedBalance(ALEX, "coins", 3.0, 0.0);
            world.seedAccount(NOOR, "Noor", 1.0, 2.0);
            world.seedBalance(NOOR, "coins", 0.0, 0.0);
            world.seedBalance(ZED, "coins", 300.0, 20.0);
            world.seedBalance(STEVE, "gems", 3617.0, 350.0);
        }

        private EconomyTestWorld world(String backend) {
            EconomyTestWorld world = "relational".equals(backend) ? EconomyTestWorld.relational() : EconomyTestWorld.cached();
            // "cached-eager": the framework's background flush writes every change at the worst moment.
            world.accounts.setEagerFlush("cached-eager".equals(backend));
            world.balances.setEagerFlush("cached-eager".equals(backend));
            seed(world);
            // Seeding writes straight to disk; nothing is pending.
            return world;
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"relational", "cached", "cached-eager"})
        @DisplayName("the next start ends with exactly the balances an uninterrupted merge produces")
        void crashAtEveryStepThenRestart(String backend) {
            EconomyTestWorld reference = world(backend);
            assertThat(merge(reference).run()).isTrue();
            reference.accounts.flush();
            reference.balances.flush();
            reference.balances.gc();
            Map<String, String> expectedAccounts = accountsOf(reference);
            assertThat(expectedAccounts).containsOnly(
                    org.assertj.core.api.Assertions.entry(STEVE.toString(), "Steve 1500.0/150.0"),
                    org.assertj.core.api.Assertions.entry(ALEX.toString(), "Alex 1018.0/7.0"),
                    org.assertj.core.api.Assertions.entry(NOOR.toString(), "Noor 1.0/2.0"),
                    org.assertj.core.api.Assertions.entry(ZED.toString(), "offline-d 300.0/20.0"));

            EconomyTestWorld counting = world(backend);
            counting.crash.armAt(0);
            assertThat(merge(counting).run()).isTrue();
            int steps = counting.crash.count();
            assertThat(steps).as("durable write steps in one merge on " + backend).isGreaterThan(4);

            for (int k = 1; k <= steps; k++) {
                EconomyTestWorld crashed = world(backend);
                crashed.crash.armAt(k);
                assertThat(merge(crashed).run()).as("a crash at step %d is reported as a failed start", k).isFalse();
                crashed.crash.disarm();
                crashed.accounts.restartFromDisk();
                crashed.balances.restartFromDisk();

                assertThat(merge(crashed).run()).as("restart after a crash at step %d", k).isTrue();
                crashed.accounts.flush();
                crashed.balances.flush();
                crashed.balances.gc();
                crashed.accounts.restartFromDisk();
                crashed.balances.restartFromDisk();

                assertThat(accountsOf(crashed)).as("accounts after a crash at step %d of %d on %s", k, steps, backend)
                        .isEqualTo(expectedAccounts);
                assertThat(crashed.balanceRows("coins")).as("second wallets after a crash at step %d", k).isEmpty();
                assertThat(balancesOf(crashed).values()).as("rows left after a crash at step %d", k)
                        .containsExactly(STEVE + " gems 3617.0/350.0");
            }
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"relational", "cached", "cached-eager"})
        @DisplayName("a second crash while the next start is finishing the first one's merge still ends exactly right")
        void crashTwiceThenRestart(String backend) {
            EconomyTestWorld reference = world(backend);
            assertThat(merge(reference).run()).isTrue();
            Map<String, String> expectedAccounts = accountsOf(reference);

            EconomyTestWorld counting = world(backend);
            counting.crash.armAt(0);
            merge(counting).run();
            int steps = counting.crash.count();

            int recoveriesChecked = 0;
            for (int first = 1; first <= steps; first++) {
                // How many durable steps the start after this first crash takes.
                EconomyTestWorld probe = world(backend);
                probe.crash.armAt(first);
                merge(probe).run();
                probe.accounts.restartFromDisk();
                probe.balances.restartFromDisk();
                probe.crash.armAt(0);
                merge(probe).run();
                int recoverySteps = probe.crash.count();

                for (int second = 1; second <= recoverySteps; second++) {
                    EconomyTestWorld crashed = world(backend);
                    crashed.crash.armAt(first);
                    merge(crashed).run();
                    crashed.accounts.restartFromDisk();
                    crashed.balances.restartFromDisk();
                    crashed.crash.armAt(second);
                    merge(crashed).run();
                    crashed.crash.disarm();
                    crashed.accounts.restartFromDisk();
                    crashed.balances.restartFromDisk();

                    assertThat(merge(crashed).run()).isTrue();
                    crashed.accounts.flush();
                    crashed.balances.flush();
                    crashed.balances.gc();
                    crashed.accounts.restartFromDisk();
                    crashed.balances.restartFromDisk();
                    assertThat(accountsOf(crashed))
                            .as("accounts after crashes at step %d, then at step %d of the next start, on %s",
                                    first, second, backend)
                            .isEqualTo(expectedAccounts);
                    assertThat(crashed.balanceRows("coins")).isEmpty();
                    recoveriesChecked++;
                }
            }
            assertThat(recoveriesChecked).as("double-crash cases checked on " + backend).isGreaterThan(0);
        }

        @Test
        @DisplayName("a marker stays short enough for a VARCHAR(255) column, however large the amounts (gate-1 IN-04)")
        void markerFitsTheColumn() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 1.0e200, 1.0e200);
            world.seedBalance(STEVE, "coins", 1.0e200, 1.0e200);
            world.seedBalance(STEVE, "coins", 1.0e200, 1.0e200);
            // Crash after the markers are written: step 1 and 2 mark, step 3 would credit.
            world.crash.armAt(3);

            assertThat(merge(world).run()).isFalse();

            for (CurrencyBalanceEntity row : world.balances.durable()) {
                assertThat(row.getCurrencyId()).startsWith(PrimaryWalletMerge.MARKER_PREFIX);
                assertThat(row.getCurrencyId().length()).as(row.getCurrencyId()).isLessThanOrEqualTo(255);
            }
            world.crash.disarm();
            assertThat(merge(world).run()).isTrue();
            assertThat(world.account(STEVE).getCash()).isEqualTo(1.0e200 + 2.0e200);
        }

        @Test
        @DisplayName("JSON-like storage: removing an empty second wallet reaches disk, so a restart does not bring it back (Codex round 1)")
        void emptyWalletRemovalIsDurableOnCachedStorage() {
            EconomyTestWorld world = EconomyTestWorld.cached();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 0.0, 0.0);

            assertThat(merge(world).run()).isTrue();
            world.balances.restartFromDisk();

            assertThat(world.balanceRows("coins")).isEmpty();
            assertThat(merge(world).run()).isTrue();
            assertThat(logged(world, "info")).hasSize(1);
        }

        @Test
        @DisplayName("JSON-like storage: the merge flushes its markers before any account write can reach disk")
        void cachedStorageIsFlushedByTheMerge() {
            EconomyTestWorld world = world("cached");

            assertThat(merge(world).run()).isTrue();
            world.accounts.restartFromDisk();
            world.balances.restartFromDisk();

            // What a restart right after the merge reads, without any background flush.
            assertThat(accountsOf(world)).containsEntry(STEVE.toString(), "Steve 1500.0/150.0");
            assertThat(world.balanceRows("coins")).isEmpty();
        }
    }

    @Nested
    @DisplayName("an account that changed after a merge started")
    class Unsettled {

        @Test
        @DisplayName("is left alone, the row is kept, and an operator is told")
        void changedAccountIsNotGuessed() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 777.0, 0.0);
            // A merge that started when the account held 500/100, adding 1000/50, then the account
            // changed (for example under an older version) before the merge could finish.
            world.seedBalance(STEVE, PrimaryWalletMerge.MARKER_PREFIX + "500.0/100.0:1000.0/50.0", 1000.0, 50.0);

            assertThat(merge(world).run()).isTrue();

            assertThat(world.account(STEVE).getCash()).isEqualTo(777.0);
            assertThat(world.account(STEVE).getBank()).isEqualTo(0.0);
            assertThat(world.balances.getAll()).hasSize(1);
            assertThat(logged(world, "warn")).containsExactly(en("economy.log.wallet_merge.unsettled",
                    "Steve", "1000.0", "50.0", PrimaryWalletMerge.MARKER_PREFIX + "500.0/100.0:1000.0/50.0"));
            assertThat(logged(world, "info")).containsExactly(
                    en("economy.log.wallet_merge.total", 0, "0", "0", 0, 1));
        }
    }

    @Nested
    @DisplayName("a storage failure")
    class Failure {

        @Test
        @DisplayName("is reported through the catalogue and the merge says it did not finish")
        void failureIsReported() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 1000.0, 50.0);
            world.crash.armAt(1);

            assertThat(merge(world).run()).isFalse();

            assertThat(logged(world, "error")).hasSize(1);
            assertThat(logged(world, "error").get(0)).startsWith(
                    en("economy.log.wallet_merge.failed", "X").substring(0, 40));
            assertThat(world.account(STEVE).getCash()).isEqualTo(500.0);
        }

        @Test
        @DisplayName("control: the in-memory store sees a write the merge makes")
        void storeSeesWrites() {
            InMemoryDataOperator<PlayerAccountEntity> op =
                    InMemoryDataOperator.relational("a", PlayerAccountEntity.class, null);
            op.insert(PlayerAccountEntity.builder().uuid("u").cash(1).build());
            assertThat(op.durable()).hasSize(1);
        }
    }
}
