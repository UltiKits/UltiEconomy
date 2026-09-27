package com.ultikits.plugins.economy;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.config.StartupWarnings;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.entity.WalletMergeClaimEntity;
import com.ultikits.plugins.economy.factory.MoneyNoteFactory;
import com.ultikits.plugins.economy.placeholder.EconomyPlaceholderExpansion;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.service.LeaderboardService;
import com.ultikits.plugins.economy.service.MergeClaim;
import com.ultikits.plugins.economy.service.PrimaryWalletMerge;
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
import java.util.UUID;

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
                    noteFactory = new MoneyNoteFactory(bukkitPlugin, this);
                }
            }
        }
        return noteFactory;
    }

    @Override
    public boolean registerSelf() {
        // UltiKits/UltiEconomy#25: the primary currency has one wallet, the account. Merge the second
        // wallet UltiEconomy 2.0.0 kept for it (once; a later start finds nothing) before registering
        // anything through which a player or another plugin could read or move a balance. A merge
        // that cannot finish refuses the module; the next start resumes it. Servers sharing one
        // database take turns through the claim, and a server whose turn has not come waits here.
        PrimaryWalletMerge merge = new PrimaryWalletMerge(this,
                getDataOperator(PlayerAccountEntity.class),
                getDataOperator(CurrencyBalanceEntity.class),
                getCurrencyManager().getPrimaryCurrencyId(),
                UltiEconomy::offlinePlayerName);
        boolean merged = new MergeClaim(this, () -> getDataOperator(WalletMergeClaimEntity.class))
                .runExclusively(merge);
        if (!merged) {
            return false;
        }
        EconomyService economyService = getContext().getBean(EconomyService.class);
        EconomyConfig config = getConfig(EconomyConfig.class);
        // Switches whose effect changed in 6.3.0 take the value on the operator's disk, which
        // they may never have chosen; say so once per boot (maintainer decision 2026-09-23).
        StartupWarnings.log(config, getLogger(), this);
        StartupWarnings.logPrimaryCurrencyConflicts(config, getCurrencyManager(), getLogger(), this);
        vaultProvider = new VaultEconomyProvider(economyService, config, this);

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
    protected void onUnregister() {
        if (vaultProvider != null) {
            Bukkit.getServicesManager().unregister(Economy.class, vaultProvider);
        }
    }

    /**
     * The name the server knows for {@code uuid}, for an account the wallet merge creates for a
     * player who had only a second wallet; the UUID itself when the server knows no name.
     */
    static String offlinePlayerName(String uuid) {
        try {
            String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
            return name != null ? name : uuid;
        } catch (RuntimeException e) {
            return uuid;
        }
    }

    @Override
    public List<String> supported() {
        return Arrays.asList("zh", "en");
    }
}
