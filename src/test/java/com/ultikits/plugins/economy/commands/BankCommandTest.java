package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.config.EconomyConfig;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("BankCommand")
@ExtendWith(MockitoExtension.class)
class BankCommandTest {

    @Mock private UltiToolsPlugin plugin;
    @Mock private EconomyService economyService;
    @Mock private CurrencyManager currencyManager;
    @Mock private Player player;

    private EconomyConfig config;
    private BankCommand command;
    private static final UUID PLAYER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        config = new EconomyConfig();
        lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        command = BankCommand.createForTest(plugin, economyService, config, currencyManager);
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

    @Test
    @DisplayName("shows bank balance for specific currency")
    void showsCurrencyBankBalance() {
        lenient().when(currencyManager.resolve("gems"))
                .thenReturn(CurrencyDefinition.builder().id("gems").build());
        when(economyService.getBank(PLAYER_UUID, "gems")).thenReturn(300.0);
        when(economyService.formatAmount(300.0, "gems")).thenReturn("G300.00");

        command.onBankCurrency(player, "gems");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(captor.capture());
        assertThat(captor.getValue()).contains("G300.00");
    }

    @Test
    @DisplayName("handleHelp sends command usage messages")
    void handleHelpShowsCommands() throws Exception {
        @SuppressWarnings("unchecked")
        CommandSender sender = mock(CommandSender.class);
        lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));

        java.lang.reflect.Method helpMethod = BankCommand.class.getDeclaredMethod("handleHelp", CommandSender.class);
        helpMethod.setAccessible(true);
        helpMethod.invoke(command, sender);

        verify(sender, atLeast(2)).sendMessage(anyString());
    }

    @Nested
    @DisplayName("Constructor wiring")
    class ConstructorWiringTests {

        @Test
        @DisplayName("Production constructor resolves the currency manager from an UltiEconomy plugin")
        void productionConstructorResolvesCurrencyManagerFromPlugin() {
            UltiEconomy ultiEconomy = mock(UltiEconomy.class);
            CurrencyManager resolvedCurrencyManager = mock(CurrencyManager.class);
            when(ultiEconomy.getCurrencyManager()).thenReturn(resolvedCurrencyManager);
            lenient().when(ultiEconomy.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
            when(resolvedCurrencyManager.resolve("gems"))
                    .thenReturn(CurrencyDefinition.builder().id("gems").build());
            when(economyService.getBank(PLAYER_UUID, "gems")).thenReturn(10.0);
            when(economyService.formatAmount(10.0, "gems")).thenReturn("G10.00");

            BankCommand realCommand = new BankCommand(ultiEconomy, economyService, config);
            realCommand.onBankCurrency(player, "gems");

            verify(resolvedCurrencyManager).resolve("gems");
            verify(economyService).getBank(PLAYER_UUID, "gems");
        }
    }

    @Nested
    @DisplayName("Unknown Currency Guard")
    class UnknownCurrencyGuardTests {

        @Test
        @DisplayName("Unknown currency is refused before any balance is read")
        void unknownCurrencyIsRefusedBeforeAnyBalanceIsRead() {
            when(currencyManager.resolve("bogus")).thenReturn(null);

            command.onBankCurrency(player, "bogus");

            verify(economyService, never()).getBank(any(UUID.class), anyString());

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player, atLeastOnce()).sendMessage(captor.capture());
            assertThat(captor.getAllValues()).anyMatch(m -> m.contains("货币不存在"));
        }

        @Test
        @DisplayName("An empty or blank currency identifier is refused the same way")
        void anEmptyOrBlankCurrencyIdentifierIsRefusedTheSameWay() {
            when(currencyManager.resolve("   ")).thenReturn(null);

            command.onBankCurrency(player, "   ");

            verify(economyService, never()).getBank(any(UUID.class), anyString());

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player, atLeastOnce()).sendMessage(captor.capture());
            assertThat(captor.getAllValues()).anyMatch(m -> m.contains("货币不存在"));
        }
    }
}
