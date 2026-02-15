package com.ultikits.plugins.economy.vault;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.service.EconomyService;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VaultEconomyProvider Tests")
class VaultEconomyProviderTest {

    @Mock private EconomyService economyService;
    @Mock private OfflinePlayer offlinePlayer;

    private EconomyConfig config;
    private VaultEconomyProvider provider;

    private static final UUID PLAYER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        config = new EconomyConfig();
        provider = new VaultEconomyProvider(economyService, config);
        lenient().when(offlinePlayer.getUniqueId()).thenReturn(PLAYER_UUID);
        lenient().when(offlinePlayer.getName()).thenReturn("Steve");
    }

    @Nested
    @DisplayName("Basic Info")
    class BasicInfoTests {

        @Test
        @DisplayName("isEnabled returns true")
        void isEnabled() {
            assertThat(provider.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("getName returns UltiEconomy")
        void getName() {
            assertThat(provider.getName()).isEqualTo("UltiEconomy");
        }

        @Test
        @DisplayName("hasBankSupport returns false (Vault shared banks not supported)")
        void hasBankSupport() {
            assertThat(provider.hasBankSupport()).isFalse();
        }

        @Test
        @DisplayName("fractionalDigits returns 2")
        void fractionalDigits() {
            assertThat(provider.fractionalDigits()).isEqualTo(2);
        }

        @Test
        @DisplayName("currencyNameSingular from config")
        void currencyNameSingular() {
            config.setCurrencyName("Gold");
            assertThat(provider.currencyNameSingular()).isEqualTo("Gold");
        }

        @Test
        @DisplayName("currencyNamePlural from config")
        void currencyNamePlural() {
            config.setCurrencyName("Gold");
            assertThat(provider.currencyNamePlural()).isEqualTo("Gold");
        }

        @Test
        @DisplayName("format uses service format")
        void format() {
            when(economyService.formatAmount(1234.5)).thenReturn("$1,234.50");
            assertThat(provider.format(1234.5)).isEqualTo("$1,234.50");
        }
    }

    @Nested
    @DisplayName("Account Operations")
    class AccountTests {

        @Test
        @DisplayName("hasAccount delegates to service")
        void hasAccount() {
            when(economyService.hasAccount(PLAYER_UUID)).thenReturn(true);
            assertThat(provider.hasAccount(offlinePlayer)).isTrue();
        }

        @Test
        @DisplayName("hasAccount with world delegates to non-world version")
        void hasAccountWithWorld() {
            when(economyService.hasAccount(PLAYER_UUID)).thenReturn(true);
            assertThat(provider.hasAccount(offlinePlayer, "world")).isTrue();
        }

        @Test
        @DisplayName("createPlayerAccount delegates to getOrCreateAccount")
        void createAccount() {
            when(economyService.hasAccount(PLAYER_UUID)).thenReturn(false);

            boolean result = provider.createPlayerAccount(offlinePlayer);
            assertThat(result).isTrue();
            verify(economyService).getOrCreateAccount(PLAYER_UUID, "Steve");
        }

        @Test
        @DisplayName("createPlayerAccount returns false if already exists")
        void createAccountAlreadyExists() {
            when(economyService.hasAccount(PLAYER_UUID)).thenReturn(true);

            boolean result = provider.createPlayerAccount(offlinePlayer);
            assertThat(result).isFalse();
            verify(economyService, never()).getOrCreateAccount(any(), any());
        }
    }

    @Nested
    @DisplayName("Balance Operations")
    class BalanceTests {

        @Test
        @DisplayName("getBalance delegates to getCash")
        void getBalance() {
            when(economyService.getCash(PLAYER_UUID)).thenReturn(1500.0);
            assertThat(provider.getBalance(offlinePlayer)).isEqualTo(1500.0);
        }

        @Test
        @DisplayName("getBalance with world delegates to non-world version")
        void getBalanceWithWorld() {
            when(economyService.getCash(PLAYER_UUID)).thenReturn(1500.0);
            assertThat(provider.getBalance(offlinePlayer, "world")).isEqualTo(1500.0);
        }

        @Test
        @DisplayName("has returns true when sufficient")
        void hasSufficient() {
            when(economyService.getCash(PLAYER_UUID)).thenReturn(1000.0);
            assertThat(provider.has(offlinePlayer, 500.0)).isTrue();
        }

        @Test
        @DisplayName("has returns false when insufficient")
        void hasInsufficient() {
            when(economyService.getCash(PLAYER_UUID)).thenReturn(1000.0);
            assertThat(provider.has(offlinePlayer, 1500.0)).isFalse();
        }

        @Test
        @DisplayName("has with world delegates to non-world version")
        void hasWithWorld() {
            when(economyService.getCash(PLAYER_UUID)).thenReturn(1000.0);
            assertThat(provider.has(offlinePlayer, "world", 500.0)).isTrue();
        }
    }

    @Nested
    @DisplayName("Withdraw Operations")
    class WithdrawTests {

        @Test
        @DisplayName("withdrawPlayer success")
        void withdrawSuccess() {
            when(economyService.takeCash(PLAYER_UUID, 300.0)).thenReturn(true);
            when(economyService.getCash(PLAYER_UUID)).thenReturn(700.0);

            EconomyResponse response = provider.withdrawPlayer(offlinePlayer, 300.0);
            assertThat(response.transactionSuccess()).isTrue();
            assertThat(response.amount).isEqualTo(300.0);
            assertThat(response.balance).isEqualTo(700.0);
        }

        @Test
        @DisplayName("withdrawPlayer failure — insufficient funds")
        void withdrawFailure() {
            when(economyService.takeCash(PLAYER_UUID, 5000.0)).thenReturn(false);
            when(economyService.getCash(PLAYER_UUID)).thenReturn(100.0);

            EconomyResponse response = provider.withdrawPlayer(offlinePlayer, 5000.0);
            assertThat(response.transactionSuccess()).isFalse();
        }

        @Test
        @DisplayName("withdrawPlayer negative amount returns failure")
        void withdrawNegative() {
            EconomyResponse response = provider.withdrawPlayer(offlinePlayer, -100);
            assertThat(response.transactionSuccess()).isFalse();
        }

        @Test
        @DisplayName("withdrawPlayer with world delegates to non-world version")
        void withdrawWithWorld() {
            when(economyService.takeCash(PLAYER_UUID, 200.0)).thenReturn(true);
            when(economyService.getCash(PLAYER_UUID)).thenReturn(800.0);

            EconomyResponse response = provider.withdrawPlayer(offlinePlayer, "world", 200.0);
            assertThat(response.transactionSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Deposit Operations")
    class DepositTests {

        @Test
        @DisplayName("depositPlayer success")
        void depositSuccess() {
            when(economyService.addCash(PLAYER_UUID, 500.0)).thenReturn(true);
            when(economyService.getCash(PLAYER_UUID)).thenReturn(1500.0);

            EconomyResponse response = provider.depositPlayer(offlinePlayer, 500.0);
            assertThat(response.transactionSuccess()).isTrue();
            assertThat(response.amount).isEqualTo(500.0);
            assertThat(response.balance).isEqualTo(1500.0);
        }

        @Test
        @DisplayName("depositPlayer failure")
        void depositFailure() {
            when(economyService.addCash(PLAYER_UUID, 500.0)).thenReturn(false);
            when(economyService.getCash(PLAYER_UUID)).thenReturn(1000.0);

            EconomyResponse response = provider.depositPlayer(offlinePlayer, 500.0);
            assertThat(response.transactionSuccess()).isFalse();
        }

        @Test
        @DisplayName("depositPlayer negative amount returns failure")
        void depositNegative() {
            EconomyResponse response = provider.depositPlayer(offlinePlayer, -50);
            assertThat(response.transactionSuccess()).isFalse();
        }

        @Test
        @DisplayName("depositPlayer with world delegates to non-world version")
        void depositWithWorld() {
            when(economyService.addCash(PLAYER_UUID, 300.0)).thenReturn(true);
            when(economyService.getCash(PLAYER_UUID)).thenReturn(1300.0);

            EconomyResponse response = provider.depositPlayer(offlinePlayer, "world", 300.0);
            assertThat(response.transactionSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Deprecated String-based Methods")
    class DeprecatedStringTests {

        @Test
        @DisplayName("getBalance by name delegates to OfflinePlayer method")
        void getBalanceByName() {
            // String-based methods are deprecated stubs — return 0
            assertThat(provider.getBalance("Steve")).isEqualTo(0);
        }

        @Test
        @DisplayName("has by name delegates")
        void hasByName() {
            assertThat(provider.has("Steve", 500.0)).isFalse();
        }
    }

    @Nested
    @DisplayName("Bank Account Operations (Vault shared banks — not supported)")
    class VaultBankTests {

        @Test
        @DisplayName("createBank returns not implemented")
        void createBank() {
            EconomyResponse response = provider.createBank("TestBank", offlinePlayer);
            assertThat(response.transactionSuccess()).isFalse();
            assertThat(response.type).isEqualTo(EconomyResponse.ResponseType.NOT_IMPLEMENTED);
        }

        @Test
        @DisplayName("deleteBank returns not implemented")
        void deleteBank() {
            EconomyResponse response = provider.deleteBank("TestBank");
            assertThat(response.transactionSuccess()).isFalse();
            assertThat(response.type).isEqualTo(EconomyResponse.ResponseType.NOT_IMPLEMENTED);
        }

        @Test
        @DisplayName("bankBalance returns not implemented")
        void bankBalance() {
            EconomyResponse response = provider.bankBalance("TestBank");
            assertThat(response.transactionSuccess()).isFalse();
            assertThat(response.type).isEqualTo(EconomyResponse.ResponseType.NOT_IMPLEMENTED);
        }

        @Test
        @DisplayName("isBankOwner returns not implemented")
        void isBankOwner() {
            EconomyResponse response = provider.isBankOwner("TestBank", offlinePlayer);
            assertThat(response.transactionSuccess()).isFalse();
            assertThat(response.type).isEqualTo(EconomyResponse.ResponseType.NOT_IMPLEMENTED);
        }

        @Test
        @DisplayName("isBankMember returns not implemented")
        void isBankMember() {
            EconomyResponse response = provider.isBankMember("TestBank", offlinePlayer);
            assertThat(response.transactionSuccess()).isFalse();
            assertThat(response.type).isEqualTo(EconomyResponse.ResponseType.NOT_IMPLEMENTED);
        }

        @Test
        @DisplayName("bankDeposit returns not implemented")
        void bankDeposit() {
            EconomyResponse response = provider.bankDeposit("TestBank", 100.0);
            assertThat(response.transactionSuccess()).isFalse();
            assertThat(response.type).isEqualTo(EconomyResponse.ResponseType.NOT_IMPLEMENTED);
        }

        @Test
        @DisplayName("bankWithdraw returns not implemented")
        void bankWithdraw() {
            EconomyResponse response = provider.bankWithdraw("TestBank", 100.0);
            assertThat(response.transactionSuccess()).isFalse();
            assertThat(response.type).isEqualTo(EconomyResponse.ResponseType.NOT_IMPLEMENTED);
        }

        @Test
        @DisplayName("getBanks returns empty list")
        void getBanks() {
            assertThat(provider.getBanks()).isEmpty();
        }
    }
}
