package com.ultikits.plugins.economy.config;

import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.configuration.file.YamlConfiguration;

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
 * <p><b>Settings that were removed.</b> Deleting a {@code @ConfigEntry} field removes the key from
 * the code, not from anybody's disk: the framework never deletes a key it no longer declares, so a
 * server that has run this module still has it, with whatever value its operator set. Each one still
 * present is named once per boot, with where its job went. The check reads an explicit list rather
 * than reporting every undeclared key, because an unknown key is not necessarily one this module
 * ever had.
 *
 * <p>Each warning names the module, the file and the key, says what the server is doing because
 * of it, and says exactly how to change it.
 */
public final class StartupWarnings {

    /** The module name each warning names. */
    private static final String MODULE = "UltiEconomy";

    /** This module's runtime name, which is what {@code /ul reload <name>} expects. */
    private static final String RUNTIME_NAME = "UltiTools-Economy";

    /**
     * One entry per removed key: the key path as it appears in the file, then where its job went.
     * The second element completes the sentence "... and can be deleted from the file -- %s."
     */
    private static final String[][] REMOVED = {
            {"interest.interval",
                    "interest is paid every 1800 seconds (30 minutes), a fixed period; making it"
                            + " configurable is requested of the framework as"
                            + " UltiKits/UltiTools-Reborn#531 (UltiKits/UltiEconomy#15)"},
            {"leaderboard.update-interval",
                    "the leaderboard is refreshed every 60 seconds, a fixed period; making it"
                            + " configurable is requested of the framework as"
                            + " UltiKits/UltiTools-Reborn#531 (UltiKits/UltiEconomy#15)"},
    };

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
                    "%s: interest.enabled is true in %s, so interest is paid: every 1800 seconds"
                            + " (30 minutes, a fixed period), every positive bank balance -- in the"
                            + " primary currency and in every currency with bank-enabled: true -- earns"
                            + " interest.rate = %s of itself, %s, never above the bank's own maximum"
                            + " balance. This creates money. Before this release (UltiEconomy 2.0.0 and"
                            + " earlier) this switch had no effect and no interest was ever paid. If"
                            + " several servers share this database, each one that has interest on pays"
                            + " the full rate, so turn it on for exactly one of them. To stop paying"
                            + " interest, set interest.enabled: false in %s and run /ul reload %s; the"
                            + " next payment is skipped.",
                    MODULE, file, config.getInterestRate(), capText, file, RUNTIME_NAME));
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
        YamlConfiguration onDisk = config.getConfig();
        if (onDisk == null) {
            return;
        }
        for (String[] removed : REMOVED) {
            if (onDisk.contains(removed[0])) {
                logger.warn(String.format(
                        "%s: '%s' in %s no longer has any effect and can be deleted from the file"
                                + " -- %s.",
                        MODULE, removed[0], file, removed[1]));
            }
        }
    }
}
