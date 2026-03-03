package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.model.CurrencyDefinition;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.service.TaxService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("EcoAdminCommand")
@ExtendWith(MockitoExtension.class)
class EcoAdminCommandTest {

    @Mock private UltiToolsPlugin plugin;
    @Mock private EconomyService economyService;
    @Mock private CommandSender sender;
    @Mock private OfflinePlayer targetPlayer;

    private EcoAdminCommand command;
    private static final UUID TARGET_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(targetPlayer.getUniqueId()).thenReturn(TARGET_UUID);
        lenient().when(targetPlayer.getName()).thenReturn("Steve");
        lenient().when(targetPlayer.hasPlayedBefore()).thenReturn(true);
        command = new EcoAdminCommand(plugin, economyService);
    }

    @Nested
    @DisplayName("Give")
    class GiveTests {

        @Test
        @DisplayName("give adds cash and confirms")
        void giveSuccess() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(targetPlayer);
                when(economyService.addCash(TARGET_UUID, 500.0)).thenReturn(true);
                when(economyService.formatAmount(500.0)).thenReturn("$500.00");

                command.onGive(sender, "Steve", "500");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("已给予").contains("Steve").contains("$500.00");
            }
        }

        @Test
        @DisplayName("give with invalid amount shows error")
        void giveInvalidAmount() {
            command.onGive(sender, "Steve", "abc");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("无效的金额");
        }

        @Test
        @DisplayName("give to unknown player shows error")
        void giveUnknownPlayer() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                OfflinePlayer unknown = mock(OfflinePlayer.class);
                when(unknown.hasPlayedBefore()).thenReturn(false);
                when(unknown.isOnline()).thenReturn(false);
                bukkit.when(() -> Bukkit.getOfflinePlayer("Nobody")).thenReturn(unknown);

                command.onGive(sender, "Nobody", "100");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("玩家不存在");
            }
        }
    }

    @Nested
    @DisplayName("Take")
    class TakeTests {

        @Test
        @DisplayName("take removes cash and confirms")
        void takeSuccess() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(targetPlayer);
                when(economyService.takeCash(TARGET_UUID, 300.0)).thenReturn(true);
                when(economyService.formatAmount(300.0)).thenReturn("$300.00");

                command.onTake(sender, "Steve", "300");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("已扣除").contains("Steve").contains("$300.00");
            }
        }

        @Test
        @DisplayName("take with insufficient funds shows error")
        void takeInsufficientFunds() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(targetPlayer);
                when(economyService.takeCash(TARGET_UUID, 999999.0)).thenReturn(false);

                command.onTake(sender, "Steve", "999999");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("余额不足");
            }
        }
    }

    @Nested
    @DisplayName("Set")
    class SetTests {

        @Test
        @DisplayName("set changes balance and confirms")
        void setSuccess() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(targetPlayer);
                when(economyService.setCash(TARGET_UUID, 2000.0)).thenReturn(true);
                when(economyService.formatAmount(2000.0)).thenReturn("$2,000.00");

                command.onSet(sender, "Steve", "2000");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("已设置").contains("Steve").contains("$2,000.00");
            }
        }

        @Test
        @DisplayName("set to zero is allowed")
        void setToZero() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(targetPlayer);
                when(economyService.setCash(TARGET_UUID, 0.0)).thenReturn(true);
                when(economyService.formatAmount(0.0)).thenReturn("$0.00");

                command.onSet(sender, "Steve", "0");

                verify(economyService).setCash(TARGET_UUID, 0.0);
            }
        }

        @Test
        @DisplayName("set negative amount shows error")
        void setNegative() {
            command.onSet(sender, "Steve", "-100");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("无效的金额");
        }
    }

    @Nested
    @DisplayName("HandleHelp")
    class HelpTests {

        @Test
        @DisplayName("handleHelp sends command usage")
        void handleHelp() {
            command.handleHelp(sender);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender, atLeast(4)).sendMessage(captor.capture());
            assertThat(captor.getAllValues().get(0)).contains("UltiEconomy Admin");
        }
    }

    @Nested
    @DisplayName("Failure paths")
    class FailurePaths {

        @Test
        @DisplayName("give failure sends error message")
        void giveFailure() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(targetPlayer);
                when(economyService.addCash(TARGET_UUID, 500.0)).thenReturn(false);

                command.onGive(sender, "Steve", "500");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("操作失败");
            }
        }

        @Test
        @DisplayName("set failure sends error message")
        void setFailure() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(targetPlayer);
                when(economyService.setCash(TARGET_UUID, 100.0)).thenReturn(false);

                command.onSet(sender, "Steve", "100");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("操作失败");
            }
        }

        @Test
        @DisplayName("give with negative amount shows error")
        void giveNegative() {
            command.onGive(sender, "Steve", "-50");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("无效的金额");
        }
    }

    @Nested
    @DisplayName("Check")
    class CheckTests {

        @Test
        @DisplayName("check shows player's full financial info")
        void checkSuccess() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(targetPlayer);
                when(economyService.getCash(TARGET_UUID)).thenReturn(1000.0);
                when(economyService.getBank(TARGET_UUID)).thenReturn(5000.0);
                when(economyService.getTotalWealth(TARGET_UUID)).thenReturn(6000.0);
                when(economyService.formatAmount(1000.0)).thenReturn("$1,000.00");
                when(economyService.formatAmount(5000.0)).thenReturn("$5,000.00");
                when(economyService.formatAmount(6000.0)).thenReturn("$6,000.00");

                command.onCheck(sender, "Steve");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender, times(4)).sendMessage(captor.capture());
                List<String> messages = captor.getAllValues();

                assertThat(messages.get(0)).contains("Steve");
                assertThat(messages.get(1)).contains("$1,000.00");
                assertThat(messages.get(2)).contains("$5,000.00");
                assertThat(messages.get(3)).contains("$6,000.00");
            }
        }
    }

    @Nested
    @DisplayName("Currency-aware operations")
    class CurrencyOps {

        @Test
        @DisplayName("give with currency adds to specific currency")
        void giveCurrency() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(targetPlayer);
                when(economyService.addCash(TARGET_UUID, 500.0, "gems")).thenReturn(true);
                when(economyService.formatAmount(500.0, "gems")).thenReturn("G500.00");

                command.onGiveCurrency(sender, "Steve", "500", "gems");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("已给予").contains("G500.00");
            }
        }

        @Test
        @DisplayName("take with currency removes from specific currency")
        void takeCurrency() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(targetPlayer);
                when(economyService.takeCash(TARGET_UUID, 200.0, "gems")).thenReturn(true);
                when(economyService.formatAmount(200.0, "gems")).thenReturn("G200.00");

                command.onTakeCurrency(sender, "Steve", "200", "gems");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("已扣除").contains("G200.00");
            }
        }

        @Test
        @DisplayName("set with currency sets specific currency")
        void setCurrency() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(targetPlayer);
                when(economyService.setCash(TARGET_UUID, 1000.0, "gems")).thenReturn(true);
                when(economyService.formatAmount(1000.0, "gems")).thenReturn("G1,000.00");

                command.onSetCurrency(sender, "Steve", "1000", "gems");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("已设置").contains("G1,000.00");
            }
        }

        @Test
        @DisplayName("check with currency shows specific currency info")
        void checkCurrency() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(targetPlayer);
                when(economyService.getCash(TARGET_UUID, "gems")).thenReturn(250.0);
                when(economyService.getBank(TARGET_UUID, "gems")).thenReturn(0.0);
                when(economyService.getTotalWealth(TARGET_UUID, "gems")).thenReturn(250.0);
                when(economyService.formatAmount(250.0, "gems")).thenReturn("G250.00");
                when(economyService.formatAmount(0.0, "gems")).thenReturn("G0.00");

                command.onCheckCurrency(sender, "Steve", "gems");

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(sender, times(4)).sendMessage(captor.capture());
                List<String> messages = captor.getAllValues();
                assertThat(messages.get(0)).contains("gems");
                assertThat(messages.get(1)).contains("G250.00");
            }
        }
    }

    @Nested
    @DisplayName("Treasury")
    class TreasuryTests {

        @Mock private TaxService taxService;
        @Mock private CurrencyManager currencyManager;

        private EcoAdminCommand treasuryCommand;

        @BeforeEach
        void setUp() {
            lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
            treasuryCommand = new EcoAdminCommand(plugin, economyService, taxService, currencyManager);
        }

        @Test
        @DisplayName("treasury shows balances for all currencies")
        void treasuryShowsBalances() {
            CurrencyDefinition coins = CurrencyDefinition.builder()
                    .id("coins").displayName("Coins").symbol("$").initialCash(1000.0)
                    .bankEnabled(true).minDeposit(100.0).primary(true).build();
            CurrencyDefinition gems = CurrencyDefinition.builder()
                    .id("gems").displayName("Gems").symbol("G").build();
            when(currencyManager.getAllCurrencies()).thenReturn(Arrays.asList(coins, gems));
            when(taxService.getTreasuryBalance("coins")).thenReturn(5000.0);
            when(taxService.getTreasuryBalance("gems")).thenReturn(100.0);
            when(economyService.formatAmount(5000.0, "coins")).thenReturn("$5,000.00");
            when(economyService.formatAmount(100.0, "gems")).thenReturn("G100.00");

            treasuryCommand.onTreasury(sender);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender, times(2)).sendMessage(captor.capture());
            assertThat(captor.getAllValues().get(0)).contains("Coins").contains("$5,000.00");
            assertThat(captor.getAllValues().get(1)).contains("Gems").contains("G100.00");
        }

        @Test
        @DisplayName("treasury withdraw from primary currency")
        void treasuryWithdrawPrimary() throws Exception {
            CurrencyDefinition coins = CurrencyDefinition.builder()
                    .id("coins").displayName("Coins").symbol("$").primary(true).build();
            when(currencyManager.getPrimaryCurrency()).thenReturn(coins);
            when(taxService.withdrawFromTreasury(500.0, "coins")).thenReturn(true);
            when(economyService.formatAmount(500.0, "coins")).thenReturn("$500.00");

            treasuryCommand.onTreasuryWithdraw(sender, "500");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("已从国库提取").contains("$500.00");
        }

        @Test
        @DisplayName("treasury withdraw fails on insufficient balance")
        void treasuryWithdrawInsufficient() throws Exception {
            CurrencyDefinition coins = CurrencyDefinition.builder()
                    .id("coins").displayName("Coins").symbol("$").primary(true).build();
            when(currencyManager.getPrimaryCurrency()).thenReturn(coins);
            when(taxService.withdrawFromTreasury(999999.0, "coins")).thenReturn(false);

            treasuryCommand.onTreasuryWithdraw(sender, "999999");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("国库余额不足");
        }

        @Test
        @DisplayName("treasury withdraw with specific currency")
        void treasuryWithdrawCurrency() throws Exception {
            when(taxService.withdrawFromTreasury(200.0, "gems")).thenReturn(true);
            when(economyService.formatAmount(200.0, "gems")).thenReturn("G200.00");

            treasuryCommand.onTreasuryWithdrawCurrency(sender, "200", "gems");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("已从国库提取").contains("G200.00");
        }

        @Test
        @DisplayName("treasury commands show error when tax disabled")
        void treasuryDisabled() {
            EcoAdminCommand noTaxCommand = new EcoAdminCommand(plugin, economyService);

            noTaxCommand.onTreasury(sender);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("税收系统未启用");
        }
    }
}
