package com.ultikits.plugins.economy;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.entity.WalletMergeClaimEntity;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.service.PrimaryWalletMerge;
import com.ultikits.plugins.economy.testsupport.InMemoryDataOperator;
import com.ultikits.plugins.economy.testsupport.SteppedOperator;
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
        return module(accounts, balances,
                InMemoryDataOperator.relational("economy_wallet_merge_claim", WalletMergeClaimEntity.class, null),
                logger);
    }

    private UltiEconomy module(DataOperator<PlayerAccountEntity> accounts,
                               DataOperator<CurrencyBalanceEntity> balances,
                               DataOperator<WalletMergeClaimEntity> claims, PluginLogger logger) {
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
        when(plugin.getDataOperator(WalletMergeClaimEntity.class)).thenReturn(claims);
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

    @Test
    @DisplayName("on a database another server is merging, the module writes nothing to the wallets, waits for that server, and then starts")
    void waitsForTheServerHoldingTheClaim() {
        InMemoryDataOperator<PlayerAccountEntity> accounts =
                InMemoryDataOperator.relational("economy_accounts", PlayerAccountEntity.class, null);
        InMemoryDataOperator<CurrencyBalanceEntity> balances =
                InMemoryDataOperator.relational("currency_balances", CurrencyBalanceEntity.class, null);
        InMemoryDataOperator<WalletMergeClaimEntity> claims =
                InMemoryDataOperator.relational("economy_wallet_merge_claim", WalletMergeClaimEntity.class, null);
        accounts.seed(PlayerAccountEntity.builder().uuid(STEVE.toString()).playerName("Steve").cash(500).bank(100).build());
        balances.seed(CurrencyBalanceEntity.builder().uuid(STEVE.toString()).currencyId("coins").cash(1000).bank(50).build());
        WalletMergeClaimEntity held = new WalletMergeClaimEntity("other-server", "1", "2026-09-25T08:00:00Z");
        held.setId("primary-wallet-merge");
        claims.seed(held);
        PluginLogger otherLogger = mock(PluginLogger.class);
        com.ultikits.ultitools.abstracts.UltiToolsPlugin other = mock(com.ultikits.ultitools.abstracts.UltiToolsPlugin.class);
        when(other.getLogger()).thenReturn(otherLogger);
        when(other.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        // The other server finishes its merge and removes its claim while this module sleeps its first
        // second (a real one: the module's own clock), before the module looks at the claim again.
        boolean[] otherFinished = {false};
        int[] looks = {0};
        DataOperator<WalletMergeClaimEntity> claimsSeenByThisModule = new SteppedOperator<>("claims", claims, call -> {
            if (call.startsWith("getById") && ++looks[0] == 2) {
                otherFinished[0] = true;
                new PrimaryWalletMerge(other, accounts, balances, "coins", uuid -> "x").run();
                claims.delById("primary-wallet-merge");
            }
        });
        // What this module writes to the wallets.
        List<String> writes = new ArrayList<>();
        DataOperator<PlayerAccountEntity> accountsSeen = new SteppedOperator<>("economy_accounts", accounts, call -> {
            if (!call.startsWith("get") && !call.startsWith("exist")) {
                writes.add(call);
            }
        });
        DataOperator<CurrencyBalanceEntity> balancesSeen = new SteppedOperator<>("currency_balances", balances, call -> {
            if (!call.startsWith("get") && !call.startsWith("exist")) {
                writes.add(call);
            }
        });
        PluginLogger logger = mock(PluginLogger.class);
        UltiEconomy module = module(accountsSeen, balancesSeen, claimsSeenByThisModule, logger);
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("Vault")).thenReturn(mock(Plugin.class));
        ServicesManager services = mock(ServicesManager.class);

        boolean started;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            started = module.registerSelf();
        }

        assertThat(started).isTrue();
        assertThat(writes).isEmpty();
        assertThat(otherFinished[0]).isTrue();
        assertThat(accounts.getAll()).hasSize(1);
        assertThat(accounts.getAll().get(0).getCash()).isEqualTo(1500.0);
        assertThat(accounts.getAll().get(0).getBank()).isEqualTo(150.0);
        verify(logger).info(String.format(CatalogueText.text("en", "economy.log.wallet_merge.claim_waiting"),
                "2026-09-25T08:00:00Z"));
        verify(logger).info(CatalogueText.text("en", "economy.log.wallet_merge.claim_wait_over"));
        assertThat(claims.durable()).isEmpty();
        verify(services).register(eq(Economy.class), any(Economy.class), any(Plugin.class), any(ServicePriority.class));
    }
}
