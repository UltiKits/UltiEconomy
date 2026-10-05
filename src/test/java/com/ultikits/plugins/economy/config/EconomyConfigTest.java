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
        assertThat(config.isTaxEnabled()).isTrue();
        assertThat(config.isTransactionTaxEnabled()).isTrue();
        assertThat(config.getTransactionTaxRate()).isEqualTo(0.05);
        assertThat(config.getTransactionTaxExemptPermission()).isEqualTo("ultieconomy.tax.exempt");
    }

    /**
     * UltiKits/UltiEconomy#27, maintainer decision 2026-09-29: the wealth tax never collected anything
     * -- nothing scheduled it, nothing debited a balance, no setting defined its brackets -- so its
     * three settings are deleted in 6.3.0 and implementing it is the feature request
     * UltiKits/UltiEconomy#38. Nothing may declare a {@code tax.wealth-tax.*} entry any more.
     */
    @Test
    @DisplayName("no tax.wealth-tax.* setting is declared (UltiEconomy#27)")
    void noWealthTaxSettingIsDeclared() {
        assertThat(declaredPaths()).isNotEmpty().contains("tax.enabled")
                .noneMatch(path -> path.startsWith("tax.wealth-tax"));
    }

    /**
     * UltiKits/UltiEconomy#36: {@code leaderboard.display-count} was written into every file and read
     * by nothing -- no command or placeholder of this module shows a fixed number of leaderboard entries
     * (each top-N placeholder names its own N). So the declaration is deleted rather than wired.
     */
    @Test
    @DisplayName("leaderboard.display-count is not declared; leaderboard.update-interval still is (UltiEconomy#36)")
    void noLeaderboardDisplayCountIsDeclared() {
        assertThat(declaredPaths()).contains("leaderboard.update-interval")
                .doesNotContain("leaderboard.display-count");
    }

    /** Every {@code @ConfigEntry} path {@link EconomyConfig} declares. */
    static java.util.List<String> declaredPaths() {
        java.util.List<String> paths = new java.util.ArrayList<>();
        for (java.lang.reflect.Field field : EconomyConfig.class.getDeclaredFields()) {
            com.ultikits.ultitools.annotations.ConfigEntry entry =
                    field.getAnnotation(com.ultikits.ultitools.annotations.ConfigEntry.class);
            if (entry != null) {
                paths.add(entry.path());
            }
        }
        return paths;
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

        assertThat(config.getInitialCash()).isEqualTo(500.0);
        assertThat(config.getCurrencyName()).isEqualTo("Gold");
        assertThat(config.getCurrencySymbol()).isEqualTo("G");
        assertThat(config.isBankEnabled()).isFalse();
        assertThat(config.getMinDeposit()).isEqualTo(50.0);
        assertThat(config.getMaxBankBalance()).isEqualTo(100000.0);
        assertThat(config.isInterestEnabled()).isTrue();
        assertThat(config.getInterestRate()).isEqualTo(0.05);
        assertThat(config.getMaxInterest()).isEqualTo(5000.0);
    }
}
