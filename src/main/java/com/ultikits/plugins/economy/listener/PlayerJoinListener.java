package com.ultikits.plugins.economy.listener;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.model.CurrencyDefinition;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@EventListener
public class PlayerJoinListener implements Listener {

    private EconomyService economyService;
    private CurrencyManager currencyManager;

    public PlayerJoinListener(UltiToolsPlugin plugin, EconomyService economyService) {
        this.economyService = economyService;
        this.currencyManager = ((UltiEconomy) plugin).getCurrencyManager();
    }

    @SuppressWarnings("all")
    static PlayerJoinListener createForTest(EconomyService economyService, CurrencyManager currencyManager) {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            PlayerJoinListener listener = (PlayerJoinListener) unsafe.allocateInstance(PlayerJoinListener.class);
            listener.economyService = economyService;
            listener.currencyManager = currencyManager;
            return listener;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        economyService.getOrCreateAccount(player.getUniqueId(), player.getName());

        if (currencyManager != null) {
            for (CurrencyDefinition currency : currencyManager.getAllCurrencies()) {
                economyService.getOrCreateBalance(
                        player.getUniqueId(), player.getName(), currency.getId());
            }
        }
    }
}
