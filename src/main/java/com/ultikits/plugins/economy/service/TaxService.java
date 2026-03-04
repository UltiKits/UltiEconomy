package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.TreasuryEntity;
import com.ultikits.ultitools.interfaces.DataOperator;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

public class TaxService {

    private final EconomyConfig config;
    private final DataOperator<TreasuryEntity> treasuryDataOperator;

    public TaxService(EconomyConfig config, DataOperator<TreasuryEntity> treasuryDataOperator) {
        this.config = config;
        this.treasuryDataOperator = treasuryDataOperator;
    }

    public double calculateTransactionTax(double amount) {
        if (!config.isTransactionTaxEnabled()) {
            return 0.0;
        }
        return amount * config.getTransactionTaxRate();
    }

    public double calculateWealthTax(double totalWealth, List<TaxBracket> brackets) {
        double tax = 0.0;
        for (TaxBracket bracket : brackets) {
            if (totalWealth <= bracket.threshold) {
                break;
            }
            double taxableInBracket;
            if (bracket.ceiling < 0) {
                // Unbounded top bracket
                taxableInBracket = totalWealth - bracket.threshold;
            } else if (totalWealth >= bracket.ceiling) {
                taxableInBracket = bracket.ceiling - bracket.threshold;
            } else {
                taxableInBracket = totalWealth - bracket.threshold;
            }
            tax += taxableInBracket * bracket.rate;
        }
        return tax;
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

    @Getter
    @AllArgsConstructor
    public static class TaxBracket {
        private final double threshold;
        private final double ceiling;
        private final double rate;
    }
}
