package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
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
public class MoneyCommand extends BaseCommandExecutor {

    private UltiToolsPlugin plugin;
    private EconomyService economyService;
    private CurrencyManager currencyManager;

    public MoneyCommand(UltiToolsPlugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.currencyManager = ((UltiEconomy) plugin).getCurrencyManager();
    }

    @SuppressWarnings("all")
    private static MoneyCommand allocate() {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            return (MoneyCommand) unsafe.allocateInstance(MoneyCommand.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static MoneyCommand createForTest(UltiToolsPlugin plugin, EconomyService economyService,
                                       CurrencyManager currencyManager) {
        MoneyCommand cmd = allocate();
        cmd.plugin = plugin;
        cmd.economyService = economyService;
        cmd.currencyManager = currencyManager;
        return cmd;
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

    @CmdMapping(format = "<currency>")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onCurrencyBalance(@CmdSender Player player, @CmdParam("currency") String currencyId) {
        double cash = economyService.getCash(player.getUniqueId(), currencyId);
        double bank = economyService.getBank(player.getUniqueId(), currencyId);
        double total = economyService.getTotalWealth(player.getUniqueId(), currencyId);

        String formattedCash = economyService.formatAmount(cash, currencyId);
        String formattedBank = economyService.formatAmount(bank, currencyId);
        String formattedTotal = economyService.formatAmount(total, currencyId);

        player.sendMessage(ChatColor.GOLD + "=== " + plugin.i18n("经济系统") + " (" + currencyId + ") ===");
        player.sendMessage(ChatColor.YELLOW + String.format(plugin.i18n("你的余额: %s"), formattedCash));
        player.sendMessage(ChatColor.YELLOW + String.format(plugin.i18n("你的银行存款: %s"), formattedBank));
        player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("总资产: %s"), formattedTotal));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== UltiEconomy ===");
        sender.sendMessage(ChatColor.YELLOW + "/money" + ChatColor.GRAY + " - " + plugin.i18n("查看余额"));
        sender.sendMessage(ChatColor.YELLOW + "/money <currency>" + ChatColor.GRAY + " - " + plugin.i18n("查看指定货币余额"));
    }
}
