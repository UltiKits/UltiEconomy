package com.ultikits.plugins.economy.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlayerAccountEntity Tests")
class PlayerAccountEntityTest {

    @Test
    @DisplayName("should create entity with builder")
    void shouldCreateWithBuilder() {
        PlayerAccountEntity entity = PlayerAccountEntity.builder()
                .uuid("550e8400-e29b-41d4-a716-446655440000")
                .playerName("TestPlayer")
                .cash(1000.0)
                .bank(500.0)
                .build();

        assertThat(entity.getUuid()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(entity.getPlayerName()).isEqualTo("TestPlayer");
        assertThat(entity.getCash()).isEqualTo(1000.0);
        assertThat(entity.getBank()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("should default cash and bank to zero")
    void shouldDefaultToZero() {
        PlayerAccountEntity entity = new PlayerAccountEntity();
        assertThat(entity.getCash()).isEqualTo(0.0);
        assertThat(entity.getBank()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("should calculate total wealth")
    void shouldCalculateTotalWealth() {
        PlayerAccountEntity entity = PlayerAccountEntity.builder()
                .cash(750.0)
                .bank(1250.0)
                .build();
        assertThat(entity.getTotalWealth()).isEqualTo(2000.0);
    }

    @Test
    @DisplayName("should allow setters")
    void shouldAllowSetters() {
        PlayerAccountEntity entity = new PlayerAccountEntity();
        entity.setUuid("test-uuid");
        entity.setPlayerName("Steve");
        entity.setCash(100.0);
        entity.setBank(200.0);

        assertThat(entity.getUuid()).isEqualTo("test-uuid");
        assertThat(entity.getPlayerName()).isEqualTo("Steve");
        assertThat(entity.getCash()).isEqualTo(100.0);
        assertThat(entity.getBank()).isEqualTo(200.0);
    }
}
