package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.config.StartupWarnings;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.entity.TreasuryEntity;
import com.ultikits.plugins.economy.entity.WalletMergeClaimEntity;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.service.EconomyTestWorld;
import com.ultikits.plugins.economy.service.TaxService;
import com.ultikits.plugins.economy.testsupport.InMemoryDataOperator;
import com.ultikits.plugins.economy.vault.VaultEconomyProvider;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code config.yml} owns the primary currency's name and symbol; the primary entry of
 * {@code currencies.yml} follows it, and a different name or symbol written there is reported at
 * start-up. The shipped name is written into {@code config.yml} in the server's language (maintainer
 * decision 2026-09-27, UltiKits/UltiEconomy#32).
 */
@DisplayName("config.yml owns the primary currency's name and symbol (#32)")
class PrimaryCurrencyNamingTest {

    @TempDir
    Path folder;

    private UltiEconomy plugin;
    private EconomyConfig config;
    private PluginLogger logger;

    private void currenciesFile(String displayName, String symbol) throws Exception {
        Path file = folder.resolve("currencies.yml");
        Files.write(file, ("currencies:\n  coins:\n    display-name: '" + displayName + "'\n    symbol: '" + symbol
                + "'\n    primary: true\n  gems:\n    display-name: 'Gems'\n    symbol: 'G'\n    bank-enabled: true\n")
                .getBytes(StandardCharsets.UTF_8));
        Method getConfigFile = UltiToolsPlugin.class.getDeclaredMethod("getConfigFile", String.class);
        getConfigFile.setAccessible(true); // NOPMD - protected lookup the module's currency registry uses
        doReturn(file.toFile()).when(plugin);
        getConfigFile.invoke(plugin, "config/currencies.yml");
    }

