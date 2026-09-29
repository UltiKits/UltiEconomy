package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
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

    @Mock private com.ultikits.plugins.economy.UltiEconomy plugin;
    @Mock private EconomyService economyService;
    @Mock private Player sender;
    @Mock private Player target;
    @Mock private Server server;

    private PayCommand command;
    private static final UUID SENDER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID TARGET_UUID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getCurrencyManager()).thenReturn(com.ultikits.plugins.economy.testsupport.Currencies.coinsAndGems());
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
        lenient().when(sender.getUniqueId()).thenReturn(SENDER_UUID);
        lenient().when(sender.getName()).thenReturn("Alice");
        lenient().when(target.getUniqueId()).thenReturn(TARGET_UUID);
        lenient().when(target.getName()).thenReturn("Bob");
        command = new PayCommand(plugin, economyService);
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
                // Paying yourself is its own refusal, not an invalid amount.
                assertThat(captor.getValue()).contains("不能向自己转账");
            }
        }
    }

    @Nested
    @DisplayName("Currency Failure Cases")
    class CurrencyFailureCases {

        @Test
        @DisplayName("currency transfer with invalid amount shows error")
        void currencyInvalidAmount() {
            command.onPayWithCurrency(sender, "Bob", "abc", "gems");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("无效的金额");
        }

        @Test
        @DisplayName("currency transfer with zero amount shows error")
        void currencyZeroAmount() {
            command.onPayWithCurrency(sender, "Bob", "0", "gems");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("金额必须大于零");
        }

        @Test
        @DisplayName("currency transfer with negative amount shows error")
        void currencyNegativeAmount() {
            command.onPayWithCurrency(sender, "Bob", "-50", "gems");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("金额必须大于零");
        }

        @Test
        @DisplayName("currency transfer to offline player shows error")
        void currencyOfflinePlayer() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer("Nobody")).thenReturn(null);

                command.onPayWithCurrency(sender, "Nobody", "100", "gems");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("玩家不存在");
            }
        }

        @Test
        @DisplayName("currency self-transfer shows error")
        void currencySelfTransfer() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                Player self = mock(Player.class);
                when(self.getUniqueId()).thenReturn(SENDER_UUID);
                bukkit.when(() -> Bukkit.getPlayer("Alice")).thenReturn(self);

                command.onPayWithCurrency(sender, "Alice", "100", "gems");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                // Paying yourself is its own refusal, not an invalid amount.
                assertThat(captor.getValue()).contains("不能向自己转账");
            }
        }
    }

    @Test
    @DisplayName("handleHelp sends command usage messages")
    void handleHelpShowsCommands() throws Exception {
        @SuppressWarnings("unchecked")
        CommandSender helpSender = mock(CommandSender.class);
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));

        java.lang.reflect.Method helpMethod = PayCommand.class.getDeclaredMethod("handleHelp", CommandSender.class);
        helpMethod.setAccessible(true);
        helpMethod.invoke(command, helpSender);

        verify(helpSender, atLeast(2)).sendMessage(anyString());
    }

    @Nested
    @DisplayName("an unknown currency is refused (#13)")
    class UnknownCurrency {

        @Test
        @DisplayName("/pay <player> <amount> <unknown currency> says the currency does not exist and moves nothing")
        void refusesAnUnknownCurrency() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer("Bob")).thenReturn(target);

                command.onPayWithCurrency(sender, "Bob", "100", "rubies");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains(CatalogueText.text("zh", "economy.error.currency_not_found"));
                verify(economyService, never()).transfer(any(), any(), anyDouble(), anyString());
                verify(target, never()).sendMessage(anyString());
            }
        }
    }
}
