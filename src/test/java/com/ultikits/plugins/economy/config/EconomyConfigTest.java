package com.ultikits.plugins.economy.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EconomyConfig Tests")
class EconomyConfigTest {

    @Test
    @DisplayName("should have sensible defaults")
    void shouldHaveDefaults() {
        EconomyConfig config = new EconomyConfig();
        assertThat(config.getInitialCash()).isEqualTo(1000.0);
        assertThat(config.getCurrencyName()).isEqualTo("Coins");
        assertThat(config.getCurrencySymbol()).isEqualTo("$");
        assertThat(config.isBankEnabled()).isTrue();
        assertThat(config.getMinDeposit()).isEqualTo(100.0);
        assertThat(config.getMaxBankBalance()).isEqualTo(-1.0);
        assertThat(config.isInterestEnabled()).isFalse();
        assertThat(config.getInterestRate()).isEqualTo(0.03);
        assertThat(config.getMaxInterest()).isEqualTo(10000.0);
        assertThat(config.getLeaderboardDisplayCount()).isEqualTo(10);
        assertThat(config.isTaxEnabled()).isTrue();
        assertThat(config.isTransactionTaxEnabled()).isTrue();
        assertThat(config.getTransactionTaxRate()).isEqualTo(0.05);
        assertThat(config.getTransactionTaxExemptPermission()).isEqualTo("ultieconomy.tax.exempt");
        assertThat(config.isWealthTaxEnabled()).isFalse();
        assertThat(config.getWealthTaxInterval()).isEqualTo(3600);
        assertThat(config.getWealthTaxExemptPermission()).isEqualTo("ultieconomy.wealthtax.exempt");
    }

    /**
     * The declared default of the master tax switch follows what a server did before 6.3.0, when
     * nothing read the key and a transfer was taxed whenever {@code tax.transaction-tax.enabled}
     * was on (UltiKits/UltiEconomy#16, maintainer decision 2026-09-23). It only reaches a file
     * that does not hold the key yet -- see {@code ConfigFileWriteBackTest}.
     */
    @Test
    @DisplayName("tax.enabled is declared true, matching the transfer tax a server already took (UltiEconomy#16)")
    void taxMasterSwitchIsDeclaredOn() {
        assertThat(new EconomyConfig().isTaxEnabled()).isTrue();
    }

    @Test
    @DisplayName("should allow setting values")
    void shouldAllowSettingValues() {
        EconomyConfig config = new EconomyConfig();
        config.setInitialCash(500.0);
        config.setCurrencyName("Gold");
        config.setCurrencySymbol("G");
        config.setBankEnabled(false);
        config.setMinDeposit(50.0);
        config.setMaxBankBalance(100000.0);
        config.setInterestEnabled(true); // the declared default is false, so set the other value
        config.setInterestRate(0.05);
        config.setMaxInterest(5000.0);
        config.setLeaderboardDisplayCount(20);

        assertThat(config.getInitialCash()).isEqualTo(500.0);
        assertThat(config.getCurrencyName()).isEqualTo("Gold");
        assertThat(config.getCurrencySymbol()).isEqualTo("G");
        assertThat(config.isBankEnabled()).isFalse();
        assertThat(config.getMinDeposit()).isEqualTo(50.0);
        assertThat(config.getMaxBankBalance()).isEqualTo(100000.0);
        assertThat(config.isInterestEnabled()).isTrue();
        assertThat(config.getInterestRate()).isEqualTo(0.05);
        assertThat(config.getMaxInterest()).isEqualTo(5000.0);
        assertThat(config.getLeaderboardDisplayCount()).isEqualTo(20);
    }
}
