package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("LeaderboardService")
@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock private DataOperator<PlayerAccountEntity> dataOperator;

    private EconomyConfig config;
    private LeaderboardService service;

    private static final UUID UUID_RICH = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID UUID_MIDDLE = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID UUID_POOR = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");

    @BeforeEach
    void setUp() {
        config = new EconomyConfig();
        service = new LeaderboardService(config, dataOperator);
    }

    @Nested
    @DisplayName("refreshLeaderboard")
    class RefreshTests {

        @Test
        @DisplayName("loads and sorts accounts by total wealth descending")
        void sortsDescending() {
            List<PlayerAccountEntity> accounts = Arrays.asList(
                    PlayerAccountEntity.builder().uuid(UUID_POOR.toString()).playerName("Poor").cash(100).bank(0).build(),
                    PlayerAccountEntity.builder().uuid(UUID_RICH.toString()).playerName("Rich").cash(5000).bank(10000).build(),
                    PlayerAccountEntity.builder().uuid(UUID_MIDDLE.toString()).playerName("Middle").cash(2000).bank(1000).build()
            );
            when(dataOperator.getAll()).thenReturn(accounts);

            service.refreshLeaderboard();

            List<LeaderboardService.LeaderboardEntry> top = service.getTopPlayers(10);
            assertThat(top).hasSize(3);
            assertThat(top.get(0).getPlayerName()).isEqualTo("Rich");
            assertThat(top.get(0).getTotalWealth()).isEqualTo(15000.0);
            assertThat(top.get(1).getPlayerName()).isEqualTo("Middle");
            assertThat(top.get(1).getTotalWealth()).isEqualTo(3000.0);
            assertThat(top.get(2).getPlayerName()).isEqualTo("Poor");
            assertThat(top.get(2).getTotalWealth()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("handles empty account list")
        void emptyAccounts() {
            when(dataOperator.getAll()).thenReturn(Collections.emptyList());

            service.refreshLeaderboard();

            assertThat(service.getTopPlayers(10)).isEmpty();
        }

        @Test
        @DisplayName("refresh replaces old data")
        void refreshReplacesOldData() {
            List<PlayerAccountEntity> initial = Collections.singletonList(
                    PlayerAccountEntity.builder().uuid(UUID_RICH.toString()).playerName("Rich").cash(1000).bank(0).build()
            );
            when(dataOperator.getAll()).thenReturn(initial);
            service.refreshLeaderboard();
            assertThat(service.getTopPlayers(10)).hasSize(1);

            List<PlayerAccountEntity> updated = Arrays.asList(
                    PlayerAccountEntity.builder().uuid(UUID_RICH.toString()).playerName("Rich").cash(5000).bank(0).build(),
                    PlayerAccountEntity.builder().uuid(UUID_POOR.toString()).playerName("Poor").cash(500).bank(0).build()
            );
            when(dataOperator.getAll()).thenReturn(updated);
            service.refreshLeaderboard();

            List<LeaderboardService.LeaderboardEntry> top = service.getTopPlayers(10);
            assertThat(top).hasSize(2);
            assertThat(top.get(0).getTotalWealth()).isEqualTo(5000.0);
        }
    }

    @Nested
    @DisplayName("getTopPlayers")
    class TopPlayersTests {

        @Test
        @DisplayName("limits results to requested count")
        void limitsCount() {
            List<PlayerAccountEntity> accounts = Arrays.asList(
                    PlayerAccountEntity.builder().uuid(UUID_RICH.toString()).playerName("Rich").cash(5000).bank(0).build(),
                    PlayerAccountEntity.builder().uuid(UUID_MIDDLE.toString()).playerName("Middle").cash(3000).bank(0).build(),
                    PlayerAccountEntity.builder().uuid(UUID_POOR.toString()).playerName("Poor").cash(1000).bank(0).build()
            );
            when(dataOperator.getAll()).thenReturn(accounts);
            service.refreshLeaderboard();

            List<LeaderboardService.LeaderboardEntry> top = service.getTopPlayers(2);
            assertThat(top).hasSize(2);
            assertThat(top.get(0).getPlayerName()).isEqualTo("Rich");
            assertThat(top.get(1).getPlayerName()).isEqualTo("Middle");
        }

        @Test
        @DisplayName("returns all when count exceeds list size")
        void countExceedsList() {
            List<PlayerAccountEntity> accounts = Collections.singletonList(
                    PlayerAccountEntity.builder().uuid(UUID_RICH.toString()).playerName("Rich").cash(5000).bank(0).build()
            );
            when(dataOperator.getAll()).thenReturn(accounts);
            service.refreshLeaderboard();

            assertThat(service.getTopPlayers(100)).hasSize(1);
        }

        @Test
        @DisplayName("returns empty list before any refresh")
        void beforeRefresh() {
            assertThat(service.getTopPlayers(10)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPlayerRank")
    class PlayerRankTests {

        @Test
        @DisplayName("returns correct 1-based rank")
        void correctRank() {
            List<PlayerAccountEntity> accounts = Arrays.asList(
                    PlayerAccountEntity.builder().uuid(UUID_RICH.toString()).playerName("Rich").cash(5000).bank(10000).build(),
                    PlayerAccountEntity.builder().uuid(UUID_MIDDLE.toString()).playerName("Middle").cash(2000).bank(1000).build(),
                    PlayerAccountEntity.builder().uuid(UUID_POOR.toString()).playerName("Poor").cash(100).bank(0).build()
            );
            when(dataOperator.getAll()).thenReturn(accounts);
            service.refreshLeaderboard();

            assertThat(service.getPlayerRank(UUID_RICH)).isEqualTo(1);
            assertThat(service.getPlayerRank(UUID_MIDDLE)).isEqualTo(2);
            assertThat(service.getPlayerRank(UUID_POOR)).isEqualTo(3);
        }

        @Test
        @DisplayName("returns -1 for unknown player")
        void unknownPlayer() {
            when(dataOperator.getAll()).thenReturn(Collections.emptyList());
            service.refreshLeaderboard();

            UUID unknown = UUID.fromString("999e8400-e29b-41d4-a716-446655440000");
            assertThat(service.getPlayerRank(unknown)).isEqualTo(-1);
        }

        @Test
        @DisplayName("returns -1 before any refresh")
        void beforeRefresh() {
            assertThat(service.getPlayerRank(UUID_RICH)).isEqualTo(-1);
        }
    }
}
