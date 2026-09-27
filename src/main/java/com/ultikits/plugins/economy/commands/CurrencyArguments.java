package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.model.CurrencyDefinition;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Resolves a currency identifier typed on a command line before any balance is read or written,
 * through the module's one validation point, {@code CurrencyManager#resolve}.
 */
final class CurrencyArguments {

    private CurrencyArguments() {
    }

    /**
     * Resolves {@code rawId}, or tells the sender that no such currency exists.
     * <p>
     * A write command refuses an unknown currency before calling the economy service, and passes
     * the canonical identifier on: the service's write paths would otherwise run under whatever the
     * sender typed (UltiKits/UltiEconomy#13).
     *
     * @param plugin the module
     * @param sender who typed the command
     * @param rawId  the identifier as typed
     * @return the currency, or {@code null} after the refusal was sent
     */
    static CurrencyDefinition resolveOrRefuse(UltiToolsPlugin plugin, CommandSender sender, String rawId) {
        CurrencyDefinition currency = ((UltiEconomy) plugin).getCurrencyManager().resolve(rawId);
        if (currency == null) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.currency_not_found"));
        }
        return currency;
    }
}
