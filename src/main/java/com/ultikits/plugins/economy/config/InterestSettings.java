package com.ultikits.plugins.economy.config;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.ConfigChangeListener;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import java.math.BigDecimal;

/**
 * Keeps {@code interest.rate} and {@code interest.max-interest} inside the values the module can use
 * (maintainer decision 2026-09-27, UltiKits/UltiEconomy#29): the rate is a fraction per payment from
 * 0 to 1, and the cap is -1 (no cap) or at least 0. A value outside its range is not used -- a typo
 * such as {@code rate: 3} would pay 300% of every bank balance per payment -- so the declared default
 * is used instead, with a warning that names the key, the value as written and the default. The
 * operator's file is not changed. The check runs when the module loads and again every time the
 * framework reloads the file.
 */
public final class InterestSettings {

    private static final String MODULE = "UltiEconomy";
    private static final String RUNTIME_NAME = "UltiTools-Economy";

    private InterestSettings() {
    }

    /**
     * Checks both values now and again after every reload of the file.
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
     * Replaces each out-of-range value with its declared default, warning once for each.
     */
    static void enforce(EconomyConfig config, PluginLogger logger, UltiToolsPlugin plugin) {
        EconomyConfig declared = new EconomyConfig();
        double rate = config.getInterestRate();
        if (!(rate >= 0 && rate <= 1)) {
            config.setInterestRate(declared.getInterestRate());
            warn(config, logger, plugin, "interest.rate", rate, plugin.i18n("economy.warn.range_interest_rate"),
                    declared.getInterestRate());
        }
        double cap = config.getMaxInterest();
        if (!(cap == -1 || cap >= 0)) {
            config.setMaxInterest(declared.getMaxInterest());
            warn(config, logger, plugin, "interest.max-interest", cap, plugin.i18n("economy.warn.range_max_interest"),
                    declared.getMaxInterest());
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
