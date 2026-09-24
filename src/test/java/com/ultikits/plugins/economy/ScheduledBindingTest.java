package com.ultikits.plugins.economy;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.service.InterestService;
import com.ultikits.plugins.economy.service.LeaderboardService;
import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.Scheduled;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shape of this module's two config-bound {@code @Scheduled} declarations
 * (UltiKits/UltiEconomy#15 on UltiKits/UltiTools-Reborn#531), and the {@code plugin.yml} floor they
 * require.
 *
 * <p>An older framework does not know the binding elements and drops them silently: the bound
 * method would then run once at load instead of on its interval -- for the interest payment, one
 * immediate payment and never another. {@code api-version: 630} makes an older framework refuse the
 * module instead ({@code COMPATIBILITY.md}, "A floor no linker enforces").
 */
@DisplayName("Config-bound @Scheduled declarations and the api-version floor (UltiEconomy#15)")
class ScheduledBindingTest {

    @Test
    @DisplayName("the interest payment binds its period and its first delay to interest.interval, sync, with no literal")
    void interestPaymentIsBoundToItsInterval() throws Exception {
        Scheduled scheduled = InterestService.class.getDeclaredMethod("payInterestIfEnabled")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.config()).isEqualTo(EconomyConfig.class);
        assertThat(scheduled.periodKey()).isEqualTo("interest.interval");
        assertThat(scheduled.delayKey()).isEqualTo("interest.interval");
        assertThat(scheduled.period()).as("no literal period beside the binding").isEqualTo(-1L);
        assertThat(scheduled.delay()).as("no literal delay beside the binding").isEqualTo(0L);
        assertThat(scheduled.async()).isFalse();
    }

    @Test
    @DisplayName("the leaderboard refresh binds its period to leaderboard.update-interval and runs at load, sync, with no literal")
    void leaderboardRefreshIsBoundToItsInterval() throws Exception {
        Scheduled scheduled = LeaderboardService.class.getDeclaredMethod("refreshAll")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.config()).isEqualTo(EconomyConfig.class);
        assertThat(scheduled.periodKey()).isEqualTo("leaderboard.update-interval");
        assertThat(scheduled.delayKey()).as("first refresh at load").isEmpty();
        assertThat(scheduled.period()).as("no literal period beside the binding").isEqualTo(-1L);
        assertThat(scheduled.delay()).isEqualTo(0L);
        assertThat(scheduled.async()).isFalse();
        // Control: a binding is present at all, so the floor below is required, not optional.
        assertThat(scheduled.config()).isNotEqualTo(AbstractConfigEntity.class);
    }

    @Test
    @DisplayName("plugin.yml declares api-version 630, the floor a config binding requires")
    void pluginYmlDeclaresTheBindingFloor() throws Exception {
        YamlConfiguration pluginYml;
        try (InputStream in = ScheduledBindingTest.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            assertThat(in).as("plugin.yml on the classpath").isNotNull();
            pluginYml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        // Control: the file read is this module's own plugin.yml.
        assertThat(pluginYml.getString("main")).isEqualTo("com.ultikits.plugins.economy.UltiEconomy");
        assertThat(pluginYml.getInt("api-version")).isEqualTo(630);
    }
}
