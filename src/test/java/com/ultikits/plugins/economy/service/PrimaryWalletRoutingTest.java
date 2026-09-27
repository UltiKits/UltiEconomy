package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.placeholder.EconomyPlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiEconomy#25: the primary currency has exactly one wallet, the account row
 * ({@code economy_accounts}) that Vault, {@code /money}, {@code /pay} and {@code /bank} use. Every path
 * that names the primary currency by its id -- {@code /money coins}, {@code /pay ... coins},
 * {@code /eco give ... coins}, {@code %ultieconomy_coins_cash%}, {@code /note ... coins} -- reads and writes
 * that same row. Asserted on what the storage holds afterwards, not on which method was called.
 *
 * <p>Several tests seed a leftover {@code currency_balances} row for the primary currency, as every
 * server that ran 2.0.0 has one for each player; the currency-named paths must neither read nor write it.
 */
@DisplayName("UltiKits/UltiEconomy#25: one wallet for the primary currency")
class PrimaryWalletRoutingTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private EconomyTestWorld world;
    private EconomyServiceImpl service;

    @BeforeEach
    void setUp() {
        world = EconomyTestWorld.relational();
        service = world.service;
    }

    /** The leftover second-wallet row's state, as a string, to assert it never moves. */
    private String leftover(UUID uuid) {
        StringBuilder sb = new StringBuilder();
        for (CurrencyBalanceEntity b : world.balanceRows("coins")) {
            if (uuid.toString().equals(b.getUuid())) {
                sb.append(b.getCash()).append('/').append(b.getBank()).append(';');
            }
        }
        return sb.toString();
    }

    @Nested
    @DisplayName("reads")
    class Reads {

        @Test
        @DisplayName("every currency-named read of the primary currency returns the account wallet")
        void currencyNamedReadsReturnTheAccountWallet() {
            world.seedAccount(STEVE, "Steve", 700.0, 300.0);
            world.seedBalance(STEVE, "coins", 1000.0, 0.0);

            assertThat(service.getCash(STEVE, "coins")).isEqualTo(700.0).isEqualTo(service.getCash(STEVE));
            assertThat(service.getBank(STEVE, "coins")).isEqualTo(300.0).isEqualTo(service.getBank(STEVE));
            assertThat(service.getTotalWealth(STEVE, "coins")).isEqualTo(1000.0);
            CurrencyBalanceEntity view = service.getBalance(STEVE, "coins");
            assertThat(view).isNotNull();
            assertThat(view.getCash()).isEqualTo(700.0);
            assertThat(view.getBank()).isEqualTo(300.0);
            assertThat(view.getCurrencyId()).isEqualTo("coins");
        }

        @Test
        @DisplayName("a player with no account has no primary-currency balance, whatever currency_balances holds")
        void noAccountMeansNoPrimaryBalance() {
            world.seedBalance(STEVE, "coins", 1000.0, 0.0);

            assertThat(service.hasBalance(STEVE, "coins")).isFalse();
            assertThat(service.getBalance(STEVE, "coins")).isNull();
            assertThat(service.getCash(STEVE, "coins")).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("writes")
    class Writes {

        @Test
        @DisplayName("every currency-named write of the primary currency moves the account wallet and nothing else")
        void currencyNamedWritesMoveTheAccountWallet() {
            world.seedAccount(STEVE, "Steve", 1000.0, 0.0);
            world.seedBalance(STEVE, "coins", 1000.0, 0.0);
            String before = leftover(STEVE);

            assertThat(service.addCash(STEVE, 50.0, "coins")).isTrue();
            assertThat(world.account(STEVE).getCash()).isEqualTo(1050.0);
            assertThat(service.takeCash(STEVE, 20.0, "coins")).isTrue();
            assertThat(world.account(STEVE).getCash()).isEqualTo(1030.0);
            assertThat(service.setCash(STEVE, 900.0, "coins")).isTrue();
            assertThat(world.account(STEVE).getCash()).isEqualTo(900.0);
            assertThat(service.addBank(STEVE, 40.0, "coins")).isTrue();
            assertThat(service.takeBank(STEVE, 10.0, "coins")).isTrue();
            assertThat(world.account(STEVE).getBank()).isEqualTo(30.0);
            assertThat(service.setBank(STEVE, 70.0, "coins")).isTrue();
            assertThat(world.account(STEVE).getBank()).isEqualTo(70.0);
            assertThat(service.depositToBank(STEVE, 200.0, "coins")).isTrue();
            assertThat(service.withdrawFromBank(STEVE, 100.0, "coins")).isTrue();
            PlayerAccountEntity after = world.account(STEVE);
            assertThat(after.getCash()).isEqualTo(800.0);
            assertThat(after.getBank()).isEqualTo(170.0);
            // Vault reads the same row.
            assertThat(service.getCash(STEVE)).isEqualTo(800.0);
            assertThat(leftover(STEVE)).as("the leftover second-wallet row").isEqualTo(before);
        }

        @Test
        @DisplayName("a refused currency-named write changes nothing")
        void refusedWritesChangeNothing() {
            world.seedAccount(STEVE, "Steve", 100.0, 50.0);

            assertThat(service.takeCash(STEVE, 101.0, "coins")).isFalse();
            assertThat(service.takeBank(STEVE, 51.0, "coins")).isFalse();
            assertThat(service.withdrawFromBank(STEVE, 51.0, "coins")).isFalse();
            assertThat(world.account(STEVE).getCash()).isEqualTo(100.0);
            assertThat(world.account(STEVE).getBank()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("paying in the primary currency by name moves both players' account wallets")
        void transferByNameMovesAccountWallets() {
            world.seedAccount(STEVE, "Steve", 500.0, 0.0);
            world.seedAccount(ALEX, "Alex", 10.0, 0.0);
            world.seedBalance(STEVE, "coins", 1000.0, 0.0);
            world.seedBalance(ALEX, "coins", 1000.0, 0.0);

            assertThat(service.transfer(STEVE, ALEX, 120.0, "coins")).isTrue();

            assertThat(world.account(STEVE).getCash()).isEqualTo(380.0);
            assertThat(world.account(ALEX).getCash()).isEqualTo(130.0);
            assertThat(leftover(STEVE)).isEqualTo("1000.0/0.0;");
            assertThat(leftover(ALEX)).isEqualTo("1000.0/0.0;");
        }
    }

    @Nested
    @DisplayName("config.yml governs the primary currency")
    class Settings {

        @Test
        @DisplayName("a deposit by currency name obeys config.yml, not the primary block of currencies.yml")
        void depositByNameObeysConfigYml() {
            // currencies.yml says: bank disabled, minimum deposit 500, bank cap 1000.
            // config.yml (defaults) says: bank enabled, minimum deposit 100, no cap.
            world.seedAccount(STEVE, "Steve", 5000.0, 0.0);

            assertThat(service.depositToBank(STEVE, 200.0, "coins")).isTrue();
            assertThat(service.depositToBank(STEVE, 2000.0, "coins")).isTrue();
            assertThat(world.account(STEVE).getBank()).isEqualTo(2200.0);

            assertThat(service.depositToBank(STEVE, 50.0, "coins")).as("below config.yml's minimum").isFalse();
            world.config.setMaxBankBalance(2300.0);
            assertThat(service.depositToBank(STEVE, 200.0, "coins")).as("above config.yml's cap").isFalse();
            world.config.setMaxBankBalance(-1);
            world.config.setBankEnabled(false);
            assertThat(service.depositToBank(STEVE, 200.0, "coins")).as("config.yml's bank.enabled: false").isFalse();
            assertThat(world.account(STEVE).getBank()).isEqualTo(2200.0);
        }

        @Test
        @DisplayName("a withdrawal by currency name obeys config.yml's bank.enabled, as the plain /withdraw does")
        void withdrawByNameObeysBankEnabled() {
            world.seedAccount(STEVE, "Steve", 0.0, 500.0);
            world.config.setBankEnabled(false);

            assertThat(service.withdrawFromBank(STEVE, 100.0, "coins")).isFalse();
            assertThat(world.account(STEVE).getBank()).isEqualTo(500.0);

            // Control: with the bank enabled the same call moves the money.
            world.config.setBankEnabled(true);
            assertThat(service.withdrawFromBank(STEVE, 100.0, "coins")).isTrue();
            assertThat(world.account(STEVE).getBank()).isEqualTo(400.0);
            assertThat(world.account(STEVE).getCash()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("creating a primary-currency balance by name creates only the account, with config.yml's starting cash")
        void getOrCreateBalanceByNameCreatesOnlyTheAccount() {
            CurrencyBalanceEntity view = service.getOrCreateBalance(STEVE, "Steve", "coins");

            assertThat(view.getCash()).isEqualTo(1000.0);
            assertThat(world.account(STEVE).getCash()).isEqualTo(1000.0);
            assertThat(world.account(STEVE).getPlayerName()).isEqualTo("Steve");
            assertThat(world.balanceRows("coins")).isEmpty();

            service.getOrCreateBalance(STEVE, "Steve", "coins");
            assertThat(world.accounts.getAll()).hasSize(1);
            assertThat(world.account(STEVE).getCash()).isEqualTo(1000.0);
        }
    }

    @Nested
    @DisplayName("other currencies")
    class OtherCurrencies {

        @Test
        @DisplayName("a non-primary currency keeps its own wallet and never touches the account")
        void nonPrimaryKeepsItsOwnWallet() {
            world.seedAccount(STEVE, "Steve", 1000.0, 0.0);

            service.getOrCreateBalance(STEVE, "Steve", "gems");
            assertThat(service.addCash(STEVE, 10.0, "gems")).isTrue();
            assertThat(service.depositToBank(STEVE, 4.0, "gems")).isTrue();

            List<CurrencyBalanceEntity> gems = world.balanceRows("gems");
            assertThat(gems).hasSize(1);
            assertThat(gems.get(0).getCash()).isEqualTo(11.0);
            assertThat(gems.get(0).getBank()).isEqualTo(4.0);
            assertThat(world.account(STEVE).getCash()).isEqualTo(1000.0);
            assertThat(world.account(STEVE).getBank()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("leaderboard and placeholders")
    class Placeholders {

        @Test
        @DisplayName("%ultieconomy_coins_*% and the coins leaderboard read the account wallet")
        void currencyScopedPlaceholdersReadTheAccountWallet() {
            world.seedAccount(STEVE, "Steve", 5000.0, 0.0);
            world.seedAccount(ALEX, "Alex", 100.0, 0.0);
            // The leftover rows rank the other way round.
            world.seedBalance(STEVE, "coins", 0.0, 0.0);
            world.seedBalance(ALEX, "coins", 9000.0, 0.0);
            LeaderboardService leaderboard = world.leaderboard();
            leaderboard.refreshAll();
            EconomyPlaceholderExpansion expansion =
                    new EconomyPlaceholderExpansion(service, leaderboard, world.currencies);
            OfflinePlayer steve = mock(OfflinePlayer.class);
            when(steve.getUniqueId()).thenReturn(STEVE);

            assertThat(expansion.onRequest(steve, "coins_cash")).isEqualTo(expansion.onRequest(steve, "cash"));
            assertThat(expansion.onRequest(steve, "coins_cash")).isEqualTo(String.format("%.2f", 5000.0));
            assertThat(expansion.onRequest(steve, "coins_rank")).isEqualTo("1");
            List<LeaderboardService.LeaderboardEntry> top = leaderboard.getTopPlayers(2, "coins");
            assertThat(top).extracting(LeaderboardService.LeaderboardEntry::getPlayerName)
                    .containsExactly("Steve", "Alex");
            assertThat(top.get(0).getTotalWealth()).isEqualTo(5000.0);
        }
    }
}
