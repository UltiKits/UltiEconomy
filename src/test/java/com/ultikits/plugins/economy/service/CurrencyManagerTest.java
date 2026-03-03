package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.model.CurrencyDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.Collection;

@DisplayName("CurrencyManager Tests")
class CurrencyManagerTest {

    private static final String YAML_CONTENT =
            "currencies:\n" +
            "  coins:\n" +
            "    display-name: 'Coins'\n" +
            "    symbol: '$'\n" +
            "    initial-cash: 1000.0\n" +
            "    bank-enabled: true\n" +
            "    min-deposit: 100.0\n" +
            "    max-bank-balance: -1\n" +
            "    primary: true\n" +
            "  gems:\n" +
            "    display-name: 'Gems'\n" +
            "    symbol: 'G'\n" +
            "    initial-cash: 0.0\n" +
            "    bank-enabled: false\n" +
            "    primary: false\n";

    private CurrencyManager manager;

    @BeforeEach
    void setUp() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader(YAML_CONTENT));
        manager = new CurrencyManager(yaml);
    }

    @Test
    @DisplayName("loads all currencies from YAML")
    void loadsAll() {
        Collection<CurrencyDefinition> all = manager.getAllCurrencies();
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("getCurrency returns correct definition")
    void getCurrency() {
        CurrencyDefinition coins = manager.getCurrency("coins");
        assertThat(coins).isNotNull();
        assertThat(coins.getDisplayName()).isEqualTo("Coins");
        assertThat(coins.getSymbol()).isEqualTo("$");
        assertThat(coins.getInitialCash()).isEqualTo(1000.0);
        assertThat(coins.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("getPrimaryCurrency returns the primary one")
    void getPrimary() {
        CurrencyDefinition primary = manager.getPrimaryCurrency();
        assertThat(primary.getId()).isEqualTo("coins");
    }

    @Test
    @DisplayName("getCurrency returns null for unknown")
    void unknownReturnsNull() {
        assertThat(manager.getCurrency("unknown")).isNull();
    }

    @Test
    @DisplayName("hasCurrency checks existence")
    void hasCurrency() {
        assertThat(manager.hasCurrency("coins")).isTrue();
        assertThat(manager.hasCurrency("nope")).isFalse();
    }

    @Test
    @DisplayName("throws if no primary currency defined")
    void noPrimary() {
        String yaml = "currencies:\n  gems:\n    display-name: 'Gems'\n    symbol: 'G'\n    primary: false\n";
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        assertThatThrownBy(() -> new CurrencyManager(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primary");
    }
}
