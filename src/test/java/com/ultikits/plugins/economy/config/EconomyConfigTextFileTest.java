package com.ultikits.plugins.economy.config;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Plan 17-73 S5 for this module's one shipped-text writer ({@code UltiEconomy#writeConfigTextInServerLanguage},
 * reached through {@code onReload()}): on a real {@code config/config.yml} written by the framework's real
 * {@link EconomyConfig#init}, a language switch re-renders only the {@code currency-name} line, while a typo value
 * and a hand-written comment elsewhere in the same file stay byte for byte (UltiKits/UltiTools-Reborn#611:
 * a save writes only the settings the module changed, and only where the file still holds the text it read).
 */
@DisplayName("config.yml: a language switch re-renders only the shipped currency name (plan 17-73 S5)")
class EconomyConfigTextFileTest {

    @TempDir
    Path tempDir;

    private final String[] language = {"zh"};

    private final PluginLogger logger = mock(PluginLogger.class);

    private EconomyConfig current;

    private final UltiEconomy plugin = Mockito.mock(UltiEconomy.class, invocation -> {
        String name = invocation.getMethod().getName();
        switch (name) {
            case "onReload":
            case "shippedCatalogueTexts":
                return invocation.callRealMethod();
            case "getConfigFolder":
                return tempDir.toString();
            case "getConfigFile":
                return new File(tempDir.toFile(), invocation.<String>getArgument(0));
            case "i18n":
                return CatalogueText.answer(language[0]).answer(invocation);
            case "getLanguageCode":
                return language[0];
            case "getLogger":
                return logger;
            case "getPluginName":
                return "UltiEconomy";
            case "getConfig":
                return invocation.getArgument(0) == EconomyConfig.class ? current : Answers.RETURNS_DEFAULTS.answer(invocation);
            default:
                return Answers.RETURNS_DEFAULTS.answer(invocation);
        }
    });

    private File file() {
        return new File(tempDir.toFile(), "config/config.yml");
    }

    private String text() throws Exception {
        return new String(Files.readAllBytes(file().toPath()), StandardCharsets.UTF_8);
    }

    /** The module's {@code onReload()} (protected), as the framework calls it after rebuilding the language. */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private void reload() throws Exception {
        Method onReload = UltiToolsPlugin.class.getDeclaredMethod("onReload");
        onReload.setAccessible(true);
        onReload.invoke(plugin);
    }

    @Test
    @DisplayName("a language switch re-renders only the currency-name line; a typo value and a hand-written comment stay byte for byte")
    void languageSwitchRewritesOnlyTheCurrencyNameLine() throws Exception {
        Files.createDirectories(file().getParentFile().toPath());
        EconomyConfig config = spy(new EconomyConfig());
        config.init(plugin);
        current = config;
        reload();
        String zhName = CatalogueText.text("zh", "economy.config.currency_name");
        assertThat(YamlConfiguration.loadConfiguration(file()).getString("currency-name")).as("the zh start").isEqualTo(zhName);

        String started = text();
        String edited = started.replaceFirst("(?m)^initial-cash: 1000\\.0$", "# Operator note: keep this\ninitial-cash: 1OOO");
        assertThat(edited).as("the typo and the comment applied to:\n" + started).isNotEqualTo(started);
        Files.write(file().toPath(), edited.getBytes(StandardCharsets.UTF_8));

        language[0] = "en";
        config.reload();
        assertThat(text()).as("the entity's reload writes nothing (this file has no token comments)").isEqualTo(edited);
        reload();
        String after = text();

        String[] was = edited.split("\n", -1);
        String[] now = after.split("\n", -1);
        assertThat(now.length).as("line count unchanged; the file now:\n" + after).isEqualTo(was.length);
        List<String> changed = new ArrayList<>();
        for (int i = 0; i < was.length; i++) {
            if (!was[i].equals(now[i])) {
                changed.add(was[i] + " -> " + now[i]);
                assertThat(now[i]).as("line " + (i + 1) + " is the currency name").startsWith("currency-name: ");
                assertThat(was[i]).as("line " + (i + 1) + " was the currency name").startsWith("currency-name: ");
            }
        }
        assertThat(changed).as("the lines the module's save changed").hasSize(1);
        assertThat(after).as("the operator's typo and comment").contains("# Operator note: keep this\ninitial-cash: 1OOO\n");
        assertThat(YamlConfiguration.loadConfiguration(file()).getString("currency-name")).as("follows the switch")
                .isEqualTo(CatalogueText.text("en", "economy.config.currency_name"));
        verify(config, times(2)).save();
    }
}
