package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("InterestService")
@ExtendWith(MockitoExtension.class)
class InterestServiceTest {

    @Mock private UltiToolsPlugin plugin;
    @Mock private EconomyService economyService;
    @Mock private DataOperator<PlayerAccountEntity> dataOperator;

    private EconomyConfig config;
    private InterestService service;

    private static final UUID PLAYER1_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID PLAYER2_UUID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        config = new EconomyConfig();
        lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        service = new InterestService(plugin, economyService, config, dataOperator);
    }

    @Nested
    @DisplayName("calculateInterest")
    class CalculateInterestTests {

        @Test
        @DisplayName("calculates interest correctly")
        void basicCalculation() throws Exception {
            config.setInterestRate(0.03);
            Method method = InterestService.class.getDeclaredMethod("calculateInterest", double.class);
            method.setAccessible(true);
            double interest = (double) method.invoke(service, 10000.0);
            assertThat(interest).isEqualTo(300.0);
        }

        @Test
        @DisplayName("caps at maxInterest")
        void cappedInterest() throws Exception {
            config.setInterestRate(0.1);
            config.setMaxInterest(500.0);
            Method method = InterestService.class.getDeclaredMethod("calculateInterest", double.class);
            method.setAccessible(true);
            double interest = (double) method.invoke(service, 10000.0);
            assertThat(interest).isEqualTo(500.0);
        }

        @Test
        @DisplayName("zero bank returns zero interest")
        void zeroBankBalance() throws Exception {
            Method method = InterestService.class.getDeclaredMethod("calculateInterest", double.class);
            method.setAccessible(true);
            double interest = (double) method.invoke(service, 0.0);
            assertThat(interest).isEqualTo(0.0);
        }

        @Test
        @DisplayName("negative bank returns zero interest")
        void negativeBankBalance() throws Exception {
            Method method = InterestService.class.getDeclaredMethod("calculateInterest", double.class);
            method.setAccessible(true);
            double interest = (double) method.invoke(service, -100.0);
            assertThat(interest).isEqualTo(0.0);
        }

        @Test
        @DisplayName("unlimited maxInterest when set to -1")
        void unlimitedMaxInterest() throws Exception {
            config.setInterestRate(0.5);
            config.setMaxInterest(-1);
            Method method = InterestService.class.getDeclaredMethod("calculateInterest", double.class);
            method.setAccessible(true);
            double interest = (double) method.invoke(service, 100000.0);
            assertThat(interest).isEqualTo(50000.0);
        }
    }

    @Nested
    @DisplayName("distributeInterest")
    class DistributeTests {

        @Test
        @DisplayName("distributes interest to accounts with positive bank")
        void distributesToPositiveBank() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);

                PlayerAccountEntity account1 = PlayerAccountEntity.builder()
                        .uuid(PLAYER1_UUID.toString())
                        .playerName("Player1")
                        .cash(100.0)
                        .bank(10000.0)
                        .build();

                when(dataOperator.getAll()).thenReturn(Collections.singletonList(account1));

                service.distributeInterest();

                // 10000 * 0.03 = 300
                verify(economyService).addBank(PLAYER1_UUID, 300.0);
            }
        }

        @Test
        @DisplayName("skips accounts with zero bank balance")
        void skipsZeroBank() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);

                PlayerAccountEntity account = PlayerAccountEntity.builder()
                        .uuid(PLAYER1_UUID.toString())
                        .playerName("Player1")
                        .cash(5000.0)
                        .bank(0.0)
                        .build();

                when(dataOperator.getAll()).thenReturn(Collections.singletonList(account));

                service.distributeInterest();

                verify(economyService, never()).addBank(any(), anyDouble());
            }
        }

        @Test
        @DisplayName("caps interest at maxInterest config value")
        void capsInterest() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);

                config.setMaxInterest(500.0);
                config.setInterestRate(0.1);

                PlayerAccountEntity account = PlayerAccountEntity.builder()
                        .uuid(PLAYER1_UUID.toString())
                        .playerName("Rich")
                        .cash(0.0)
                        .bank(100000.0)
                        .build();

                when(dataOperator.getAll()).thenReturn(Collections.singletonList(account));

                service.distributeInterest();

                // 100000 * 0.1 = 10000, capped at 500
                verify(economyService).addBank(PLAYER1_UUID, 500.0);
            }
        }

        @Test
        @DisplayName("notifies online players about interest")
        void notifiesOnlinePlayers() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                Player onlinePlayer = mock(Player.class);
                when(onlinePlayer.isOnline()).thenReturn(true);
                bukkit.when(() -> Bukkit.getPlayer(PLAYER1_UUID)).thenReturn(onlinePlayer);

                when(economyService.formatAmount(300.0)).thenReturn("$300.00");

                PlayerAccountEntity account = PlayerAccountEntity.builder()
                        .uuid(PLAYER1_UUID.toString())
                        .playerName("Player1")
                        .cash(0.0)
                        .bank(10000.0)
                        .build();

                when(dataOperator.getAll()).thenReturn(Collections.singletonList(account));

                service.distributeInterest();

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(onlinePlayer).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("银行利息到账").contains("$300.00");
            }
        }

        @Test
        @DisplayName("handles multiple accounts")
        void multipleAccounts() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);

                PlayerAccountEntity account1 = PlayerAccountEntity.builder()
                        .uuid(PLAYER1_UUID.toString())
                        .playerName("Player1")
                        .cash(0.0)
                        .bank(5000.0)
                        .build();
                PlayerAccountEntity account2 = PlayerAccountEntity.builder()
                        .uuid(PLAYER2_UUID.toString())
                        .playerName("Player2")
                        .cash(0.0)
                        .bank(20000.0)
                        .build();

                when(dataOperator.getAll()).thenReturn(Arrays.asList(account1, account2));

                service.distributeInterest();

                // 5000 * 0.03 = 150, 20000 * 0.03 = 600
                verify(economyService).addBank(PLAYER1_UUID, 150.0);
                verify(economyService).addBank(PLAYER2_UUID, 600.0);
            }
        }

        @Test
        @DisplayName("handles empty account list")
        void emptyAccounts() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                when(dataOperator.getAll()).thenReturn(Collections.emptyList());

                service.distributeInterest();

                verify(economyService, never()).addBank(any(), anyDouble());
            }
        }
    }
}
