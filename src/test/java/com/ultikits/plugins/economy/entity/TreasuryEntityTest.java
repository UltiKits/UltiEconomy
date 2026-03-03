package com.ultikits.plugins.economy.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TreasuryEntity")
class TreasuryEntityTest {

    @Test
    @DisplayName("builder creates entity with all fields")
    void builderCreates() {
        TreasuryEntity entity = TreasuryEntity.builder()
                .currencyId("coins")
                .balance(50000.0)
                .build();

        assertThat(entity.getCurrencyId()).isEqualTo("coins");
        assertThat(entity.getBalance()).isEqualTo(50000.0);
    }

    @Test
    @DisplayName("default balance is zero")
    void defaultBalance() {
        TreasuryEntity entity = TreasuryEntity.builder()
                .currencyId("gems")
                .build();

        assertThat(entity.getBalance()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("setter updates balance")
    void setterUpdatesBalance() {
        TreasuryEntity entity = TreasuryEntity.builder()
                .currencyId("coins")
                .balance(1000.0)
                .build();

        entity.setBalance(2500.0);
        assertThat(entity.getBalance()).isEqualTo(2500.0);
    }
}
