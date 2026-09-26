package com.ultikits.plugins.economy.listener;

import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.service.EconomyTestWorld;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringReader;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("PlayerJoinListener")
@ExtendWith(MockitoExtension.class)
class PlayerJoinListenerTest {

    @Mock private EconomyService economyService;
    @Mock private Player player;

    private CurrencyManager currencyManager;
    private PlayerJoinListener listener;

    private static final UUID PLAYER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    private static final String CURRENCIES_YAML =
            "currencies:\n" +
            "  coins:\n" +
            "    display-name: 'Coins'\n" +
            "    symbol: '$'\n" +
            "    primary: true\n" +
            "  gems:\n" +
            "    display-name: 'Gems'\n" +
            "    symbol: 'G'\n" +
            "    primary: false\n";

    @BeforeEach
    void setUp() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader(CURRENCIES_YAML));
        currencyManager = new CurrencyManager(yaml);
        listener = PlayerJoinListener.createForTest(economyService, currencyManager);
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        lenient().when(player.getName()).thenReturn("TestPlayer");
    }

    @Test
    @DisplayName("creates primary account on join")
    void createsAccountForNewPlayer() {
        PlayerAccountEntity newAccount = PlayerAccountEntity.builder()
                .uuid(PLAYER_UUID.toString())
                .playerName("TestPlayer")
                .cash(1000.0)
                .bank(0.0)
                .build();
        when(economyService.getOrCreateAccount(PLAYER_UUID, "TestPlayer")).thenReturn(newAccount);

        PlayerJoinEvent event = new PlayerJoinEvent(player, "TestPlayer joined");
        listener.onPlayerJoin(event);

        verify(economyService).getOrCreateAccount(PLAYER_UUID, "TestPlayer");
    }

    @Nested
    @DisplayName("Multi-currency balance creation")
    class CurrencyBalanceTests {

        @Test
        @DisplayName("creates a balance for every non-primary currency on join, and none for the primary one (UltiKits/UltiEconomy#25)")
        void createsBalanceForEachNonPrimaryCurrency() {
            PlayerJoinEvent event = new PlayerJoinEvent(player, "TestPlayer joined");
            listener.onPlayerJoin(event);

            verify(economyService).getOrCreateAccount(PLAYER_UUID, "TestPlayer");
            verify(economyService).getOrCreateBalance(PLAYER_UUID, "TestPlayer", "gems");
            verify(economyService, never()).getOrCreateBalance(PLAYER_UUID, "TestPlayer", "coins");
        }

        @Test
        @DisplayName("a first join leaves exactly one primary-currency wallet holding config.yml's initial-cash, once (UltiKits/UltiEconomy#25)")
        void firstJoinCreditsTheStartingAmountOnce() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            PlayerJoinListener real = PlayerJoinListener.createForTest(world.service, world.currencies);

            real.onPlayerJoin(new PlayerJoinEvent(player, "TestPlayer joined"));
            real.onPlayerJoin(new PlayerJoinEvent(player, "TestPlayer joined"));

            assertThat(world.accounts.getAll()).hasSize(1);
            assertThat(world.account(PLAYER_UUID).getCash()).isEqualTo(1000.0);
            assertThat(world.balanceRows("coins")).as("second primary-currency wallet").isEmpty();
            assertThat(world.balanceRows("gems")).hasSize(1);
            assertThat(world.balanceRows("gems").get(0).getCash()).isEqualTo(5.0);
            // However it is read, the primary currency holds the starting amount once.
            assertThat(world.service.getCash(PLAYER_UUID)).isEqualTo(1000.0);
            assertThat(world.service.getCash(PLAYER_UUID, "coins")).isEqualTo(1000.0);
            double everyPrimaryWallet = 0;
            for (PlayerAccountEntity a : world.accounts.getAll()) {
                everyPrimaryWallet += a.getCash() + a.getBank();
            }
            for (CurrencyBalanceEntity b : world.balanceRows("coins")) {
                everyPrimaryWallet += b.getCash() + b.getBank();
            }
            assertThat(everyPrimaryWallet).isEqualTo(1000.0);
        }

        @Test
        @DisplayName("without CurrencyManager only creates primary account")
        void withoutCurrencyManager() {
            PlayerJoinListener legacyListener = PlayerJoinListener.createForTest(economyService, null);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "TestPlayer joined");
            legacyListener.onPlayerJoin(event);

            verify(economyService).getOrCreateAccount(PLAYER_UUID, "TestPlayer");
            verify(economyService, never()).getOrCreateBalance(any(), any(), any());
        }
    }
}
