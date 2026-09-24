package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CmdExecutor(
        permission = "ultieconomy.pay",
        description = "economy.command.pay.description",
        alias = {"pay"}
)
public class PayCommand extends BaseCommandExecutor {

    private final UltiToolsPlugin plugin;
    private final EconomyService economyService;

    public PayCommand(UltiToolsPlugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
    }

    @CmdMapping(format = "<player> <amount>")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onPay(
            @CmdSender Player sender,
            @CmdParam("player") String targetName,
            @CmdParam("amount") String amountStr) {

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.invalid_amount"));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.amount_not_positive"));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.player_not_found"));
            return;
        }

        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.invalid_amount"));
            return;
        }

        boolean success = economyService.transfer(sender.getUniqueId(), target.getUniqueId(), amount);
        if (success) {
            String formatted = economyService.formatAmount(amount);
            sender.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("economy.pay.sent"), formatted, target.getName()));
            target.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("economy.pay.received"), sender.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.insufficient_balance"));
        }
    }

    @CmdMapping(format = "<player> <amount> <currency>")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onPayWithCurrency(
            @CmdSender Player sender,
            @CmdParam("player") String targetName,
            @CmdParam("amount") String amountStr,
            @CmdParam("currency") String currencyId) {

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.invalid_amount"));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.amount_not_positive"));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.player_not_found"));
            return;
        }

        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.invalid_amount"));
            return;
        }

        boolean success = economyService.transfer(sender.getUniqueId(), target.getUniqueId(), amount, currencyId);
        if (success) {
            String formatted = economyService.formatAmount(amount, currencyId);
            sender.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("economy.pay.sent"), formatted, target.getName()));
            target.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("economy.pay.received"), sender.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.insufficient_balance"));
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + plugin.i18n("economy.help.header.pay") + " ===");
        sender.sendMessage(ChatColor.YELLOW + "/pay <player> <amount>" + ChatColor.GRAY + " - " + plugin.i18n("economy.help.pay"));
        sender.sendMessage(ChatColor.YELLOW + "/pay <player> <amount> <currency>" + ChatColor.GRAY + " - " + plugin.i18n("economy.help.pay_currency"));
    }
}
