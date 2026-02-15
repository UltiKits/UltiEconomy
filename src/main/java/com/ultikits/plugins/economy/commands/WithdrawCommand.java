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
        permission = "ultieconomy.withdraw",
        description = "从银行取款",
        alias = {"withdraw", "qk"}
)
public class WithdrawCommand extends AbstractCommandExecutor {

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

        boolean success = economyService.withdrawFromBank(player.getUniqueId(), amount);
        if (success) {
            String formatted = economyService.formatAmount(amount);
            player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("成功从银行取出 %s"), formatted));
        } else {
            player.sendMessage(ChatColor.RED + plugin.i18n("银行存款不足"));
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== UltiEconomy Withdraw ===");
        sender.sendMessage(ChatColor.YELLOW + "/withdraw <amount>" + ChatColor.GRAY + " - " + plugin.i18n("从银行取款"));
    }
}
