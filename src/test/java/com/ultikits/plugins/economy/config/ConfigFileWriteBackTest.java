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

    /**
     * {@code interest.interval} and {@code leaderboard.update-interval} are declared again, bound
     * to the two scheduled tasks through the framework's config-bound {@code @Scheduled}
     * (UltiKits/UltiEconomy#15, UltiKits/UltiTools-Reborn#531). A first boot writes both, with the
     * defaults that reproduce today's timing (1800 and 60 seconds), into the operator's file.
     */
    @Test
    @DisplayName("A first boot writes interest.interval: 1800 and leaderboard.update-interval: 60 into the operator's file (UltiEconomy#15)")
    void firstBootWritesBothIntervalKeysWithTheirDefaults() throws Exception {
        File file = writeOperatorFile("");

        new EconomyConfig().init(moduleWithConfigFolder(serverDir.toFile()));

        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(file);
        assertThat(onDisk.contains("interest.interval"))
                .withFailMessage("interest.interval was not written; file now reads:%n%s", read(file))
                .isTrue();
        assertThat(onDisk.getInt("interest.interval")).isEqualTo(1800);
        assertThat(onDisk.contains("leaderboard.update-interval"))
                .withFailMessage("leaderboard.update-interval was not written; file now reads:%n%s", read(file))
                .isTrue();
        assertThat(onDisk.getInt("leaderboard.update-interval")).isEqualTo(60);
    }

    /**
     * The shipped {@code config/config.yml} is what a new server starts from. It declares interest
     * off, matching the Java default (maintainer decision 2026-09-23), and carries both interval
     * keys at the values the Java fields declare.
     */
    @Test
    @DisplayName("The shipped config.yml declares interest.enabled: false, interest.interval: 1800 and leaderboard.update-interval: 60 (UltiEconomy#15)")
    void shippedFileDeclaresInterestOffAndBothIntervals() throws Exception {
        YamlConfiguration shipped;
        try (java.io.InputStream in = ConfigFileWriteBackTest.class.getClassLoader()
                .getResourceAsStream("config/config.yml")) {
            assertThat(in).as("shipped config/config.yml on the classpath").isNotNull();
            shipped = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }

        // Control: the file was read -- it holds the rate this module declares.
        assertThat(shipped.getDouble("interest.rate")).isEqualTo(0.03);
        assertThat(shipped.contains("interest.enabled")).isTrue();
        assertThat(shipped.getBoolean("interest.enabled")).isFalse();
        assertThat(shipped.contains("interest.interval")).isTrue();
        assertThat(shipped.getInt("interest.interval")).isEqualTo(1800);
        assertThat(shipped.contains("leaderboard.update-interval")).isTrue();
        assertThat(shipped.getInt("leaderboard.update-interval")).isEqualTo(60);
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
