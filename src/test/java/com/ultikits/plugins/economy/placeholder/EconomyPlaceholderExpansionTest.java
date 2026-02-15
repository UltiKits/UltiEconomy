package com.ultikits.plugins.economy.placeholder;

import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.service.LeaderboardService;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@DisplayName("EconomyPlaceholderExpansion")
@ExtendWith(MockitoExtension.class)
class EconomyPlaceholderExpansionTest {

    @Mock private EconomyService economyService;
    @Mock private LeaderboardService leaderboardService;
    @Mock private OfflinePlayer player;

    private EconomyPlaceholderExpansion expansion;

    private static final UUID PLAYER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @BeforeEach
    void setUp() {
        expansion = new EconomyPlaceholderExpansion(economyService, leaderboardService);
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_UUID);
    }

    @Nested
    @DisplayName("Metadata")
    class MetadataTests {

        @Test
        @DisplayName("identifier is ultieconomy")
        void identifier() {
            assertThat(expansion.getIdentifier()).isEqualTo("ultieconomy");
        }

        @Test
        @DisplayName("author is wisdomme")
        void author() {
            assertThat(expansion.getAuthor()).isEqualTo("wisdomme");
        }

        @Test
        @DisplayName("persists across reloads")
        void persists() {
            assertThat(expansion.persist()).isTrue();
        }
    }

    @Nested
    @DisplayName("Balance placeholders")
    class BalanceTests {

        @Test
        @DisplayName("cash returns formatted cash balance")
        void cash() {
            when(economyService.getCash(PLAYER_UUID)).thenReturn(1234.56);
            assertThat(expansion.onRequest(player, "cash")).isEqualTo("1234.56");
        }

        @Test
        @DisplayName("bank returns formatted bank balance")
        void bank() {
            when(economyService.getBank(PLAYER_UUID)).thenReturn(5000.0);
            assertThat(expansion.onRequest(player, "bank")).isEqualTo("5000.00");
        }

        @Test
        @DisplayName("total returns total wealth")
        void total() {
            when(economyService.getTotalWealth(PLAYER_UUID)).thenReturn(6234.56);
            assertThat(expansion.onRequest(player, "total")).isEqualTo("6234.56");
        }

        @Test
        @DisplayName("cash_formatted returns with currency symbol")
        void cashFormatted() {
            when(economyService.getCash(PLAYER_UUID)).thenReturn(1234.56);
            when(economyService.formatAmount(1234.56)).thenReturn("$1,234.56");
            assertThat(expansion.onRequest(player, "cash_formatted")).isEqualTo("$1,234.56");
        }
    }

    @Nested
    @DisplayName("Rank placeholder")
    class RankTests {

        @Test
        @DisplayName("rank returns player rank")
        void rank() {
            when(leaderboardService.getPlayerRank(PLAYER_UUID)).thenReturn(3);
            assertThat(expansion.onRequest(player, "rank")).isEqualTo("3");
        }

        @Test
        @DisplayName("rank returns - when not ranked")
        void rankNotFound() {
            when(leaderboardService.getPlayerRank(PLAYER_UUID)).thenReturn(-1);
            assertThat(expansion.onRequest(player, "rank")).isEqualTo("-");
        }
    }

    @Nested
    @DisplayName("Top player placeholders")
    class TopTests {

        @Test
        @DisplayName("top_name_1 returns first place name")
        void topName() {
            LeaderboardService.LeaderboardEntry entry =
                    new LeaderboardService.LeaderboardEntry("uuid1", "RichPlayer", 50000.0);
            when(leaderboardService.getTopPlayers(1)).thenReturn(Collections.singletonList(entry));
            assertThat(expansion.onRequest(player, "top_name_1")).isEqualTo("RichPlayer");
        }

        @Test
        @DisplayName("top_balance_1 returns first place balance")
        void topBalance() {
            LeaderboardService.LeaderboardEntry entry =
                    new LeaderboardService.LeaderboardEntry("uuid1", "RichPlayer", 50000.0);
            when(leaderboardService.getTopPlayers(1)).thenReturn(Collections.singletonList(entry));
            assertThat(expansion.onRequest(player, "top_balance_1")).isEqualTo("50000.00");
        }

        @Test
        @DisplayName("top_name_N with out-of-range returns -")
        void topNameOutOfRange() {
            when(leaderboardService.getTopPlayers(99)).thenReturn(Collections.emptyList());
            assertThat(expansion.onRequest(player, "top_name_99")).isEqualTo("-");
        }

        @Test
        @DisplayName("top_balance_N with out-of-range returns 0")
        void topBalanceOutOfRange() {
            when(leaderboardService.getTopPlayers(99)).thenReturn(Collections.emptyList());
            assertThat(expansion.onRequest(player, "top_balance_99")).isEqualTo("0.00");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("null player returns empty string")
        void nullPlayer() {
            assertThat(expansion.onRequest(null, "cash")).isEqualTo("");
        }

        @Test
        @DisplayName("unknown placeholder returns null")
        void unknownPlaceholder() {
            assertThat(expansion.onRequest(player, "unknown")).isNull();
        }

        @Test
        @DisplayName("case insensitive matching")
        void caseInsensitive() {
            when(economyService.getCash(PLAYER_UUID)).thenReturn(100.0);
            assertThat(expansion.onRequest(player, "CASH")).isEqualTo("100.00");
        }
    }
}
