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
 * {@code tax.transaction-tax.rate} is the fraction of a transfer kept as tax, so only 0 to 1 is usable:
 * a negative rate credits the recipient more than the payer paid, creating money, and a rate above 1
 * credits the recipient a negative amount, taking money from them. Like {@code interest.rate}
 * (UltiKits/UltiEconomy#29, maintainer decision 2026-09-27 for every value the module cannot use), a
 * value outside the range is replaced by the declared default with a warning naming the key, the value
 * as written and the default, at load and after every reload.
 */
@DisplayName("tax.transaction-tax.rate out of range falls back to its default with a warning")
class TransactionTaxRateRangeTest {

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
    @CsvSource({"0", "0.05", "1"})
    @DisplayName("A transaction tax rate from 0 to 1 is used as written, with no warning")
    void aRateInRangeIsKept(double rate) {
        UltiEconomy module = module();
        config.setTransactionTaxRate(rate);

        start(module);

        assertThat(config.getTransactionTaxRate()).isEqualTo(rate);
        assertThat(warnings()).noneMatch(w -> w.contains("tax.transaction-tax.rate"));
    }

    @ParameterizedTest(name = "rate {0} falls back to 0.05")
    @CsvSource({"2", "1.5", "-0.5"})
    @DisplayName("A transaction tax rate outside 0 to 1 is replaced by the default 0.05, and the warning names the key, the value and the default")
    void aRateOutOfRangeFallsBack(String written) {
        UltiEconomy module = module();
        config.setTransactionTaxRate(Double.parseDouble(written));

        start(module);

        assertThat(config.getTransactionTaxRate()).isEqualTo(0.05);
        assertThat(warnings()).anySatisfy(w -> assertThat(w)
                .contains("tax.transaction-tax.rate").contains(written).contains("0.05"));
    }

    @Test
    @DisplayName("After a reload the check runs again: a rate edited to -0.5 falls back to 0.05")
    void theCheckRunsAgainAfterAReload() throws Exception {
        UltiEconomy module = module();
        start(module);

        config.setTransactionTaxRate(-0.5);
        fireReload(config);

        assertThat(config.getTransactionTaxRate()).isEqualTo(0.05);
        verify(logger).warn(org.mockito.ArgumentMatchers.contains("tax.transaction-tax.rate"));
    }
}
