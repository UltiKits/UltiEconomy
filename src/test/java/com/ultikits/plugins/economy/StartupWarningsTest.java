package com.ultikits.plugins.economy;

import static org.mockito.ArgumentMatchers.anyString;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.testsupport.InMemoryDataOperator;
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

import java.io.StringReader;
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
        @DisplayName("interest.enabled: true logs one warning naming the rate, the interval, the cap and how to turn it off")
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
                    .contains("every interest.interval = 1800 seconds")
                    .contains("interest.max-interest = 10000.0")
                    .contains("interest.enabled: false")
                    // Servers sharing one database each pay the full rate.
                    .contains("If several servers share this database")
                    .contains("exactly one of them");
        }

        @Test
        @DisplayName("interest.enabled: true names the configured interval, not a fixed one (UltiTools-Reborn#531 binding)")
        void interestOnNamesTheConfiguredInterval() {
            EconomyConfig config = new EconomyConfig();
            config.setInterestEnabled(true);
            com.ultikits.plugins.economy.config.ConfigEntryAccess.set(config, "interest.interval", 900);

            List<String> warnings = warningsMentioning("interest.enabled is true", bootWith(config));

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0))
                    .contains("every interest.interval = 900 seconds")
                    .doesNotContain("fixed");
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
        @DisplayName("interest.enabled: true describes the primary currency's one wallet, in both languages (UltiKits/UltiEconomy#25)")
        void interestWarningDescribesOneWallet() {
            EconomyConfig config = new EconomyConfig();
            config.setInterestEnabled(true);

            List<String> en = warningsMentioning("interest.enabled is true", bootWith(config));
            List<String> zh = warningsMentioning("interest.enabled", bootWith(config, new YamlConfiguration(), "zh"));

            assertThat(en).hasSize(1);
            assertThat(en.get(0))
                    .contains("paid once per player")
                    .doesNotContain("per-currency row");
            assertThat(zh).hasSize(1);
            assertThat(zh.get(0))
                    .contains("每位玩家只发放一次")
                    .doesNotContain("那一行上重复发放");
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
     * {@code interest.interval} and {@code leaderboard.update-interval} were briefly removed on this
     * branch and are declared again, bound to the two scheduled tasks (UltiKits/UltiTools-Reborn#531).
     * A value an operator kept in the file is a live setting, so nothing may call it dead.
     */
    @Nested
    @DisplayName("Interval keys are live settings (UltiEconomy#15)")
    class IntervalKeysAreLive {

        @Test
        @DisplayName("interest.interval and leaderboard.update-interval in the file are not reported as removed")
        void intervalKeysAreNotReportedAsRemoved() {
            YamlConfiguration onDisk = new YamlConfiguration();
            onDisk.set("interest.interval", 900);
            onDisk.set("leaderboard.update-interval", 30);

            List<String> warnings = bootWith(new EconomyConfig(), onDisk);

            assertThat(warningsMentioning("no longer has any effect", warnings)).isEmpty();
            assertThat(warningsMentioning("interest.interval'", warnings)).isEmpty();
            assertThat(warningsMentioning("leaderboard.update-interval'", warnings)).isEmpty();
        }
    }

    @Nested
    @DisplayName("the warnings follow the language setting")
    class LanguageSetting {

        @Test
        @DisplayName("under language: zh both warnings are Chinese, keeping the key names, the file and the values")
        void warningsInChinese() {
            EconomyConfig config = new EconomyConfig();
            config.setInterestEnabled(true);
            config.setTaxEnabled(false);
            List<String> warnings = bootWith(config, new YamlConfiguration(), "zh");
            assertThat(warnings).hasSize(2);
            for (String warning : warnings) {
                assertThat(warning).as(warning).matches("(?s).*\\p{IsHan}.*")
                        .doesNotContain("so interest is paid").doesNotContain("so no tax is collected")
                        .contains("UltiEconomy").contains(CONFIG_FILE);
            }
            assertThat(warnings.get(0)).contains("interest.enabled: false").contains("/ul reload UltiTools-Economy");
            assertThat(warnings.get(1)).contains("tax.enabled: true").contains("/ul reload UltiTools-Economy");
        }
    }

    /**
     * UltiKits/UltiEconomy#25 follow-up (maintainer, 2026-09-24: "config.yml governs, warn when they
     * differ"): the primary currency has one wallet, governed by {@code config/config.yml}; a
     * different value for it in {@code config/currencies.yml} is named at load.
     */
    @Nested
    @DisplayName("primary-currency settings in currencies.yml (UltiEconomy#25)")
    class PrimaryCurrencySettings {

        private static final String AGREEING = "currencies:\n  coins:\n    initial-cash: 1000.0\n"
                + "    bank-enabled: true\n    min-deposit: 100.0\n    max-bank-balance: -1\n    primary: true\n";

        private List<String> conflicts(String currenciesYaml, String language) {
            return warningsMentioning("config/currencies.yml",
                    bootWith(new EconomyConfig(), new YamlConfiguration(), language, currenciesYaml));
        }

        @Test
        @DisplayName("each of the four settings that differs is named with its file, key and both values, and config.yml's value is the one that applies")
        void everyDifferenceIsNamed() {
            List<String> warnings = conflicts("currencies:\n  coins:\n    initial-cash: 250.0\n"
                    + "    bank-enabled: false\n    min-deposit: 50.0\n    max-bank-balance: 5000.0\n    primary: true\n", "en");

            String text = CatalogueText.text("en", "economy.warn.primary_currency_setting");
            assertThat(warnings).containsExactly(
                    String.format(text, "UltiEconomy", "config/currencies.yml", "currencies.coins.initial-cash", "250.0",
                            CONFIG_FILE, "initial-cash", "1000.0", "currencies.coins.initial-cash", "config/currencies.yml"),
                    String.format(text, "UltiEconomy", "config/currencies.yml", "currencies.coins.bank-enabled", "false",
                            CONFIG_FILE, "bank.enabled", "true", "currencies.coins.bank-enabled", "config/currencies.yml"),
                    String.format(text, "UltiEconomy", "config/currencies.yml", "currencies.coins.min-deposit", "50.0",
                            CONFIG_FILE, "bank.min-deposit", "100.0", "currencies.coins.min-deposit", "config/currencies.yml"),
                    String.format(text, "UltiEconomy", "config/currencies.yml", "currencies.coins.max-bank-balance", "5000.0",
                            CONFIG_FILE, "bank.max-balance", "-1.0", "currencies.coins.max-bank-balance", "config/currencies.yml"));
            assertThat(warnings.get(2)).contains("bank.min-deposit = 100.0 applies");
        }

        @Test
        @DisplayName("Control: values that agree with config.yml log nothing")
        void agreeingValuesAreSilent() {
            assertThat(conflicts(AGREEING, "en")).isEmpty();
        }

        @Test
        @DisplayName("two ways of writing \"no cap\" (0 and -1) are not a conflict")
        void twoUnlimitedCapsAgree() {
            assertThat(conflicts("currencies:\n  coins:\n    max-bank-balance: 0\n    primary: true\n", "en")).isEmpty();
            // Control: a real cap against "no cap" is still named.
            assertThat(conflicts("currencies:\n  coins:\n    max-bank-balance: 10.0\n    primary: true\n", "en")).hasSize(1);
        }

        @Test
        @DisplayName("Control: a setting currencies.yml does not contain is not reported")
        void absentKeysAreSilent() {
            assertThat(conflicts("currencies:\n  coins:\n    display-name: 'Coins'\n    primary: true\n", "en")).isEmpty();
        }

        @Test
        @DisplayName("under language: zh the warning is Chinese and keeps both files, both keys and both values")
        void warningInChinese() {
            List<String> warnings = conflicts("currencies:\n  coins:\n    min-deposit: 50.0\n    primary: true\n", "zh");

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0)).matches("(?s).*\\p{IsHan}.*")
                    .contains("currencies.coins.min-deposit = 50.0").contains("bank.min-deposit = 100.0")
                    .contains(CONFIG_FILE);
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
        return bootWith(config, onDisk, "en");
    }

    /** As {@link #bootWith(EconomyConfig, YamlConfiguration)}, with the module speaking {@code language}. */
    static List<String> bootWith(EconomyConfig config, YamlConfiguration onDisk, String language) {
        // The primary-currency block agrees with config.yml's defaults, so loading logs nothing about it.
        return bootWith(config, onDisk, language, "currencies:\n  coins:\n    initial-cash: 1000.0\n    bank-enabled: true\n"
                + "    min-deposit: 100.0\n    max-bank-balance: -1\n    primary: true\n");
    }

    /** As {@link #bootWith(EconomyConfig, YamlConfiguration, String)}, with {@code currenciesYaml} as {@code config/currencies.yml}. */
    static List<String> bootWith(EconomyConfig config, YamlConfiguration onDisk, String language, String currenciesYaml) {
        EconomyConfig effective = org.mockito.Mockito.spy(config);
        when(effective.getConfig()).thenReturn(onDisk);
        when(effective.getConfigFilePath()).thenReturn(CONFIG_FILE);

        UltiEconomy plugin = mock(UltiEconomy.class);
        PluginLogger logger = mock(PluginLogger.class);
        when(plugin.getLogger()).thenReturn(logger);
        // Answer i18n from the real shipped catalogue of the language the case names.
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer(language));
        when(plugin.getConfig(EconomyConfig.class)).thenReturn(effective);
        SimpleContainer context = mock(SimpleContainer.class);
        when(plugin.getContext()).thenReturn(context);
        // Storage is empty, so the wallet merge logs nothing (UltiKits/UltiEconomy#25).
        when(plugin.getCurrencyManager()).thenReturn(new CurrencyManager(YamlConfiguration.loadConfiguration(
                new StringReader(currenciesYaml))));
        when(plugin.getDataOperator(PlayerAccountEntity.class)).thenReturn(
                InMemoryDataOperator.relational("economy_accounts", PlayerAccountEntity.class, null));
        when(plugin.getDataOperator(CurrencyBalanceEntity.class)).thenReturn(
                InMemoryDataOperator.relational("currency_balances", CurrencyBalanceEntity.class, null));
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
