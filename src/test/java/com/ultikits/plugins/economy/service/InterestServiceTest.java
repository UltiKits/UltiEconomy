package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("InterestService")
@ExtendWith(MockitoExtension.class)
class InterestServiceTest {

    @Mock private UltiToolsPlugin plugin;
    @Mock private EconomyService economyService;
    @Mock private DataOperator<PlayerAccountEntity> dataOperator;
    @Mock private DataOperator<CurrencyBalanceEntity> currencyDataOperator;

    private EconomyConfig config;
    private CurrencyManager currencyManager;
    private InterestService service;

    private static final UUID PLAYER1_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID PLAYER2_UUID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

    private static final String CURRENCIES_YAML =
            "currencies:\n" +
            "  coins:\n" +
            "    display-name: 'Coins'\n" +
            "    symbol: '$'\n" +
            "    primary: true\n" +
            "    bank-enabled: true\n" +
            "  gems:\n" +
            "    display-name: 'Gems'\n" +
            "    symbol: 'G'\n" +
            "    primary: false\n" +
            "    bank-enabled: false\n" +
            "  silver:\n" +
            "    display-name: 'Silver'\n" +
            "    symbol: 'S'\n" +
            "    primary: false\n" +
            "    bank-enabled: true\n";

    @BeforeEach
    void setUp() {
        config = new EconomyConfig();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader(CURRENCIES_YAML));
        currencyManager = new CurrencyManager(yaml);
        lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(currencyDataOperator.getAll()).thenReturn(Collections.emptyList());
        service = InterestService.createForTest(plugin, economyService, config, dataOperator, currencyDataOperator, currencyManager);
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
        void distributesToPositiveBank() throws Exception {
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
                assertPrimaryBankWritten(PLAYER1_UUID, 10300.0);
            }
        }

        @Test
        @DisplayName("skips accounts with zero bank balance")
        void skipsZeroBank() throws Exception {
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

                verify(dataOperator, never()).update(any(PlayerAccountEntity.class));
            }
        }

        @Test
        @DisplayName("caps interest at maxInterest config value")
        void capsInterest() throws Exception {
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
                assertPrimaryBankWritten(PLAYER1_UUID, 100500.0);
            }
        }

        @Test
        @DisplayName("notifies online players about interest")
        void notifiesOnlinePlayers() throws Exception {
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
        void multipleAccounts() throws Exception {
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
                assertPrimaryBankWritten(PLAYER1_UUID, 5150.0);
                assertPrimaryBankWritten(PLAYER2_UUID, 20600.0);
            }
        }

        @Test
        @DisplayName("handles empty account list")
        void emptyAccounts() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                when(dataOperator.getAll()).thenReturn(Collections.emptyList());

                service.distributeInterest();

                verify(dataOperator, never()).update(any(PlayerAccountEntity.class));
            }
        }
    }

    @Nested
    @DisplayName("Currency interest distribution")
    class CurrencyInterestTests {

        @Test
        @DisplayName("distributes interest for bank-enabled non-primary currency balances")
        void distributesToBankEnabledCurrencies() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);
                when(dataOperator.getAll()).thenReturn(Collections.emptyList());

                CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                        .uuid(PLAYER1_UUID.toString())
                        .currencyId("silver")
                        .cash(100.0)
                        .bank(5000.0)
                        .build();
                when(currencyDataOperator.getAll()).thenReturn(Collections.singletonList(balance));

                service.distributeInterest();

                // 5000 * 0.03 = 150
                assertCurrencyBankWritten(PLAYER1_UUID, "silver", 5150.0);
            }
        }

        @Test
        @DisplayName("skips currencies where bank is not enabled")
        void skipsBankDisabledCurrencies() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);
                when(dataOperator.getAll()).thenReturn(Collections.emptyList());

                CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                        .uuid(PLAYER1_UUID.toString())
                        .currencyId("gems")
                        .cash(100.0)
                        .bank(5000.0)
                        .build();
                when(currencyDataOperator.getAll()).thenReturn(Collections.singletonList(balance));

                service.distributeInterest();

                verify(currencyDataOperator, never()).update(any(CurrencyBalanceEntity.class));
            }
        }

        @Test
        @DisplayName("caps currency interest at maxInterest")
        void capsCurrencyInterest() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);
                when(dataOperator.getAll()).thenReturn(Collections.emptyList());

                config.setMaxInterest(200.0);
                config.setInterestRate(0.1);

                CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                        .uuid(PLAYER1_UUID.toString())
                        .currencyId("silver")
                        .cash(0.0)
                        .bank(50000.0)
                        .build();
                when(currencyDataOperator.getAll()).thenReturn(Collections.singletonList(balance));

                service.distributeInterest();

                // 50000 * 0.1 = 5000, capped at 200
                assertCurrencyBankWritten(PLAYER1_UUID, "silver", 50200.0);
            }
        }

        @Test
        @DisplayName("notifies online player about currency interest")
        void notifiesCurrencyInterest() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                Player onlinePlayer = mock(Player.class);
                when(onlinePlayer.isOnline()).thenReturn(true);
                bukkit.when(() -> Bukkit.getPlayer(PLAYER1_UUID)).thenReturn(onlinePlayer);
                when(dataOperator.getAll()).thenReturn(Collections.emptyList());
                when(economyService.formatAmount(150.0, "silver")).thenReturn("$150.00");

                CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                        .uuid(PLAYER1_UUID.toString())
                        .currencyId("silver")
                        .cash(0.0)
                        .bank(5000.0)
                        .build();
                when(currencyDataOperator.getAll()).thenReturn(Collections.singletonList(balance));

                service.distributeInterest();

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(onlinePlayer).sendMessage(captor.capture());
                assertThat(captor.getValue()).contains("Silver").contains("$150.00");
            }
        }

        @Test
        @DisplayName("skips currency balances with zero bank")
        void skipsZeroBankCurrency() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);
                when(dataOperator.getAll()).thenReturn(Collections.emptyList());

                CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                        .uuid(PLAYER1_UUID.toString())
                        .currencyId("silver")
                        .cash(500.0)
                        .bank(0.0)
                        .build();
                when(currencyDataOperator.getAll()).thenReturn(Collections.singletonList(balance));

                service.distributeInterest();

                verify(currencyDataOperator, never()).update(any(CurrencyBalanceEntity.class));
            }
        }

        @Test
        @DisplayName("distributeInterest skips per-currency interest entirely when the module has no currency data operator")
        void skipsPerCurrencyWhenOperatorAbsent() throws Exception {
            InterestService noCurrencyOpService = InterestService.createForTest(
                    plugin, economyService, config, dataOperator, null, currencyManager);

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);

                PlayerAccountEntity account = PlayerAccountEntity.builder()
                        .uuid(PLAYER1_UUID.toString())
                        .playerName("Player1")
                        .cash(0.0)
                        .bank(10000.0)
                        .build();
                when(dataOperator.getAll()).thenReturn(Collections.singletonList(account));

                noCurrencyOpService.distributeInterest();

                // Primary interest still runs, but the per-currency loop is never reached.
                assertPrimaryBankWritten(PLAYER1_UUID, 10300.0);
                verifyNoInteractions(currencyDataOperator);
            }
        }
    }

    /**
     * Interest is paid on a schedule the framework runs (UltiKits/UltiEconomy#15). Before 6.3.0
     * nothing scheduled {@link InterestService#distributeInterest()}, so no interest was ever paid
     * on any server whatever {@code interest.enabled} said.
     *
     * <p>The period and the first delay are both {@code interest.interval} seconds, bound through
     * the framework's config-bound {@code @Scheduled} (UltiKits/UltiTools-Reborn#531), so the first
     * payment comes one full interval after load. {@code interest.enabled} is read at every run, so
     * a {@code /ul reload} that flips it takes effect at the next payment.
     */
    @Nested
    @DisplayName("Scheduled payment (UltiEconomy#15)")
    class ScheduleTests {

        private final PlayerAccountEntity saver = PlayerAccountEntity.builder()
                .uuid(PLAYER1_UUID.toString()).playerName("Saver").cash(0.0).bank(10000.0).build();

        @Test
        @DisplayName("interest.interval at its declared 1800: one repeating sync task, first run after 36000 ticks, then every 36000")
        void registersOneSyncTaskOnTheDeclaredInterval() throws Exception {
            assertThat(com.ultikits.plugins.economy.config.ConfigEntryAccess.get(config, "interest.interval"))
                    .isEqualTo(1800);
            List<ScheduledRegistration.Call> calls = ScheduledRegistration.register(service, config);

            assertThat(calls).hasSize(1);
            ScheduledRegistration.Call call = calls.get(0);
            assertThat(call.method).isEqualTo("runTaskTimer");
            assertThat(call.period).isEqualTo(36000L);
            assertThat(call.delay).isEqualTo(36000L);
            assertThat(call.task).isNotNull();
        }

        @Test
        @DisplayName("interest.interval: 900 -- first run after 18000 ticks, then every 18000 (UltiTools-Reborn#531 binding)")
        void registersOnTheConfiguredInterval() throws Exception {
            com.ultikits.plugins.economy.config.ConfigEntryAccess.set(config, "interest.interval", 900);

            List<ScheduledRegistration.Call> calls = ScheduledRegistration.register(service, config);

            assertThat(calls).hasSize(1);
            assertThat(calls.get(0).method).isEqualTo("runTaskTimer");
            assertThat(calls.get(0).period).isEqualTo(18000L);
            assertThat(calls.get(0).delay).isEqualTo(18000L);
        }

        @Test
        @DisplayName("interest.enabled: true -- a scheduled run pays interest")
        void enabledRunPays() throws Exception {
            config.setInterestEnabled(true);
            when(dataOperator.getAll()).thenReturn(Collections.singletonList(saver));
            Runnable task = ScheduledRegistration.register(service, config).get(0).task;

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);
                assertThat(ScheduledRegistration.runAndCollectWarnings(task, bukkit)).isEmpty();
            }

            assertPrimaryBankWritten(PLAYER1_UUID, 10300.0);
        }

        @Test
        @DisplayName("interest.enabled: false -- a scheduled run reads no account and pays nothing")
        void disabledRunDoesNothing() throws Exception {
            config.setInterestEnabled(false);
            lenient().when(dataOperator.getAll()).thenReturn(Collections.singletonList(saver));
            Runnable task = ScheduledRegistration.register(service, config).get(0).task;

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);
                assertThat(ScheduledRegistration.runAndCollectWarnings(task, bukkit)).isEmpty();
            }

            verify(dataOperator, never()).getAll();
            verify(currencyDataOperator, never()).getAll();
            verifyNoInteractions(economyService);
        }

        @Test
        @DisplayName("interest.enabled is read at every run: on, off, on pays twice")
        void switchIsReadAtEveryRun() throws Exception {
            when(dataOperator.getAll()).thenReturn(Collections.singletonList(saver));
            Runnable task = ScheduledRegistration.register(service, config).get(0).task;

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);
                config.setInterestEnabled(true);
                assertThat(ScheduledRegistration.runAndCollectWarnings(task, bukkit)).isEmpty();
                config.setInterestEnabled(false);
                assertThat(ScheduledRegistration.runAndCollectWarnings(task, bukkit)).isEmpty();
                config.setInterestEnabled(true);
                assertThat(ScheduledRegistration.runAndCollectWarnings(task, bukkit)).isEmpty();
            }

            // Two payments compound on the same row: 10000 -> 10300 -> 10609.
            verify(dataOperator, times(2)).update(saver);
            assertThat(saver.getBank()).isCloseTo(10609.0, within(1e-6));
        }

        @Test
        @DisplayName("the service is not conditional on interest.enabled, so turning it on by reload has something to run")
        void serviceExistsWhateverTheSwitchSaysAtBoot() throws Exception {
            assertThat(InterestService.class.isAnnotationPresent(
                    com.ultikits.ultitools.annotations.ConditionalOnConfig.class)).isFalse();
        }

        @Test
        @DisplayName("interest.enabled is declared false: paying interest creates money, so it is an operator's choice")
        void switchIsDeclaredOff() throws Exception {
            assertThat(new EconomyConfig().isInterestEnabled()).isFalse();
        }
    }

    /**
     * How a payment writes (gate-1 review of UltiKits/UltiEconomy#15, findings WR-01, WR-03 and
     * WR-04). A payment runs on the main thread, so it must credit the rows it already read instead
     * of looking each one up again; it must respect the bank caps a deposit respects; and it must
     * tell a player about interest only when the credit was actually written.
     */
    @Nested
    @DisplayName("Payment writes (UltiEconomy#15 gate-1)")
    class PaymentWriteTests {

        @Test
        @DisplayName("credits the rows getAll() returned: no per-account query, no EconomyService round trip")
        void creditsTheRowsItReadWithoutQueryingAgain() throws Exception {
            PlayerAccountEntity a = account(PLAYER1_UUID, 1000.0);
            PlayerAccountEntity b = account(PLAYER2_UUID, 2000.0);
            CurrencyBalanceEntity coins = balance(PLAYER1_UUID, "silver", 3000.0);
            when(dataOperator.getAll()).thenReturn(Arrays.asList(a, b));
            when(currencyDataOperator.getAll()).thenReturn(Collections.singletonList(coins));

            runPayment();

            verify(dataOperator, times(1)).getAll();
            verify(dataOperator, never()).query();
            verify(currencyDataOperator, times(1)).getAll();
            verify(currencyDataOperator, never()).query();
            verify(economyService, never()).addBank(any(), anyDouble());
            verify(economyService, never()).addBank(any(), anyDouble(), anyString());
            verify(dataOperator).update(a);
            verify(dataOperator).update(b);
            verify(currencyDataOperator).update(coins);
            assertThat(a.getBank()).isCloseTo(1030.0, within(1e-6));
            assertThat(b.getBank()).isCloseTo(2060.0, within(1e-6));
            assertThat(coins.getBank()).isCloseTo(3090.0, within(1e-6));
        }

        @Test
        @DisplayName("bank.max-balance: a balance at the cap gets nothing and no message; one 100 below it gets exactly 100")
        void primaryInterestStopsAtTheBankCap() throws Exception {
            config.setMaxBankBalance(100000.0);
            PlayerAccountEntity atCap = account(PLAYER1_UUID, 100000.0);
            PlayerAccountEntity nearCap = account(PLAYER2_UUID, 99900.0);
            when(dataOperator.getAll()).thenReturn(Arrays.asList(atCap, nearCap));
            Player online = mock(Player.class);
            // Lenient: a correct payment never reaches the owner of a balance already at the cap.
            lenient().when(online.isOnline()).thenReturn(true);

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(PLAYER1_UUID)).thenReturn(online);
                bukkit.when(() -> Bukkit.getPlayer(PLAYER2_UUID)).thenReturn(null);
                service.distributeInterest();
            }

            verify(dataOperator, never()).update(atCap);
            assertThat(atCap.getBank()).isEqualTo(100000.0);
            verify(online, never()).sendMessage(anyString());
            verify(dataOperator).update(nearCap);
            assertThat(nearCap.getBank()).isCloseTo(100000.0, within(1e-6));
        }

        @Test
        @DisplayName("a currency's max-bank-balance caps that currency's interest the same way")
        void currencyInterestStopsAtThatCurrencysCap() throws Exception {
            CurrencyManager capped = new CurrencyManager(YamlConfiguration.loadConfiguration(new StringReader(
                    "currencies:\n"
                            + "  coins:\n    display-name: 'Coins'\n    symbol: '$'\n    primary: true\n"
                            + "  silver:\n    display-name: 'Silver'\n    symbol: 'S'\n    primary: false\n"
                            + "    bank-enabled: true\n    max-bank-balance: 1000.0\n")));
            InterestService cappedService = InterestService.createForTest(
                    plugin, economyService, config, dataOperator, currencyDataOperator, capped);
            CurrencyBalanceEntity atCap = balance(PLAYER1_UUID, "silver", 1000.0);
            CurrencyBalanceEntity nearCap = balance(PLAYER2_UUID, "silver", 990.0);
            when(dataOperator.getAll()).thenReturn(Collections.emptyList());
            when(currencyDataOperator.getAll()).thenReturn(Arrays.asList(atCap, nearCap));

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);
                cappedService.distributeInterest();
            }

            verify(currencyDataOperator, never()).update(atCap);
            assertThat(atCap.getBank()).isEqualTo(1000.0);
            verify(currencyDataOperator).update(nearCap);
            assertThat(nearCap.getBank()).isCloseTo(1000.0, within(1e-6));
        }

        @Test
        @DisplayName("Control: bank.max-balance -1 (unlimited) does not cap interest")
        void unlimitedBankCapDoesNotCap() throws Exception {
            config.setMaxBankBalance(-1);
            PlayerAccountEntity rich = account(PLAYER1_UUID, 100000.0);
            when(dataOperator.getAll()).thenReturn(Collections.singletonList(rich));

            runPayment();

            assertThat(rich.getBank()).isCloseTo(103000.0, within(1e-6));
        }

        @Test
        @DisplayName("a failed write sends no 'interest received' message, and the next account is still paid")
        void failedWriteIsNotAnnounced() throws Exception {
            PlayerAccountEntity failing = account(PLAYER1_UUID, 10000.0);
            PlayerAccountEntity next = account(PLAYER2_UUID, 10000.0);
            when(dataOperator.getAll()).thenReturn(Arrays.asList(failing, next));
            doThrow(new IllegalAccessException("write failed")).when(dataOperator).update(failing);
            com.ultikits.ultitools.interfaces.impl.logger.PluginLogger logger =
                    mock(com.ultikits.ultitools.interfaces.impl.logger.PluginLogger.class);
            lenient().when(plugin.getLogger()).thenReturn(logger);
            Player failingOwner = mock(Player.class);
            lenient().when(failingOwner.isOnline()).thenReturn(true);
            Player nextOwner = mock(Player.class);
            when(nextOwner.isOnline()).thenReturn(true);
            when(economyService.formatAmount(300.0)).thenReturn("$300.00");

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(PLAYER1_UUID)).thenReturn(failingOwner);
                bukkit.when(() -> Bukkit.getPlayer(PLAYER2_UUID)).thenReturn(nextOwner);
                service.distributeInterest();
            }

            verify(failingOwner, never()).sendMessage(anyString());
            verify(nextOwner).sendMessage(contains("$300.00"));
            assertThat(next.getBank()).isCloseTo(10300.0, within(1e-6));
        }

        @Test
        @DisplayName("interest.rate 0 or negative writes nothing and tells nobody")
        void nonPositiveRatePaysAndAnnouncesNothing() throws Exception {
            PlayerAccountEntity saver = account(PLAYER1_UUID, 10000.0);
            when(dataOperator.getAll()).thenReturn(Collections.singletonList(saver));
            Player owner = mock(Player.class);
            lenient().when(owner.isOnline()).thenReturn(true);

            for (double rate : new double[] {0.0, -0.03}) {
                config.setInterestRate(rate);
                try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                    bukkit.when(() -> Bukkit.getPlayer(PLAYER1_UUID)).thenReturn(owner);
                    service.distributeInterest();
                }
            }

            verify(dataOperator, never()).update(any(PlayerAccountEntity.class));
            verify(owner, never()).sendMessage(anyString());
            assertThat(saver.getBank()).isEqualTo(10000.0);
        }

        /**
         * Maintainer ruling 2026-09-23 ("interest is paid on one wallet only"; UltiKits/UltiEconomy#25):
         * the primary currency is held twice -- the account row every bare command and Vault read
         * (`/bank`, `/money`, `/eco check <player>`, `/deposit <amount>`), and a per-currency row for
         * the primary id created on join. Interest is paid once, on the account row.
         */
        @Test
        @DisplayName("a player with bank money in both primary wallets gets one payment, from the account's bank balance")
        void primaryCurrencyIsPaidOnceFromTheAccount() throws Exception {
            PlayerAccountEntity account = account(PLAYER1_UUID, 10000.0);
            CurrencyBalanceEntity primaryRow = balance(PLAYER1_UUID, "coins", 10000.0);
            when(dataOperator.getAll()).thenReturn(Collections.singletonList(account));
            lenient().when(currencyDataOperator.getAll()).thenReturn(Collections.singletonList(primaryRow));
            Player owner = mock(Player.class);
            when(owner.isOnline()).thenReturn(true);
            when(economyService.formatAmount(300.0)).thenReturn("$300.00");

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(PLAYER1_UUID)).thenReturn(owner);
                service.distributeInterest();
            }

            assertThat(account.getBank()).isCloseTo(10300.0, within(1e-6));
            verify(currencyDataOperator, never()).update(any(CurrencyBalanceEntity.class));
            assertThat(primaryRow.getBank()).isEqualTo(10000.0);
            verify(owner, times(1)).sendMessage(anyString());
        }

        @Test
        @DisplayName("the per-payment cap binds at interest.max-interest for the primary currency, not twice that")
        void primaryCurrencyCapIsTheDeclaredOne() throws Exception {
            config.setInterestRate(0.03);
            config.setMaxInterest(10000.0);
            PlayerAccountEntity account = account(PLAYER1_UUID, 1000000.0);
            CurrencyBalanceEntity primaryRow = balance(PLAYER1_UUID, "coins", 1000000.0);
            when(dataOperator.getAll()).thenReturn(Collections.singletonList(account));
            lenient().when(currencyDataOperator.getAll()).thenReturn(Collections.singletonList(primaryRow));

            runPayment();

            double credited = (account.getBank() - 1000000.0) + (primaryRow.getBank() - 1000000.0);
            assertThat(credited).isCloseTo(10000.0, within(1e-6));
        }

        private void runPayment() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);
                service.distributeInterest();
            }
        }
    }

    private static PlayerAccountEntity account(UUID uuid, double bank) {
        return PlayerAccountEntity.builder().uuid(uuid.toString()).playerName("P").cash(0.0).bank(bank).build();
    }

    private static CurrencyBalanceEntity balance(UUID uuid, String currencyId, double bank) {
        return CurrencyBalanceEntity.builder().uuid(uuid.toString()).currencyId(currencyId).cash(0.0).bank(bank).build();
    }

    /** The primary account of {@code uuid} was written exactly once, holding {@code expectedBank}. */
    private void assertPrimaryBankWritten(UUID uuid, double expectedBank) throws Exception {
        ArgumentCaptor<PlayerAccountEntity> written = ArgumentCaptor.forClass(PlayerAccountEntity.class);
        verify(dataOperator, atLeastOnce()).update(written.capture());
        long matching = written.getAllValues().stream().filter(e -> uuid.toString().equals(e.getUuid())).count();
        assertThat(matching).as("writes of %s's account", uuid).isEqualTo(1);
        PlayerAccountEntity entity = written.getAllValues().stream()
                .filter(e -> uuid.toString().equals(e.getUuid())).findFirst().get();
        assertThat(entity.getBank()).isCloseTo(expectedBank, within(1e-6));
    }

    /** The {@code currencyId} balance of {@code uuid} was written exactly once, holding {@code expectedBank}. */
    private void assertCurrencyBankWritten(UUID uuid, String currencyId, double expectedBank) throws Exception {
        ArgumentCaptor<CurrencyBalanceEntity> written = ArgumentCaptor.forClass(CurrencyBalanceEntity.class);
        verify(currencyDataOperator, atLeastOnce()).update(written.capture());
        List<CurrencyBalanceEntity> matching = new java.util.ArrayList<>();
        for (CurrencyBalanceEntity e : written.getAllValues()) {
            if (uuid.toString().equals(e.getUuid()) && currencyId.equals(e.getCurrencyId())) {
                matching.add(e);
            }
        }
        assertThat(matching).as("writes of %s's %s balance", uuid, currencyId).hasSize(1);
        assertThat(matching.get(0).getBank()).isCloseTo(expectedBank, within(1e-6));
    }

    @Nested
    @DisplayName("Invalid UUID handling")
    class InvalidUuidTests {

        // notifyPlayer(String, double) and notifyPlayer(String, double, String) are private and
        // called only after distributeInterest() has already parsed the same UUID string
        // successfully -- so the malformed-UUID
        // catch blocks inside them are unreachable through distributeInterest() itself. They are
        // invoked directly via reflection here, the same technique this file already uses for the
        // private calculateInterest(double).

        @Test
        @DisplayName("notifyPlayer swallows an invalid UUID instead of propagating the parse failure")
        void notifyPlayerSwallowsInvalidUuid() throws Exception {
            Method method = InterestService.class.getDeclaredMethod("notifyPlayer", String.class, double.class);
            method.setAccessible(true);

            assertThatCode(() -> method.invoke(service, "not-a-real-uuid", 100.0))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("notifyPlayer with currencyId swallows an invalid UUID instead of propagating the parse failure")
        void notifyPlayerWithCurrencySwallowsInvalidUuid() throws Exception {
            Method method = InterestService.class.getDeclaredMethod(
                    "notifyPlayer", String.class, double.class, String.class);
            method.setAccessible(true);

            assertThatCode(() -> method.invoke(service, "also-not-a-real-uuid", 100.0, "coins"))
                    .doesNotThrowAnyException();
        }
    }
}
