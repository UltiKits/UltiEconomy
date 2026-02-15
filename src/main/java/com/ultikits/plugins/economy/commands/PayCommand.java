package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.AbstractCommandExecutor;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CmdExecutor(
        permission = "ultieconomy.pay",
        description = "转账给其他玩家",
        alias = {"pay"}
)
public class PayCommand extends AbstractCommandExecutor {

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
            sender.sendMessage(ChatColor.RED + plugin.i18n("无效的金额"));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("金额必须大于零"));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("玩家不存在"));
            return;
        }

        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("无效的金额"));
            return;
        }

        boolean success = economyService.transfer(sender.getUniqueId(), target.getUniqueId(), amount);
        if (success) {
            String formatted = economyService.formatAmount(amount);
            sender.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("成功转账 %s 给 %s"), formatted, target.getName()));
            target.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("%s 向你转账了 %s"), sender.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("余额不足"));
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== UltiEconomy Pay ===");
        sender.sendMessage(ChatColor.YELLOW + "/pay <player> <amount>" + ChatColor.GRAY + " - " + plugin.i18n("转账"));
    }
}
