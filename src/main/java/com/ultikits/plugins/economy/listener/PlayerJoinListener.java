package com.ultikits.plugins.economy.listener;

import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@EventListener
public class PlayerJoinListener implements Listener {

    private final EconomyService economyService;

    public PlayerJoinListener(EconomyService economyService) {
        this.economyService = economyService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        economyService.getOrCreateAccount(player.getUniqueId(), player.getName());
    }
}
