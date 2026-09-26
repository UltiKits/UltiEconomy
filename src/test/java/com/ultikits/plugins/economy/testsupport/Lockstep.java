package com.ultikits.plugins.economy.testsupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Test support: runs several "servers" -- each a {@link Callable} on its own thread -- side by side
 * against shared storage, one storage call at a time, as a discrete-event simulation of servers running
 * in parallel. Every storage call a server makes goes through {@link #stepper(String)} (see
 * {@link SteppedOperator}), which hands control to the scheduler before the call runs.
 *
 * <p>Each server has its own virtual clock, {@link #now(String)}, in milliseconds from 0. A storage call
 * takes the call cost times a random factor between 0.5 and 1.5; a {@link #sleep(String, long)} takes
 * exactly the time slept. The scheduler always runs next the call that starts earliest across all
 * servers (a tie is broken at random), and each call happens at the moment it starts. So the servers'
 * clocks are one clock seen from several places, as on real machines, and the random call lengths
 * vary the interleaving from seed to seed, reproducibly.
 */
public final class Lockstep {

    /** Virtual milliseconds one storage call takes on average, unless the constructor says otherwise. */
    public static final long CALL_COST_MS = 20;

    private final Random random;
    private final long callCostMs;
    private final Map<String, Semaphore> go = new ConcurrentHashMap<>();
    private final Semaphore paused = new Semaphore(0);
    private final List<String> live = new CopyOnWriteArrayList<>();
    private final Map<String, Long> clock = new ConcurrentHashMap<>();
    private final Map<String, Long> nextStart = new ConcurrentHashMap<>();
    private final Map<String, Long> nextLength = new ConcurrentHashMap<>();
    private final List<String> trace = Collections.synchronizedList(new ArrayList<String>());

    public Lockstep(long seed) {
        this(seed, CALL_COST_MS);
    }

    /** @param callCostMs average virtual milliseconds each storage call takes (a slow database: large) */
    public Lockstep(long seed, long callCostMs) {
        this.random = new Random(seed);
        this.callCostMs = callCostMs;
    }

    /** {@code server}'s clock: when its last call or sleep ended. */
    public long now(String server) {
        Long t = clock.get(server);
        return t == null ? 0L : t;
    }

    /** What the servers did, in order: "time server: call". */
    public List<String> trace() {
        synchronized (trace) {
            return new ArrayList<>(trace);
        }
    }

    /** A callback that makes {@code server} wait for its turn before each storage call. */
    public Consumer<String> stepper(String server) {
        return what -> step(server, what, false, callCostMs);
    }

    /** {@code server} sleeps {@code millis}; the other servers run meanwhile. */
    public void sleep(String server, long millis) {
        step(server, "sleep " + millis, true, millis);
    }

    private void step(String server, String what, boolean exact, long cost) {
        Semaphore mine = go.get(server);
        if (mine == null) {
            throw new IllegalStateException("unknown server " + server);
        }
        long length;
        synchronized (random) {
            length = exact ? cost : Math.max(1L, Math.round(cost * (0.5 + random.nextDouble())));
        }
        nextStart.put(server, now(server));
        nextLength.put(server, length);
        trace.add(now(server) + " " + server + ": " + what);
        paused.release();
        acquire(mine);
    }

    /**
     * Runs every server to completion and returns what each returned (or the {@link Throwable} it
     * threw), by name.
     */
    public Map<String, Object> run(Map<String, Callable<?>> servers) {
        Map<String, Object> results = new ConcurrentHashMap<>();
        List<Thread> threads = new ArrayList<>();
        for (Map.Entry<String, Callable<?>> server : servers.entrySet()) {
            String name = server.getKey();
            go.put(name, new Semaphore(0));
            clock.put(name, 0L);
            nextStart.put(name, 0L);
            nextLength.put(name, 0L);
            live.add(name);
            Thread thread = new Thread(() -> {
                acquire(go.get(name));
                try {
                    Object result = server.getValue().call();
                    results.put(name, result == null ? "null" : result);
                } catch (Throwable e) {
                    results.put(name, e);
                } finally {
                    trace.add(now(name) + " " + name + ": finished");
                    live.remove(name);
                    paused.release();
                }
            }, "lockstep-" + name);
            thread.setDaemon(true);
            threads.add(thread);
        }
        for (Thread thread : threads) {
            thread.start();
        }
        while (!live.isEmpty()) {
            String next = earliest();
            // The call happens now, at its start; the server's clock moves to its end.
            clock.put(next, nextStart.get(next) + nextLength.get(next));
            go.get(next).release();
            try {
                if (!paused.tryAcquire(30, TimeUnit.SECONDS)) {
                    throw new AssertionError("server " + next + " did not reach its next step; trace: " + trace());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        for (Thread thread : threads) {
            try {
                thread.join(TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        return new LinkedHashMap<>(results);
    }

    /** The live server whose next call starts first; a tie is broken at random. */
    private String earliest() {
        List<String> first = new ArrayList<>();
        long best = Long.MAX_VALUE;
        for (String server : live) {
            long start = nextStart.get(server);
            if (start < best) {
                best = start;
                first.clear();
                first.add(server);
            } else if (start == best) {
                first.add(server);
            }
        }
        synchronized (random) {
            return first.get(random.nextInt(first.size()));
        }
    }

    private static void acquire(Semaphore semaphore) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
