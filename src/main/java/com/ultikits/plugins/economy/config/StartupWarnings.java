package com.ultikits.plugins.economy.config;

import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

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
     */
    public static void log(EconomyConfig config, PluginLogger logger) {
        if (config == null || logger == null) {
            return;
        }
        String file = config.getConfigFilePath();
        if (config.isInterestEnabled()) {
            double cap = config.getMaxInterest();
            String capText = cap > 0
                    ? "capped at interest.max-interest = " + cap + " per payment"
                    : "with no cap, because interest.max-interest = " + cap + " is not above 0";
            logger.warn(String.format(
                    "%s: interest.enabled is true in %s, so interest is paid: every interest.interval"
                            + " = %d seconds (the first payment one interval after load), a player's"
                            + " primary-currency bank balance"
                            + " (the one /bank, /money and Vault show; paid once per player, not also on"
                            + " the per-currency row that /bank <primary currency> shows) and their bank"
                            + " balance in every other currency with bank-enabled: true each earn"
                            + " interest.rate = %s of itself, %s, never above the bank's own maximum"
                            + " balance. This creates money. Before this release (UltiEconomy 2.0.0 and"
                            + " earlier) this switch had no effect and no interest was ever paid. If"
                            + " several servers share this database, each one that has interest on pays"
                            + " the full rate, so turn it on for exactly one of them. To stop paying"
                            + " interest, set interest.enabled: false in %s and run /ul reload %s; the"
                            + " next payment is skipped.",
                    MODULE, file, config.getInterestInterval(), config.getInterestRate(), capText, file,
                    RUNTIME_NAME));
        }
        if (!config.isTaxEnabled()) {
            logger.warn(String.format(
                    "%s: tax.enabled is false in %s, so no tax is collected at all: transfers pay no"
                            + " transaction tax, whatever tax.transaction-tax.* says, and no wealth tax"
                            + " is taken. Before this release (UltiEconomy 2.0.0 and earlier) this switch"
                            + " had no effect and transfers were"
                            + " taxed whenever tax.transaction-tax.enabled was true. To collect taxes,"
                            + " set tax.enabled: true in %s and run /ul reload %s; the next transfer"
                            + " is taxed.",
                    MODULE, file, file, RUNTIME_NAME));
        }
    }
}
