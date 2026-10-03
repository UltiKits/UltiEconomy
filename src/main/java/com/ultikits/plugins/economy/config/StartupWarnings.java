package com.ultikits.plugins.economy.config;

import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Warnings this module logs once per boot about settings whose effect changed in 6.3.0.
 *
 * <p><b>Switches that now take effect.</b> Before this release (UltiEconomy 2.0.0 and earlier)
 * nothing read {@code tax.enabled}, and
 * nothing ever scheduled the interest payment {@code interest.enabled} was declared to control, so
 * whatever an operator's file held for either had no effect. 6.3.0 makes both take effect, and the
 * maintainer decided (2026-09-23) that the value already on the operator's disk is the one that
 * applies -- announced loudly rather than silently. The 1.0.0 and 2.0.0 shipped file said
 * {@code interest.enabled: true}, and on first boot 2.0.0 wrote its declared default
 * {@code tax.enabled: false} into the file, so an existing server most likely starts paying interest
 * and stops taking the transfer tax on upgrade without its operator having chosen either. These
 * warnings are how the operator finds out.
 *
 * <p>Each warning names the module, the file and the key, says what the server is doing because
 * of it, and says exactly how to change it.
 */
public final class StartupWarnings {

    /** The module name each warning names. */
    private static final String MODULE = "UltiEconomy";

    /** The file that defines the currencies, relative to the module's config folder. */
    private static final String CURRENCIES_FILE = "config/currencies.yml";

    /** The primary currency's symbol every earlier version shipped in {@code currencies.yml}. */
    private static final String SHIPPED_SYMBOL = "$";

    /** This module's runtime name, which is what {@code /ul reload <name>} expects. */
    private static final String RUNTIME_NAME = "UltiTools-Economy";

    private StartupWarnings() {
    }

    /**
     * Logs one warning per switch whose current value changes what this server does compared with
     * UltiEconomy 2.0.0 and earlier.
     *
     * @param config the module's configuration, after the framework has loaded it; may be null, in
     *               which case nothing is reported
     * @param logger the module's logger; may be null, in which case nothing is reported
     * @param plugin the module, whose language catalogue gives the warnings their text; may be null,
     *               in which case nothing is reported
     */
    public static void log(EconomyConfig config, PluginLogger logger, UltiToolsPlugin plugin) {
        if (config == null || logger == null || plugin == null) {
            return;
        }
        String file = config.getConfigFilePath();
        if (config.isInterestEnabled()) {
            double cap = config.getMaxInterest();
            String capText = cap > 0
                    ? String.format(plugin.i18n("economy.warn.interest_cap"), cap)
                    : String.format(plugin.i18n("economy.warn.interest_no_cap"), cap);
            logger.warn(String.format(plugin.i18n("economy.warn.interest_enabled_one_wallet"),
                    MODULE, file, config.getInterestInterval(), config.getInterestRate(), capText, file,
                    RUNTIME_NAME));
        }
        if (!config.isTaxEnabled()) {
            logger.warn(String.format(plugin.i18n("economy.warn.tax_disabled"), MODULE, file, file, RUNTIME_NAME));
        }
    }

