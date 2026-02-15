package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("BankCommand")
@ExtendWith(MockitoExtension.class)
class BankCommandTest {

    @Mock private UltiToolsPlugin plugin;
    @Mock private EconomyService economyService;
    @Mock private Player player;

    private EconomyConfig config;
    private BankCommand command;
    private static final UUID PLAYER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        config = new EconomyConfig();
        lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        command = new BankCommand(plugin, economyService, config);
    }

    @Test
    @DisplayName("shows bank balance when enabled")
    void showsBankBalance() {
        when(economyService.getBank(PLAYER_UUID)).thenReturn(5000.0);
        when(economyService.formatAmount(5000.0)).thenReturn("$5,000.00");

        command.onBank(player);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(captor.capture());
        assertThat(captor.getValue()).contains("$5,000.00");
    }

    @Test
    @DisplayName("shows error when bank disabled")
    void bankDisabled() {
        config.setBankEnabled(false);

        command.onBank(player);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(captor.capture());
        assertThat(captor.getValue()).contains("银行功能未启用");
        verify(economyService, never()).getBank(any());
    }
}
