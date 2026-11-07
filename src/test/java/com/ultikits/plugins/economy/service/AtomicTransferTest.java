package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.impl.data.json.SimpleJsonDataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** Real framework JSON transactions, not an in-memory imitation of their commit semantics. */
class AtomicTransferTest {
    private static final UUID FROM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TO = UUID.fromString("00000000-0000-0000-0000-000000000002");
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulTransferSurvivesReopeningWithoutAFlush(boolean currency) throws Exception {
        Fixture f = new Fixture(currency);
        assertThat(f.transfer()).isTrue();
        f.assertCash(900, 100);
        f.reopen();
        f.assertCash(900, 100);
    }

    @ParameterizedTest
    @CsvSource({"false,REFUSE", "true,REFUSE", "false,BEFORE", "true,BEFORE", "false,AFTER", "true,AFTER"})
    void failedCreditRollsBackBothWalletsWithoutARefund(boolean currency, String fault) throws Exception {
        Fixture f = new Fixture(currency);
        f.fault(fault);
        assertThat(f.transfer()).isFalse();
        f.assertCash(1000, 0);
        f.reopen();
        f.assertCash(1000, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fatalInterruptionBeforeCreditRollsBackTheDebit(boolean currency) throws Exception {
        Fixture f = new Fixture(currency);
        f.fault("FATAL");
        assertThatThrownBy(f::transfer).isInstanceOf(AssertionError.class);
        f.assertCash(1000, 0);
        f.reopen();
        f.assertCash(1000, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unknownCommitOutcomeIsNotRefundedOrRetried(boolean currency) throws Exception {
        Fixture f = new Fixture(currency);
        // Models a JDBC commit whose acknowledgement is lost: both wallets have committed.
        if (currency) {
            doAnswer(call -> {
                call.callRealMethod();
                throw new IllegalStateException("commit acknowledgement lost");
            }).when(f.balances).transaction(any(Callable.class));
        } else {
            doAnswer(call -> {
                call.callRealMethod();
                throw new IllegalStateException("commit acknowledgement lost");
            }).when(f.accounts).transaction(any(Callable.class));
        }
        assertThatThrownBy(f::transfer).isInstanceOf(IllegalStateException.class)
                .hasMessage("commit acknowledgement lost");
        f.assertCash(900, 100);
        f.reopen();
        f.assertCash(900, 100);
    }

    private final class Fixture {
        private final boolean currency;
        private SimpleJsonDataOperator<PlayerAccountEntity> accounts;
        private SimpleJsonDataOperator<CurrencyBalanceEntity> balances;
        private EconomyServiceImpl service;

        Fixture(boolean currency) throws Exception {
            this.currency = currency;
            reopen();
            accounts.transaction((Callable<Void>) () -> {
                accounts.insert(PlayerAccountEntity.builder().uuid(FROM.toString()).playerName("Sender")
                        .cash(1000).bank(0).build());
                accounts.insert(PlayerAccountEntity.builder().uuid(TO.toString()).playerName("Receiver")
                        .cash(0).bank(0).build());
                return null;
            });
            balances.transaction((Callable<Void>) () -> {
                balances.insert(CurrencyBalanceEntity.builder().uuid(FROM.toString()).currencyId("gems")
                        .cash(1000).bank(0).build());
                balances.insert(CurrencyBalanceEntity.builder().uuid(TO.toString()).currencyId("gems")
                        .cash(0).bank(0).build());
                return null;
            });
        }

        void reopen() {
            accounts = spy(new SimpleJsonDataOperator<>(directory.resolve("accounts").toString(), PlayerAccountEntity.class));
            balances = spy(new SimpleJsonDataOperator<>(directory.resolve("balances").toString(), CurrencyBalanceEntity.class));
            UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
            when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
            EconomyConfig config = new EconomyConfig();
            config.setTaxEnabled(false);
            CurrencyManager currencies = new CurrencyManager(YamlConfiguration.loadConfiguration(
                    new StringReader(EconomyTestWorld.CURRENCIES_YAML)));
            service = EconomyServiceImpl.createForTest(plugin, accounts, config, balances, currencies);
        }

        boolean transfer() {
            return currency ? service.transfer(FROM, TO, 100, "gems") : service.transfer(FROM, TO, 100);
        }

        void assertCash(double sender, double receiver) {
            assertThat(currency ? service.getCash(FROM, "gems") : service.getCash(FROM)).isEqualTo(sender);
            assertThat(currency ? service.getCash(TO, "gems") : service.getCash(TO)).isEqualTo(receiver);
        }

        void fault(String fault) {
            if (currency) {
                intercept(balances, service.getBalance(TO, "gems").getId(), fault);
            } else {
                intercept(accounts, service.getAccount(TO).getId(), fault);
            }
        }
    }

    private static <T extends BaseDataEntity<String>> void intercept(
            SimpleJsonDataOperator<T> operator, String receiverId, String fault) {
        doAnswer(call -> {
            T row = call.getArgument(0);
            if (!receiverId.equals(row.getId())) {
                return call.callRealMethod();
            }
            if ("REFUSE".equals(fault)) {
                return false;
            }
            if ("FATAL".equals(fault)) {
                throw new AssertionError("interrupted before credit");
            }
            if ("AFTER".equals(fault)) {
                call.callRealMethod();
            }
            throw new IllegalStateException("credit failed " + fault);
        }).when(operator).updateIf(any(), any(WhereCondition[].class));
    }
}
