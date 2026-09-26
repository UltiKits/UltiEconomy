package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;

import java.util.UUID;

public interface EconomyService {

    // --- Account-wallet methods ---
    // The primary currency's one wallet: the account row (economy_accounts) that Vault, /money,
    // /pay and /bank use. The currency-aware methods below, given the primary currency's id,
    // read and write this same wallet (UltiKits/UltiEconomy#25).

    PlayerAccountEntity getAccount(UUID playerUuid);

    PlayerAccountEntity getOrCreateAccount(UUID playerUuid, String playerName);

    boolean hasAccount(UUID playerUuid);

    double getCash(UUID playerUuid);

    double getBank(UUID playerUuid);

    double getTotalWealth(UUID playerUuid);

    boolean setCash(UUID playerUuid, double amount);

    boolean setBank(UUID playerUuid, double amount);

    boolean addCash(UUID playerUuid, double amount);

    boolean addBank(UUID playerUuid, double amount);

    boolean takeCash(UUID playerUuid, double amount);

    boolean takeBank(UUID playerUuid, double amount);

    boolean transfer(UUID from, UUID to, double amount);

    boolean depositToBank(UUID playerUuid, double amount);

    boolean withdrawFromBank(UUID playerUuid, double amount);

    String formatAmount(double amount);

    // --- Currency-aware methods ---
    // A non-primary currency has its own row in currency_balances; the primary currency's id is
    // routed to the account-wallet methods above.

    CurrencyBalanceEntity getBalance(UUID playerUuid, String currencyId);

    CurrencyBalanceEntity getOrCreateBalance(UUID playerUuid, String playerName, String currencyId);

    boolean hasBalance(UUID playerUuid, String currencyId);

    double getCash(UUID playerUuid, String currencyId);

    double getBank(UUID playerUuid, String currencyId);

    double getTotalWealth(UUID playerUuid, String currencyId);

    boolean setCash(UUID playerUuid, double amount, String currencyId);

    boolean setBank(UUID playerUuid, double amount, String currencyId);

    boolean addCash(UUID playerUuid, double amount, String currencyId);

    boolean addBank(UUID playerUuid, double amount, String currencyId);

    boolean takeCash(UUID playerUuid, double amount, String currencyId);

    boolean takeBank(UUID playerUuid, double amount, String currencyId);

    boolean transfer(UUID from, UUID to, double amount, String currencyId);

    boolean depositToBank(UUID playerUuid, double amount, String currencyId);

    boolean withdrawFromBank(UUID playerUuid, double amount, String currencyId);

    String formatAmount(double amount, String currencyId);

    String getPrimaryCurrencyId();
}
