package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.TreasuryEntity;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("TaxService")
@ExtendWith(MockitoExtension.class)
class TaxServiceTest {

    @Mock private EconomyConfig config;
    @Mock private DataOperator<TreasuryEntity> treasuryDataOperator;

    private TaxService taxService;

    private static final List<TaxService.TaxBracket> BRACKETS = Arrays.asList(
            new TaxService.TaxBracket(0, 10000, 0.0),
            new TaxService.TaxBracket(10000, 100000, 0.01),
            new TaxService.TaxBracket(100000, -1, 0.02)
    );

    @BeforeEach
    void setUp() {
        taxService = new TaxService(config, treasuryDataOperator);
    }

    @Nested
    @DisplayName("Transaction Tax")
    class TransactionTaxTests {

        @Test
        @DisplayName("calculates 5% transaction tax")
        void calculates5Percent() {
            when(config.isTransactionTaxEnabled()).thenReturn(true);
            when(config.getTransactionTaxRate()).thenReturn(0.05);

            double tax = taxService.calculateTransactionTax(100.0);
            assertThat(tax).isEqualTo(5.0);
        }

        @Test
        @DisplayName("calculates 10% transaction tax")
        void calculates10Percent() {
            when(config.isTransactionTaxEnabled()).thenReturn(true);
            when(config.getTransactionTaxRate()).thenReturn(0.10);

            double tax = taxService.calculateTransactionTax(250.0);
            assertThat(tax).isEqualTo(25.0);
        }

        @Test
        @DisplayName("returns 0 when transaction tax disabled")
        void disabledReturnsZero() {
            when(config.isTransactionTaxEnabled()).thenReturn(false);

            double tax = taxService.calculateTransactionTax(100.0);
            assertThat(tax).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns 0 for zero amount")
        void zeroAmount() {
            when(config.isTransactionTaxEnabled()).thenReturn(true);
            when(config.getTransactionTaxRate()).thenReturn(0.05);

            double tax = taxService.calculateTransactionTax(0.0);
            assertThat(tax).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Wealth Tax")
    class WealthTaxTests {

        @Test
        @DisplayName("progressive brackets: 0% below 10k, 1% on 10k-100k, 2% above")
        void progressiveBrackets() {
            // Player has 150,000 total wealth
            // Bracket 1: 0-10,000 @ 0% = 0
            // Bracket 2: 10,000-100,000 @ 1% = 900
            // Bracket 3: 100,000-150,000 @ 2% = 1000
            // Total tax = 1900
            double tax = taxService.calculateWealthTax(150000.0, BRACKETS);
            assertThat(tax).isCloseTo(1900.0, within(0.01));
        }

        @Test
        @DisplayName("no tax below first bracket threshold")
        void belowFirstBracket() {
            double tax = taxService.calculateWealthTax(5000.0, BRACKETS);
            assertThat(tax).isEqualTo(0.0);
        }

        @Test
        @DisplayName("taxes only the amount within each bracket")
        void partialSecondBracket() {
            // Player has 50,000
            // Bracket 1: 0-10,000 @ 0% = 0
            // Bracket 2: 10,000-50,000 @ 1% = 400
            double tax = taxService.calculateWealthTax(50000.0, BRACKETS);
            assertThat(tax).isCloseTo(400.0, within(0.01));
        }

        @Test
        @DisplayName("returns 0 for zero wealth")
        void zeroWealth() {
            double tax = taxService.calculateWealthTax(0.0, BRACKETS);
            assertThat(tax).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns 0 for empty brackets")
        void emptyBrackets() {
            double tax = taxService.calculateWealthTax(100000.0, Collections.emptyList());
            assertThat(tax).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Treasury")
    class TreasuryTests {

        @Test
        @DisplayName("depositToTreasury creates new entry when none exists")
        void createsNewEntry() throws IllegalAccessException {
            when(treasuryDataOperator.query()).thenReturn(new MockQuery<>(Collections.emptyList()));

            taxService.depositToTreasury(500.0, "coins");

            ArgumentCaptor<TreasuryEntity> captor = ArgumentCaptor.forClass(TreasuryEntity.class);
            verify(treasuryDataOperator).insert(captor.capture());
            assertThat(captor.getValue().getCurrencyId()).isEqualTo("coins");
            assertThat(captor.getValue().getBalance()).isEqualTo(500.0);
        }

        @Test
        @DisplayName("depositToTreasury adds to existing balance")
        void addsToExisting() throws IllegalAccessException {
            TreasuryEntity existing = TreasuryEntity.builder()
                    .currencyId("coins")
                    .balance(1000.0)
                    .build();
            when(treasuryDataOperator.query()).thenReturn(new MockQuery<>(Collections.singletonList(existing)));

            taxService.depositToTreasury(500.0, "coins");

            verify(treasuryDataOperator).update(existing);
            assertThat(existing.getBalance()).isEqualTo(1500.0);
        }

        @Test
        @DisplayName("getTreasuryBalance returns balance for existing currency")
        void getsBalance() {
            TreasuryEntity entry = TreasuryEntity.builder()
                    .currencyId("coins")
                    .balance(5000.0)
                    .build();
            when(treasuryDataOperator.query()).thenReturn(new MockQuery<>(Collections.singletonList(entry)));

            assertThat(taxService.getTreasuryBalance("coins")).isEqualTo(5000.0);
        }

        @Test
        @DisplayName("getTreasuryBalance returns 0 for non-existing currency")
        void returnsZeroForMissing() {
            when(treasuryDataOperator.query()).thenReturn(new MockQuery<>(Collections.emptyList()));

            assertThat(taxService.getTreasuryBalance("gems")).isEqualTo(0.0);
        }

        @Test
        @DisplayName("withdrawFromTreasury reduces balance")
        void withdrawReduces() throws IllegalAccessException {
            TreasuryEntity entry = TreasuryEntity.builder()
                    .currencyId("coins")
                    .balance(5000.0)
                    .build();
            when(treasuryDataOperator.query()).thenReturn(new MockQuery<>(Collections.singletonList(entry)));

            boolean result = taxService.withdrawFromTreasury(2000.0, "coins");

            assertThat(result).isTrue();
            assertThat(entry.getBalance()).isEqualTo(3000.0);
            verify(treasuryDataOperator).update(entry);
        }

        @Test
        @DisplayName("withdrawFromTreasury fails when insufficient balance")
        void withdrawFailsInsufficient() throws IllegalAccessException {
            TreasuryEntity entry = TreasuryEntity.builder()
                    .currencyId("coins")
                    .balance(100.0)
                    .build();
            when(treasuryDataOperator.query()).thenReturn(new MockQuery<>(Collections.singletonList(entry)));

            boolean result = taxService.withdrawFromTreasury(500.0, "coins");

            assertThat(result).isFalse();
            assertThat(entry.getBalance()).isEqualTo(100.0);
        }
    }
}
