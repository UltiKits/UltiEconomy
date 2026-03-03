package com.ultikits.plugins.economy;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.factory.MoneyNoteFactory;
import com.ultikits.plugins.economy.placeholder.EconomyPlaceholderExpansion;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.service.LeaderboardService;
import com.ultikits.plugins.economy.vault.VaultEconomyProvider;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@UltiToolsModule
public class UltiEconomy extends UltiToolsPlugin {

    private VaultEconomyProvider vaultProvider;
    private volatile CurrencyManager currencyManager;
    private volatile MoneyNoteFactory noteFactory;

    public CurrencyManager getCurrencyManager() {
        if (currencyManager == null) {
            synchronized (this) {
                if (currencyManager == null) {
                    File currenciesFile = getConfigFile("config/currencies.yml");
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(currenciesFile);
                    currencyManager = new CurrencyManager(yaml);
                }
            }
        }
        return currencyManager;
    }

    public MoneyNoteFactory getMoneyNoteFactory() {
        if (noteFactory == null) {
            synchronized (this) {
                if (noteFactory == null) {
                    Plugin bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");
                    noteFactory = new MoneyNoteFactory(bukkitPlugin);
                }
            }
        }
        return noteFactory;
    }

    @Override
    public boolean registerSelf() {
        EconomyService economyService = getContext().getBean(EconomyService.class);
        EconomyConfig config = getConfig(EconomyConfig.class);
        vaultProvider = new VaultEconomyProvider(economyService, config);

        Plugin vaultPlugin = Bukkit.getPluginManager().getPlugin("Vault");
        if (vaultPlugin != null) {
            Bukkit.getServicesManager().register(
                    Economy.class, vaultProvider, vaultPlugin, ServicePriority.Normal);
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            LeaderboardService leaderboardService = getContext().getBean(LeaderboardService.class);
            new EconomyPlaceholderExpansion(economyService, leaderboardService,
                    getCurrencyManager()).register();
        }

        return true;
    }

    @Override
    public void unregisterSelf() {
        if (vaultProvider != null) {
            Bukkit.getServicesManager().unregister(Economy.class, vaultProvider);
        }
    }

    @Override
    public List<String> supported() {
        return Arrays.asList("zh", "en");
    }
}
