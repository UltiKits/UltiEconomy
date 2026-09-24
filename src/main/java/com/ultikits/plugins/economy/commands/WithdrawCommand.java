package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CmdExecutor(
        permission = "ultieconomy.withdraw",
        description = "economy.help.withdraw",
        alias = {"withdraw", "qk"}
)
public class WithdrawCommand extends BaseCommandExecutor {

    private final UltiToolsPlugin plugin;
    private final EconomyService economyService;
    private final EconomyConfig config;

    public WithdrawCommand(UltiToolsPlugin plugin, EconomyService economyService, EconomyConfig config) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.config = config;
    }

    @CmdMapping(format = "<amount>")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onWithdraw(@CmdSender Player player, @CmdParam("amount") String amountStr) {
        if (!config.isBankEnabled()) {
            player.sendMessage(ChatColor.RED + plugin.i18n("economy.error.bank_disabled"));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + plugin.i18n("economy.error.invalid_amount"));
            return;
        }

        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + plugin.i18n("economy.error.amount_not_positive"));
            return;
        }

        boolean success = economyService.withdrawFromBank(player.getUniqueId(), amount);
        if (success) {
            String formatted = economyService.formatAmount(amount);
            player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("economy.withdraw.success"), formatted));
        } else {
            player.sendMessage(ChatColor.RED + plugin.i18n("economy.error.insufficient_bank_balance"));
        }
    }

    @CmdMapping(format = "<amount> <currency>")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onWithdrawCurrency(
            @CmdSender Player player,
            @CmdParam("amount") String amountStr,
            @CmdParam("currency") String currencyId) {

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + plugin.i18n("economy.error.invalid_amount"));
            return;
        }

        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + plugin.i18n("economy.error.amount_not_positive"));
            return;
        }

        boolean success = economyService.withdrawFromBank(player.getUniqueId(), amount, currencyId);
        if (success) {
            String formatted = economyService.formatAmount(amount, currencyId);
            player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("economy.withdraw.success"), formatted));
        } else {
            player.sendMessage(ChatColor.RED + plugin.i18n("economy.error.insufficient_bank_balance"));
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== UltiEconomy Withdraw ===");
        sender.sendMessage(ChatColor.YELLOW + "/withdraw <amount>" + ChatColor.GRAY + " - " + plugin.i18n("economy.help.withdraw"));
        sender.sendMessage(ChatColor.YELLOW + "/withdraw <amount> <currency>" + ChatColor.GRAY + " - " + plugin.i18n("economy.help.withdraw_currency"));
    }
}
