package com.ultikits.plugins.economy.listener;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.model.CurrencyDefinition;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@EventListener
public class PlayerJoinListener implements Listener {

    private final EconomyService economyService;
    private final CurrencyManager currencyManager;

    public PlayerJoinListener(EconomyService economyService, CurrencyManager currencyManager) {
        this.economyService = economyService;
        this.currencyManager = currencyManager;
    }

    // Backward-compatible constructor
    public PlayerJoinListener(EconomyService economyService) {
        this(economyService, null);
    }

    @Autowired
    public PlayerJoinListener(UltiToolsPlugin plugin) {
        this(plugin.getContext().getBean(EconomyService.class),
             ((UltiEconomy) plugin).getCurrencyManager());
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
