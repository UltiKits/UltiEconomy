package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.TreasuryEntity;
import com.ultikits.plugins.economy.model.CurrencyDefinition;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.service.TaxService;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

@CmdExecutor(
        permission = "ultieconomy.admin",
        description = "economy.command.eco.description",
        alias = {"eco"}
)
public class EcoAdminCommand extends BaseCommandExecutor {

    private UltiToolsPlugin plugin;
    private EconomyService economyService;
    private TaxService taxService;
    private CurrencyManager currencyManager;

    public EcoAdminCommand(UltiToolsPlugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.taxService = new TaxService(
                plugin.getConfig(EconomyConfig.class),
                plugin.getDataOperator(TreasuryEntity.class));
        this.currencyManager = ((UltiEconomy) plugin).getCurrencyManager();
    }

    @SuppressWarnings("all")
    private static EcoAdminCommand allocate() {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            return (EcoAdminCommand) unsafe.allocateInstance(EcoAdminCommand.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static EcoAdminCommand createForTest(UltiToolsPlugin plugin, EconomyService economyService) {
        EcoAdminCommand cmd = allocate();
        cmd.plugin = plugin;
        cmd.economyService = economyService;
        return cmd;
    }

    static EcoAdminCommand createForTest(UltiToolsPlugin plugin, EconomyService economyService,
                                         CurrencyManager currencyManager) {
        EcoAdminCommand cmd = allocate();
        cmd.plugin = plugin;
        cmd.economyService = economyService;
        cmd.currencyManager = currencyManager;
        return cmd;
    }

    static EcoAdminCommand createForTest(UltiToolsPlugin plugin, EconomyService economyService,
                                         TaxService taxService, CurrencyManager currencyManager) {
        EcoAdminCommand cmd = allocate();
        cmd.plugin = plugin;
        cmd.economyService = economyService;
        cmd.taxService = taxService;
        cmd.currencyManager = currencyManager;
        return cmd;
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
                    plugin.i18n("economy.admin.gave"), target.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.operation_failed"));
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
                    plugin.i18n("economy.admin.took"), target.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.insufficient_balance"));
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
                    plugin.i18n("economy.admin.set"), target.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.operation_failed"));
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
                plugin.i18n("economy.admin.check_cash"), target.getName(), economyService.formatAmount(cash)));
        sender.sendMessage(ChatColor.YELLOW + String.format(
                plugin.i18n("economy.admin.check_bank"), target.getName(), economyService.formatAmount(bank)));
        sender.sendMessage(ChatColor.GREEN + String.format(
                plugin.i18n("economy.money.total"), economyService.formatAmount(total)));
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

        CurrencyDefinition currency = currencyManager.resolve(currencyId);
        if (currency == null) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.currency_not_found"));
            return;
        }

        boolean success = economyService.addCash(target.getUniqueId(), amount, currency.getId());
        if (success) {
            String formatted = economyService.formatAmount(amount, currency.getId());
            sender.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("economy.admin.gave"), target.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.operation_failed"));
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

        CurrencyDefinition currency = currencyManager.resolve(currencyId);
        if (currency == null) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.currency_not_found"));
            return;
        }

        boolean success = economyService.takeCash(target.getUniqueId(), amount, currency.getId());
        if (success) {
            String formatted = economyService.formatAmount(amount, currency.getId());
            sender.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("economy.admin.took"), target.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.insufficient_balance"));
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

        CurrencyDefinition currency = currencyManager.resolve(currencyId);
        if (currency == null) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.currency_not_found"));
            return;
        }

        boolean success = economyService.setCash(target.getUniqueId(), amount, currency.getId());
        if (success) {
            String formatted = economyService.formatAmount(amount, currency.getId());
            sender.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("economy.admin.set"), target.getName(), formatted));
        } else {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.operation_failed"));
        }
    }

    @CmdMapping(format = "check <player> <currency>")
    public void onCheckCurrency(
            @CmdSender CommandSender sender,
            @CmdParam("player") String playerName,
            @CmdParam("currency") String currencyId) {

        OfflinePlayer target = resolvePlayer(sender, playerName);
        if (target == null) return;

        CurrencyDefinition currency = currencyManager.resolve(currencyId);
        if (currency == null) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.currency_not_found"));
            return;
        }
        String resolvedId = currency.getId();

        double cash = economyService.getCash(target.getUniqueId(), resolvedId);
        double bank = economyService.getBank(target.getUniqueId(), resolvedId);
        double total = economyService.getTotalWealth(target.getUniqueId(), resolvedId);

        sender.sendMessage(ChatColor.GOLD + "=== " + target.getName() + " (" + resolvedId + ") ===");
        sender.sendMessage(ChatColor.YELLOW + String.format(
                plugin.i18n("economy.admin.check_cash"), target.getName(), economyService.formatAmount(cash, resolvedId)));
        sender.sendMessage(ChatColor.YELLOW + String.format(
                plugin.i18n("economy.admin.check_bank"), target.getName(), economyService.formatAmount(bank, resolvedId)));
        sender.sendMessage(ChatColor.GREEN + String.format(
                plugin.i18n("economy.money.total"), economyService.formatAmount(total, resolvedId)));
    }

    @CmdMapping(format = "treasury")
    public void onTreasury(@CmdSender CommandSender sender) {
        if (taxService == null) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.tax_disabled"));
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
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.tax_disabled"));
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
                        plugin.i18n("economy.treasury.withdrawn"), formatted));
            } else {
                sender.sendMessage(ChatColor.RED + plugin.i18n("economy.treasury.insufficient"));
            }
        } catch (IllegalAccessException e) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.operation_failed"));
        }
    }

    @CmdMapping(format = "treasury withdraw <amount> <currency>")
    public void onTreasuryWithdrawCurrency(
            @CmdSender CommandSender sender,
            @CmdParam("amount") String amountStr,
            @CmdParam("currency") String currencyId) {
        if (taxService == null) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.tax_disabled"));
            return;
        }
        double amount = parseAmount(sender, amountStr);
        if (amount <= 0) return;
        try {
            boolean success = taxService.withdrawFromTreasury(amount, currencyId);
            if (success) {
                String formatted = economyService.formatAmount(amount, currencyId);
                sender.sendMessage(ChatColor.GREEN + String.format(
                        plugin.i18n("economy.treasury.withdrawn"), formatted));
            } else {
                sender.sendMessage(ChatColor.RED + plugin.i18n("economy.treasury.insufficient"));
            }
        } catch (IllegalAccessException e) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.operation_failed"));
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
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.player_not_found"));
            return null;
        }
        return player;
    }

    private double parseAmount(CommandSender sender, String amountStr) {
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount < 0) {
                sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.invalid_amount"));
                return -1;
            }
            return amount;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("economy.error.invalid_amount"));
            return -1;
        }
    }
}
