package com.ultikits.plugins.economy.listener;

import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.service.EconomyService;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@DisplayName("PlayerJoinListener")
@ExtendWith(MockitoExtension.class)
class PlayerJoinListenerTest {

    @Mock private EconomyService economyService;
    @Mock private Player player;

    private PlayerJoinListener listener;

    private static final UUID PLAYER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @BeforeEach
    void setUp() {
        listener = new PlayerJoinListener(economyService);
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        lenient().when(player.getName()).thenReturn("TestPlayer");
    }

    @Test
    @DisplayName("creates account for new player on join")
    void createsAccountForNewPlayer() {
        PlayerAccountEntity newAccount = PlayerAccountEntity.builder()
                .uuid(PLAYER_UUID.toString())
                .playerName("TestPlayer")
                .cash(1000.0)
                .bank(0.0)
                .build();
        when(economyService.getOrCreateAccount(PLAYER_UUID, "TestPlayer")).thenReturn(newAccount);

        PlayerJoinEvent event = new PlayerJoinEvent(player, "TestPlayer joined");
        listener.onPlayerJoin(event);

        verify(economyService).getOrCreateAccount(PLAYER_UUID, "TestPlayer");
    }

    @Test
    @DisplayName("returns existing account for returning player")
    void returnsExistingForReturningPlayer() {
        PlayerAccountEntity existing = PlayerAccountEntity.builder()
                .uuid(PLAYER_UUID.toString())
                .playerName("TestPlayer")
                .cash(5000.0)
                .bank(2000.0)
                .build();
        when(economyService.getOrCreateAccount(PLAYER_UUID, "TestPlayer")).thenReturn(existing);

        PlayerJoinEvent event = new PlayerJoinEvent(player, "TestPlayer joined");
        listener.onPlayerJoin(event);

        verify(economyService).getOrCreateAccount(PLAYER_UUID, "TestPlayer");
    }
}
