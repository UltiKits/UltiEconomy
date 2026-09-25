package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.entity.WalletMergeClaimEntity;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.testsupport.InMemoryDataOperator.SimulatedCrash;
import com.ultikits.plugins.economy.testsupport.SteppedOperator;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.exceptions.ErrorCode;
import com.ultikits.ultitools.interfaces.DataOperator;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiEconomy#25, the claim that lets one server at a time run the wallet merge on a database
 * several servers share ({@link MergeClaim}): it is taken only when there is something to merge; a
 * server that does not hold it waits and then starts without merging again; a claim whose holder
 * stopped is taken over after half a minute of no heartbeat, and the merge it left half done is finished
 * once; a holder that lost its claim stops; a storage that refuses the claim refuses the module.
 */
@DisplayName("UltiKits/UltiEconomy#25: the wallet-merge claim")
class MergeClaimTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID NOOR = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID ZED = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    /** A clock that moves only when told to, a sleep that only moves it, and a hook on each sleep. */
    private static final class ManualTiming implements MergeClaim.Timing {
        long now;
        long tickPerRead;
        long slept;
        int sleeps;
        IntConsumer onSleep = n -> { };

        @Override
        public long millis() {
            long t = now;
            now += tickPerRead;
            return t;
        }

        @Override
        public void sleep(long millis) {
            slept += millis;
            now += millis;
            onSleep.accept(++sleeps);
        }
    }

    private static PrimaryWalletMerge merge(EconomyTestWorld world) {
        return new PrimaryWalletMerge(world.plugin, world.accounts, world.balances,
                world.currencies.getPrimaryCurrencyId(), uuid -> "offline-" + uuid.substring(uuid.length() - 1));
    }

    private static MergeClaim claim(EconomyTestWorld world, MergeClaim.Timing timing) {
        return new MergeClaim(world.plugin, () -> world.claims, timing);
    }

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

    private static Map<String, String> accountsOf(EconomyTestWorld world) {
        Map<String, String> out = new TreeMap<>();
        for (PlayerAccountEntity a : world.accounts.getAll()) {
            String previous = out.put(a.getUuid(), a.getCash() + "/" + a.getBank());
            assertThat(previous).as("a second account for %s", a.getUuid()).isNull();
        }
        return out;
    }

    private static List<String> pendingRows(EconomyTestWorld world) {
        List<String> out = new ArrayList<>();
        for (CurrencyBalanceEntity b : world.balances.getAll()) {
            if (!"gems".equals(b.getCurrencyId())) {
                out.add(b.getUuid() + " " + b.getCurrencyId());
            }
        }
        return out;
    }

    @Nested
    @DisplayName("with nothing to merge")
    class NothingToMerge {

        @Test
        @DisplayName("a fresh install or a merged database starts at once and never gets the claim table")
        void noClaimTable() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(ZED, "Zed", 20.0, 0.0);
            world.seedBalance(ZED, "gems", 5.0, 5.0);
            AtomicInteger asked = new AtomicInteger();
            Supplier<DataOperator<WalletMergeClaimEntity>> store = () -> {
                asked.incrementAndGet();
                return world.claims;
            };

            assertThat(new MergeClaim(world.plugin, store, new ManualTiming()).runExclusively(merge(world))).isTrue();

            assertThat(asked).hasValue(0);
            assertThat(world.crash.steps()).isEmpty();
            assertThat(mockingDetails(world.logger).getInvocations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("one server")
    class OneServer {

        @Test
        @DisplayName("takes the claim, merges, and removes the claim")
        void mergesAndReleases() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 1000.0, 50.0);
            ManualTiming timing = new ManualTiming();

            assertThat(claim(world, timing).runExclusively(merge(world))).isTrue();

            assertThat(accountsOf(world)).containsOnly(org.assertj.core.api.Assertions.entry(STEVE.toString(), "1500.0/150.0"));
            assertThat(pendingRows(world)).isEmpty();
            assertThat(world.claims.durable()).isEmpty();
            assertThat(world.crash.steps().get(0)).isEqualTo("insert economy_wallet_merge_claim " + MergeClaim.CLAIM_ID);
            assertThat(timing.slept).isZero();
        }

        @Test
        @DisplayName("control: a claim held by another server is what makes a server wait")
        void controlHeldClaimMakesItWait() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 1000.0, 50.0);
            world.seedClaim("other-server", "1", "2026-09-25T08:00:00Z");
            ManualTiming timing = new ManualTiming();
            timing.onSleep = n -> {
                // The other server finishes on this server's first sleep.
                merge(world).run();
            };

            assertThat(claim(world, timing).runExclusively(merge(world))).isTrue();

            assertThat(timing.slept).isEqualTo(MergeClaim.POLL_MILLIS);
        }
    }

    @Nested
    @DisplayName("a server that does not hold the claim")
    class Waiting {

        @Test
        @DisplayName("waits while the holder's heartbeat changes -- five minutes and more -- makes no write, and starts once the holder has merged")
        void waitsForABeatingHolder() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 1000.0, 50.0);
            world.seedClaim("other-server", "0", "2026-09-25T08:00:00Z");
            List<String> writesBeforeTheHolderMerged = new ArrayList<>();
            ManualTiming timing = new ManualTiming();
            timing.onSleep = n -> {
                if (n % 5 == 0) {
                    // The holder beats every five seconds.
                    world.claims.update("heartbeat", String.valueOf(n), MergeClaim.CLAIM_ID);
                }
                if (n == 400) {
                    for (String step : world.crash.steps()) {
                        if (!step.equals("update economy_wallet_merge_claim " + MergeClaim.CLAIM_ID + " heartbeat")) {
                            writesBeforeTheHolderMerged.add(step);
                        }
                    }
                    // The holder finishes its merge; its claim is still there.
                    merge(world).run();
                }
            };

            assertThat(claim(world, timing).runExclusively(merge(world))).isTrue();

            assertThat(timing.slept).isEqualTo(400 * MergeClaim.POLL_MILLIS);
            assertThat(writesBeforeTheHolderMerged).isEmpty();
            assertThat(accountsOf(world)).containsEntry(STEVE.toString(), "1500.0/150.0");
            assertThat(pendingRows(world)).isEmpty();
            assertThat(logged(world, "warn")).isEmpty();
            String waiting = en("economy.log.wallet_merge.claim_waiting", "2026-09-25T08:00:00Z");
            assertThat(logged(world, "info").stream().filter(waiting::equals)).hasSize(40);
            assertThat(logged(world, "info")).last()
                    .isEqualTo(en("economy.log.wallet_merge.claim_finished_elsewhere"));
            // The holder's claim is the holder's to remove.
            assertThat(world.claims.getById(MergeClaim.CLAIM_ID).getClaimOwner()).isEqualTo("other-server");
        }

        @Test
        @DisplayName("takes over a claim whose heartbeat has not changed for half a minute, and finishes the merge the stopped holder left half done, once")
        void takesOverAStaleClaim() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedAccount(NOOR, "Noor", 10.0, 0.0);
            // The stopped server had marked Steve's row and credited nothing yet.
            world.seedBalance(STEVE, PrimaryWalletMerge.MARKER_PREFIX + "500.0/100.0:1000.0/50.0", 1000.0, 50.0);
            world.seedBalance(NOOR, "coins", 4.0, 6.0);
            world.seedClaim("stopped-server", "7", "2026-09-25T08:00:00Z");
            ManualTiming timing = new ManualTiming();

            assertThat(claim(world, timing).runExclusively(merge(world))).isTrue();

            assertThat(timing.slept).isEqualTo(MergeClaim.STALE_MILLIS);
            assertThat(logged(world, "warn")).containsExactly(
                    en("economy.log.wallet_merge.claim_stale", "2026-09-25T08:00:00Z", MergeClaim.STALE_MILLIS / 1000L));
            assertThat(accountsOf(world)).containsOnly(
                    org.assertj.core.api.Assertions.entry(STEVE.toString(), "1500.0/150.0"),
                    org.assertj.core.api.Assertions.entry(NOOR.toString(), "14.0/6.0"));
            assertThat(pendingRows(world)).isEmpty();
            assertThat(world.claims.durable()).isEmpty();
        }

        @Test
        @DisplayName("tries again when the holder removes its claim between this server's insert and its read")
        void claimReleasedInBetween() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 1000.0, 50.0);
            world.seedClaim("other-server", "3", "2026-09-25T08:00:00Z");
            AtomicInteger reads = new AtomicInteger();
            DataOperator<WalletMergeClaimEntity> claims = new SteppedOperator<>("claims", world.claims, call -> {
                if (call.startsWith("getById") && reads.incrementAndGet() == 1) {
                    // The holder finishes and removes its claim right now.
                    merge(world).run();
                    world.claims.delById(MergeClaim.CLAIM_ID);
                }
            });
            ManualTiming timing = new ManualTiming();

            assertThat(new MergeClaim(world.plugin, () -> claims, timing).runExclusively(merge(world))).isTrue();

            assertThat(logged(world, "error")).isEmpty();
            assertThat(accountsOf(world)).containsEntry(STEVE.toString(), "1500.0/150.0");
            assertThat(world.claims.durable()).isEmpty();
            assertThat(timing.slept).isZero();
        }
    }

    @Nested
    @DisplayName("a server that stops in the middle")
    class Stops {

        private EconomyTestWorld world(String shape) {
            EconomyTestWorld world = "relational".equals(shape) ? EconomyTestWorld.relational() : EconomyTestWorld.cached();
            if ("cached-eager".equals(shape)) {
                world.accounts.setEagerFlush(true);
                world.balances.setEagerFlush(true);
                world.claims.setEagerFlush(true);
            }
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 1000.0, 50.0);
            world.seedBalance(ALEX, "coins", 7.5, 0.0);
            world.seedAccount(NOOR, "Noor", 10.0, 0.0);
            world.seedBalance(NOOR, "coins", 1.0, 2.0);
            world.seedBalance(NOOR, "coins", 3.0, 4.0);
            world.seedAccount(ZED, "Zed", 20.0, 0.0);
            world.seedBalance(ZED, "gems", 5.0, 5.0);
            return world;
        }

        /** A clock that moves a second at every look, so the holder beats while it merges. */
        private ManualTiming ticking() {
            ManualTiming timing = new ManualTiming();
            timing.tickPerRead = 1_000L;
            return timing;
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"relational", "cached", "cached-eager"})
        @DisplayName("at any write -- claim, heartbeat, merge step or release -- and the next server to start finishes the merge once")
        void anyWrite(String shape) {
            EconomyTestWorld control = world(shape);
            assertThat(claim(control, ticking()).runExclusively(merge(control))).isTrue();
            int writes = control.crash.count();
            if ("relational".equals(shape)) {
                // The heartbeat writes are among the crash points (JSON-like writes are steps only as flushes).
                assertThat(control.crash.steps()).anyMatch(s -> s.contains("heartbeat"));
            }

            for (int k = 1; k <= writes; k++) {
                EconomyTestWorld world = world(shape);
                world.crash.armAt(k);
                try {
                    claim(world, ticking()).runExclusively(merge(world));
                } catch (SimulatedCrash e) {
                    // A crash inside the release's own clean-up escapes; the server is dead either way.
                }
                world.restartFromDisk();
                world.crash.disarm();
                ManualTiming next = new ManualTiming();

                boolean started = claim(world, next).runExclusively(merge(world));

                assertThat(started).as("crash at write %d of %d", k, writes).isTrue();
                assertThat(accountsOf(world)).as("crash at write %d of %d", k, writes).containsOnly(
                        org.assertj.core.api.Assertions.entry(STEVE.toString(), "1500.0/150.0"),
                        org.assertj.core.api.Assertions.entry(ALEX.toString(), "7.5/0.0"),
                        org.assertj.core.api.Assertions.entry(NOOR.toString(), "14.0/6.0"),
                        org.assertj.core.api.Assertions.entry(ZED.toString(), "20.0/0.0"));
                assertThat(pendingRows(world)).as("crash at write %d of %d", k, writes).isEmpty();
                assertThat(world.balances.durable()).as("crash at write %d of %d", k, writes).hasSize(1);
                // A claim can be left only by a crash while it was being removed, after the merge had
                // finished; nothing is pending then, so no later start waits for it.
                if (!world.claims.getAll().isEmpty()) {
                    assertThat(next.slept).as("crash at write %d of %d", k, writes).isZero();
                }
                assertThat(next.slept).as("crash at write %d of %d", k, writes).isIn(0L, MergeClaim.STALE_MILLIS);
            }
        }
    }

    @Nested
    @DisplayName("a holder that lost its claim")
    class Lost {

        @Test
        @DisplayName("stops at its next heartbeat, refuses the module, and leaves the new holder's claim alone")
        void stops() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 1000.0, 50.0);
            MergeClaim.Timing timing = new MergeClaim.Timing() {
                private int reads;

                @Override
                public long millis() {
                    if (++reads == 2) {
                        // This server stalled for longer than the stale time, and another took the claim over.
                        world.claims.update("claim_owner", "taker", MergeClaim.CLAIM_ID);
                        return MergeClaim.STALE_MILLIS + MergeClaim.BEAT_MILLIS;
                    }
                    return 0L;
                }

                @Override
                public void sleep(long millis) {
                    throw new AssertionError("no wait expected");
                }
            };

            assertThat(claim(world, timing).runExclusively(merge(world))).isFalse();

            assertThat(logged(world, "error")).containsExactly(en("economy.log.wallet_merge.failed",
                    CatalogueText.text("en", "economy.log.wallet_merge.claim_lost")));
            assertThat(world.claims.getById(MergeClaim.CLAIM_ID).getClaimOwner()).isEqualTo("taker");
            assertThat(accountsOf(world)).containsEntry(STEVE.toString(), "500.0/100.0");
        }
    }

    @Nested
    @DisplayName("a storage that refuses the claim")
    class Refused {

        @Test
        @DisplayName("refuses the module, names the storage error, and merges nothing")
        void refusesTheModule() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 500.0, 100.0);
            world.seedBalance(STEVE, "coins", 1000.0, 50.0);
            @SuppressWarnings("unchecked")
            DataOperator<WalletMergeClaimEntity> broken = mock(DataOperator.class);
            doThrow(new DataAccessException(ErrorCode.DATA_OPERATION_FAILED, "disk full")).when(broken).insert(any());
            when(broken.getById(any())).thenReturn(null);

            assertThat(new MergeClaim(world.plugin, () -> broken, new ManualTiming()).runExclusively(merge(world))).isFalse();

            assertThat(logged(world, "error")).containsExactly(en("economy.log.wallet_merge.failed", "disk full"));
            assertThat(accountsOf(world)).containsEntry(STEVE.toString(), "500.0/100.0");
            assertThat(world.balanceRows("coins")).hasSize(1);
        }
    }
}
