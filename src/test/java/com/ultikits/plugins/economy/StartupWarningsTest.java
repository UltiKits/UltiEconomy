package com.ultikits.plugins.economy;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * The warnings this module logs once per boot about switches whose effect changes on upgrade
 * (maintainer decision 2026-09-23, "on-disk values take effect, announced loudly").
 *
 * <p>Every case boots the module through its real {@link UltiEconomy#registerSelf()} and collects
 * everything logged at WARN or ERROR level, whatever overload was used, so a warning cannot pass a
 * case by being routed somewhere the case does not look.
 *
 * <h2>Controls</h2>
 * A warning that is never logged and a server whose switches need no warning look the same in a
 * log. So every warning has a case where it must appear and a case where it must not, and the
 * first is what makes the second meaningful.
 */
@DisplayName("UltiEconomy load-time warnings")
class StartupWarningsTest {

    private static final String CONFIG_FILE = "config/config.yml";

    @Nested
    @DisplayName("tax.enabled (UltiEconomy#16)")
    class TaxMasterSwitch {

        @Test
        @DisplayName("tax.enabled: false logs one warning that no transaction tax and no wealth tax is collected")
        void taxOffIsAnnounced() {
            EconomyConfig config = new EconomyConfig();
            config.setTaxEnabled(false);

            List<String> warnings = warningsMentioning("tax.enabled", bootWith(config));

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0))
                    .contains("UltiEconomy")
                    .contains(CONFIG_FILE)
                    .contains("tax.enabled")
                    .contains("no transaction tax")
                    .contains("no wealth tax")
                    .contains("tax.enabled: true");
        }

        @Test
        @DisplayName("Control: tax.enabled: true logs no tax warning")
        void taxOnIsNotAnnounced() {
            EconomyConfig config = new EconomyConfig();
            config.setTaxEnabled(true);

            assertThat(warningsMentioning("tax.enabled", bootWith(config))).isEmpty();
        }
    }

    @Nested
    @DisplayName("interest.enabled (UltiEconomy#15)")
    class InterestSwitch {

        @Test
        @DisplayName("interest.enabled: true logs one warning naming the rate, the fixed interval, the cap and how to turn it off")
        void interestOnIsAnnounced() {
            EconomyConfig config = new EconomyConfig();
            config.setInterestEnabled(true);
            config.setInterestRate(0.03);
            config.setMaxInterest(10000.0);

            List<String> warnings = warningsMentioning("interest.enabled is true", bootWith(config));

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0))
                    .contains("UltiEconomy")
                    .contains(CONFIG_FILE)
                    .contains("interest.rate = 0.03")
                    .contains("every 1800 seconds")
                    .contains("interest.max-interest = 10000.0")
                    .contains("interest.enabled: false");
        }

        @Test
        @DisplayName("interest.enabled: true with max-interest -1 says the payment has no cap")
        void interestOnWithoutCapSaysSo() {
            EconomyConfig config = new EconomyConfig();
            config.setInterestEnabled(true);
            config.setMaxInterest(-1);

            List<String> warnings = warningsMentioning("interest.enabled is true", bootWith(config));

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0)).contains("no cap");
        }

        @Test
        @DisplayName("Control: interest.enabled: false logs no interest warning")
        void interestOffIsNotAnnounced() {
            EconomyConfig config = new EconomyConfig();
            config.setInterestEnabled(false);

            assertThat(warningsMentioning("interest.enabled", bootWith(config))).isEmpty();
        }
    }

    /**
     * Two interval keys were removed because the periods they named are fixed in a framework
     * {@code @Scheduled} annotation (UltiKits/UltiEconomy#15, UltiKits/UltiTools-Reborn#531). Removing
     * a key from the code does not remove it from an operator's file, so each still-present one is
     * named once per boot.
     */
    @Nested
    @DisplayName("Removed interval keys still in the file (UltiEconomy#15)")
    class RemovedKeys {

        @Test
        @DisplayName("interest.interval still in the file is named, with the fixed 1800 s and the framework request")
        void residualInterestInterval() {
            List<String> warnings = residueWarningsFor("interest.interval", 900);

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0))
                    .contains("UltiEconomy")
                    .contains(CONFIG_FILE)
                    .contains("'interest.interval'")
                    .contains("1800 seconds")
                    .contains("UltiKits/UltiTools-Reborn#531");
        }

        @Test
        @DisplayName("leaderboard.update-interval still in the file is named, with the fixed 60 s and the framework request")
        void residualLeaderboardInterval() {
            List<String> warnings = residueWarningsFor("leaderboard.update-interval", 30);

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0))
                    .contains("UltiEconomy")
                    .contains(CONFIG_FILE)
                    .contains("'leaderboard.update-interval'")
                    .contains("60 seconds")
                    .contains("UltiKits/UltiTools-Reborn#531");
        }

        @Test
        @DisplayName("Control: a file with no removed key produces no removed-key warning")
        void cleanFileIsNotReported() {
            assertThat(warningsMentioning("no longer has any effect",
                    bootWith(new EconomyConfig(), new YamlConfiguration()))).isEmpty();
        }

        @Test
        @DisplayName("Control: a key this module still reads (interest.rate) is not reported as removed")
        void stillDeclaredKeyIsNotReported() {
            YamlConfiguration onDisk = new YamlConfiguration();
            onDisk.set("interest.rate", 0.03);

            assertThat(warningsMentioning("no longer has any effect",
                    bootWith(new EconomyConfig(), onDisk))).isEmpty();
        }

        private List<String> residueWarningsFor(String key, Object value) {
            YamlConfiguration onDisk = new YamlConfiguration();
            onDisk.set(key, value);
            return warningsMentioning("'" + key + "'", bootWith(new EconomyConfig(), onDisk));
        }
    }

    // ==================== helpers ====================

    static List<String> warningsMentioning(String needle, List<String> warnings) {
        List<String> matching = new ArrayList<>();
        for (String warning : warnings) {
            if (warning.contains(needle)) {
                matching.add(warning);
            }
        }
        return matching;
    }

    /**
     * Boots the module with {@code config} as its effective configuration and an operator file
     * holding only the keys this module still declares, and returns every message logged at WARN or
     * ERROR level while doing so.
     */
    static List<String> bootWith(EconomyConfig config) {
        return bootWith(config, new YamlConfiguration());
    }

    /**
     * As {@link #bootWith(EconomyConfig)}, with {@code onDisk} standing for the operator's parsed
     * file. {@code AbstractConfigEntity#getConfig()} returns that parsed file, including keys the
     * entity no longer declares, so a spy that returns {@code onDisk} is how a unit test presents
     * "this key is still in your file".
     */
    static List<String> bootWith(EconomyConfig config, YamlConfiguration onDisk) {
        EconomyConfig effective = org.mockito.Mockito.spy(config);
        when(effective.getConfig()).thenReturn(onDisk);
        when(effective.getConfigFilePath()).thenReturn(CONFIG_FILE);

        UltiEconomy plugin = mock(UltiEconomy.class);
        PluginLogger logger = mock(PluginLogger.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getConfig(EconomyConfig.class)).thenReturn(effective);
        SimpleContainer context = mock(SimpleContainer.class);
        when(plugin.getContext()).thenReturn(context);
        when(context.getBean(EconomyService.class)).thenReturn(mock(EconomyService.class));
        when(plugin.registerSelf()).thenCallRealMethod();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            assertThat(plugin.registerSelf()).isTrue();
        }

        List<String> logged = new ArrayList<>();
        for (Invocation invocation : mockingDetails(logger).getInvocations()) {
            String method = invocation.getMethod().getName();
            if (!"warn".equals(method) && !"error".equals(method)) {
                continue;
            }
            for (Object argument : invocation.getArguments()) {
                if (argument instanceof String) {
                    logged.add((String) argument);
                }
            }
        }
        return logged;
    }
}
