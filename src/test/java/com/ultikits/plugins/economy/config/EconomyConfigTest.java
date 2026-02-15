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
        assertThat(config.isInterestEnabled()).isTrue();
        assertThat(config.getInterestRate()).isEqualTo(0.03);
        assertThat(config.getInterestInterval()).isEqualTo(1800);
        assertThat(config.getMaxInterest()).isEqualTo(10000.0);
        assertThat(config.getLeaderboardUpdateInterval()).isEqualTo(60);
        assertThat(config.getLeaderboardDisplayCount()).isEqualTo(10);
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
        config.setInterestEnabled(false);
        config.setInterestRate(0.05);
        config.setInterestInterval(3600);
        config.setMaxInterest(5000.0);
        config.setLeaderboardUpdateInterval(120);
        config.setLeaderboardDisplayCount(20);

        assertThat(config.getInitialCash()).isEqualTo(500.0);
        assertThat(config.getCurrencyName()).isEqualTo("Gold");
        assertThat(config.getCurrencySymbol()).isEqualTo("G");
        assertThat(config.isBankEnabled()).isFalse();
        assertThat(config.getMinDeposit()).isEqualTo(50.0);
        assertThat(config.getMaxBankBalance()).isEqualTo(100000.0);
        assertThat(config.isInterestEnabled()).isFalse();
        assertThat(config.getInterestRate()).isEqualTo(0.05);
        assertThat(config.getInterestInterval()).isEqualTo(3600);
        assertThat(config.getMaxInterest()).isEqualTo(5000.0);
        assertThat(config.getLeaderboardUpdateInterval()).isEqualTo(120);
        assertThat(config.getLeaderboardDisplayCount()).isEqualTo(20);
    }
}
