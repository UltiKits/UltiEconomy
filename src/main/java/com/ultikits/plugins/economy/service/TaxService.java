package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.TreasuryEntity;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

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

    /** How many times one treasury change is attempted when another writer keeps changing the row. */
    private static final int MAX_WRITE_ATTEMPTS = 3;

    /**
     * Adds {@code amount} to the treasury of {@code currencyId}. The write applies only while the row
     * still holds the balance read ({@code DataOperator#updateIf}), so a deposit another server made in
     * between is not overwritten (UltiKits/UltiEconomy#41); when it does not apply, the row is read
     * again, at most {@link #MAX_WRITE_ATTEMPTS} times. With no row -- none yet, or removed after it was
     * read (UltiKits/UltiEconomy#42) -- a row is created with an id derived from the currency
     * ({@link #treasuryId}), so two servers making the first deposit at once create one row: the second
     * insert is refused and that server reads again and adds to the row.
     *
     * @return true when the amount was stored; false when the row kept changing on every attempt
     */
    public boolean depositToTreasury(double amount, String currencyId) throws IllegalAccessException {
        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            List<TreasuryEntity> results = treasuryDataOperator.query()
                    .where("currency_id").eq(currencyId)
                    .list();
            if (results.isEmpty()) {
                TreasuryEntity entity = TreasuryEntity.builder()
                        .currencyId(currencyId)
                        .balance(amount)
                        .build();
                entity.setId(treasuryId(currencyId));
                try {
                    treasuryDataOperator.insert(entity);
                    return true;
                } catch (RuntimeException e) {
                    // The primary key refused it: another server created this currency's row first.
                    if (treasuryDataOperator.getById(entity.getId()) == null) {
                        throw e;
                    }
                    continue;
                }
            }
            TreasuryEntity existing = results.get(0);
            double read = existing.getBalance();
            existing.setBalance(read + amount);
            if (treasuryDataOperator.updateIf(existing, balanceIs(read))) {
                return true;
            }
            existing.setBalance(read);
        }
        return false;
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

    /**
     * Takes {@code amount} from the treasury of {@code currencyId}: false when there is no row or not
     * enough in it. Conditional on the balance read, like {@link #depositToTreasury}; when the write does
     * not apply the row is read again and the withdrawal decided again (it may now be unaffordable, or
     * the row gone, UltiKits/UltiEconomy#42), at most {@link #MAX_WRITE_ATTEMPTS} times, then false.
     */
    public boolean withdrawFromTreasury(double amount, String currencyId) throws IllegalAccessException {
        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            List<TreasuryEntity> results = treasuryDataOperator.query()
                    .where("currency_id").eq(currencyId)
                    .list();
            if (results.isEmpty()) {
                return false;
            }
            TreasuryEntity entry = results.get(0);
            double read = entry.getBalance();
            if (read < amount) {
                return false;
            }
            entry.setBalance(read - amount);
            if (treasuryDataOperator.updateIf(entry, balanceIs(read))) {
                return true;
            }
            entry.setBalance(read);
        }
        return false;
    }

    /**
     * The id a treasury row this class creates gets: the same for a currency on every server, so the
     * primary key admits one; a plain (name-based) UUID, safe as a JSON file name.
     */
    static String treasuryId(String currencyId) {
        return UUID.nameUUIDFromBytes(("UltiEconomy treasury:" + currencyId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static WhereCondition balanceIs(double balance) {
        return WhereCondition.builder().column("balance").value(balance).build();
    }
}
