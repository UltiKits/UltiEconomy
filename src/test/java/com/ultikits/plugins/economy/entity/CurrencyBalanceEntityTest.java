package com.ultikits.plugins.economy.entity;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CurrencyBalanceEntity Tests")
class CurrencyBalanceEntityTest {

    @Test
    @DisplayName("builder creates entity with all fields")
    void builderCreates() {
        CurrencyBalanceEntity entity = CurrencyBalanceEntity.builder()
                .uuid("550e8400-e29b-41d4-a716-446655440000")
                .currencyId("coins")
                .cash(1000.0)
                .bank(500.0)
                .build();

        assertThat(entity.getUuid()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(entity.getCurrencyId()).isEqualTo("coins");
        assertThat(entity.getCash()).isEqualTo(1000.0);
        assertThat(entity.getBank()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("getTotalWealth returns cash + bank")
    void totalWealth() {
        CurrencyBalanceEntity entity = CurrencyBalanceEntity.builder()
                .uuid("test-uuid")
                .currencyId("coins")
                .cash(300.0)
                .bank(700.0)
                .build();

        assertThat(entity.getTotalWealth()).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("defaults to zero balances")
    void defaults() {
        CurrencyBalanceEntity entity = CurrencyBalanceEntity.builder()
                .uuid("test-uuid")
                .currencyId("gems")
                .build();

        assertThat(entity.getCash()).isEqualTo(0.0);
        assertThat(entity.getBank()).isEqualTo(0.0);
    }
}
