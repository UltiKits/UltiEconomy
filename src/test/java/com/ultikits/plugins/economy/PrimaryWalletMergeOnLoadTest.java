package com.ultikits.plugins.economy;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.testsupport.InMemoryDataOperator;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiEconomy#25: the one-time merge runs when the module loads, and finishes before the
 * module registers its Vault provider -- the first thing through which another plugin or a player's
 * command could read or move a balance. A merge that cannot finish refuses the module, so nothing can
 * transact on a half-merged store.
 */
@DisplayName("UltiKits/UltiEconomy#25: the wallet merge runs at load, before Vault")
class PrimaryWalletMergeOnLoadTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private UltiEconomy module(DataOperator<PlayerAccountEntity> accounts,
                               DataOperator<CurrencyBalanceEntity> balances, PluginLogger logger) {
        UltiEconomy plugin = mock(UltiEconomy.class);
        EconomyConfig config = new EconomyConfig();
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        when(plugin.getConfig(EconomyConfig.class)).thenReturn(config);
        SimpleContainer context = mock(SimpleContainer.class);
        when(plugin.getContext()).thenReturn(context);
        when(context.getBean(EconomyService.class)).thenReturn(mock(EconomyService.class));
        when(plugin.getCurrencyManager()).thenReturn(new CurrencyManager(YamlConfiguration.loadConfiguration(
                new StringReader("currencies:\n  coins:\n    initial-cash: 1000.0\n    bank-enabled: true\n"
                        + "    min-deposit: 100.0\n    max-bank-balance: -1\n    primary: true\n"))));
        when(plugin.getDataOperator(PlayerAccountEntity.class)).thenReturn(accounts);
        when(plugin.getDataOperator(CurrencyBalanceEntity.class)).thenReturn(balances);
        when(plugin.registerSelf()).thenCallRealMethod();
        return plugin;
    }

    @Test
    @DisplayName("the second wallet is already merged when the Vault provider is registered")
    void mergeFinishesBeforeVault() {
        InMemoryDataOperator<PlayerAccountEntity> accounts =
                InMemoryDataOperator.relational("economy_accounts", PlayerAccountEntity.class, null);
        InMemoryDataOperator<CurrencyBalanceEntity> balances =
                InMemoryDataOperator.relational("currency_balances", CurrencyBalanceEntity.class, null);
        accounts.seed(PlayerAccountEntity.builder().uuid(STEVE.toString()).playerName("Steve").cash(500).bank(100).build());
        balances.seed(CurrencyBalanceEntity.builder().uuid(STEVE.toString()).currencyId("coins").cash(1000).bank(50).build());
        UltiEconomy module = module(accounts, balances, mock(PluginLogger.class));
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("Vault")).thenReturn(mock(Plugin.class));
        ServicesManager services = mock(ServicesManager.class);
        List<String> accountAtRegistration = new ArrayList<>();
        doAnswer(inv -> {
            PlayerAccountEntity steve = accounts.getAll().get(0);
            accountAtRegistration.add(steve.getCash() + "/" + steve.getBank() + " rows=" + balances.getAll().size());
            return null;
        }).when(services).register(eq(Economy.class), any(Economy.class), any(Plugin.class), any(ServicePriority.class));

        boolean started;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            started = module.registerSelf();
        }

        assertThat(started).isTrue();
        assertThat(accountAtRegistration).containsExactly("1500.0/150.0 rows=0");
    }

    @Test
    @DisplayName("a merge that cannot finish refuses the module and registers nothing")
    void failedMergeRefusesTheModule() {
        InMemoryDataOperator<PlayerAccountEntity> accounts =
                InMemoryDataOperator.relational("economy_accounts", PlayerAccountEntity.class, null);
        @SuppressWarnings("unchecked")
        DataOperator<CurrencyBalanceEntity> broken = mock(DataOperator.class);
        when(broken.getAll()).thenThrow(new IllegalStateException("disk full"));
        PluginLogger logger = mock(PluginLogger.class);
        UltiEconomy module = module(accounts, broken, logger);
        PluginManager pluginManager = mock(PluginManager.class);
        // Vault present, PlaceholderAPI absent: a module that went on loading would register with Vault,
        // which is what the assertion below looks for (gate-1 IN-03).
        when(pluginManager.getPlugin("Vault")).thenReturn(mock(Plugin.class));
        ServicesManager services = mock(ServicesManager.class);

        boolean started;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            started = module.registerSelf();
        }

        assertThat(started).isFalse();
        verify(services, never()).register(any(), any(), any(), any());
        verify(logger).error(String.format(CatalogueText.text("en", "economy.log.wallet_merge.failed"), "disk full"));
    }
}
