package com.ultikits.plugins.economy.config;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A value outside its range is not used, and the operator's file is not changed: the module saves
 * {@code config.yml} itself when it writes the currency name in the server's language, on enable and
 * after a reload, and that save must not write the fallback over what the operator wrote. After a
 * reload the range check runs before that save, so the fallback has to be the value the module reads,
 * not the value it holds for the file (UltiKits/UltiEconomy#29, #32).
 */
@DisplayName("an out-of-range value falls back without the module's own save rewriting the operator's file")
class ConfigRangesFileTest {

    @TempDir
    Path serverDir;

    @Test
    @DisplayName("after the range check, a save of config.yml keeps the operator's values and the module still uses the defaults")
    void aSaveAfterTheRangeCheckKeepsTheOperatorsValues() throws Exception {
        File file = serverDir.resolve("config").resolve("config.yml").toFile();
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), ("interest:\n  rate: 3.0\n  max-interest: -2.0\n"
                + "tax:\n  transaction-tax:\n    rate: -0.5\n").getBytes(StandardCharsets.UTF_8));
        EconomyConfig config = new EconomyConfig();
        config.init(moduleWithConfigFolder(serverDir.toFile()));
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        when(plugin.i18n(anyString())).thenReturn("%s %s %s %s %s %s %s");

        ConfigRanges.enforce(config, mock(PluginLogger.class), plugin);
        config.save();

        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(file);
        assertThat(onDisk.getDouble("interest.rate")).isEqualTo(3.0);
        assertThat(onDisk.getDouble("interest.max-interest")).isEqualTo(-2.0);
        assertThat(onDisk.getDouble("tax.transaction-tax.rate")).isEqualTo(-0.5);
        assertThat(config.getInterestRate()).isEqualTo(0.03);
        assertThat(config.getMaxInterest()).isEqualTo(10000.0);
        assertThat(config.getTransactionTaxRate()).isEqualTo(0.05);
    }

    /** As {@code ConfigFileWriteBackTest}: a module allocated without a server, with its config folder set. */
    private static UltiEconomy moduleWithConfigFolder(File folder) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        UltiEconomy module = (UltiEconomy) unsafe.allocateInstance(UltiEconomy.class);
        Field folderField = UltiToolsPlugin.class.getDeclaredField("resourceFolderPath");
        folderField.setAccessible(true);
        folderField.set(module, folder.getAbsolutePath());
        return module;
    }
}
