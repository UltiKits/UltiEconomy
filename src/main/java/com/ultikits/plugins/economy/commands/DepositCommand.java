package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.AbstractCommandExecutor;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CmdExecutor(
        permission = "ultieconomy.deposit",
        description = "存款到银行",
        alias = {"deposit", "ck"}
)
public class DepositCommand extends AbstractCommandExecutor {

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
            player.sendMessage(ChatColor.RED + plugin.i18n("银行功能未启用"));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + plugin.i18n("无效的金额"));
            return;
        }

        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + plugin.i18n("金额必须大于零"));
            return;
        }

        if (amount < config.getMinDeposit()) {
            String minFormatted = economyService.formatAmount(config.getMinDeposit());
            player.sendMessage(ChatColor.RED + String.format(plugin.i18n("最低存款金额: %s"), minFormatted));
            return;
        }

        boolean success = economyService.depositToBank(player.getUniqueId(), amount);
        if (success) {
            String formatted = economyService.formatAmount(amount);
            player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("成功存入 %s 到银行"), formatted));
        } else {
            // Could be insufficient cash or max bank balance exceeded
            double maxBalance = config.getMaxBankBalance();
            double currentBank = economyService.getBank(player.getUniqueId());
            if (maxBalance > 0 && currentBank + amount > maxBalance) {
                player.sendMessage(ChatColor.RED + plugin.i18n("银行余额已达上限"));
            } else {
                player.sendMessage(ChatColor.RED + plugin.i18n("余额不足"));
            }
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== UltiEconomy Deposit ===");
        sender.sendMessage(ChatColor.YELLOW + "/deposit <amount>" + ChatColor.GRAY + " - " + plugin.i18n("存款到银行"));
    }
}
