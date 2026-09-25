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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Test support: runs several "servers" -- each a {@link Callable} on its own thread -- against shared
 * storage, one storage call at a time, in an order chosen by a seeded random number generator. Every
 * storage call a server makes goes through {@link #stepper(String)} (see {@link SteppedOperator}), which
 * hands control back to the scheduler before the call runs; the scheduler then lets one server, picked
 * at random, run up to its next storage call. So one seed is one interleaving of the servers' storage
 * calls, reproducible, and a loop over seeds explores many of them.
 *
 * <p>Time is virtual: {@link #now()} starts at 0, every storage call costs {@link #CALL_COST_MS}, and a
 * server's {@link #sleep(String, long)} is itself a step that advances the clock by the time slept.
 */
public final class Lockstep {

    /** Virtual milliseconds one storage call takes. */
    public static final long CALL_COST_MS = 20;

    private final Random random;
    private final Map<String, Semaphore> go = new ConcurrentHashMap<>();
    private final Semaphore paused = new Semaphore(0);
    private final List<String> live = new CopyOnWriteArrayList<>();
    private final List<String> trace = Collections.synchronizedList(new ArrayList<String>());
    private final AtomicLong clock = new AtomicLong();

    public Lockstep(long seed) {
        this.random = new Random(seed);
    }

    /** The virtual time, in milliseconds. */
    public long now() {
        return clock.get();
    }

    /** What the servers did, in order: "server: call". */
    public List<String> trace() {
        synchronized (trace) {
            return new ArrayList<>(trace);
        }
    }

    /** A callback that makes {@code server} wait for its turn before each storage call. */
    public Consumer<String> stepper(String server) {
        return what -> step(server, what, CALL_COST_MS);
    }

    /** {@code server} sleeps {@code millis} of virtual time; the other servers may run meanwhile. */
    public void sleep(String server, long millis) {
        step(server, "sleep " + millis, millis);
    }

    private void step(String server, String what, long cost) {
        Semaphore mine = go.get(server);
        if (mine == null) {
            throw new IllegalStateException("unknown server " + server);
        }
        trace.add(server + ": " + what);
        clock.addAndGet(cost);
        paused.release();
        acquire(mine);
    }

    /**
     * Runs every server to completion, one step at a time, and returns what each returned (or the
     * {@link Throwable} it threw), by name.
     */
    public Map<String, Object> run(Map<String, Callable<?>> servers) {
        Map<String, Object> results = new ConcurrentHashMap<>();
        List<Thread> threads = new ArrayList<>();
        for (Map.Entry<String, Callable<?>> server : servers.entrySet()) {
            String name = server.getKey();
            go.put(name, new Semaphore(0));
            live.add(name);
            Thread thread = new Thread(() -> {
                acquire(go.get(name));
                try {
                    Object result = server.getValue().call();
                    results.put(name, result == null ? "null" : result);
                } catch (Throwable e) {
                    results.put(name, e);
                } finally {
                    trace.add(name + ": finished");
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
            String next = live.get(random.nextInt(live.size()));
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

    private static void acquire(Semaphore semaphore) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
