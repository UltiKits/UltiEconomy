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

@DisplayName("WithdrawCommand")
@ExtendWith(MockitoExtension.class)
class WithdrawCommandTest {

    @Mock private UltiToolsPlugin plugin;
    @Mock private EconomyService economyService;
    @Mock private Player player;

    private EconomyConfig config;
    private WithdrawCommand command;
    private static final UUID PLAYER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        config = new EconomyConfig();
        lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        command = new WithdrawCommand(plugin, economyService, config);
    }

    @Test
    @DisplayName("successful withdrawal")
    void successfulWithdraw() {
        when(economyService.withdrawFromBank(PLAYER_UUID, 500.0)).thenReturn(true);
        when(economyService.formatAmount(500.0)).thenReturn("$500.00");

        command.onWithdraw(player, "500");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(captor.capture());
        assertThat(captor.getValue()).contains("成功从银行取出").contains("$500.00");
    }

    @Test
    @DisplayName("bank disabled shows error")
    void bankDisabled() {
        config.setBankEnabled(false);

        command.onWithdraw(player, "100");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(captor.capture());
        assertThat(captor.getValue()).contains("银行功能未启用");
    }

    @Test
    @DisplayName("invalid amount shows error")
    void invalidAmount() {
        command.onWithdraw(player, "abc");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(captor.capture());
        assertThat(captor.getValue()).contains("无效的金额");
    }

    @Test
    @DisplayName("zero amount shows error")
    void zeroAmount() {
        command.onWithdraw(player, "0");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(captor.capture());
        assertThat(captor.getValue()).contains("金额必须大于零");
    }

    @Test
    @DisplayName("insufficient bank balance shows error")
    void insufficientBalance() {
        when(economyService.withdrawFromBank(PLAYER_UUID, 5000.0)).thenReturn(false);

        command.onWithdraw(player, "5000");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(captor.capture());
        assertThat(captor.getValue()).contains("银行存款不足");
    }
}
