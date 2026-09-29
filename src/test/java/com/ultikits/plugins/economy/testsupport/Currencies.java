package com.ultikits.plugins.economy.testsupport;

import com.ultikits.plugins.economy.service.CurrencyManager;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * A real currency registry for command tests: {@code coins} (primary) and {@code gems}.
 */
public final class Currencies {

    private Currencies() {
    }

    public static CurrencyManager coinsAndGems() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("currencies.coins.display-name", "Coins");
        yaml.set("currencies.coins.symbol", "$");
        yaml.set("currencies.coins.primary", true);
        yaml.set("currencies.gems.display-name", "Gems");
        yaml.set("currencies.gems.symbol", "G");
        yaml.set("currencies.gems.bank-enabled", true);
        return new CurrencyManager(yaml);
    }
}
