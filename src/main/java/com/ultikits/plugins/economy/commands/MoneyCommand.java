package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.AbstractCommandExecutor;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CmdExecutor(
        permission = "ultieconomy.money",
        description = "查看余额",
        alias = {"money", "bal"}
)
public class MoneyCommand extends AbstractCommandExecutor {

    private final UltiToolsPlugin plugin;
    private final EconomyService economyService;

    public MoneyCommand(UltiToolsPlugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
    }

    @CmdMapping(format = "")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onBalance(@CmdSender Player player) {
        double cash = economyService.getCash(player.getUniqueId());
        double bank = economyService.getBank(player.getUniqueId());
        double total = economyService.getTotalWealth(player.getUniqueId());

        String formattedCash = economyService.formatAmount(cash);
        String formattedBank = economyService.formatAmount(bank);
        String formattedTotal = economyService.formatAmount(total);

        player.sendMessage(ChatColor.GOLD + "=== " + plugin.i18n("经济系统") + " ===");
        player.sendMessage(ChatColor.YELLOW + String.format(plugin.i18n("你的余额: %s"), formattedCash));
        player.sendMessage(ChatColor.YELLOW + String.format(plugin.i18n("你的银行存款: %s"), formattedBank));
        player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("总资产: %s"), formattedTotal));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== UltiEconomy ===");
        sender.sendMessage(ChatColor.YELLOW + "/money" + ChatColor.GRAY + " - " + plugin.i18n("查看余额"));
    }
}
