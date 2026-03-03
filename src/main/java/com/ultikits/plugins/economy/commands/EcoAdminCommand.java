package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.TreasuryEntity;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.service.TaxService;
import com.ultikits.ultitools.abstracts.AbstractCommandExecutor;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
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
    private TaxService taxService;
    private CurrencyManager currencyManager;

    public EcoAdminCommand(UltiToolsPlugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
    }

    public EcoAdminCommand(UltiToolsPlugin plugin, EconomyService economyService,
                           TaxService taxService, CurrencyManager currencyManager) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.taxService = taxService;
        this.currencyManager = currencyManager;
    }

    @Autowired
    public EcoAdminCommand(UltiToolsPlugin plugin) {
        this(plugin,
             plugin.getContext().getBean(EconomyService.class),
             new TaxService(
                     plugin.getConfig(EconomyConfig.class),
                     plugin.getDataOperator(TreasuryEntity.class)),
             ((UltiEconomy) plugin).getCurrencyManager());
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

    // --- Currency-aware overloads ---

    @CmdMapping(format = "give <player> <amount> <currency>")
    public void onGiveCurrency(
            @CmdSender CommandSender sender,
            @CmdParam("player") String playerName,
            @CmdParam("amount") String amountStr,
            @CmdParam("currency") String currencyId) {

        double amount = parseAmount(sender, amountStr);
        if (amount <= 0) return;

        OfflinePlayer target = resolvePlayer(sender, playerName);
        if (target == null) return;

        boolean success = economyService.addCash(target.getUniqueId(), amount, currencyId);
        if (success) {
            String formatted = economyService.formatAmount(amount, currencyId);
            sender.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("已给予 %s %s"), target.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("操作失败"));
        }
    }

    @CmdMapping(format = "take <player> <amount> <currency>")
    public void onTakeCurrency(
            @CmdSender CommandSender sender,
            @CmdParam("player") String playerName,
            @CmdParam("amount") String amountStr,
            @CmdParam("currency") String currencyId) {

        double amount = parseAmount(sender, amountStr);
        if (amount <= 0) return;

        OfflinePlayer target = resolvePlayer(sender, playerName);
        if (target == null) return;

        boolean success = economyService.takeCash(target.getUniqueId(), amount, currencyId);
        if (success) {
            String formatted = economyService.formatAmount(amount, currencyId);
            sender.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("已扣除 %s %s"), target.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("余额不足"));
        }
    }

    @CmdMapping(format = "set <player> <amount> <currency>")
    public void onSetCurrency(
            @CmdSender CommandSender sender,
            @CmdParam("player") String playerName,
            @CmdParam("amount") String amountStr,
            @CmdParam("currency") String currencyId) {

        double amount = parseAmount(sender, amountStr);
        if (amount < 0) return;

        OfflinePlayer target = resolvePlayer(sender, playerName);
        if (target == null) return;

        boolean success = economyService.setCash(target.getUniqueId(), amount, currencyId);
        if (success) {
            String formatted = economyService.formatAmount(amount, currencyId);
            sender.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("已设置 %s 的余额为 %s"), target.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("操作失败"));
        }
    }

    @CmdMapping(format = "check <player> <currency>")
    public void onCheckCurrency(
            @CmdSender CommandSender sender,
            @CmdParam("player") String playerName,
            @CmdParam("currency") String currencyId) {

        OfflinePlayer target = resolvePlayer(sender, playerName);
        if (target == null) return;

        double cash = economyService.getCash(target.getUniqueId(), currencyId);
        double bank = economyService.getBank(target.getUniqueId(), currencyId);
        double total = economyService.getTotalWealth(target.getUniqueId(), currencyId);

        sender.sendMessage(ChatColor.GOLD + "=== " + target.getName() + " (" + currencyId + ") ===");
        sender.sendMessage(ChatColor.YELLOW + String.format(
                plugin.i18n("%s 的余额: %s"), target.getName(), economyService.formatAmount(cash, currencyId)));
        sender.sendMessage(ChatColor.YELLOW + String.format(
                plugin.i18n("%s 的银行存款: %s"), target.getName(), economyService.formatAmount(bank, currencyId)));
        sender.sendMessage(ChatColor.GREEN + String.format(
                plugin.i18n("总资产: %s"), economyService.formatAmount(total, currencyId)));
    }

    @CmdMapping(format = "treasury")
    public void onTreasury(@CmdSender CommandSender sender) {
        if (taxService == null) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("税收系统未启用"));
            return;
        }
        if (currencyManager != null) {
            for (com.ultikits.plugins.economy.model.CurrencyDefinition def : currencyManager.getAllCurrencies()) {
                double balance = taxService.getTreasuryBalance(def.getId());
                String formatted = economyService.formatAmount(balance, def.getId());
                sender.sendMessage(ChatColor.YELLOW + def.getDisplayName() + ": " + formatted);
            }
        }
    }

    @CmdMapping(format = "treasury withdraw <amount>")
    public void onTreasuryWithdraw(
            @CmdSender CommandSender sender,
            @CmdParam("amount") String amountStr) {
        if (taxService == null) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("税收系统未启用"));
            return;
        }
        double amount = parseAmount(sender, amountStr);
        if (amount <= 0) return;
        String primaryId = currencyManager.getPrimaryCurrency().getId();
        try {
            boolean success = taxService.withdrawFromTreasury(amount, primaryId);
            if (success) {
                String formatted = economyService.formatAmount(amount, primaryId);
                sender.sendMessage(ChatColor.GREEN + String.format(
                        plugin.i18n("已从国库提取 %s"), formatted));
            } else {
                sender.sendMessage(ChatColor.RED + plugin.i18n("国库余额不足"));
            }
        } catch (IllegalAccessException e) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("操作失败"));
        }
    }

    @CmdMapping(format = "treasury withdraw <amount> <currency>")
    public void onTreasuryWithdrawCurrency(
            @CmdSender CommandSender sender,
            @CmdParam("amount") String amountStr,
            @CmdParam("currency") String currencyId) {
        if (taxService == null) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("税收系统未启用"));
            return;
        }
        double amount = parseAmount(sender, amountStr);
        if (amount <= 0) return;
        try {
            boolean success = taxService.withdrawFromTreasury(amount, currencyId);
            if (success) {
                String formatted = economyService.formatAmount(amount, currencyId);
                sender.sendMessage(ChatColor.GREEN + String.format(
                        plugin.i18n("已从国库提取 %s"), formatted));
            } else {
                sender.sendMessage(ChatColor.RED + plugin.i18n("国库余额不足"));
            }
        } catch (IllegalAccessException e) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("操作失败"));
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== UltiEconomy Admin ===");
        sender.sendMessage(ChatColor.YELLOW + "/eco give <player> <amount> [currency]");
        sender.sendMessage(ChatColor.YELLOW + "/eco take <player> <amount> [currency]");
        sender.sendMessage(ChatColor.YELLOW + "/eco set <player> <amount> [currency]");
        sender.sendMessage(ChatColor.YELLOW + "/eco check <player> [currency]");
        sender.sendMessage(ChatColor.YELLOW + "/eco treasury");
        sender.sendMessage(ChatColor.YELLOW + "/eco treasury withdraw <amount> [currency]");
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
