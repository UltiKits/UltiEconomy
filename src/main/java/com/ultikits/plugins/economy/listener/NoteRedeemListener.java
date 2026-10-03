package com.ultikits.plugins.economy.listener;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.factory.MoneyNoteFactory;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

@EventListener
public class NoteRedeemListener implements Listener {

    private UltiToolsPlugin plugin;
    private EconomyService economyService;
    private MoneyNoteFactory noteFactory;

    public NoteRedeemListener(UltiToolsPlugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.noteFactory = ((UltiEconomy) plugin).getMoneyNoteFactory();
    }

    @SuppressWarnings("all")
    static NoteRedeemListener createForTest(UltiToolsPlugin plugin, EconomyService economyService, MoneyNoteFactory noteFactory) {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            NoteRedeemListener listener = (NoteRedeemListener) unsafe.allocateInstance(NoteRedeemListener.class);
            listener.plugin = plugin;
            listener.economyService = economyService;
            listener.noteFactory = noteFactory;
            return listener;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!noteFactory.isMoneyNote(held)) {
            return;
        }

        double value = noteFactory.getNoteValue(held);
        String currencyId = noteFactory.getNoteCurrency(held);
        if (!currencyId.equals(economyService.getPrimaryCurrencyId()) && !isConfigured(currencyId)) {
            refuseRemovedCurrency(plugin, player, value, currencyId);
            event.setCancelled(true);
            return;
        }

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
            player.sendMessage(ChatColor.GREEN + String.format(
                    plugin.i18n("economy.note.redeemed"), formatted));
        }

        event.setCancelled(true);
    }

    /** Whether {@code currencyId} is still a currency of {@code currencies.yml}. */
    private boolean isConfigured(String currencyId) {
        com.ultikits.plugins.economy.service.CurrencyManager currencies = ((UltiEconomy) plugin).getCurrencyManager();
        return currencies != null && currencies.hasCurrency(currencyId);
    }

    /**
     * Refuses a money note whose currency an operator removed from {@code currencies.yml}
     * (UltiKits/UltiEconomy#37, maintainer decision 2026-10-04): nothing is credited and the note is
     * kept, the player is told the currency no longer exists, and an operator-facing line names it.
     * Without this a player who still had a wallet row for that currency redeemed the note into a
     * wallet no command shows. Shared with {@code /note redeem}.
     *
     * @param plugin     the module, for its catalogue and logger
     * @param player     the player holding the note
     * @param value      the note's face value
     * @param currencyId the note's currency, no longer configured
     */
    public static void refuseRemovedCurrency(UltiToolsPlugin plugin, Player player, double value, String currencyId) {
        player.sendMessage(ChatColor.RED + String.format(plugin.i18n("economy.note.currency_removed"), currencyId));
        plugin.getLogger().warn(String.format(plugin.i18n("economy.log.note_currency_removed"),
                player.getName(), String.valueOf(value), currencyId));
    }
}
