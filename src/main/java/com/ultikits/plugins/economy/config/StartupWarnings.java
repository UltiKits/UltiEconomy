package com.ultikits.plugins.economy.config;

import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

/**
 * Warnings this module logs once per boot about switches whose effect changed in 6.3.0.
 *
 * <p><b>Why they exist.</b> Before 6.3.0 nothing read {@code tax.enabled}, so whatever an
 * operator's file held had no effect. 6.3.0 makes it take effect, and the maintainer decided
 * (2026-09-23) that the value already on the operator's disk is the one that applies -- announced
 * loudly rather than silently. The framework writes a key missing from the file with the field's
 * declared default on first boot, and until 6.3.0 that default was {@code false}, so an existing
 * server's file very likely says {@code tax.enabled: false} without its operator ever having chosen
 * it. Upgrading therefore stops the transfer tax on that server. This warning is how the operator
 * finds out.
 *
 * <p>Each warning names the module, the file and the key, says what the server is doing because
 * of the value, and says exactly how to change it.
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
     * the release before 6.3.0.
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
        if (!config.isTaxEnabled()) {
            logger.warn(String.format(
                    "%s: tax.enabled is false in %s, so no tax is collected at all: transfers pay no"
                            + " transaction tax, whatever tax.transaction-tax.* says, and no wealth tax"
                            + " is taken. Before 6.3.0 this switch had no effect and transfers were"
                            + " taxed whenever tax.transaction-tax.enabled was true. To collect taxes,"
                            + " set tax.enabled: true in %s and run /ul reload %s; the next transfer"
                            + " is taxed.",
                    MODULE, file, file, RUNTIME_NAME));
        }
    }
}
