package com.ultikits.plugins.economy;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.entity.WalletMergeClaimEntity;
import com.ultikits.plugins.economy.i18n.CatalogueText;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiEconomy#35: a bank cap is -1 (no cap) or a value above 0. Every reader checks the cap as
 * {@code maxBalance > 0}, so before this fix {@code 0} and every other negative value silently meant
 * "no cap" too, while the setting documents only {@code -1}; an operator who writes {@code 0} meaning
 * "no bank deposits" got an unlimited bank. By the maintainer's rule of 2026-09-27 a value the module
 * cannot use is refused and named: the declared default ({@code -1}) is used, with a warning naming the
 * key, the value as written and the default. The same rule covers each currency's own
 * {@code max-bank-balance} in {@code currencies.yml}, the third cap site the issue names
 * ({@code EconomyServiceImpl#depositToBank(UUID, double, String)}).
 */
@DisplayName("UltiEconomy#35: a bank cap outside -1 or above 0 falls back to -1 with a warning")
class BankMaxBalanceRangeTest {

    private static final String CURRENCIES = "currencies:\n  coins:\n    primary: true\n";

    private EconomyConfig config;
    private PluginLogger logger;

    private UltiEconomy module(String currenciesYaml) {
        UltiEconomy plugin = mock(UltiEconomy.class);
        config = new EconomyConfig();
        logger = mock(PluginLogger.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        when(plugin.getConfig(EconomyConfig.class)).thenReturn(config);
        SimpleContainer context = mock(SimpleContainer.class);
        when(plugin.getContext()).thenReturn(context);
        when(context.getBean(EconomyService.class)).thenReturn(mock(EconomyService.class));
        when(plugin.getCurrencyManager()).thenReturn(new CurrencyManager(YamlConfiguration.loadConfiguration(
                new StringReader(currenciesYaml))));
        when(plugin.getDataOperator(PlayerAccountEntity.class)).thenReturn(
                InMemoryDataOperator.relational("economy_accounts", PlayerAccountEntity.class, null));
        when(plugin.getDataOperator(CurrencyBalanceEntity.class)).thenReturn(
                InMemoryDataOperator.relational("currency_balances", CurrencyBalanceEntity.class, null));
        when(plugin.getDataOperator(WalletMergeClaimEntity.class)).thenReturn(
                InMemoryDataOperator.relational("economy_wallet_merge_claim", WalletMergeClaimEntity.class, null));
        when(plugin.registerSelf()).thenCallRealMethod();
        return plugin;
    }

    private void start(UltiEconomy module) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            assertThat(module.registerSelf()).isTrue();
        }
    }

    private List<String> warnings() {
        ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
        verify(logger, atLeast(0)).warn(line.capture());
        return line.getAllValues();
    }

    private static void fireReload(EconomyConfig config) throws Exception {
        // What the framework does at the end of reloading a config file in place.
        Method notify = com.ultikits.ultitools.abstracts.AbstractConfigEntity.class.getDeclaredMethod("notifyChangeListeners");
        notify.setAccessible(true); // NOPMD - the framework's own reload step, protected
        notify.invoke(config);
    }

    @Nested
    @DisplayName("bank.max-balance in config.yml (the primary currency's bank)")
    class PrimaryBankCap {

        @ParameterizedTest(name = "bank.max-balance {0} is used as written")
        @CsvSource({"-1", "5000", "0.5"})
        @DisplayName("-1 (no cap) and a cap above 0 are used as written, with no warning")
        void aUsableCapIsKept(double cap) {
            UltiEconomy module = module(CURRENCIES);
            config.setMaxBankBalance(cap);

            start(module);

            assertThat(config.getMaxBankBalance()).isEqualTo(cap);
            assertThat(warnings()).noneMatch(w -> w.contains("bank.max-balance"));
        }

        @ParameterizedTest(name = "bank.max-balance {0} falls back to -1")
        @CsvSource({"0", "-5", "-0.5"})
        @DisplayName("0 and any negative value other than -1 are refused: -1 (no cap) is used, and the warning names the key, the value and the default")
        void anUnusableCapFallsBack(String written) {
            UltiEconomy module = module(CURRENCIES);
            config.setMaxBankBalance(Double.parseDouble(written));

            start(module);

            assertThat(config.getMaxBankBalance()).isEqualTo(-1.0);
            assertThat(warnings()).anySatisfy(w -> assertThat(w)
                    .contains("bank.max-balance").contains(" is " + written + ",").contains("default -1"));
        }

        @Test
        @DisplayName("an infinite cap (.inf) is refused like any other value outside the range: no cap is written -1")
        void anInfiniteCapFallsBack() {
            UltiEconomy module = module(CURRENCIES);
            config.setMaxBankBalance(Double.POSITIVE_INFINITY);

            start(module);

            assertThat(config.getMaxBankBalance()).isEqualTo(-1.0);
            assertThat(warnings()).anySatisfy(w -> assertThat(w).contains("bank.max-balance").contains("Infinity"));
        }

        @Test
        @DisplayName("after a reload the check runs again: a cap edited to 0 falls back to -1 with a warning")
        void theCheckRunsAgainAfterAReload() throws Exception {
            UltiEconomy module = module(CURRENCIES);
            start(module);
            assertThat(warnings()).noneMatch(w -> w.contains("bank.max-balance"));

            config.setMaxBankBalance(0);
            fireReload(config);

            assertThat(config.getMaxBankBalance()).isEqualTo(-1.0);
            assertThat(warnings()).anySatisfy(w -> assertThat(w).contains("bank.max-balance").contains(" is 0,"));
        }
    }

    @Nested
    @DisplayName("max-bank-balance of a non-primary currency in currencies.yml")
    class CurrencyBankCap {

        private String currencies(String silverCap) {
            return "currencies:\n  coins:\n    primary: true\n    max-bank-balance: 0\n"
                    + "  silver:\n    bank-enabled: true\n    max-bank-balance: " + silverCap + "\n";
        }

        @ParameterizedTest(name = "max-bank-balance {0} is used as written")
        @CsvSource({"-1", "500"})
        @DisplayName("-1 (no cap) and a cap above 0 are used as written, with no warning")
        void aUsableCapIsKept(double cap) {
            UltiEconomy module = module(currencies(String.valueOf(cap)));

            start(module);

            assertThat(module.getCurrencyManager().getCurrency("silver").getMaxBankBalance()).isEqualTo(cap);
            assertThat(warnings()).noneMatch(w -> w.contains("max-bank-balance"));
        }

        @ParameterizedTest(name = "max-bank-balance {0} falls back to -1")
        @CsvSource({"0", "-5"})
        @DisplayName("0 and any negative value other than -1 are refused: -1 (no cap) is used, and the warning names the file, the key, the value and the default")
        void anUnusableCapFallsBack(String written) {
            UltiEconomy module = module(currencies(written));

            start(module);

            assertThat(module.getCurrencyManager().getCurrency("silver").getMaxBankBalance()).isEqualTo(-1.0);
            assertThat(warnings()).anySatisfy(w -> assertThat(w).contains("config/currencies.yml")
                    .contains("currencies.silver.max-bank-balance").contains(" is " + written + ",").contains("default -1"));
        }

        @Test
        @DisplayName("control: the primary currency's own block is not read for the cap (config.yml governs it), so its 0 is not reported here")
        void thePrimaryBlockIsNotReported() {
            UltiEconomy module = module(currencies("500"));

            start(module);

            assertThat(warnings()).noneMatch(w -> w.contains("currencies.coins.max-bank-balance") && w.contains("default -1"));
        }
    }
}
