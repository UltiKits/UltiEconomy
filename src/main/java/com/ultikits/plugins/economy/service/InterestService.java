package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.model.CurrencyDefinition;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.ConditionalOnConfig;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnConfig(value = "config/config.yml", path = "interest.enabled")
public class InterestService {

    private final UltiToolsPlugin plugin;
    private final EconomyService economyService;
    private final EconomyConfig config;
    private final DataOperator<PlayerAccountEntity> dataOperator;
    private final DataOperator<CurrencyBalanceEntity> currencyDataOperator;
    private final CurrencyManager currencyManager;

    public InterestService(UltiToolsPlugin plugin,
                           EconomyService economyService,
                           EconomyConfig config,
                           DataOperator<PlayerAccountEntity> dataOperator,
                           DataOperator<CurrencyBalanceEntity> currencyDataOperator,
                           CurrencyManager currencyManager) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.config = config;
        this.dataOperator = dataOperator;
        this.currencyDataOperator = currencyDataOperator;
        this.currencyManager = currencyManager;
    }

    // Backward-compatible constructor
    public InterestService(UltiToolsPlugin plugin,
                           EconomyService economyService,
                           EconomyConfig config,
                           DataOperator<PlayerAccountEntity> dataOperator) {
        this(plugin, economyService, config, dataOperator, null, null);
    }

    /**
     * Distributes interest to all accounts with positive bank balance.
     * Handles both primary currency (PlayerAccountEntity) and per-currency balances
     * (CurrencyBalanceEntity) for bank-enabled currencies.
     * Called periodically by the scheduled task.
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
