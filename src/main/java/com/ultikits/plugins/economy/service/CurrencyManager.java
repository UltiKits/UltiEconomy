package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.model.CurrencyDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;
import java.util.function.Supplier;

public class CurrencyManager {

    private final Map<String, CurrencyDefinition> currencies = new LinkedHashMap<>();
    private final String primaryCurrencyId;
    private final ConfigurationSection primarySection;
    // config.yml's currency-name and currency-symbol, read live (UltiKits/UltiEconomy#32); null until set
    private Supplier<String> primaryName;
    private Supplier<String> primarySymbol;

    public CurrencyManager(YamlConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection("currencies");
        if (section == null) {
            throw new IllegalStateException("No 'currencies' section in currencies.yml");
        }

        String foundPrimary = null;
        ConfigurationSection foundSection = null;
        for (String id : section.getKeys(false)) {
            ConfigurationSection cs = section.getConfigurationSection(id);
            if (cs == null) continue;

            CurrencyDefinition def = CurrencyDefinition.builder()
                    .id(id)
                    .displayName(cs.getString("display-name", id))
                    .symbol(cs.getString("symbol", ""))
                    .initialCash(cs.getDouble("initial-cash", 0.0))
                    .bankEnabled(cs.getBoolean("bank-enabled", false))
                    .minDeposit(cs.getDouble("min-deposit", 0.0))
                    .maxBankBalance(cs.getDouble("max-bank-balance", -1))
                    .primary(cs.getBoolean("primary", false))
                    .build();

            currencies.put(id, def);
            if (def.isPrimary()) {
                if (foundPrimary != null) {
                    throw new IllegalStateException("Multiple primary currencies: " + foundPrimary + " and " + id);
                }
                foundPrimary = id;
                foundSection = cs;
            }
        }

        if (foundPrimary == null) {
            throw new IllegalStateException("No primary currency defined in currencies.yml");
        }
        this.primaryCurrencyId = foundPrimary;
        this.primarySection = foundSection;
    }

    /**
     * The primary currency's own block of {@code currencies.yml}, as the file wrote it -- so a caller
     * can tell a key the operator set from one left out. Only its {@code display-name} and
     * {@code symbol} are read for the primary currency; its money settings come from
     * {@code config.yml} (UltiKits/UltiEconomy#25).
     */
    public ConfigurationSection getPrimarySection() {
        return primarySection;
    }

    /**
     * Makes the primary currency's display name and symbol those of {@code config.yml}
     * ({@code currency-name}, {@code currency-symbol}), read each time the currency is looked up so a
     * reload is followed; the primary block of {@code currencies.yml} no longer decides them
     * (maintainer decision 2026-09-27, UltiKits/UltiEconomy#32).
     *
     * @param name   supplies the primary currency's display name
     * @param symbol supplies the primary currency's symbol
     */
    public void usePrimaryNaming(Supplier<String> name, Supplier<String> symbol) {
        this.primaryName = name;
        this.primarySymbol = symbol;
    }

    /** A currency as callers see it: the primary one carries config.yml's name and symbol. */
    private CurrencyDefinition present(CurrencyDefinition def) {
        if (def == null || !def.isPrimary() || primaryName == null) {
            return def;
        }
        return CurrencyDefinition.builder()
                .id(def.getId())
                .displayName(primaryName.get())
                .symbol(primarySymbol.get())
                .initialCash(def.getInitialCash())
                .bankEnabled(def.isBankEnabled())
                .minDeposit(def.getMinDeposit())
                .maxBankBalance(def.getMaxBankBalance())
                .primary(true)
                .build();
    }

    public CurrencyDefinition getCurrency(String id) {
        return present(currencies.get(id));
    }

    /**
     * Resolves a currency identifier as typed by a command sender, trimming incidental
     * whitespace before lookup. This is the single validation choke point every
     * currency-taking command must call before reading or mutating a balance under a
     * caller-supplied identifier: {@code null}, blank, and unknown identifiers all
     * resolve to {@code null}, and callers must refuse the request without touching
     * any balance rows.
     *
     * @param rawId the identifier as typed on the command line, may be null
     * @return the matching {@link CurrencyDefinition}, or {@code null} if the identifier
     *         is null, blank, or does not name a configured currency
     */
    public CurrencyDefinition resolve(String rawId) {
        if (rawId == null) {
            return null;
        }
        String trimmed = rawId.trim();
        return trimmed.isEmpty() ? null : present(currencies.get(trimmed));
    }

    public CurrencyDefinition getPrimaryCurrency() {
        return present(currencies.get(primaryCurrencyId));
    }

    public String getPrimaryCurrencyId() {
        return primaryCurrencyId;
    }

    public boolean hasCurrency(String id) {
        return currencies.containsKey(id);
    }

    public Collection<CurrencyDefinition> getAllCurrencies() {
        List<CurrencyDefinition> all = new ArrayList<>(currencies.size());
        for (CurrencyDefinition def : currencies.values()) {
            all.add(present(def));
        }
        return Collections.unmodifiableList(all);
    }
}
