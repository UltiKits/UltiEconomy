package com.ultikits.plugins.economy.config;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

/**
 * Settings this module used to declare in {@code config/config.yml} and no longer reads, and the
 * warning that tells an operator whose file still holds one.
 *
 * <p><b>Why a module needs this at all.</b> Deleting a {@code @ConfigEntry} field removes the key from
 * the code, not from anybody's disk: the framework writes a missing key's declared default into the
 * operator's file on first boot and never deletes a key it no longer declares, so every server that has
 * run this module still has every key below in its file, carrying whatever value its operator last set.
 * Without this warning the only sign of the removal, for that operator, is that a setting they edited
 * quietly stops being mentioned anywhere -- which looks the same as it still working.
 *
 * <p>Each line names the module, the key and the file, and says why the setting went, because "this no
 * longer does anything" without "here is why" only tells half of what the operator has to act on.
 *
 * <p>The check is scoped to an explicit list rather than derived by comparing the file with the declared
 * fields: an unknown key in the file is not necessarily a key this module ever had (a typo, a
 * hand-written note, another tool's key), and reporting all of them would hide the real removals.
 */
public final class RemovedConfigKeys {

    /** The module name each warning names. */
    private static final String MODULE = "UltiEconomy";

    /**
     * The removed keys, as each path appears in the file, in the order their warnings are logged. Why
     * each went is the language file's text, chosen in {@link #reasonFor}.
     */
    private static final String[] REMOVED = {
            // tax.wealth-tax.* (UltiKits/UltiEconomy#27)
            "tax.wealth-tax.enabled",
            "tax.wealth-tax.interval",
            "tax.wealth-tax.exempt-permission",
            // leaderboard.display-count (UltiKits/UltiEconomy#36)
    };

    private RemovedConfigKeys() {
    }

    /**
     * Why one removed key went, from the language file. Each removed key names its own case, so a key
     * added to {@link #REMOVED} without one fails loudly instead of borrowing another key's reason.
     */
    private static String reasonFor(String removedKey, UltiToolsPlugin plugin) {
        switch (removedKey) {
            // tax.wealth-tax.* (UltiKits/UltiEconomy#27)
            case "tax.wealth-tax.enabled":
            case "tax.wealth-tax.interval":
            case "tax.wealth-tax.exempt-permission":
                return plugin.i18n("economy.warn.removed_key_reason.wealth_tax");
            // leaderboard.display-count (UltiKits/UltiEconomy#36)
            default:
                throw new IllegalStateException("No reason recorded for removed key " + removedKey);
        }
    }

    /**
     * Logs one warning per removed key still present in the operator's file, in the server's language.
     *
     * <p>Asks the framework whether each removed key is in the file it last loaded
     * ({@code AbstractConfigEntity#isPresentInFile}, UltiTools-API 6.3.0). That answer covers keys this
     * entity no longer declares -- exactly what a leftover key is -- and is {@code false} for every key
     * when the file could not be read or parsed, or was never loaded, so such a file is never reported
     * as holding removed keys. A fresh install holds none of them, so a new server logs nothing.
     *
     * @param config the module's configuration, after the framework has loaded it; may be null, in which
     *               case nothing is reported
     * @param logger the module's logger; may be null, in which case nothing is reported
     * @param plugin the module, for its catalogue; may be null, in which case nothing is reported
     */
    public static void warnIfStillPresent(EconomyConfig config, PluginLogger logger, UltiToolsPlugin plugin) {
        if (config == null || logger == null || plugin == null) {
            return;
        }
        String file = config.getConfigFilePath();
        for (String removed : REMOVED) {
            if (config.isPresentInFile(removed)) {
                logger.warn(String.format(plugin.i18n("economy.warn.removed_key"), MODULE, removed, file,
                        reasonFor(removed, plugin)));
            }
        }
    }
}
