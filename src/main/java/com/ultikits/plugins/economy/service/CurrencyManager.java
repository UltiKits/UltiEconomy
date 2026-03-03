package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.model.CurrencyDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

public class CurrencyManager {

    private final Map<String, CurrencyDefinition> currencies = new LinkedHashMap<>();
    private final String primaryCurrencyId;

    public CurrencyManager(YamlConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection("currencies");
        if (section == null) {
            throw new IllegalStateException("No 'currencies' section in currencies.yml");
        }

        String foundPrimary = null;
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
            }
        }

        if (foundPrimary == null) {
            throw new IllegalStateException("No primary currency defined in currencies.yml");
        }
        this.primaryCurrencyId = foundPrimary;
    }

    public CurrencyDefinition getCurrency(String id) {
        return currencies.get(id);
    }

    public CurrencyDefinition getPrimaryCurrency() {
        return currencies.get(primaryCurrencyId);
    }

    public String getPrimaryCurrencyId() {
        return primaryCurrencyId;
    }

    public boolean hasCurrency(String id) {
        return currencies.containsKey(id);
    }

    public Collection<CurrencyDefinition> getAllCurrencies() {
        return Collections.unmodifiableCollection(currencies.values());
    }
}
