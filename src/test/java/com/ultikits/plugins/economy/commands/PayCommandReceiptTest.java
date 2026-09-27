package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.entity.TreasuryEntity;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.service.EconomyTestWorld;
import com.ultikits.plugins.economy.service.TaxService;
import com.ultikits.plugins.economy.testsupport.InMemoryDataOperator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /pay} against the real economy service over in-memory storage, so each test asserts the
 * wallets and the chat lines together: what the lines say is what the wallets hold.
 */
@DisplayName("/pay against the real economy service")
class PayCommandReceiptTest {

    private static final UUID ALICE = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID BOB = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

    private EconomyTestWorld world;
    private InMemoryDataOperator<TreasuryEntity> treasury;
    private PayCommand command;
    private Player alice;
    private Player bob;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        world = EconomyTestWorld.relational();
        treasury = InMemoryDataOperator.relational("economy_treasury", TreasuryEntity.class, world.crash);
        world.service.setTaxService(new TaxService(world.config, treasury));
        world.seedAccount(ALICE, "Alice", 1000.0, 0.0);
        world.seedAccount(BOB, "Bob", 0.0, 0.0);
        world.seedBalance(ALICE, "gems", 1000.0, 0.0);
        world.seedBalance(BOB, "gems", 0.0, 0.0);

        UltiEconomy plugin = mock(UltiEconomy.class);
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        lenient().when(plugin.getCurrencyManager()).thenReturn(world.currencies);
        command = new PayCommand(plugin, world.service);

        alice = mock(Player.class);
        lenient().when(alice.getUniqueId()).thenReturn(ALICE);
        lenient().when(alice.getName()).thenReturn("Alice");
        bob = mock(Player.class);
        lenient().when(bob.getUniqueId()).thenReturn(BOB);
        lenient().when(bob.getName()).thenReturn("Bob");
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getPlayer("Bob")).thenReturn(bob);
        bukkit.when(() -> Bukkit.getPlayer(ALICE)).thenReturn(alice);
        bukkit.when(() -> Bukkit.getPlayer(BOB)).thenReturn(bob);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private String lineTo(Player player) {
        ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(line.capture());
        return line.getValue();
    }

    private double treasuryBalance(String currencyId) {
        for (TreasuryEntity row : treasury.getAll()) {
            if (currencyId.equals(row.getCurrencyId())) {
                return row.getBalance();
            }
        }
        return 0.0;
    }

    @Nested
    @DisplayName("Untaxed transfers")
    class Untaxed {

        @BeforeEach
        void noTax() {
            world.config.setTaxEnabled(false);
        }

        @Test
        @DisplayName("A transfer moves the amount and both players are told it")
        void transferMovesTheAmount() {
            command.onPay(alice, "Bob", "500");

            assertThat(world.account(ALICE).getCash()).isEqualTo(500.0);
            assertThat(world.account(BOB).getCash()).isEqualTo(500.0);
            assertThat(lineTo(alice)).contains("500.00").contains("Bob");
            assertThat(lineTo(bob)).contains("500.00").contains("Alice");
        }

        @Test
        @DisplayName("A transfer the sender cannot cover moves nothing and tells the sender")
        void insufficientFundsMovesNothing() {
            command.onPay(alice, "Bob", "999999");

            assertThat(world.account(ALICE).getCash()).isEqualTo(1000.0);
            assertThat(world.account(BOB).getCash()).isZero();
            assertThat(lineTo(alice)).contains(CatalogueText.text("en", "economy.error.insufficient_balance"));
            verify(bob, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("A transfer in a named currency moves that currency")
        void transferInANamedCurrency() {
            command.onPayWithCurrency(alice, "Bob", "300", "gems");

            assertThat(world.balanceRows("gems")).anySatisfy(row -> {
                assertThat(row.getUuid()).isEqualTo(BOB.toString());
                assertThat(row.getCash()).isEqualTo(300.0);
            });
            assertThat(lineTo(alice)).contains("300.00").contains("Bob");
            assertThat(lineTo(bob)).contains("300.00").contains("Alice");
        }

        @Test
        @DisplayName("A named-currency transfer the sender cannot cover moves nothing")
        void namedCurrencyInsufficientFunds() {
            command.onPayWithCurrency(alice, "Bob", "999999", "gems");

            assertThat(lineTo(alice)).contains(CatalogueText.text("en", "economy.error.insufficient_balance"));
            verify(bob, never()).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("Taxed transfers show the net amount credited (#18)")
    class NetAmount {

        @BeforeEach
        void fivePercentTax() {
            world.config.setTaxEnabled(true);
            world.config.setTransactionTaxEnabled(true);
            world.config.setTransactionTaxRate(0.05);
        }

        @Test
        @DisplayName("/pay tells both players the amount the receiver was credited, after tax")
        void bothLinesShowTheNetAmount() {
            command.onPay(alice, "Bob", "100");

            assertThat(world.account(BOB).getCash()).isEqualTo(95.0);
            assertThat(lineTo(alice)).contains("95.00").doesNotContain("100.00");
            assertThat(lineTo(bob)).contains("95.00").doesNotContain("100.00");
        }

        @Test
        @DisplayName("/pay in a named currency tells both players the net amount too")
        void namedCurrencyLinesShowTheNetAmount() {
            command.onPayWithCurrency(alice, "Bob", "100", "gems");

            assertThat(lineTo(alice)).contains("95.00").doesNotContain("100.00");
            assertThat(lineTo(bob)).contains("95.00").doesNotContain("100.00");
        }
    }

    @Nested
    @DisplayName("tax.transaction-tax.exempt-permission (#26)")
    class ExemptPermission {

        @BeforeEach
        void fivePercentTax() {
            world.config.setTaxEnabled(true);
            world.config.setTransactionTaxEnabled(true);
            world.config.setTransactionTaxRate(0.05);
        }

        @Test
        @DisplayName("A sender holding the exempt permission pays no transaction tax")
        void anExemptSenderPaysNoTax() {
            when(alice.hasPermission("ultieconomy.tax.exempt")).thenReturn(true);

            command.onPay(alice, "Bob", "100");

            assertThat(world.account(BOB).getCash()).isEqualTo(100.0);
            assertThat(treasuryBalance("coins")).isZero();
        }

        @Test
        @DisplayName("A sender without it is taxed, and the tax reaches the treasury")
        void aSenderWithoutItIsTaxed() {
            command.onPay(alice, "Bob", "100");

            assertThat(world.account(BOB).getCash()).isEqualTo(95.0);
            assertThat(treasuryBalance("coins")).isEqualTo(5.0);
        }

        @Test
        @DisplayName("The exemption follows the configured permission name")
        void theExemptionFollowsTheConfiguredName() {
            world.config.setTransactionTaxExemptPermission("server.vip");
            when(alice.hasPermission("server.vip")).thenReturn(true);

            command.onPayWithCurrency(alice, "Bob", "100", "gems");

            assertThat(world.balanceRows("gems")).anySatisfy(row -> {
                assertThat(row.getUuid()).isEqualTo(BOB.toString());
                assertThat(row.getCash()).isEqualTo(100.0);
            });
        }
    }
}
