package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.factory.MoneyNoteFactory;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.AbstractCommandExecutor;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@CmdExecutor(
        permission = "ultieconomy.note",
        description = "创建/兑换纸币",
        alias = {"note"}
)
public class NoteCommand extends AbstractCommandExecutor {

    private final UltiToolsPlugin plugin;
    private final EconomyService economyService;
    private final MoneyNoteFactory noteFactory;

    public NoteCommand(UltiToolsPlugin plugin, EconomyService economyService, MoneyNoteFactory noteFactory) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.noteFactory = noteFactory;
    }

    @Autowired
    public NoteCommand(UltiToolsPlugin plugin) {
        this(plugin,
             plugin.getContext().getBean(EconomyService.class),
             ((UltiEconomy) plugin).getMoneyNoteFactory());
    }

    @CmdMapping(format = "<amount>")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onCreateNote(@CmdSender Player player, @CmdParam("amount") String amountStr) {
        double amount = parseAmount(player, amountStr);
        if (amount <= 0) return;

        String currencyId = economyService.getPrimaryCurrencyId();
        if (!economyService.takeCash(player.getUniqueId(), amount)) {
            player.sendMessage(ChatColor.RED + plugin.i18n("余额不足"));
            return;
        }

        ItemStack note = noteFactory.createNote(currencyId, amount, player.getUniqueId(), player.getName());
        player.getInventory().addItem(note);
        String formatted = economyService.formatAmount(amount);
        player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("纸币已创建: %s"), formatted));
    }

    @CmdMapping(format = "<amount> <currency>")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onCreateCurrencyNote(
            @CmdSender Player player,
            @CmdParam("amount") String amountStr,
            @CmdParam("currency") String currencyId) {
        double amount = parseAmount(player, amountStr);
        if (amount <= 0) return;

        if (!economyService.takeCash(player.getUniqueId(), amount, currencyId)) {
            player.sendMessage(ChatColor.RED + plugin.i18n("余额不足"));
            return;
        }

        ItemStack note = noteFactory.createNote(currencyId, amount, player.getUniqueId(), player.getName());
        player.getInventory().addItem(note);
        String formatted = economyService.formatAmount(amount, currencyId);
        player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("纸币已创建: %s"), formatted));
    }

    @CmdMapping(format = "redeem")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onRedeem(@CmdSender Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!noteFactory.isMoneyNote(held)) {
            player.sendMessage(ChatColor.RED + plugin.i18n("手中没有纸币"));
            return;
        }

        double value = noteFactory.getNoteValue(held);
        String currencyId = noteFactory.getNoteCurrency(held);

        boolean success;
        if (currencyId.equals(economyService.getPrimaryCurrencyId())) {
            success = economyService.addCash(player.getUniqueId(), value);
        } else {
            success = economyService.addCash(player.getUniqueId(), value, currencyId);
        }

        if (success) {
            if (held.getAmount() > 1) {
                held.setAmount(held.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            String formatted = economyService.formatAmount(value, currencyId);
            player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("纸币已兑换: %s"), formatted));
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Money Notes ===");
        sender.sendMessage(ChatColor.YELLOW + "/note <amount>" + ChatColor.GRAY + " - " + plugin.i18n("创建纸币"));
        sender.sendMessage(ChatColor.YELLOW + "/note <amount> <currency>" + ChatColor.GRAY + " - " + plugin.i18n("创建指定货币纸币"));
        sender.sendMessage(ChatColor.YELLOW + "/note redeem" + ChatColor.GRAY + " - " + plugin.i18n("兑换手中纸币"));
    }

    private double parseAmount(Player player, String amountStr) {
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                player.sendMessage(ChatColor.RED + plugin.i18n("金额必须大于零"));
                return -1;
            }
            return amount;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + plugin.i18n("无效的金额"));
            return -1;
        }
    }
}
