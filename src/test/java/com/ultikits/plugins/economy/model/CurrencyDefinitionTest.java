package com.ultikits.plugins.economy.model;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CurrencyDefinition Tests")
class CurrencyDefinitionTest {

    @Test
    @DisplayName("builder creates currency with all fields")
    void builderCreates() {
        CurrencyDefinition def = CurrencyDefinition.builder()
                .id("coins")
                .displayName("Coins")
                .symbol("$")
                .initialCash(1000.0)
                .bankEnabled(true)
                .minDeposit(100.0)
                .maxBankBalance(-1)
                .primary(true)
                .build();

        assertThat(def.getId()).isEqualTo("coins");
        assertThat(def.getDisplayName()).isEqualTo("Coins");
        assertThat(def.getSymbol()).isEqualTo("$");
        assertThat(def.getInitialCash()).isEqualTo(1000.0);
        assertThat(def.isBankEnabled()).isTrue();
        assertThat(def.getMinDeposit()).isEqualTo(100.0);
        assertThat(def.getMaxBankBalance()).isEqualTo(-1.0);
        assertThat(def.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("defaults for non-primary currency")
    void defaults() {
        CurrencyDefinition def = CurrencyDefinition.builder()
                .id("gems")
                .displayName("Gems")
                .symbol("G")
                .build();

        assertThat(def.getInitialCash()).isEqualTo(0.0);
        assertThat(def.isBankEnabled()).isFalse();
        assertThat(def.isPrimary()).isFalse();
    }
}
