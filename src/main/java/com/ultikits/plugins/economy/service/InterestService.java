package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.model.CurrencyDefinition;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Scheduled;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Pays bank interest on a fixed schedule while {@code interest.enabled} is true.
 *
 * <p>The service is created whatever {@code interest.enabled} says at boot. The switch is read at
 * every scheduled run instead, so a {@code /ul reload} that turns interest on or off takes effect at
 * the next payment without rescheduling anything (UltiKits/UltiEconomy#15). Before 6.3.0 this class
 * was {@code @ConditionalOnConfig} on the same key but nothing ever scheduled it, so no interest was
 * paid on any server.
 */
@Service
public class InterestService {

    /**
     * 1800 seconds, in ticks. Fixed: a {@code @Scheduled} period is a compile-time constant, and
     * reading it from configuration is requested of the framework as UltiKits/UltiTools-Reborn#531.
     */
    static final long PAYMENT_PERIOD_TICKS = 1800L * 20L;

    private UltiToolsPlugin plugin;
    private EconomyService economyService;
    private EconomyConfig config;
    private DataOperator<PlayerAccountEntity> dataOperator;
    private DataOperator<CurrencyBalanceEntity> currencyDataOperator;
    private CurrencyManager currencyManager;

    public InterestService(UltiToolsPlugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.config = plugin.getConfig(EconomyConfig.class);
        this.dataOperator = plugin.getDataOperator(PlayerAccountEntity.class);
        this.currencyDataOperator = plugin.getDataOperator(CurrencyBalanceEntity.class);
        this.currencyManager = ((UltiEconomy) plugin).getCurrencyManager();
    }

    @SuppressWarnings("all")
    static InterestService createForTest(UltiToolsPlugin plugin,
                                         EconomyService economyService,
                                         EconomyConfig config,
                                         DataOperator<PlayerAccountEntity> dataOperator,
                                         DataOperator<CurrencyBalanceEntity> currencyDataOperator,
                                         CurrencyManager currencyManager) {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            InterestService svc = (InterestService) unsafe.allocateInstance(InterestService.class);
            svc.plugin = plugin;
            svc.economyService = economyService;
            svc.config = config;
            svc.dataOperator = dataOperator;
            svc.currencyDataOperator = currencyDataOperator;
            svc.currencyManager = currencyManager;
            return svc;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The scheduled payment: pays interest if {@code interest.enabled} is true right now, and does
     * nothing at all otherwise -- no account is read and nobody is notified.
     *
     * <p>The first payment comes one full period after the module loads, not at load. Paying at load
     * would pay an extra time on every server restart, which a player could not cause but an
     * operator restarting often would turn into free money.
     *
     * <p>Runs on the main thread. A payment is a read-modify-write of every bank balance, and the
     * rest of this module's balance changes (commands, Vault calls from other plugins) run on the
     * main thread; running it there means no payment can interleave with them and lose an update.
     */
    @Scheduled(delay = PAYMENT_PERIOD_TICKS, period = PAYMENT_PERIOD_TICKS)
    public void payInterestIfEnabled() {
        if (!config.isInterestEnabled()) {
            return;
        }
        distributeInterest();
    }

    /**
     * Distributes interest to all accounts with positive bank balance.
     * Handles both primary currency (PlayerAccountEntity) and per-currency balances
     * (CurrencyBalanceEntity) for bank-enabled currencies.
     * Called by {@link #payInterestIfEnabled()} on the framework's schedule.
     */
    public void distributeInterest() {
        double rate = config.getInterestRate();
        double maxInterest = config.getMaxInterest();

        // Primary currency interest
        List<PlayerAccountEntity> accounts = dataOperator.getAll();
        for (PlayerAccountEntity account : accounts) {
            if (account.getBank() <= 0) {
                continue;
            }

            double interest = account.getBank() * rate;
            if (maxInterest > 0 && interest > maxInterest) {
                interest = maxInterest;
            }

            economyService.addBank(UUID.fromString(account.getUuid()), interest);
            notifyPlayer(account.getUuid(), interest);
        }

        // Per-currency interest
        if (currencyDataOperator == null || currencyManager == null) {
            return;
        }

        List<CurrencyBalanceEntity> currencyBalances = currencyDataOperator.getAll();
        for (CurrencyBalanceEntity balance : currencyBalances) {
            CurrencyDefinition def = currencyManager.getCurrency(balance.getCurrencyId());
            if (def == null || !def.isBankEnabled()) {
                continue;
            }
            if (balance.getBank() <= 0) {
                continue;
            }

            double interest = balance.getBank() * rate;
            if (maxInterest > 0 && interest > maxInterest) {
                interest = maxInterest;
            }

            economyService.addBank(UUID.fromString(balance.getUuid()), interest, balance.getCurrencyId());
            notifyPlayer(balance.getUuid(), interest, balance.getCurrencyId());
        }
    }

    /**
     * Calculates interest for a specific bank balance.
     * Visible for testing.
     */
    double calculateInterest(double bankBalance) {
        if (bankBalance <= 0) {
            return 0.0;
        }
        double interest = bankBalance * config.getInterestRate();
        double maxInterest = config.getMaxInterest();
        if (maxInterest > 0 && interest > maxInterest) {
            interest = maxInterest;
        }
        return interest;
    }

    private void notifyPlayer(String uuid, double interest) {
        try {
            Player player = Bukkit.getPlayer(UUID.fromString(uuid));
            if (player != null && player.isOnline()) {
                String formatted = economyService.formatAmount(interest);
                player.sendMessage(ChatColor.GREEN + String.format(
                        plugin.i18n("银行利息到账: %s"), formatted));
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid UUID — skip notification
        }
    }

    private void notifyPlayer(String uuid, double interest, String currencyId) {
        try {
            Player player = Bukkit.getPlayer(UUID.fromString(uuid));
            if (player != null && player.isOnline()) {
                String formatted = economyService.formatAmount(interest, currencyId);
                CurrencyDefinition def = currencyManager.getCurrency(currencyId);
                String currencyName = def != null ? def.getDisplayName() : currencyId;
                player.sendMessage(ChatColor.GREEN + String.format(
                        plugin.i18n("%s 银行利息到账: %s"), currencyName, formatted));
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid UUID — skip notification
        }
    }
}
