package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.entity.WalletMergeClaimEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.Cached;
import com.ultikits.ultitools.interfaces.DataOperator;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lets only one server at a time run the one-time primary-wallet merge when several servers share one
 * database (UltiKits/UltiEconomy#25).
 *
 * <p><b>Why a claim is needed.</b> {@link PrimaryWalletMerge} is safe against a crash, but not against a
 * second server running it at the same moment: a server that read a player's second wallet before the
 * other server merged it, and the account after, marks the rows again with a "before" balance that
 * already includes them and adds them a second time; two servers can both create the account of a
 * player who had none; and two different markers on one player's rows leave them unmerged. Measured
 * with every interleaving of two or three servers' storage calls that the concurrency test explores.
 *
 * <p><b>The claim.</b> A row with a fixed id in its own table ({@link WalletMergeClaimEntity}). The
 * table's primary key makes inserting it succeed for exactly one server: on SQLite and MySQL the
 * framework's insert of an existing id throws (the database refuses the duplicate primary key), and
 * this class reads the row back to see whose it is. A server that holds it merges, then deletes it. A
 * server that does not hold it waits, reading only the claim row, and goes on only once it holds the
 * claim itself -- after the holder removed it, or after taking it over (below) -- and has run the merge,
 * which then finds nothing left or finishes what is left; so it never starts before the merge is
 * finished. Waiting, not refusing to start, because the wait is short (the length of the other
 * server's merge, once, at the upgrade) and needs nobody to restart anything; every ten seconds it logs
 * that it is waiting. Modules load while the server starts, before its watchdog is armed (Paper arms it
 * after "Done"), so the wait cannot trip the watchdog then.
 *
 * <p><b>A server that stopped while holding the claim.</b> While it merges, the holder changes the
 * claim's heartbeat every few seconds. A waiting server that sees the same holder and heartbeat for
 * {@link #STALE_MILLIS}, by its own clock (servers' clocks are never compared), takes it to have
 * stopped: it deletes that exact claim -- the delete names the holder and the heartbeat it saw, so it
 * cannot delete a claim another waiting server has just taken -- and inserts its own. The merge's
 * per-player markers then let it finish whatever the stopped server left half done, adding nothing
 * twice. A claim row with no holder or no heartbeat was not written by this class, and cannot be taken
 * over by a delete that names exactly it, so it refuses the module with a message telling the operator
 * to delete the row. The holder checks that the claim is still its own at every heartbeat and, without
 * waiting for one, before every write to an account; a holder that finds it taken over stops merging.
 *
 * <p><b>Not covered.</b> JSON storage cannot be shared by two servers at all: each server keeps its own
 * copy of the records in memory and never rereads the files, and one server's clean-up deletes the
 * files the other wrote. A server still running a version without this claim is not stopped by it.
 * And a holder that pauses for longer than {@link #STALE_MILLIS} right after a check -- between the
 * check before an account write and that write, or between two heartbeats while it marks or removes
 * rows -- and then goes on can still overlap with the server that took over, because the framework
 * cannot make a write conditional on the claim. The writes it could still make then are one account
 * write (which could add a player's second wallet twice or undo a later change to that account) or
 * row markings and removals, which repeat what the new holder writes.
 */
public final class MergeClaim {

    /** The claim row's id; being the table's primary key, it is what lets only one server hold it. */
    static final String CLAIM_ID = "primary-wallet-merge";

    /** How long a waiting server sleeps between two looks at the claim. */
    static final long POLL_MILLIS = 1_000L;

    /** How often the holder changes the heartbeat, at most. */
    static final long BEAT_MILLIS = 5_000L;

    /**
     * How long an unchanged claim is waited for before its holder is taken to have stopped: six
     * heartbeats, far longer than a holder goes between two (a few storage calls). Taking over a
     * stopped holder's claim therefore costs about 30 seconds of startup; waiting for a live holder
     * lasts as long as its merge.
     */
    static final long STALE_MILLIS = 30_000L;

    /** How often a waiting server logs that it is waiting. */
    static final long WAIT_LOG_MILLIS = 10_000L;

    /** How many times in a row the claim may be missing right after an insert before storage is blamed. */
    private static final int MAX_VANISHED = 3;

    /** The clock and the sleep, replaceable in tests. */
    interface Timing {
        /** Milliseconds on a clock that only moves forward; only differences are used. */
        long millis();

        void sleep(long millis) throws InterruptedException;
    }

    static final Timing SYSTEM_TIMING = new Timing() {
        @Override
        public long millis() {
            return System.nanoTime() / 1_000_000L;
        }

        @Override
        public void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    };

    private final UltiToolsPlugin plugin;
    private final Supplier<DataOperator<WalletMergeClaimEntity>> store;
    private final Timing timing;
    private final String owner = UUID.randomUUID().toString();

    private DataOperator<WalletMergeClaimEntity> claims;
    private long lastBeat;
    private long beats;

    /**
     * @param plugin the module, for its logger and language catalogue
     * @param store  the claim table; asked for only when there is something to merge, so a fresh
     *               install or an already-merged database never gets the table
     */
    public MergeClaim(UltiToolsPlugin plugin, Supplier<DataOperator<WalletMergeClaimEntity>> store) {
        this(plugin, store, SYSTEM_TIMING);
    }

    MergeClaim(UltiToolsPlugin plugin, Supplier<DataOperator<WalletMergeClaimEntity>> store, Timing timing) {
        this.plugin = plugin;
        this.store = store;
        this.timing = timing;
    }

    /**
     * Runs {@code merge} if there is anything to merge and no other server is merging; waits while
     * another server is.
     *
     * @return true when the merge is finished, by this server or another; false when storage failed or
     *         this server lost the claim, which the caller must treat as "do not start"
     */
    public boolean runExclusively(PrimaryWalletMerge merge) {
        try {
            if (!merge.pending()) {
                return true;
            }
            acquire();
        } catch (RuntimeException e) {
            logFailure(e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logFailure(e);
            return false;
        }
        try {
            // After another server's merge, this finds nothing left and writes nothing.
            return merge.withClaim(this::beat, this::verify).run();
        } finally {
            release();
        }
    }

    /**
     * Takes the claim, waiting while another server holds it. While waiting it reads only the claim
     * row, once a second, never the wallets.
     */
    private void acquire() throws InterruptedException {
        claims = store.get();
        boolean seen = false;
        String seenOwner = null;
        String seenBeat = null;
        long seenSince = 0L;
        long lastWaitLog = 0L;
        boolean waited = false;
        boolean tookOver = false;
        int vanished = 0;
        while (true) {
            RuntimeException refused = null;
            try {
                claims.insert(newClaim());
            } catch (RuntimeException e) {
                refused = e;
            }
            WalletMergeClaimEntity held = claims.getById(CLAIM_ID);
            if (held == null) {
                // Either the holder released the claim between this insert and this read (try again),
                // or the storage refuses or loses the insert: after a few tries in a row, the storage.
                if (++vanished >= MAX_VANISHED) {
                    throw refused != null ? refused
                            : new IllegalStateException("the wallet-merge claim could not be stored");
                }
                continue;
            }
            vanished = 0;
            if (owner.equals(held.getClaimOwner())) {
                if (waited && !tookOver) {
                    plugin.getLogger().info(plugin.i18n("economy.log.wallet_merge.claim_wait_over"));
                }
                lastBeat = timing.millis();
                return;
            }
            if (held.getClaimOwner() == null || held.getHeartbeat() == null) {
                throw new IllegalStateException(plugin.i18n("economy.log.wallet_merge.claim_malformed"));
            }
            long now = timing.millis();
            if (!seen || !held.getClaimOwner().equals(seenOwner) || !held.getHeartbeat().equals(seenBeat)) {
                seen = true;
                seenOwner = held.getClaimOwner();
                seenBeat = held.getHeartbeat();
                seenSince = now;
            } else if (now - seenSince >= STALE_MILLIS) {
                plugin.getLogger().warn(String.format(plugin.i18n("economy.log.wallet_merge.claim_stale"),
                        String.valueOf(held.getClaimedAt()), STALE_MILLIS / 1000L));
                // Only the exact claim seen: never one another waiting server has taken meanwhile.
                claims.del(exactly(seenOwner, seenBeat));
                flushAndGc();
                tookOver = true;
                seen = false;
                continue;
            }
            if (!waited || now - lastWaitLog >= WAIT_LOG_MILLIS) {
                plugin.getLogger().info(String.format(plugin.i18n("economy.log.wallet_merge.claim_waiting"),
                        String.valueOf(held.getClaimedAt())));
                lastWaitLog = now;
                waited = true;
            }
            timing.sleep(POLL_MILLIS);
        }
    }

    /**
     * Called by the merge between its steps: every {@link #BEAT_MILLIS} at most, checks that this
     * server still holds the claim and changes its heartbeat. Throws, which stops the merge, when
     * another server has taken the claim over.
     */
    void beat() {
        long now = timing.millis();
        if (now - lastBeat < BEAT_MILLIS) {
            return;
        }
        lastBeat = now;
        verify();
        beats++;
        claims.update("heartbeat", String.valueOf(beats), CLAIM_ID);
    }

    /**
     * Called by the merge right before each write to an account, and by every heartbeat: throws, which
     * stops the merge, when the claim is no longer this server's.
     */
    void verify() {
        WalletMergeClaimEntity held = claims.getById(CLAIM_ID);
        if (held == null || !owner.equals(held.getClaimOwner())) {
            throw new IllegalStateException(plugin.i18n("economy.log.wallet_merge.claim_lost"));
        }
    }

    /** Deletes this server's claim -- only its own, never one another server has taken over. */
    private void release() {
        try {
            claims.del(where("id", CLAIM_ID), where("claim_owner", owner));
            flushAndGc();
        } catch (RuntimeException e) {
            plugin.getLogger().warn(String.format(plugin.i18n("economy.log.wallet_merge.claim_release_failed"),
                    String.valueOf(e.getMessage()), STALE_MILLIS / 1000L));
        }
    }

    private WalletMergeClaimEntity newClaim() {
        WalletMergeClaimEntity claim = new WalletMergeClaimEntity(owner, "0", Instant.now().toString());
        claim.setId(CLAIM_ID);
        return claim;
    }

    /** On JSON storage, also removes a claim file a background flush may have written. */
    private void flushAndGc() {
        if (claims instanceof Cached) {
            ((Cached) claims).flush();
            ((Cached) claims).gc();
        }
    }

    private void logFailure(Exception e) {
        plugin.getLogger().error(String.format(
                plugin.i18n("economy.log.wallet_merge.failed"), String.valueOf(e.getMessage())));
    }

    /** Conditions matching only the claim row with this holder and this heartbeat. */
    private static WhereCondition[] exactly(String claimOwner, String heartbeat) {
        return new WhereCondition[]{
                where("id", CLAIM_ID), where("claim_owner", claimOwner), where("heartbeat", heartbeat)};
    }

    private static WhereCondition where(String column, String value) {
        return WhereCondition.builder().column(column).value(value).build();
    }
}
