package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.TreasuryEntity;
import com.ultikits.ultitools.interfaces.DataOperator;

import java.util.List;

public class TaxService {

    private final EconomyConfig config;
    private final DataOperator<TreasuryEntity> treasuryDataOperator;

    public TaxService(EconomyConfig config, DataOperator<TreasuryEntity> treasuryDataOperator) {
        this.config = config;
        this.treasuryDataOperator = treasuryDataOperator;
    }

    /**
     * The tax a transfer of {@code amount} pays, or 0 when no transaction tax applies.
     *
     * <p>{@code tax.enabled} is the master switch over all taxation and is checked first, then
     * {@code tax.transaction-tax.enabled}. Both are read on every call rather than cached, so a
     * {@code /ul reload} that changes either one applies to the next transfer (UltiKits/UltiEconomy#16).
     */
    public double calculateTransactionTax(double amount) {
        if (!config.isTaxEnabled() || !config.isTransactionTaxEnabled()) {
            return 0.0;
        }
        return amount * config.getTransactionTaxRate();
    }

    public void depositToTreasury(double amount, String currencyId) throws IllegalAccessException {
        List<TreasuryEntity> results = treasuryDataOperator.query()
                .where("currency_id").eq(currencyId)
                .list();
        if (results.isEmpty()) {
            TreasuryEntity entity = TreasuryEntity.builder()
                    .currencyId(currencyId)
                    .balance(amount)
                    .build();
            treasuryDataOperator.insert(entity);
        } else {
            TreasuryEntity existing = results.get(0);
            existing.setBalance(existing.getBalance() + amount);
            treasuryDataOperator.update(existing);
        }
    }

    public double getTreasuryBalance(String currencyId) {
        List<TreasuryEntity> results = treasuryDataOperator.query()
                .where("currency_id").eq(currencyId)
                .list();
        if (results.isEmpty()) {
            return 0.0;
        }
        return results.get(0).getBalance();
    }

    public boolean withdrawFromTreasury(double amount, String currencyId) throws IllegalAccessException {
        List<TreasuryEntity> results = treasuryDataOperator.query()
                .where("currency_id").eq(currencyId)
                .list();
        if (results.isEmpty()) {
            return false;
        }
        TreasuryEntity entry = results.get(0);
        if (entry.getBalance() < amount) {
            return false;
        }
        entry.setBalance(entry.getBalance() - amount);
        treasuryDataOperator.update(entry);
        return true;
    }
}
