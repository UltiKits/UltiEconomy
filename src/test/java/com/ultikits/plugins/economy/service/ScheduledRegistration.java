package com.ultikits.plugins.economy.service;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.manager.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;

/**
 * Registers a bean's {@code @Scheduled} methods through the framework's own {@link TaskManager} --
 * the class that does it on a real server, called the way {@code PluginManager} calls it once per
 * module load -- against a mocked Bukkit scheduler, and records what reached the scheduler.
 *
 * <p>This is deliberately not a reflection read of the annotation. What a server runs is decided by
 * what {@code TaskManager} hands to {@code BukkitScheduler}: which scheduling method (sync or async,
 * repeating or one-shot), with which delay and period in ticks, and which runnable. Asserting on
 * that consumer means a period, a sync/async flag or a delay that the framework would read
 * differently from the annotation is caught here too.
 */
final class ScheduledRegistration {

    /** One call {@code TaskManager} made on the scheduler. */
    static final class Call {
        final String method;
        final Runnable task;
        final long delay;
        final long period;

        Call(String method, Runnable task, long delay, long period) {
            this.method = method;
            this.task = task;
            this.delay = delay;
            this.period = period;
        }

        @Override
        public String toString() {
            return method + "(delay=" + delay + ", period=" + period + ")";
        }
    }

    private ScheduledRegistration() {
    }

    /** Registers {@code bean} once and returns every scheduler call that registration made. */
    static List<Call> register(Object bean) {
        JavaPlugin host = mock(JavaPlugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class, invocation -> {
            Class<?> type = invocation.getMethod().getReturnType();
            return BukkitTask.class.equals(type) ? mock(BukkitTask.class) : null;
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("ultieconomy-scheduled-test"));
            new TaskManager(host).registerScheduledMethods(mock(UltiToolsPlugin.class), bean);
        }
        List<Call> calls = new ArrayList<>();
        for (Invocation invocation : mockingDetails(scheduler).getInvocations()) {
            Object[] args = invocation.getArguments();
            String name = invocation.getMethod().getName();
            Runnable task = args.length > 1 && args[1] instanceof Runnable ? (Runnable) args[1] : null;
            long delay = args.length > 2 && args[2] instanceof Long ? (Long) args[2] : -1L;
            long period = args.length > 3 && args[3] instanceof Long ? (Long) args[3] : -1L;
            calls.add(new Call(name, task, delay, period));
        }
        return calls;
    }

    /**
     * Runs a registered task the way the scheduler would, and returns every WARNING the framework
     * logged while running it. {@code TaskManager} catches and logs any exception the scheduled
     * method throws, so a task that failed and a task that correctly did nothing look the same
     * unless the log is read -- callers assert this list is empty.
     *
     * <p>Must be called inside the caller's own {@code MockedStatic<Bukkit>} scope, which this
     * method extends with {@code Bukkit.getLogger()}.
     */
    static List<String> runAndCollectWarnings(Runnable task, MockedStatic<Bukkit> bukkit) {
        Logger logger = Logger.getLogger("ultieconomy-scheduled-run-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        final List<String> warnings = Collections.synchronizedList(new ArrayList<String>());
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
                    warnings.add(record.getMessage()
                            + (record.getThrown() == null ? "" : " :: " + record.getThrown()));
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        bukkit.when(Bukkit::getLogger).thenReturn(logger);
        task.run();
        return new ArrayList<>(warnings);
    }
}
