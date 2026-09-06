package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.model.CurrencyDefinition;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
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
public class BankCommand extends BaseCommandExecutor {

    private UltiToolsPlugin plugin;
    private EconomyService economyService;
    private EconomyConfig config;
    private CurrencyManager currencyManager;

    public BankCommand(UltiToolsPlugin plugin, EconomyService economyService, EconomyConfig config) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.config = config;
        this.currencyManager = ((UltiEconomy) plugin).getCurrencyManager();
    }

    @SuppressWarnings("all")
    private static BankCommand allocate() {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            return (BankCommand) unsafe.allocateInstance(BankCommand.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static BankCommand createForTest(UltiToolsPlugin plugin, EconomyService economyService,
                                      EconomyConfig config, CurrencyManager currencyManager) {
        BankCommand cmd = allocate();
        cmd.plugin = plugin;
        cmd.economyService = economyService;
        cmd.config = config;
        cmd.currencyManager = currencyManager;
        return cmd;
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

    @CmdMapping(format = "<currency>")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onBankCurrency(@CmdSender Player player, @CmdParam("currency") String currencyId) {
        CurrencyDefinition currency = currencyManager.resolve(currencyId);
        if (currency == null) {
            player.sendMessage(ChatColor.RED + plugin.i18n("货币不存在"));
            return;
        }

        double bank = economyService.getBank(player.getUniqueId(), currency.getId());
        String formatted = economyService.formatAmount(bank, currency.getId());
        player.sendMessage(ChatColor.YELLOW + String.format(plugin.i18n("你的银行存款: %s"), formatted));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== UltiEconomy Bank ===");
        sender.sendMessage(ChatColor.YELLOW + "/bank" + ChatColor.GRAY + " - " + plugin.i18n("查看银行存款"));
        sender.sendMessage(ChatColor.YELLOW + "/bank <currency>" + ChatColor.GRAY + " - " + plugin.i18n("查看指定货币银行存款"));
    }
}
