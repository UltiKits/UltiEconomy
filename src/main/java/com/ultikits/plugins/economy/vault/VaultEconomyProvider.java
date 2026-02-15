package com.ultikits.plugins.economy.vault;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.service.EconomyService;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;

/**
 * Vault Economy adapter that delegates all operations to EconomyService.
 * Implements Economy directly (not AbstractEconomy) since our service is UUID-based.
 * String-based deprecated methods return zero/false stubs since we can't reliably
 * resolve player names to UUIDs without Bukkit dependency in this class.
 * Vault's "bank" methods (shared/guild banks) are NOT_IMPLEMENTED — UltiEconomy's
 * bank feature is per-player, exposed through our own commands, not through Vault.
 */
public class VaultEconomyProvider implements Economy {

    private final EconomyService economyService;
    private final EconomyConfig config;

    public VaultEconomyProvider(EconomyService economyService, EconomyConfig config) {
        this.economyService = economyService;
        this.config = config;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "UltiEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        // Vault "bank" = shared/guild banks, not our per-player bank
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return economyService.formatAmount(amount);
    }

    @Override
    public String currencyNamePlural() {
        return config.getCurrencyName();
    }

    @Override
    public String currencyNameSingular() {
        return config.getCurrencyName();
    }

    // ========================
    // Account operations
    // ========================

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return economyService.hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(String playerName) {
        return false; // Deprecated — cannot resolve name to UUID without Bukkit
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return false;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (economyService.hasAccount(player.getUniqueId())) {
            return false;
        }
        economyService.getOrCreateAccount(player.getUniqueId(), player.getName());
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return false;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return false;
    }

    // ========================
    // Balance operations
    // ========================

    @Override
    public double getBalance(OfflinePlayer player) {
        return economyService.getCash(player.getUniqueId());
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public double getBalance(String playerName) {
        return 0;
    }

    @Override
    public double getBalance(String playerName, String world) {
        return 0;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economyService.getCash(player.getUniqueId()) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return false;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return false;
    }

    // ========================
    // Withdraw operations
    // ========================

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(amount, 0, ResponseType.FAILURE, "Cannot withdraw negative amount");
        }
        boolean success = economyService.takeCash(player.getUniqueId(), amount);
        double balance = economyService.getCash(player.getUniqueId());
        if (success) {
            return new EconomyResponse(amount, balance, ResponseType.SUCCESS, "");
        }
        return new EconomyResponse(amount, balance, ResponseType.FAILURE, "Insufficient funds");
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return new EconomyResponse(amount, 0, ResponseType.FAILURE, "Deprecated string-based method");
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return new EconomyResponse(amount, 0, ResponseType.FAILURE, "Deprecated string-based method");
    }

    // ========================
    // Deposit operations
    // ========================

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(amount, 0, ResponseType.FAILURE, "Cannot deposit negative amount");
        }
        boolean success = economyService.addCash(player.getUniqueId(), amount);
        double balance = economyService.getCash(player.getUniqueId());
        if (success) {
            return new EconomyResponse(amount, balance, ResponseType.SUCCESS, "");
        }
        return new EconomyResponse(amount, balance, ResponseType.FAILURE, "Deposit failed");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return new EconomyResponse(amount, 0, ResponseType.FAILURE, "Deprecated string-based method");
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return new EconomyResponse(amount, 0, ResponseType.FAILURE, "Deprecated string-based method");
    }

    // ========================
    // Vault shared bank operations — NOT SUPPORTED
    // UltiEconomy's "bank" is per-player, not shared/guild banks.
    // ========================

    private EconomyResponse notImplemented() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "UltiEconomy does not support Vault shared banks");
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }
}
