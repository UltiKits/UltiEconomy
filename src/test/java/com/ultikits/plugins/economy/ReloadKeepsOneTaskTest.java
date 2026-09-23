package com.ultikits.plugins.economy;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.service.InterestServiceTestAccess;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.manager.ConfigManager;
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
import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Threat T-17-15-01 of plan 17-15: interest paid twice per interval because a reload added a second
 * interest task. Gate-1 review WR-05 found no test anywhere asserting that a reload adds none; the
 * property held only because {@code UltiToolsPlugin#reloadSelf()} happens not to touch the task
 * manager. UltiKits/UltiTools-Reborn#531 is about to make reload reschedule bound timers, which is
 * exactly where a missing cancel would add a second one.
 *
 * <p>This registers the module's two scheduled services through the framework's real
 * {@link TaskManager} -- the way {@code PluginManager} does once per module load -- then runs the
 * framework's real, {@code final} {@link UltiToolsPlugin#reloadSelf()} twice on a real
 * {@link UltiEconomy} instance, and counts every call that reached the Bukkit scheduler. The
 * framework singleton it reaches through {@code UltiTools.getInstance()} is a mock, so whatever a
 * future reload does through the scheduler is counted, and whatever it does through a part of the
 * framework this test does not set up fails loudly instead of passing quietly.
 *
 * <h2>Control</h2>
 * {@code ConfigManager#reloadConfigs(module)} is verified to have run twice, so the reloads really
 * went through the framework's reload path; a test that never reloaded would also see one task per
 * method.
 */
@DisplayName("Reload keeps exactly one scheduled task per method (T-17-15-01, UltiEconomy#15)")
class ReloadKeepsOneTaskTest {

    @TempDir
    Path configFolder;

    private Object previousInstance;

    @AfterEach
    void restoreFrameworkSingleton() throws Exception {
        instanceField().set(null, previousInstance);
    }

    @Test
    @DisplayName("two /ul reload runs leave one interest task and one leaderboard task, with their periods")
    void twoReloadsAddNoTask() throws Exception {
        UltiEconomy module = module();
        ConfigManager configManager = mock(ConfigManager.class);
        UltiTools framework = mock(UltiTools.class);
        when(framework.getConfigManager()).thenReturn(configManager);
        YamlConfiguration frameworkConfig = new YamlConfiguration();
        frameworkConfig.set("language", "en");
        when(framework.getConfig()).thenReturn(frameworkConfig);
        when(framework.getLogger()).thenReturn(Logger.getLogger("ultieconomy-reload-test"));
        when(framework.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        previousInstance = instanceField().get(null);
        instanceField().set(null, framework);

        JavaPlugin host = mock(JavaPlugin.class);
        // Every task the scheduler hands out is recorded with the call that created it, so the
        // assertion below counts LIVE tasks: a reload that cancels a task and schedules its
        // replacement (UltiKits/UltiTools-Reborn#531's design for a changed period) still leaves one.
        final List<String[]> created = new ArrayList<>();
        final List<BukkitTask> tasks = new ArrayList<>();
        BukkitScheduler scheduler = mock(BukkitScheduler.class, invocation -> {
            if (!BukkitTask.class.equals(invocation.getMethod().getReturnType())) {
                return null;
            }
            BukkitTask task = mock(BukkitTask.class);
            Object[] args = invocation.getArguments();
            created.add(new String[] {invocation.getMethod().getName(),
                    args.length > 2 ? String.valueOf(args[2]) : "?",
                    args.length > 3 ? String.valueOf(args[3]) : "-"});
            tasks.add(task);
            return task;
        });

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("ultieconomy-reload-test"));

            TaskManager taskManager = new TaskManager(host);
            for (Object bean : InterestServiceTestAccess.scheduledBeans(module, config())) {
                taskManager.registerScheduledMethods(module, bean);
            }
            assertThat(liveTasks(created, tasks)).as("after load").containsExactlyInAnyOrder(
                    "runTaskTimer(delay=36000, period=36000)",
                    "runTaskTimer(delay=0, period=1200)");

            module.reloadSelf();
            module.reloadSelf();
        }

        verify(configManager, times(2)).reloadConfigs(module);
        assertThat(liveTasks(created, tasks)).as("live tasks after two reloads").containsExactlyInAnyOrder(
                "runTaskTimer(delay=36000, period=36000)",
                "runTaskTimer(delay=0, period=1200)");
    }

    /** Every created task that nothing has cancelled, described by the call that created it. */
    private static List<String> liveTasks(List<String[]> created, List<BukkitTask> tasks) {
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

    private EconomyConfig config() {
        EconomyConfig config = new EconomyConfig();
        config.setInterestEnabled(true);
        return config;
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
