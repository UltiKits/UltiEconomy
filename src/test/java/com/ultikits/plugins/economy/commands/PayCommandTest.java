package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("PayCommand")
@ExtendWith(MockitoExtension.class)
class PayCommandTest {

    @Mock private UltiToolsPlugin plugin;
    @Mock private EconomyService economyService;
    @Mock private Player sender;
    @Mock private Player target;
    @Mock private Server server;

    private PayCommand command;
    private static final UUID SENDER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID TARGET_UUID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(sender.getUniqueId()).thenReturn(SENDER_UUID);
        lenient().when(sender.getName()).thenReturn("Alice");
        lenient().when(target.getUniqueId()).thenReturn(TARGET_UUID);
        lenient().when(target.getName()).thenReturn("Bob");
        command = new PayCommand(plugin, economyService);
    }

    @Nested
    @DisplayName("Success Cases")
    class SuccessCases {

        @Test
        @DisplayName("successful transfer notifies both players")
        void successfulTransfer() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer("Bob")).thenReturn(target);

                when(economyService.transfer(SENDER_UUID, TARGET_UUID, 500.0)).thenReturn(true);
                when(economyService.formatAmount(500.0)).thenReturn("$500.00");

                command.onPay(sender, "Bob", "500");

                ArgumentCaptor<String> senderCaptor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(senderCaptor.capture());
                assertThat(senderCaptor.getValue()).contains("成功转账").contains("$500.00").contains("Bob");

                ArgumentCaptor<String> targetCaptor = ArgumentCaptor.forClass(String.class);
                verify(target).sendMessage(targetCaptor.capture());
                assertThat(targetCaptor.getValue()).contains("Alice").contains("$500.00");
            }
        }
    }

    @Nested
    @DisplayName("Failure Cases")
    class FailureCases {

        @Test
        @DisplayName("invalid amount shows error")
        void invalidAmount() {
            command.onPay(sender, "Bob", "abc");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("无效的金额");
        }

        @Test
        @DisplayName("zero amount shows error")
        void zeroAmount() {
            command.onPay(sender, "Bob", "0");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("金额必须大于零");
        }

        @Test
        @DisplayName("negative amount shows error")
        void negativeAmount() {
            command.onPay(sender, "Bob", "-50");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("金额必须大于零");
        }

        @Test
        @DisplayName("offline player shows error")
        void offlinePlayer() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer("Nobody")).thenReturn(null);

                command.onPay(sender, "Nobody", "100");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("玩家不存在");
            }
        }

        @Test
        @DisplayName("self-transfer shows error")
        void selfTransfer() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                // Return a player with same UUID as sender
                Player self = mock(Player.class);
                when(self.getUniqueId()).thenReturn(SENDER_UUID);
                bukkit.when(() -> Bukkit.getPlayer("Alice")).thenReturn(self);

                command.onPay(sender, "Alice", "100");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("无效的金额");
            }
        }

        @Test
        @DisplayName("insufficient funds shows error")
        void insufficientFunds() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer("Bob")).thenReturn(target);
                when(economyService.transfer(SENDER_UUID, TARGET_UUID, 999999.0)).thenReturn(false);

                command.onPay(sender, "Bob", "999999");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("余额不足");
            }
        }
    }
}
