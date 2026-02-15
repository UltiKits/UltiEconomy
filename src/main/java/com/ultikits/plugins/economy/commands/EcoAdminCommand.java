package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.AbstractCommandExecutor;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

@CmdExecutor(
        permission = "ultieconomy.admin",
        description = "经济管理命令",
        alias = {"eco"}
)
public class EcoAdminCommand extends AbstractCommandExecutor {

    private final UltiToolsPlugin plugin;
    private final EconomyService economyService;

    public EcoAdminCommand(UltiToolsPlugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
    }

    @CmdMapping(format = "give <player> <amount>")
    public void onGive(
            @CmdSender CommandSender sender,
            @CmdParam("player") String playerName,
            @CmdParam("amount") String amountStr) {

        double amount = parseAmount(sender, amountStr);
        if (amount <= 0) return;

        OfflinePlayer target = resolvePlayer(sender, playerName);
        if (target == null) return;

        boolean success = economyService.addCash(target.getUniqueId(), amount);
        if (success) {
            String formatted = economyService.formatAmount(amount);
            sender.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("已给予 %s %s"), target.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("操作失败"));
        }
    }

    @CmdMapping(format = "take <player> <amount>")
    public void onTake(
            @CmdSender CommandSender sender,
            @CmdParam("player") String playerName,
            @CmdParam("amount") String amountStr) {

        double amount = parseAmount(sender, amountStr);
        if (amount <= 0) return;

        OfflinePlayer target = resolvePlayer(sender, playerName);
        if (target == null) return;

        boolean success = economyService.takeCash(target.getUniqueId(), amount);
        if (success) {
            String formatted = economyService.formatAmount(amount);
            sender.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("已扣除 %s %s"), target.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("余额不足"));
        }
    }

    @CmdMapping(format = "set <player> <amount>")
    public void onSet(
            @CmdSender CommandSender sender,
            @CmdParam("player") String playerName,
            @CmdParam("amount") String amountStr) {

        double amount = parseAmount(sender, amountStr);
        if (amount < 0) return;

        OfflinePlayer target = resolvePlayer(sender, playerName);
        if (target == null) return;

        boolean success = economyService.setCash(target.getUniqueId(), amount);
        if (success) {
            String formatted = economyService.formatAmount(amount);
            sender.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("已设置 %s 的余额为 %s"), target.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("操作失败"));
        }
    }

    @CmdMapping(format = "check <player>")
    public void onCheck(
            @CmdSender CommandSender sender,
            @CmdParam("player") String playerName) {

        OfflinePlayer target = resolvePlayer(sender, playerName);
        if (target == null) return;

        double cash = economyService.getCash(target.getUniqueId());
        double bank = economyService.getBank(target.getUniqueId());
        double total = economyService.getTotalWealth(target.getUniqueId());

        sender.sendMessage(ChatColor.GOLD + "=== " + target.getName() + " ===");
        sender.sendMessage(ChatColor.YELLOW + String.format(
                plugin.i18n("%s 的余额: %s"), target.getName(), economyService.formatAmount(cash)));
        sender.sendMessage(ChatColor.YELLOW + String.format(
                plugin.i18n("%s 的银行存款: %s"), target.getName(), economyService.formatAmount(bank)));
        sender.sendMessage(ChatColor.GREEN + String.format(
                plugin.i18n("总资产: %s"), economyService.formatAmount(total)));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== UltiEconomy Admin ===");
        sender.sendMessage(ChatColor.YELLOW + "/eco give <player> <amount>");
        sender.sendMessage(ChatColor.YELLOW + "/eco take <player> <amount>");
        sender.sendMessage(ChatColor.YELLOW + "/eco set <player> <amount>");
        sender.sendMessage(ChatColor.YELLOW + "/eco check <player>");
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolvePlayer(CommandSender sender, String name) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        if (!player.hasPlayedBefore() && !player.isOnline()) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("玩家不存在"));
            return null;
        }
        return player;
    }

    private double parseAmount(CommandSender sender, String amountStr) {
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount < 0) {
                sender.sendMessage(ChatColor.RED + plugin.i18n("无效的金额"));
                return -1;
            }
            return amount;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("无效的金额"));
            return -1;
        }
    }
}