    @BeforeEach
    void setUp() {
        plugin = mock(UltiEconomy.class);
        config = spy(new EconomyConfig());
        logger = mock(PluginLogger.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        when(plugin.getConfig(EconomyConfig.class)).thenReturn(config);
        when(plugin.getCurrencyManager()).thenCallRealMethod();
    }

    @Nested
    @DisplayName("At run time the primary currency shows config.yml's name and symbol")
    class RunTime {

        @BeforeEach
        void names() throws Exception {
            config.setCurrencyName("Gold");
            config.setCurrencySymbol("G$");
            currenciesFile("Coins", "$");
        }

        @Test
        @DisplayName("Vault, /eco treasury and the currency-scoped format all show Gold and G$")
        void everyReaderShowsTheConfigName() throws Exception {
            CurrencyManager currencies = plugin.getCurrencyManager();
            assertThat(currencies.getPrimaryCurrency().getDisplayName()).isEqualTo("Gold");
            assertThat(currencies.getPrimaryCurrency().getSymbol()).isEqualTo("G$");

            VaultEconomyProvider vault = new VaultEconomyProvider(mock(EconomyService.class), config, plugin);
            assertThat(vault.currencyNamePlural()).isEqualTo("Gold");

            EconomyTestWorld world = EconomyTestWorld.relational();
            com.ultikits.plugins.economy.service.EconomyServiceImpl service =
                    com.ultikits.plugins.economy.service.EconomyServiceImplAccess.withCurrencies(world, config, currencies);
            assertThat(service.formatAmount(10.0, "coins")).isEqualTo("G$10.00");

            InMemoryDataOperator<TreasuryEntity> treasury =
                    InMemoryDataOperator.relational("economy_treasury", TreasuryEntity.class, null);
            EcoAdminCommand admin = EcoAdminCommand.createForTest(plugin, service, new TaxService(config, treasury), currencies);
            CommandSender console = mock(CommandSender.class);
            admin.onTreasury(console);
            ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
            verify(console, atLeast(1)).sendMessage(lines.capture());
            assertThat(lines.getAllValues()).anySatisfy(line -> assertThat(line).contains("Gold: G$0.00"));
            assertThat(lines.getAllValues()).noneMatch(line -> line.contains("Coins"));
        }

        @Test
        @DisplayName("A later change to config.yml (a reload) is followed without a restart")
        void aReloadedNameIsFollowed() {
            CurrencyManager currencies = plugin.getCurrencyManager();

            config.setCurrencyName("Crowns");

            assertThat(currencies.getCurrency("coins").getDisplayName()).isEqualTo("Crowns");
            assertThat(currencies.resolve("coins").getDisplayName()).isEqualTo("Crowns");
            assertThat(currencies.getAllCurrencies()).anySatisfy(def -> assertThat(def.getDisplayName()).isEqualTo("Crowns"));
            assertThat(currencies.getCurrency("gems").getDisplayName()).isEqualTo("Gems");
        }
    }

    @Nested
    @DisplayName("Start-up warning when currencies.yml names the primary currency differently")
    class Warnings {

        private List<String> warnings() {
            ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
            verify(logger, atLeast(0)).warn(line.capture());
            return line.getAllValues();
        }

        @Test
        @DisplayName("A display-name or symbol the operator wrote that differs from config.yml is reported, naming both")
        void anEditedDifferentNameIsReported() throws Exception {
            config.setCurrencyName("Gold");
            currenciesFile("Bullion", "B");

            StartupWarnings.logPrimaryCurrencyConflicts(config, plugin.getCurrencyManager(), logger, plugin);

            assertThat(warnings()).anySatisfy(w -> assertThat(w).contains("display-name").contains("Bullion")
                    .contains("currency-name").contains("Gold"));
            assertThat(warnings()).anySatisfy(w -> assertThat(w).contains("symbol").contains("B")
                    .contains("currency-symbol").contains("$"));
        }

        @Test
        @DisplayName("The shipped display-name, in any language, is not reported: it simply follows config.yml")
        void theShippedNameIsNotReported() throws Exception {
            config.setCurrencyName("金币");
            currenciesFile("Coins", "$");

            StartupWarnings.logPrimaryCurrencyConflicts(config, plugin.getCurrencyManager(), logger, plugin);

            assertThat(warnings()).noneMatch(w -> w.contains("display-name"));
            assertThat(warnings()).noneMatch(w -> w.contains("symbol"));
        }
    }

    @Nested
    @DisplayName("The shipped name is written into config.yml in the server's language")
    class ServerLanguage {

        private void start(String language) throws Exception {
            currenciesFile("Coins", "$");
            doNothing().when(config).save();
            when(plugin.getLanguageCode()).thenReturn(language);
            SimpleContainer context = mock(SimpleContainer.class);
            when(plugin.getContext()).thenReturn(context);
            when(context.getBean(EconomyService.class)).thenReturn(mock(EconomyService.class));
            when(plugin.getDataOperator(PlayerAccountEntity.class)).thenReturn(
                    InMemoryDataOperator.relational("economy_accounts", PlayerAccountEntity.class, null));
            when(plugin.getDataOperator(CurrencyBalanceEntity.class)).thenReturn(
                    InMemoryDataOperator.relational("currency_balances", CurrencyBalanceEntity.class, null));
            when(plugin.getDataOperator(WalletMergeClaimEntity.class)).thenReturn(
                    InMemoryDataOperator.relational("economy_wallet_merge_claim", WalletMergeClaimEntity.class, null));
            when(plugin.registerSelf()).thenCallRealMethod();
            Method onReload = UltiToolsPlugin.class.getDeclaredMethod("onReload");
            onReload.setAccessible(true); // NOPMD - stubbing the protected hook the framework calls after a reload
            org.mockito.Mockito.doCallRealMethod().when(plugin);
            onReload.invoke(plugin);
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
                assertThat(plugin.registerSelf()).isTrue();
            }
        }

        @Test
        @DisplayName("Under language: zh the shipped Coins becomes the Chinese name, and the file is saved")
        void theShippedNameFollowsTheLanguage() throws Exception {
            start("zh");

            assertThat(config.getCurrencyName()).isEqualTo(CatalogueText.text("zh", "economy.config.currency_name"));
            assertThat(config.getCurrencyName()).isNotEqualTo("Coins");
            verify(config).save();
        }

        @Test
        @DisplayName("A reload after the server's language changed rewrites a name that is still built-in")
        void aReloadFollowsALanguageSwitch() throws Exception {
            start("en");
            assertThat(config.getCurrencyName()).isEqualTo("Coins");

            when(plugin.getLanguageCode()).thenReturn("zh");
            Method onReload = UltiToolsPlugin.class.getDeclaredMethod("onReload");
            onReload.setAccessible(true); // NOPMD - the framework calls this protected hook after a reload
            onReload.invoke(plugin);

            assertThat(config.getCurrencyName()).isEqualTo(CatalogueText.text("zh", "economy.config.currency_name"));
        }

        @Test
        @DisplayName("A name the operator chose is kept")
        void anOperatorNameIsKept() throws Exception {
            config.setCurrencyName("Gold");

            start("zh");

            assertThat(config.getCurrencyName()).isEqualTo("Gold");
        }
    }
}
