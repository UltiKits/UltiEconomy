package com.ultikits.plugins.economy;

import com.ultikits.plugins.economy.config.ConfigEntryAccess;
import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.service.InterestServiceTestAccess;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.manager.ConfigManager;
import com.ultikits.ultitools.manager.PluginManager;
import com.ultikits.ultitools.manager.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The risk: interest paid twice per interval because a reload added a second interest task. A
 * review found no test anywhere asserting that a reload adds none; the property held only because
 * {@code UltiToolsPlugin#reloadSelf()} happens not to touch the task manager.
 * UltiKits/UltiTools-Reborn#531 is about to make reload reschedule bound timers, which is exactly
 * where a missing cancel would add a second one.
 *
 * <p>This registers the module's two scheduled services through the framework's real
 * {@link TaskManager} -- the way {@code PluginManager} does once per module load -- then runs the
 * framework's real, {@code final} {@link UltiToolsPlugin#reloadSelf()} twice on a real
 * {@link UltiEconomy} instance, and counts every call that reached the Bukkit scheduler. The
 * framework singleton it reaches through {@code UltiTools.getInstance()} is a mock, so whatever a
 * future reload does through the scheduler is counted, and whatever it does through a part of the
 * framework this test does not set up fails loudly instead of passing quietly.
 *
 * <p>Since UltiKits/UltiTools-Reborn#531 the two tasks are config-bound, and the reload path reaches
 * the task manager: {@code reloadSelf()} calls {@code PluginManager#applyReloadedConfigBindings},
 * which asks {@code TaskManager#rescheduleBound} to apply a changed interval. The real
 * {@code PluginManager} method runs here against the same {@code TaskManager} the tasks were
 * registered with, so a reschedule that forgot to cancel the old task would show up as a second
 * live task.
 *
 * <h2>Control</h2>
 * {@code ConfigManager#reloadConfigs(module)} is verified to have run twice, so the reloads really
 * went through the framework's reload path; a test that never reloaded would also see one task per
 * method. The changed-interval case additionally sees the interest task replaced by one with the
 * new period, so the reschedule path it guards really ran.
 */
@DisplayName("Reload keeps exactly one scheduled task per method (UltiEconomy#15)")
class ReloadKeepsOneTaskTest {

    @TempDir
    Path configFolder;

    private Object previousInstance;

    @AfterEach
    void restoreFrameworkSingleton() throws Exception {
        instanceField().set(null, previousInstance);
    }

    @Test
    @DisplayName("two /ul reload runs with unchanged values leave one interest task and one leaderboard task, untouched")
    void twoReloadsAddNoTask() throws Exception {
        Harness h = new Harness(new EconomyConfig());

        h.load();
        assertThat(h.liveTasks()).as("after load").containsExactlyInAnyOrder(
                "runTaskTimer(delay=36000, period=36000)",
                "runTaskTimer(delay=0, period=1200)");

        h.reload(200);
        h.reload(300);

        verify(h.configManager, times(2)).reloadConfigs(h.module);
        assertThat(h.liveTasks()).as("live tasks after two reloads").containsExactlyInAnyOrder(
                "runTaskTimer(delay=36000, period=36000)",
                "runTaskTimer(delay=0, period=1200)");
        // Untouched means not re-armed at all: a reload that cancelled and re-armed each task with its
        // original delay would print the same strings and restart the clock (a postponed payment).
        assertThat(h.tasks).as("tasks created since load").hasSize(2);
    }

    /**
     * The common operator case: interest has already been paid, then the interval is changed. The
     * next payment must be the last payment plus the new interval -- not a fresh interval from the
     * reload (postponed), not at once (early) -- and there must still be one live interest task.
     */
    @Test
    @DisplayName("after a payment, a reload with a changed interest.interval puts the next payment at last payment + new interval, one live task per method")
    void reloadAfterAPaymentKeepsThePhaseFromThatPayment() throws Exception {
        EconomyConfig config = new EconomyConfig();
        Harness h = new Harness(config);

        h.load();                                    // armed at tick 100: delay 36000
        h.runInterestTaskAt(40000);                  // the scheduler fires it: the last payment is at 40000
        ConfigEntryAccess.set(config, "interest.interval", 900);
        h.reload(41000);                             // 1000 ticks after that payment

        assertThat(h.warnings).as("framework warnings while running the task").isEmpty();
        // Next payment: 40000 + 18000 = 58000, i.e. 17000 ticks after the reload at 41000.
        assertThat(h.liveTasks()).as("live tasks after a changed interval").containsExactlyInAnyOrder(
                "runTaskTimer(delay=17000, period=18000)",
                "runTaskTimer(delay=0, period=1200)");
        assertThat(h.tasks).as("the interest task was replaced once, the leaderboard task not at all").hasSize(3);
    }

    @Test
    @DisplayName("a reload with a changed interest.interval replaces the interest task, keeping its phase: still one live task per method")
    void reloadWithAChangedIntervalKeepsOneTaskPerMethod() throws Exception {
        EconomyConfig config = new EconomyConfig();
        Harness h = new Harness(config);

        h.load();                                    // armed at tick 100
        ConfigEntryAccess.set(config, "interest.interval", 900);
        h.reload(200);                               // 100 ticks after arming
        h.reload(300);                               // unchanged again: must not touch it

        verify(h.configManager, times(2)).reloadConfigs(h.module);
        // Not yet run: first run = arm tick + new delay = 100 + 18000, i.e. 17900 ticks after the
        // reload at tick 200 -- neither early (not at once) nor postponed (not a fresh 18000).
        assertThat(h.liveTasks()).as("live tasks after a changed interval").containsExactlyInAnyOrder(
                "runTaskTimer(delay=17900, period=18000)",
                "runTaskTimer(delay=0, period=1200)");
    }

    /** One module, its two scheduled services, the real task manager, and the real reload path. */
    private final class Harness {
        final EconomyConfig config;
        final UltiEconomy module;
        final ConfigManager configManager = mock(ConfigManager.class);
        final List<String[]> created = new ArrayList<>();
        final List<BukkitTask> tasks = new ArrayList<>();
        final List<Runnable> runnables = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final AtomicInteger tick = new AtomicInteger(100);
        final BukkitScheduler scheduler;
        final TaskManager taskManager;

        @SuppressWarnings({"unchecked", "rawtypes"})
        Harness(EconomyConfig config) throws Exception {
            this.config = config;
            this.module = module();
            // Every task the scheduler hands out is recorded with the call that created it, so the
            // assertions count LIVE tasks: a task that is cancelled and replaced still counts once.
            scheduler = mock(BukkitScheduler.class, invocation -> {
                if (!BukkitTask.class.equals(invocation.getMethod().getReturnType())) {
                    return null;
                }
                BukkitTask task = mock(BukkitTask.class);
                Object[] args = invocation.getArguments();
                runnables.add(args.length > 1 && args[1] instanceof Runnable ? (Runnable) args[1] : null);
                created.add(new String[] {invocation.getMethod().getName(),
                        args.length > 2 ? String.valueOf(args[2]) : "?",
                        args.length > 3 ? String.valueOf(args[3]) : "-"});
                tasks.add(task);
                return task;
            });
            taskManager = new TaskManager(mock(JavaPlugin.class));
            PluginManager pluginManager = realPluginManagerWith(taskManager);

            when(configManager.getConfigEntities(any(UltiToolsPlugin.class), eq(EconomyConfig.class)))
                    .thenReturn((List) Collections.singletonList(config));
            UltiTools framework = mock(UltiTools.class);
            when(framework.getConfigManager()).thenReturn(configManager);
            when(framework.getPluginManager()).thenReturn(pluginManager);
            YamlConfiguration frameworkConfig = new YamlConfiguration();
            frameworkConfig.set("language", "en");
            when(framework.getConfig()).thenReturn(frameworkConfig);
            when(framework.getLogger()).thenReturn(Logger.getLogger("ultieconomy-reload-test"));
            when(framework.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
            previousInstance = instanceField().get(null);
            instanceField().set(null, framework);
        }

        void load() {
            try (MockedStatic<Bukkit> bukkit = bukkit()) {
                for (Object bean : InterestServiceTestAccess.scheduledBeans(module, config)) {
                    taskManager.registerScheduledMethods(module, bean);
                }
            }
        }

        /** Runs the live interest task's runnable as the scheduler would, at {@code atTick}. */
        void runInterestTaskAt(int atTick) {
            tick.set(atTick);
            int index = -1;
            for (int i = 0; i < created.size(); i++) {
                if ("36000".equals(created.get(i)[2])) {
                    index = i;
                }
            }
            assertThat(index).as("an interest task with period 36000 was created").isGreaterThanOrEqualTo(0);
            try (MockedStatic<Bukkit> bukkit = bukkit()) {
                runnables.get(index).run();
            }
        }

        void reload(int atTick) {
            tick.set(atTick);
            try (MockedStatic<Bukkit> bukkit = bukkit()) {
                module.reloadSelf();
            }
        }

        private MockedStatic<Bukkit> bukkit() {
            MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            Logger logger = Logger.getLogger("ultieconomy-reload-test-" + System.identityHashCode(this));
            logger.setUseParentHandlers(false);
            if (logger.getHandlers().length == 0) {
                logger.addHandler(new java.util.logging.Handler() {
                    @Override
                    public void publish(java.util.logging.LogRecord record) {
                        if (record.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
                            warnings.add(record.getMessage() + (record.getThrown() == null ? "" : " :: " + record.getThrown()));
                        }
                    }

                    @Override
                    public void flush() {
                    }

                    @Override
                    public void close() {
                    }
                });
            }
            bukkit.when(Bukkit::getLogger).thenReturn(logger);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getCurrentTick).thenAnswer(inv -> tick.get());
            return bukkit;
        }

        /** Every created task that nothing has cancelled, described by the call that created it. */
        List<String> liveTasks() {
            List<String> live = new ArrayList<>();
            for (int i = 0; i < tasks.size(); i++) {
                boolean cancelled = false;
                for (Invocation invocation : mockingDetails(tasks.get(i)).getInvocations()) {
                    if ("cancel".equals(invocation.getMethod().getName())) {
                        cancelled = true;
                    }
                }
                if (!cancelled) {
                    String[] call = created.get(i);
                    live.add(call[0] + "(delay=" + call[1] + ", period=" + call[2] + ")");
                }
            }
            return live;
        }
    }

    /**
     * The framework's own {@code PluginManager}, allocated without its server-bound constructor, with
     * only the task manager set: {@code applyReloadedConfigBindings} is then the real reload step.
     */
    private static PluginManager realPluginManagerWith(TaskManager taskManager) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        PluginManager pluginManager = (PluginManager) unsafe.allocateInstance(PluginManager.class);
        Field field = PluginManager.class.getDeclaredField("taskManager");
        field.setAccessible(true);
        field.set(pluginManager, taskManager);
        return pluginManager;
    }

    private UltiEconomy module() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        UltiEconomy module = (UltiEconomy) unsafe.allocateInstance(UltiEconomy.class);
        set(module, "resourceFolderPath", configFolder.toFile().getAbsolutePath());
        set(module, "pluginName", "UltiTools-Economy");
        return module;
    }

    private static void set(UltiToolsPlugin module, String name, Object value) throws Exception {
        Field field = UltiToolsPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(module, value);
    }

    private static Field instanceField() throws Exception {
        Field field = UltiTools.class.getDeclaredField("ultiTools");
        field.setAccessible(true);
        return field;
    }
}
