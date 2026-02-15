package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.entity.PlayerAccountEntity;

import java.util.UUID;

public interface EconomyService {

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
}
