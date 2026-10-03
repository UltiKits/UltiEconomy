package com.ultikits.plugins.economy.service;

import com.ultikits.ultitools.entities.WhereCondition;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("TaxService")
@ExtendWith(MockitoExtension.class)
class TaxServiceTest {

    @Mock private EconomyConfig config;
    @Mock private DataOperator<TreasuryEntity> treasuryDataOperator;

    private TaxService taxService;

    @BeforeEach
    void setUp() {
        taxService = new TaxService(config, treasuryDataOperator);
        // Treasury writes are conditional, DataOperator#updateIf (UltiKits/UltiEconomy#41, #42); true = written.
        lenient().when(treasuryDataOperator.updateIf(any(TreasuryEntity.class), any(WhereCondition[].class))).thenReturn(true);
    }

    @Nested
    @DisplayName("Transaction Tax")
    class TransactionTaxTests {

        /**
         * These cases are about the transaction-tax sub-switch and its rate, so the master switch
         * above it (UltiKits/UltiEconomy#16) is held on. Lenient because the disabled-sub-switch
         * case below never needs to read it.
         */
        @BeforeEach
        void masterSwitchOn() {
            lenient().when(config.isTaxEnabled()).thenReturn(true);
        }

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

    /**
     * {@code tax.enabled} is the master switch over all taxation (UltiKits/UltiEconomy#16). Before
     * 6.3.0 nothing read it, so a transfer was taxed whenever {@code tax.transaction-tax.enabled}
     * was true regardless of this key.
     */
    @Nested
    @DisplayName("Master switch tax.enabled (UltiEconomy#16)")
    class MasterSwitchTests {

        @Test
        @DisplayName("tax.enabled: false takes no transaction tax even when the transaction tax is enabled")
        void masterOffTakesNoTransactionTax() {
            lenient().when(config.isTaxEnabled()).thenReturn(false);
            lenient().when(config.isTransactionTaxEnabled()).thenReturn(true);
            lenient().when(config.getTransactionTaxRate()).thenReturn(0.05);

            assertThat(taxService.calculateTransactionTax(100.0)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("tax.enabled: true takes the transaction tax at its configured rate")
        void masterOnTakesTransactionTax() {
            lenient().when(config.isTaxEnabled()).thenReturn(true);
            lenient().when(config.isTransactionTaxEnabled()).thenReturn(true);
            lenient().when(config.getTransactionTaxRate()).thenReturn(0.05);

            assertThat(taxService.calculateTransactionTax(100.0)).isEqualTo(5.0);
        }

        @Test
        @DisplayName("tax.enabled is read at every transfer, so a reload that flips it applies to the next one")
        void masterSwitchIsReadAtEveryCall() {
            lenient().when(config.isTaxEnabled()).thenReturn(true, false, true);
            lenient().when(config.isTransactionTaxEnabled()).thenReturn(true);
            lenient().when(config.getTransactionTaxRate()).thenReturn(0.05);

            assertThat(taxService.calculateTransactionTax(100.0)).isEqualTo(5.0);
            assertThat(taxService.calculateTransactionTax(100.0)).isEqualTo(0.0);
            assertThat(taxService.calculateTransactionTax(100.0)).isEqualTo(5.0);
        }
    }

    /**
     * UltiKits/UltiEconomy#27, maintainer decision 2026-09-29: the wealth tax was a calculation nothing
     * called -- no schedule, no debit, no setting for its brackets. It is deleted with its settings, and
     * implementing a wealth tax is the feature request UltiKits/UltiEconomy#38.
     */
    @Test
    @DisplayName("TaxService has no wealth-tax calculation and no bracket type (UltiEconomy#27)")
    void noWealthTaxCalculation() {
        assertThat(Arrays.stream(TaxService.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
                .contains("calculateTransactionTax")
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("wealth"));
        assertThat(TaxService.class.getDeclaredClasses()).noneMatch(c -> c.getSimpleName().contains("Bracket"));
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

        /**
         * UltiKits/UltiEconomy#42: a treasury row another writer removed between the read and the write
         * matches no stored row (the conditional write does not apply; the re-read finds none). A deposit then takes the method's own "no
         * treasury row yet" branch, so the tax is stored instead of lost; a withdrawal fails as for no row.
         */
        @Test
        @DisplayName("depositToTreasury stores a new row with the amount when the row it read is gone (UltiEconomy#42)")
        void depositRecreatesAVanishedRow() throws IllegalAccessException {
            TreasuryEntity existing = TreasuryEntity.builder().currencyId("coins").balance(1000.0).build();
            // The conditional write does not apply, and the re-read finds no row.
            when(treasuryDataOperator.query()).thenReturn(new MockQuery<>(Collections.singletonList(existing)),
                    new MockQuery<>(Collections.<TreasuryEntity>emptyList()));
            when(treasuryDataOperator.updateIf(eq(existing), any(WhereCondition[].class))).thenReturn(false);

            taxService.depositToTreasury(500.0, "coins");

            ArgumentCaptor<TreasuryEntity> inserted = ArgumentCaptor.forClass(TreasuryEntity.class);
            verify(treasuryDataOperator).insert(inserted.capture());
            assertThat(inserted.getValue().getCurrencyId()).isEqualTo("coins");
            assertThat(inserted.getValue().getBalance()).isEqualTo(500.0);
        }

        @Test
        @DisplayName("withdrawFromTreasury returns false when the row it read is gone (UltiEconomy#42)")
        void withdrawFailsOnAVanishedRow() throws IllegalAccessException {
            TreasuryEntity entry = TreasuryEntity.builder().currencyId("coins").balance(5000.0).build();
            when(treasuryDataOperator.query()).thenReturn(new MockQuery<>(Collections.singletonList(entry)),
                    new MockQuery<>(Collections.<TreasuryEntity>emptyList()));
            when(treasuryDataOperator.updateIf(eq(entry), any(WhereCondition[].class))).thenReturn(false);

            assertThat(taxService.withdrawFromTreasury(2000.0, "coins")).isFalse();
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

            verify(treasuryDataOperator).updateIf(eq(existing), any(WhereCondition[].class));
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
            verify(treasuryDataOperator).updateIf(eq(entry), any(WhereCondition[].class));
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

        @Test
        @DisplayName("withdrawFromTreasury fails when no treasury entry exists for the currency")
        void withdrawFailsWhenNoEntry() throws IllegalAccessException {
            when(treasuryDataOperator.query()).thenReturn(new MockQuery<>(Collections.emptyList()));

            // amount=0.0 deliberately: a "treat missing entry as zero balance" implementation
            // (constructing a synthetic zero-balance entry instead of returning early) would let
            // 0.0 < 0.0 fail its own balance check and fall through to update() -- both this
            // path and the correct early-return end up with the same (false, no update) surface,
            // so amount=0.0 alone doesn't discriminate them; the real proof is `never().updateIf()`.
            boolean result = taxService.withdrawFromTreasury(0.0, "gems");

            assertThat(result).isFalse();
            verify(treasuryDataOperator, never()).updateIf(any(), any(WhereCondition[].class));
            // Also prove the missing-entry branch doesn't take the "auto-create a treasury row"
            // shortcut a copy-paste from depositToTreasury's insert-when-absent logic could
            // introduce -- update()-never() alone doesn't rule out an errant insert() call.
            verify(treasuryDataOperator, never()).insert(any());
        }
    }
}
