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
                    plugin.i18n("纸币已兑换: %s"), formatted));
        }

        event.setCancelled(true);
    }
}
