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
        permission = "ultieconomy.bank",
        description = "查看银行存款",
        alias = {"bank"}
)
public class BankCommand extends AbstractCommandExecutor {

    private final UltiToolsPlugin plugin;
    private final EconomyService economyService;
    private final EconomyConfig config;

    public BankCommand(UltiToolsPlugin plugin, EconomyService economyService, EconomyConfig config) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.config = config;
    }

    @CmdMapping(format = "")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onBank(@CmdSender Player player) {
        if (!config.isBankEnabled()) {
            player.sendMessage(ChatColor.RED + plugin.i18n("银行功能未启用"));
            return;
        }

        double bank = economyService.getBank(player.getUniqueId());
        String formatted = economyService.formatAmount(bank);
        player.sendMessage(ChatColor.YELLOW + String.format(plugin.i18n("你的银行存款: %s"), formatted));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== UltiEconomy Bank ===");
        sender.sendMessage(ChatColor.YELLOW + "/bank" + ChatColor.GRAY + " - " + plugin.i18n("查看银行存款"));
    }
}
