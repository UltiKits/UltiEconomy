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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code interest.rate} must be between 0 and 1 and {@code interest.max-interest} must be -1 (no cap)
 * or at least 0 (maintainer decision 2026-09-27). A value outside its range is not used: the
 * declared default is, with a warning naming the key, the value as written and the default -- at
 * load and again after every reload (UltiKits/UltiEconomy#29).
 */
@DisplayName("interest.rate and interest.max-interest out of range fall back to their defaults with a warning (#29)")
class InterestSettingsRangeTest {

    private EconomyConfig config;
    private PluginLogger logger;

    private UltiEconomy module() {
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
                new StringReader("currencies:\n  coins:\n    primary: true\n"))));
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

    @ParameterizedTest(name = "rate {0} is used as written")
    @CsvSource({"0", "0.03", "1"})
    @DisplayName("A rate from 0 to 1 is used as written, with no warning")
    void aRateInRangeIsKept(double rate) {
        UltiEconomy module = module();
        config.setInterestRate(rate);

        start(module);

        assertThat(config.getInterestRate()).isEqualTo(rate);
        assertThat(warnings()).noneMatch(w -> w.contains("interest.rate"));
    }

    @ParameterizedTest(name = "rate {0} falls back to 0.03")
    @CsvSource({"3", "1.01", "-0.01"})
    @DisplayName("A rate outside 0 to 1 is replaced by the default 0.03, and the warning names the key, the value and the default")
    void aRateOutOfRangeFallsBack(String written) {
        UltiEconomy module = module();
        config.setInterestRate(Double.parseDouble(written));

        start(module);

        assertThat(config.getInterestRate()).isEqualTo(0.03);
        assertThat(warnings()).anySatisfy(w -> assertThat(w)
                .contains("interest.rate").contains(written).contains("0.03"));
    }

    @ParameterizedTest(name = "max-interest {0} is used as written")
    @CsvSource({"-1", "0", "10000"})
    @DisplayName("A max-interest of -1 (no cap) or at least 0 is used as written")
    void aCapInRangeIsKept(double cap) {
        UltiEconomy module = module();
        config.setMaxInterest(cap);

        start(module);

        assertThat(config.getMaxInterest()).isEqualTo(cap);
        assertThat(warnings()).noneMatch(w -> w.contains("interest.max-interest"));
    }

    @ParameterizedTest(name = "max-interest {0} falls back to 10000")
    @CsvSource({"-0.5", "-2"})
    @DisplayName("A max-interest below 0 other than -1 is replaced by the default 10000, with a warning")
    void aCapOutOfRangeFallsBack(String written) {
        UltiEconomy module = module();
        config.setMaxInterest(Double.parseDouble(written));

        start(module);

        assertThat(config.getMaxInterest()).isEqualTo(10000.0);
        assertThat(warnings()).anySatisfy(w -> assertThat(w)
                .contains("interest.max-interest").contains(written).contains("10000"));
    }

    @Test
    @DisplayName("After a reload the check runs again: a rate edited to 3 falls back to 0.03")
    void theCheckRunsAgainAfterAReload() throws Exception {
        UltiEconomy module = module();
        start(module);
        verify(logger, never()).warn(org.mockito.ArgumentMatchers.contains("interest.rate"));

        config.setInterestRate(3);
        fireReload(config);

        assertThat(config.getInterestRate()).isEqualTo(0.03);
        verify(logger).warn(org.mockito.ArgumentMatchers.contains("interest.rate"));
    }
}
