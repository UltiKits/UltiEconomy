package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.model.CurrencyDefinition;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MoneyCommand")
@ExtendWith(MockitoExtension.class)
class MoneyCommandTest {

    @Mock private UltiToolsPlugin plugin;
    @Mock private EconomyService economyService;
    @Mock private CurrencyManager currencyManager;
    @Mock private Player player;

    private MoneyCommand command;
    private static final UUID PLAYER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        command = MoneyCommand.createForTest(plugin, economyService, currencyManager);
    }

    @Test
    @DisplayName("shows cash, bank, and total wealth")
    void showsAllBalances() {
        when(economyService.getCash(PLAYER_UUID)).thenReturn(1500.0);
        when(economyService.getBank(PLAYER_UUID)).thenReturn(3000.0);
        when(economyService.getTotalWealth(PLAYER_UUID)).thenReturn(4500.0);
        when(economyService.formatAmount(1500.0)).thenReturn("$1,500.00");
        when(economyService.formatAmount(3000.0)).thenReturn("$3,000.00");
        when(economyService.formatAmount(4500.0)).thenReturn("$4,500.00");

        command.onBalance(player);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player, times(4)).sendMessage(captor.capture());
        List<String> messages = captor.getAllValues();

        assertThat(messages.get(0)).contains("经济系统");
        assertThat(messages.get(1)).contains("$1,500.00");
        assertThat(messages.get(2)).contains("$3,000.00");
        assertThat(messages.get(3)).contains("$4,500.00");
    }

    @Test
    @DisplayName("shows zero balances for new player")
    void showsZeroBalances() {
        when(economyService.getCash(PLAYER_UUID)).thenReturn(0.0);
        when(economyService.getBank(PLAYER_UUID)).thenReturn(0.0);
        when(economyService.getTotalWealth(PLAYER_UUID)).thenReturn(0.0);
        when(economyService.formatAmount(0.0)).thenReturn("$0.00");

        command.onBalance(player);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player, times(4)).sendMessage(captor.capture());
        List<String> messages = captor.getAllValues();
        assertThat(messages.get(1)).contains("$0.00");
    }

    @Test
    @DisplayName("shows balance for specific currency")
    void showsCurrencyBalance() {
        lenient().when(currencyManager.getCurrency("gems"))
                .thenReturn(CurrencyDefinition.builder().id("gems").build());
        when(economyService.getCash(PLAYER_UUID, "gems")).thenReturn(250.0);
        when(economyService.getBank(PLAYER_UUID, "gems")).thenReturn(0.0);
        when(economyService.getTotalWealth(PLAYER_UUID, "gems")).thenReturn(250.0);
        when(economyService.formatAmount(250.0, "gems")).thenReturn("G250.00");
        when(economyService.formatAmount(0.0, "gems")).thenReturn("G0.00");

        command.onCurrencyBalance(player, "gems");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player, times(4)).sendMessage(captor.capture());
        List<String> messages = captor.getAllValues();
        assertThat(messages.get(0)).contains("gems");
        assertThat(messages.get(1)).contains("G250.00");
    }

    @Test
    @DisplayName("handleHelp sends command usage messages")
    void handleHelpShowsCommands() throws Exception {
        @SuppressWarnings("unchecked")
        CommandSender sender = mock(CommandSender.class);
        lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));

        java.lang.reflect.Method helpMethod = MoneyCommand.class.getDeclaredMethod("handleHelp", CommandSender.class);
        helpMethod.setAccessible(true);
        helpMethod.invoke(command, sender);

        verify(sender, atLeast(2)).sendMessage(anyString());
    }

    // ============================
    // Unknown currency guard
    // ============================

    @Nested
    @DisplayName("Unknown Currency Guard")
    class UnknownCurrencyGuardTests {

        @Test
        @DisplayName("Unknown currency is refused before any balance is read")
        void unknownCurrencyIsRefusedBeforeAnyBalanceIsRead() {
            when(currencyManager.getCurrency("bogus")).thenReturn(null);

            command.onCurrencyBalance(player, "bogus");

            verify(economyService, never()).getCash(any(UUID.class), anyString());
            verify(economyService, never()).getBank(any(UUID.class), anyString());
            verify(economyService, never()).getTotalWealth(any(UUID.class), anyString());

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player, atLeastOnce()).sendMessage(captor.capture());
            assertThat(captor.getAllValues()).anyMatch(m -> m.contains("货币不存在"));
        }

        @Test
        @DisplayName("Known currency still reports its balance")
        void knownCurrencyStillReportsItsBalance() {
            lenient().when(currencyManager.getCurrency("gems"))
                    .thenReturn(CurrencyDefinition.builder().id("gems").build());
            when(economyService.getCash(PLAYER_UUID, "gems")).thenReturn(250.0);
            when(economyService.getBank(PLAYER_UUID, "gems")).thenReturn(0.0);
            when(economyService.getTotalWealth(PLAYER_UUID, "gems")).thenReturn(250.0);
            when(economyService.formatAmount(250.0, "gems")).thenReturn("G250.00");
            when(economyService.formatAmount(0.0, "gems")).thenReturn("G0.00");

            command.onCurrencyBalance(player, "gems");

            verify(economyService).getCash(PLAYER_UUID, "gems");
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player, atLeastOnce()).sendMessage(captor.capture());
            assertThat(captor.getAllValues()).anyMatch(m -> m.contains("G250.00"));
        }

        @Test
        @DisplayName("An empty or blank currency identifier is refused the same way")
        void anEmptyOrBlankCurrencyIdentifierIsRefusedTheSameWay() {
            command.onCurrencyBalance(player, "   ");

            verify(economyService, never()).getCash(any(UUID.class), anyString());
            verify(currencyManager, never()).getCurrency(anyString());

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player, atLeastOnce()).sendMessage(captor.capture());
            assertThat(captor.getAllValues()).anyMatch(m -> m.contains("货币不存在"));
        }
    }
}
