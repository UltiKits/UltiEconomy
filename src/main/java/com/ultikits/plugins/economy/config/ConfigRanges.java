package com.ultikits.plugins.economy.config;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.ConfigChangeListener;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import java.math.BigDecimal;

/**
 * Keeps configuration values inside the values the module can use. {@code interest.rate} and
 * {@code interest.max-interest} (maintainer decision 2026-09-27, UltiKits/UltiEconomy#29): the rate is
 * a fraction per payment from 0 to 1, and the cap is -1 (no cap) or at least 0. A value outside its range is not used -- a typo
 * such as {@code rate: 3} would pay 300% of every bank balance per payment -- so the declared default
 * is used instead, with a warning that names the key, the value as written and the default. The
 * operator's file is not changed. The check runs when the module loads and again every time the
 * framework reloads the file.
 * <p>
 * The same rule covers {@code tax.transaction-tax.rate}, the fraction of a transfer kept as tax: from 0
 * to 1. A negative rate credited the recipient more than the payer paid, creating money, and a rate
 * above 1 credited the recipient a negative amount.
 */
public final class ConfigRanges {

    private static final String MODULE = "UltiEconomy";
    private static final String RUNTIME_NAME = "UltiTools-Economy";

    private ConfigRanges() {
    }

    /**
     * Checks every ranged value now and again after every reload of the file.
     *
     * @param config the module's configuration, as the framework loaded it
     * @param logger the module's logger
     * @param plugin the module, for its catalogue
     * @return the listener that re-checks after a reload, for the module to remove on unload
     */
    public static ConfigChangeListener watch(EconomyConfig config, PluginLogger logger, UltiToolsPlugin plugin) {
        enforce(config, logger, plugin);
        ConfigChangeListener listener = changed -> enforce(config, logger, plugin);
        config.addChangeListener(listener);
        return listener;
    }

    /**
     * Warns once for each value outside its range. The value is not changed: {@link EconomyConfig}'s
     * getters answer the declared default while the file's value is outside its range, so the module
     * never uses it, and a save of the file writes back what the operator wrote.
     */
    static void enforce(EconomyConfig config, PluginLogger logger, UltiToolsPlugin plugin) {
        double rate = config.writtenInterestRate();
        if (!EconomyConfig.isUsableFraction(rate)) {
            warn(config, logger, plugin, "interest.rate", rate, plugin.i18n("economy.warn.range_interest_rate"),
                    EconomyConfig.DEFAULT_INTEREST_RATE);
        }
        double cap = config.writtenMaxInterest();
        if (!EconomyConfig.isUsableCap(cap)) {
            warn(config, logger, plugin, "interest.max-interest", cap, plugin.i18n("economy.warn.range_max_interest"),
                    EconomyConfig.DEFAULT_MAX_INTEREST);
        }
        double taxRate = config.writtenTransactionTaxRate();
        if (!EconomyConfig.isUsableFraction(taxRate)) {
            warn(config, logger, plugin, "tax.transaction-tax.rate", taxRate,
                    plugin.i18n("economy.warn.range_transaction_tax_rate"), EconomyConfig.DEFAULT_TRANSACTION_TAX_RATE);
        }
    }

    private static void warn(EconomyConfig config, PluginLogger logger, UltiToolsPlugin plugin, String key,
                             double written, String range, double fallback) {
        logger.warn(String.format(plugin.i18n("economy.warn.value_out_of_range"), MODULE, key,
                config.getConfigFilePath(), plain(written), range, plain(fallback), RUNTIME_NAME));
    }

    /** A number as an operator would write it: no trailing zeros, no exponent. */
    private static String plain(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
