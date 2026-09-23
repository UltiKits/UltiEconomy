package com.ultikits.plugins.economy.config;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
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

/**
 * What an operator's {@code config/config.yml} actually holds after this module has booted once,
 * for a key the shipped resource does not contain.
 *
 * <p>This matters for UltiKits/UltiEconomy#16: {@code tax.enabled} is not in the shipped
 * {@code config/config.yml}, so what an existing server has on disk for it is whatever the framework
 * writes when the key is missing. These cases run the framework's own
 * {@code AbstractConfigEntity#init} against a real file on disk, rather than reading its source and
 * inferring the answer.
 *
 * <h2>Measured answer</h2>
 * A missing key is written into the operator's file with the value the Java field held at that
 * boot -- its declared default -- and the file is saved. So every server that has booted a build
 * whose {@code EconomyConfig#taxEnabled} was declared {@code false} now has
 * {@code tax.enabled: false} on disk, and changing the declared default later does not change that
 * file: the file value wins on every later boot.
 *
 * <h2>Controls</h2>
 * {@link #aKeyAlreadyOnDiskIsNotOverwrittenByTheDeclaredDefault()} is the other half of the
 * measurement: a file that already holds the key keeps its own value, even when it disagrees with
 * the declared default. Without it, "the file holds the declared default" could equally mean
 * "the framework rewrites every key on every boot", which is a different world for an upgrade.
 */
@DisplayName("Config write-back of a missing key (UltiEconomy#16 measurement)")
class ConfigFileWriteBackTest {

    /** The shipped resource's shape as far as the tax section goes: it has none. */
    private static final String FILE_WITHOUT_TAX_SECTION =
            "initial-cash: 1000.0\n"
                    + "bank:\n"
                    + "  enabled: true\n";

    @TempDir
    Path serverDir;

    @Test
    @DisplayName("A tax.enabled missing from the file is written into it with the declared default")
    void missingTaxSwitchIsWrittenWithTheDeclaredDefault() throws Exception {
        File file = writeOperatorFile(FILE_WITHOUT_TAX_SECTION);
        boolean declaredDefault = new EconomyConfig().isTaxEnabled();

        new EconomyConfig().init(moduleWithConfigFolder(serverDir.toFile()));

        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(file);
        assertThat(onDisk.contains("tax.enabled"))
                .withFailMessage("tax.enabled was not written into the operator's file; file now reads:%n%s",
                        read(file))
                .isTrue();
        assertThat(onDisk.getBoolean("tax.enabled")).isEqualTo(declaredDefault);
    }

    @Test
    @DisplayName("Control: a tax.enabled already in the file keeps the file's value")
    void aKeyAlreadyOnDiskIsNotOverwrittenByTheDeclaredDefault() throws Exception {
        boolean declaredDefault = new EconomyConfig().isTaxEnabled();
        boolean operatorValue = !declaredDefault;
        File file = writeOperatorFile(FILE_WITHOUT_TAX_SECTION
                + "tax:\n  enabled: " + operatorValue + "\n");

        EconomyConfig config = new EconomyConfig();
        config.init(moduleWithConfigFolder(serverDir.toFile()));

        assertThat(YamlConfiguration.loadConfiguration(file).getBoolean("tax.enabled"))
                .isEqualTo(operatorValue);
        assertThat(config.isTaxEnabled()).isEqualTo(operatorValue);
    }

    private File writeOperatorFile(String content) throws Exception {
        File file = serverDir.resolve("config").resolve("config.yml").toFile();
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * A module instance whose config folder is {@code folder}. {@link UltiToolsPlugin}'s
     * constructors need a running server, so the instance is allocated without one and the single
     * field {@code getConfigFile} reads is set directly.
     */
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
