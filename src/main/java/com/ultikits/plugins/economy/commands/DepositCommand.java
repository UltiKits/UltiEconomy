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
        permission = "ultieconomy.deposit",
        description = "economy.help.deposit",
        alias = {"deposit", "ck"}
)
public class DepositCommand extends BaseCommandExecutor {

    private final UltiToolsPlugin plugin;
    private final EconomyService economyService;
    private final EconomyConfig config;

    public DepositCommand(UltiToolsPlugin plugin, EconomyService economyService, EconomyConfig config) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.config = config;
    }

    @CmdMapping(format = "<amount>")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onDeposit(@CmdSender Player player, @CmdParam("amount") String amountStr) {
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

        if (amount < config.getMinDeposit()) {
            String minFormatted = economyService.formatAmount(config.getMinDeposit());
            player.sendMessage(ChatColor.RED + String.format(plugin.i18n("economy.deposit.below_minimum"), minFormatted));
            return;
        }

        boolean success = economyService.depositToBank(player.getUniqueId(), amount);
        if (success) {
            String formatted = economyService.formatAmount(amount);
            player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("economy.deposit.success"), formatted));
        } else {
            // Could be insufficient cash or max bank balance exceeded
            double maxBalance = config.getMaxBankBalance();
            double currentBank = economyService.getBank(player.getUniqueId());
            if (maxBalance > 0 && currentBank + amount > maxBalance) {
                player.sendMessage(ChatColor.RED + plugin.i18n("economy.deposit.bank_full"));
            } else {
                player.sendMessage(ChatColor.RED + plugin.i18n("economy.error.insufficient_balance"));
            }
        }
    }

    @CmdMapping(format = "<amount> <currency>")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onDepositCurrency(
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

        boolean success = economyService.depositToBank(player.getUniqueId(), amount, currencyId);
        if (success) {
            String formatted = economyService.formatAmount(amount, currencyId);
            player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("economy.deposit.success"), formatted));
        } else {
            player.sendMessage(ChatColor.RED + plugin.i18n("economy.error.insufficient_balance"));
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + plugin.i18n("economy.help.header.deposit") + " ===");
        sender.sendMessage(ChatColor.YELLOW + "/deposit <amount>" + ChatColor.GRAY + " - " + plugin.i18n("economy.help.deposit"));
        sender.sendMessage(ChatColor.YELLOW + "/deposit <amount> <currency>" + ChatColor.GRAY + " - " + plugin.i18n("economy.help.deposit_currency"));
    }
}
