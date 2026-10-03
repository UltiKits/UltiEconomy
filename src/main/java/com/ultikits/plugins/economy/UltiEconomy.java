package com.ultikits.plugins.economy;

import com.ultikits.plugins.economy.config.ConfigTextDefaults;
import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.config.ConfigRanges;
import com.ultikits.plugins.economy.config.StartupWarnings;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.entity.WalletMergeClaimEntity;
import com.ultikits.plugins.economy.factory.MoneyNoteFactory;
import com.ultikits.plugins.placeholderapi.economy.EconomyPlaceholderExpansion;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.service.LeaderboardService;
import com.ultikits.plugins.economy.service.MergeClaim;
import com.ultikits.plugins.economy.service.PrimaryWalletMerge;
import com.ultikits.plugins.economy.vault.VaultEconomyProvider;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;
import com.ultikits.ultitools.interfaces.ConfigChangeListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@UltiToolsModule
public class UltiEconomy extends UltiToolsPlugin {

    private VaultEconomyProvider vaultProvider;
    // Created only when PlaceholderAPI is installed; unregistered again on unload (UltiKits/UltiEconomy#23).
    // Held as Object, not EconomyPlaceholderExpansion (UltiKits/UltiEconomy#34): the framework's
    // container reflects over this class's declared fields (AutowireFactory#autowireBean), and
    // Class#getDeclaredFields() eagerly resolves every field's declared type. A field typed
    // EconomyPlaceholderExpansion forces the JVM to load that class, which forces loading its
    // PlaceholderAPI supertype -- on a server without PlaceholderAPI this throws
    // NoClassDefFoundError and the whole module fails to load, regardless of whether PlaceholderAPI
    // is ever actually used. The concrete type is still used, and only used, inside the
    // PlaceholderAPI-present branches of registerSelf()/onUnregister() below, where the cast is
    // resolved lazily at first execution -- never on a server without PlaceholderAPI, because
    // those branches never run there.
    private Object placeholderExpansion;
    // Re-checks the interest settings after each reload; removed on unload (UltiKits/UltiEconomy#29)
    private ConfigChangeListener configRangesWatch;
    private volatile CurrencyManager currencyManager;
    private volatile MoneyNoteFactory noteFactory;

    public CurrencyManager getCurrencyManager() {
        if (currencyManager == null) {
            synchronized (this) {
                if (currencyManager == null) {
                    File currenciesFile = getConfigFile("config/currencies.yml");
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(currenciesFile);
                    CurrencyManager created = new CurrencyManager(yaml);
                    // config.yml owns the primary currency's name and symbol (UltiKits/UltiEconomy#32).
                    EconomyConfig config = getConfig(EconomyConfig.class);
                    if (config != null) {
                        created.usePrimaryNaming(config::getCurrencyName, config::getCurrencySymbol);
                    }
                    currencyManager = created;
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
        writeConfigTextInServerLanguage(config);
        // An interest rate, interest cap or transaction tax rate outside what the module can use falls back
        // to its default, now and after every reload (UltiKits/UltiEconomy#29); warned before the start-up
        // warnings print them.
        configRangesWatch = ConfigRanges.watch(config, getLogger(), this);
        // Switches whose effect changed in 6.3.0 take the value on the operator's disk, which
        // they may never have chosen; say so once per boot (maintainer decision 2026-09-23).
        StartupWarnings.log(config, getLogger(), this);
        StartupWarnings.logPrimaryCurrencyConflicts(config, getCurrencyManager(), getLogger(), this);
        // A non-primary currency's bank cap that is neither -1 nor above 0 is refused (UltiKits/UltiEconomy#35).
        StartupWarnings.logRefusedCurrencyBankCaps(getCurrencyManager(), getLogger(), this);
        vaultProvider = new VaultEconomyProvider(economyService, config, this);

        Plugin vaultPlugin = Bukkit.getPluginManager().getPlugin("Vault");
        if (vaultPlugin != null) {
            Bukkit.getServicesManager().register(
                    Economy.class, vaultProvider, vaultPlugin, ServicePriority.Normal);
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            LeaderboardService leaderboardService = getContext().getBean(LeaderboardService.class);
            EconomyPlaceholderExpansion expansion = new EconomyPlaceholderExpansion(economyService,
                    leaderboardService, getCurrencyManager());
            expansion.register();
            placeholderExpansion = expansion;
        }

        return true;
    }

    @Override
    protected void onUnregister() {
        if (vaultProvider != null) {
            Bukkit.getServicesManager().unregister(Economy.class, vaultProvider);
        }
        // The expansion would otherwise keep answering placeholders from this unloaded module until
        // the server restarts (UltiKits/UltiEconomy#23).
        if (placeholderExpansion != null) {
            ((EconomyPlaceholderExpansion) placeholderExpansion).unregister();
            placeholderExpansion = null;
        }
        if (configRangesWatch != null) {
            EconomyConfig config = getConfig(EconomyConfig.class);
            if (config != null) {
                config.removeChangeListener(configRangesWatch);
            }
            configRangesWatch = null;
        }
    }

    /**
     * Writes the configuration's built-in text in the server's language after the framework has
     * reloaded the file and the language (UltiKits/UltiEconomy#32).
     */
    @Override
    protected void onReload() {
        writeConfigTextInServerLanguage(getConfig(EconomyConfig.class));
    }

    /**
     * Writes the primary currency's name into {@code config.yml} in the server's language while it is
     * still built-in text, and saves the file when it changed; a name the operator chose is kept
     * (maintainer decision 2026-09-25, UltiKits/UltiEconomy#32).
     */
    private void writeConfigTextInServerLanguage(EconomyConfig config) {
        if (config == null || !config.materializeText(
                ConfigTextDefaults.jarLanguage(EconomyConfig.class, getLanguageCode())::getLocalizedText)) {
            return;
        }
        try {
            config.save();
        } catch (IOException e) {
            getLogger().warn(String.format(i18n("economy.warn.config_save_failed"), config.getConfigFilePath(),
                    e.getMessage()));
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
