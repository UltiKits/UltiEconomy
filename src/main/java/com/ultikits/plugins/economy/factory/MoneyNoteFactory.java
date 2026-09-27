package com.ultikits.plugins.economy.factory;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.UUID;

public class MoneyNoteFactory {

    private final Plugin plugin;
    /** The module, whose language catalogue gives the note its name and lore. */
    private final UltiToolsPlugin module;

    public MoneyNoteFactory(Plugin plugin, UltiToolsPlugin module) {
        this.plugin = plugin;
        this.module = module;
    }

    public ItemStack createNote(String currencyId, double amount, UUID creatorUuid, String creatorName) {
        ItemStack item = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyNoteData(meta, currencyId, amount, creatorUuid, creatorName);
            item.setItemMeta(meta);
        }
        return item;
    }

    void applyNoteData(ItemMeta meta, String currencyId, double amount, UUID creatorUuid, String creatorName) {
        String formattedAmount = String.format("%.2f", amount);
        // A note is identified by its persistent data below, never by this text, so a note keeps
        // working when the server's language changes after it was made.
        meta.setDisplayName(ChatColor.GOLD + String.format(module.i18n("economy.note.item_name"),
                formattedAmount, currencyId));
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + String.format(module.i18n("economy.note.item_currency"), currencyId),
                ChatColor.GRAY + String.format(module.i18n("economy.note.item_value"), formattedAmount),
                ChatColor.GRAY + String.format(module.i18n("economy.note.item_creator"), creatorName)
        ));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, "note_currency"), PersistentDataType.STRING, currencyId);
        pdc.set(new NamespacedKey(plugin, "note_value"), PersistentDataType.DOUBLE, amount);
        pdc.set(new NamespacedKey(plugin, "note_creator"), PersistentDataType.STRING, creatorUuid.toString());
        pdc.set(new NamespacedKey(plugin, "note_created_at"), PersistentDataType.LONG, System.currentTimeMillis());
    }

    public boolean isMoneyNote(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "note_value"), PersistentDataType.DOUBLE);
    }

    public double getNoteValue(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0.0;
        }
        Double value = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "note_value"), PersistentDataType.DOUBLE);
        return value != null ? value : 0.0;
    }

    public String getNoteCurrency(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "";
        }
        String currency = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "note_currency"), PersistentDataType.STRING);
        return currency != null ? currency : "";
    }
}