    /**
     * Logs one warning for each money setting of the primary currency that {@code currencies.yml}
     * sets to a value different from {@code config.yml} (UltiKits/UltiEconomy#25). The primary
     * currency has one wallet, the account, and {@code config.yml} governs it (maintainer decision
     * 2026-09-24), so the {@code currencies.yml} value has no effect; the warning names both files,
     * both keys and both values. A setting {@code currencies.yml} does not contain is not reported.
     *
     * @param config     the module's configuration; may be null, in which case nothing is reported
     * @param currencies the currency definitions; may be null, in which case nothing is reported
     * @param logger     the module's logger; may be null, in which case nothing is reported
     * @param plugin     the module, for its catalogue; may be null, in which case nothing is reported
     */
    public static void logPrimaryCurrencyConflicts(EconomyConfig config, CurrencyManager currencies,
                                                   PluginLogger logger, UltiToolsPlugin plugin) {
        if (config == null || currencies == null || logger == null || plugin == null
                || currencies.getPrimarySection() == null) {
            return;
        }
        ConfigurationSection primary = currencies.getPrimarySection();
        String prefix = "currencies." + currencies.getPrimaryCurrencyId() + ".";
        String file = config.getConfigFilePath();
        if (primary.contains("initial-cash") && Double.compare(primary.getDouble("initial-cash"), config.getInitialCash()) != 0) {
            warnConflict(logger, plugin, prefix + "initial-cash", String.valueOf(primary.getDouble("initial-cash")),
                    file, "initial-cash", String.valueOf(config.getInitialCash()));
        }
        if (primary.contains("bank-enabled") && primary.getBoolean("bank-enabled") != config.isBankEnabled()) {
            warnConflict(logger, plugin, prefix + "bank-enabled", String.valueOf(primary.getBoolean("bank-enabled")),
                    file, "bank.enabled", String.valueOf(config.isBankEnabled()));
        }
        if (primary.contains("min-deposit") && Double.compare(primary.getDouble("min-deposit"), config.getMinDeposit()) != 0) {
            warnConflict(logger, plugin, prefix + "min-deposit", String.valueOf(primary.getDouble("min-deposit")),
                    file, "bank.min-deposit", String.valueOf(config.getMinDeposit()));
        }
        // config.yml also owns the name and symbol (UltiKits/UltiEconomy#32). The name an earlier
        // version shipped, or this jar's text for it in any language, is not an operator's choice and
        // simply follows config.yml, so it is not reported.
        if (primary.contains("display-name") && !primary.getString("display-name", "").equals(config.getCurrencyName())
                && !EconomyConfig.builtInCurrencyNames().contains(primary.getString("display-name"))) {
            warnConflict(logger, plugin, prefix + "display-name", primary.getString("display-name"),
                    file, "currency-name", config.getCurrencyName());
        }
        if (primary.contains("symbol") && !primary.getString("symbol", "").equals(config.getCurrencySymbol())
                && !SHIPPED_SYMBOL.equals(primary.getString("symbol"))) {
            warnConflict(logger, plugin, prefix + "symbol", primary.getString("symbol"),
                    file, "currency-symbol", config.getCurrencySymbol());
        }
        // A cap that is neither -1 nor above 0 is refused and means "no cap" (UltiKits/UltiEconomy#35), so 0 and -1 agree.
        if (primary.contains("max-bank-balance")
                && Double.compare(cap(primary.getDouble("max-bank-balance")), cap(config.getMaxBankBalance())) != 0) {
            warnConflict(logger, plugin, prefix + "max-bank-balance", String.valueOf(primary.getDouble("max-bank-balance")),
                    file, "bank.max-balance", String.valueOf(config.getMaxBankBalance()));
        }
    }

    /**
     * Logs one warning for each non-primary currency whose {@code max-bank-balance} in
     * {@code currencies.yml} the module refused -- neither -1 (no cap) nor above 0 -- naming the file,
     * the key, the value as written and the default -1 the currency uses instead
     * (UltiKits/UltiEconomy#35). {@code currencies.yml} is read once, when the module loads.
     *
     * @param currencies the currency definitions; may be null, in which case nothing is reported
     * @param logger     the module's logger; may be null, in which case nothing is reported
     * @param plugin     the module, for its catalogue; may be null, in which case nothing is reported
     */
    public static void logRefusedCurrencyBankCaps(CurrencyManager currencies, PluginLogger logger,
                                                  UltiToolsPlugin plugin) {
        if (currencies == null || logger == null || plugin == null) {
            return;
        }
        for (Map.Entry<String, Double> refused : currencies.getRefusedBankCaps()) {
            logger.warn(String.format(plugin.i18n("economy.warn.currency_value_out_of_range"), MODULE,
                    "currencies." + refused.getKey() + ".max-bank-balance", CURRENCIES_FILE, plain(refused.getValue()),
                    plugin.i18n("economy.warn.range_max_bank_balance"), plain(EconomyConfig.DEFAULT_MAX_BANK_BALANCE)));
        }
    }

    /** A number as an operator would write it: no trailing zeros, no exponent. */
    private static String plain(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** A bank cap as every reader applies it: a value of 0 or below means "no cap". */
    private static double cap(double maxBalance) {
        return maxBalance > 0 ? maxBalance : -1;
    }

    private static void warnConflict(PluginLogger logger, UltiToolsPlugin plugin, String currenciesKey,
                                     String currenciesValue, String configFile, String configKey, String configValue) {
        logger.warn(String.format(plugin.i18n("economy.warn.primary_currency_setting"),
                MODULE, CURRENCIES_FILE, currenciesKey, currenciesValue, configFile, configKey, configValue,
                currenciesKey, CURRENCIES_FILE));
    }
}
