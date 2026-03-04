package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.model.CurrencyDefinition;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringReader;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EconomyServiceImpl Tests")
class EconomyServiceImplTest {

    @Mock private UltiToolsPlugin plugin;
    @Mock private DataOperator<PlayerAccountEntity> dataOperator;
    @Mock private DataOperator<CurrencyBalanceEntity> currencyDataOperator;
    @Mock private Query<PlayerAccountEntity> query;
    @Mock private Query<CurrencyBalanceEntity> currencyQuery;

    private EconomyConfig config;
    private CurrencyManager currencyManager;
    private EconomyServiceImpl service;

    private static final UUID PLAYER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID OTHER_UUID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

    private static final String CURRENCIES_YAML =
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

    @BeforeEach
    void setUp() {
        config = new EconomyConfig();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader(CURRENCIES_YAML));
        currencyManager = new CurrencyManager(yaml);
        service = EconomyServiceImpl.createForTest(plugin, dataOperator, config, currencyDataOperator, currencyManager);
    }

    private void mockQueryReturns(UUID uuid, PlayerAccountEntity account) {
        when(dataOperator.query()).thenReturn(query);
        when(query.where("uuid")).thenReturn(query);
        when(query.eq(uuid.toString())).thenReturn(query);
        List<PlayerAccountEntity> results = account != null
                ? Collections.singletonList(account)
                : Collections.emptyList();
        when(query.list()).thenReturn(results);
    }

    private void mockCurrencyQueryReturns(UUID uuid, String currencyId, CurrencyBalanceEntity balance) {
        when(currencyDataOperator.query()).thenReturn(currencyQuery);
        when(currencyQuery.where("uuid")).thenReturn(currencyQuery);
        when(currencyQuery.eq(uuid.toString())).thenReturn(currencyQuery);
        when(currencyQuery.and("currency_id")).thenReturn(currencyQuery);
        when(currencyQuery.eq(currencyId)).thenReturn(currencyQuery);
        List<CurrencyBalanceEntity> results = balance != null
                ? Collections.singletonList(balance)
                : Collections.emptyList();
        when(currencyQuery.list()).thenReturn(results);
    }

    private PlayerAccountEntity makeAccount(UUID uuid, String name, double cash, double bank) {
        return PlayerAccountEntity.builder()
                .uuid(uuid.toString())
                .playerName(name)
                .cash(cash)
                .bank(bank)
                .build();
    }

    @Nested
    @DisplayName("Account Operations")
    class AccountOps {

        @Test
        @DisplayName("getAccount returns existing account")
        void getAccountReturnsExisting() {
            PlayerAccountEntity account = makeAccount(PLAYER_UUID, "Steve", 1000, 500);
            mockQueryReturns(PLAYER_UUID, account);

            PlayerAccountEntity result = service.getAccount(PLAYER_UUID);
            assertThat(result).isNotNull();
            assertThat(result.getCash()).isEqualTo(1000.0);
        }

        @Test
        @DisplayName("getAccount returns null for non-existent")
        void getAccountReturnsNull() {
            mockQueryReturns(PLAYER_UUID, null);
            assertThat(service.getAccount(PLAYER_UUID)).isNull();
        }

        @Test
        @DisplayName("hasAccount returns true when exists")
        void hasAccountTrue() {
            mockQueryReturns(PLAYER_UUID, makeAccount(PLAYER_UUID, "Steve", 0, 0));
            assertThat(service.hasAccount(PLAYER_UUID)).isTrue();
        }

        @Test
        @DisplayName("hasAccount returns false when not exists")
        void hasAccountFalse() {
            mockQueryReturns(PLAYER_UUID, null);
            assertThat(service.hasAccount(PLAYER_UUID)).isFalse();
        }

        @Test
        @DisplayName("getOrCreateAccount creates new with initial cash")
        void getOrCreateCreatesNew() {
            mockQueryReturns(PLAYER_UUID, null);
            config.setInitialCash(500.0);

            PlayerAccountEntity result = service.getOrCreateAccount(PLAYER_UUID, "Steve");
            assertThat(result).isNotNull();
            assertThat(result.getCash()).isEqualTo(500.0);
            assertThat(result.getBank()).isEqualTo(0.0);
            verify(dataOperator).insert(any(PlayerAccountEntity.class));
        }

        @Test
        @DisplayName("getOrCreateAccount returns existing")
        void getOrCreateReturnsExisting() {
            PlayerAccountEntity existing = makeAccount(PLAYER_UUID, "Steve", 1000, 500);
            mockQueryReturns(PLAYER_UUID, existing);

            PlayerAccountEntity result = service.getOrCreateAccount(PLAYER_UUID, "Steve");
            assertThat(result.getCash()).isEqualTo(1000.0);
            verify(dataOperator, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("Cash Operations")
    class CashOps {

        @Test
        @DisplayName("getCash returns balance")
        void getCash() {
            mockQueryReturns(PLAYER_UUID, makeAccount(PLAYER_UUID, "Steve", 750.5, 0));
            assertThat(service.getCash(PLAYER_UUID)).isEqualTo(750.5);
        }

        @Test
        @DisplayName("getCash returns 0 for non-existent")
        void getCashNonExistent() {
            mockQueryReturns(PLAYER_UUID, null);
            assertThat(service.getCash(PLAYER_UUID)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("setCash updates balance")
        void setCash() throws Exception {
            PlayerAccountEntity account = makeAccount(PLAYER_UUID, "Steve", 1000, 0);
            mockQueryReturns(PLAYER_UUID, account);

            boolean result = service.setCash(PLAYER_UUID, 2000);
            assertThat(result).isTrue();
            assertThat(account.getCash()).isEqualTo(2000.0);
            verify(dataOperator).update(account);
        }

        @Test
        @DisplayName("setCash rejects negative")
        void setCashNegative() {
            assertThat(service.setCash(PLAYER_UUID, -100)).isFalse();
        }

        @Test
        @DisplayName("setCash fails for non-existent account")
        void setCashNonExistent() {
            mockQueryReturns(PLAYER_UUID, null);
            assertThat(service.setCash(PLAYER_UUID, 100)).isFalse();
        }

        @Test
        @DisplayName("addCash increases balance")
        void addCash() throws Exception {
            PlayerAccountEntity account = makeAccount(PLAYER_UUID, "Steve", 1000, 0);
            mockQueryReturns(PLAYER_UUID, account);

            assertThat(service.addCash(PLAYER_UUID, 500)).isTrue();
            assertThat(account.getCash()).isEqualTo(1500.0);
            verify(dataOperator).update(account);
        }

        @Test
        @DisplayName("addCash rejects zero or negative")
        void addCashInvalid() {
            assertThat(service.addCash(PLAYER_UUID, 0)).isFalse();
            assertThat(service.addCash(PLAYER_UUID, -10)).isFalse();
        }

        @Test
        @DisplayName("addCash fails for non-existent account")
        void addCashNonExistent() {
            mockQueryReturns(PLAYER_UUID, null);
            assertThat(service.addCash(PLAYER_UUID, 100)).isFalse();
        }

        @Test
        @DisplayName("takeCash decreases balance")
        void takeCash() throws Exception {
            PlayerAccountEntity account = makeAccount(PLAYER_UUID, "Steve", 1000, 0);
            mockQueryReturns(PLAYER_UUID, account);

            assertThat(service.takeCash(PLAYER_UUID, 300)).isTrue();
            assertThat(account.getCash()).isEqualTo(700.0);
            verify(dataOperator).update(account);
        }

        @Test
        @DisplayName("takeCash fails on insufficient funds")
        void takeCashInsufficient() {
            mockQueryReturns(PLAYER_UUID, makeAccount(PLAYER_UUID, "Steve", 100, 0));
            assertThat(service.takeCash(PLAYER_UUID, 500)).isFalse();
        }

        @Test
        @DisplayName("takeCash rejects zero or negative")
        void takeCashInvalid() {
            assertThat(service.takeCash(PLAYER_UUID, 0)).isFalse();
            assertThat(service.takeCash(PLAYER_UUID, -10)).isFalse();
        }
    }

    @Nested
    @DisplayName("Bank Operations")
    class BankOps {

        @Test
        @DisplayName("getBank returns balance")
        void getBank() {
            mockQueryReturns(PLAYER_UUID, makeAccount(PLAYER_UUID, "Steve", 0, 2500));
            assertThat(service.getBank(PLAYER_UUID)).isEqualTo(2500.0);
        }

        @Test
        @DisplayName("getBank returns 0 for non-existent")
        void getBankNonExistent() {
            mockQueryReturns(PLAYER_UUID, null);
            assertThat(service.getBank(PLAYER_UUID)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("setBank updates balance")
        void setBank() throws Exception {
            PlayerAccountEntity account = makeAccount(PLAYER_UUID, "Steve", 0, 500);
            mockQueryReturns(PLAYER_UUID, account);

            assertThat(service.setBank(PLAYER_UUID, 1000)).isTrue();
            assertThat(account.getBank()).isEqualTo(1000.0);
            verify(dataOperator).update(account);
        }

        @Test
        @DisplayName("setBank rejects negative")
        void setBankNegative() {
            assertThat(service.setBank(PLAYER_UUID, -100)).isFalse();
        }

        @Test
        @DisplayName("addBank increases balance")
        void addBank() throws Exception {
            PlayerAccountEntity account = makeAccount(PLAYER_UUID, "Steve", 0, 500);
            mockQueryReturns(PLAYER_UUID, account);

            assertThat(service.addBank(PLAYER_UUID, 200)).isTrue();
            assertThat(account.getBank()).isEqualTo(700.0);
            verify(dataOperator).update(account);
        }

        @Test
        @DisplayName("takeBank decreases balance")
        void takeBank() throws Exception {
            PlayerAccountEntity account = makeAccount(PLAYER_UUID, "Steve", 0, 500);
            mockQueryReturns(PLAYER_UUID, account);

            assertThat(service.takeBank(PLAYER_UUID, 200)).isTrue();
            assertThat(account.getBank()).isEqualTo(300.0);
            verify(dataOperator).update(account);
        }

        @Test
        @DisplayName("takeBank fails on insufficient funds")
        void takeBankInsufficient() {
            mockQueryReturns(PLAYER_UUID, makeAccount(PLAYER_UUID, "Steve", 0, 50));
            assertThat(service.takeBank(PLAYER_UUID, 100)).isFalse();
        }

        @Test
        @DisplayName("depositToBank moves cash to bank")
        void depositToBank() throws Exception {
            PlayerAccountEntity account = makeAccount(PLAYER_UUID, "Steve", 1000, 500);
            mockQueryReturns(PLAYER_UUID, account);

            assertThat(service.depositToBank(PLAYER_UUID, 300)).isTrue();
            assertThat(account.getCash()).isEqualTo(700.0);
            assertThat(account.getBank()).isEqualTo(800.0);
        }

        @Test
        @DisplayName("depositToBank fails on insufficient cash")
        void depositToBankInsufficient() {
            mockQueryReturns(PLAYER_UUID, makeAccount(PLAYER_UUID, "Steve", 50, 0));
            assertThat(service.depositToBank(PLAYER_UUID, 100)).isFalse();
        }

        @Test
        @DisplayName("depositToBank enforces minimum deposit")
        void depositToBankMinimum() {
            config.setMinDeposit(100.0);
            assertThat(service.depositToBank(PLAYER_UUID, 50)).isFalse();
        }

        @Test
        @DisplayName("depositToBank enforces max bank balance")
        void depositToBankMaxBalance() {
            config.setMaxBankBalance(1000.0);
            mockQueryReturns(PLAYER_UUID, makeAccount(PLAYER_UUID, "Steve", 5000, 900));
            assertThat(service.depositToBank(PLAYER_UUID, 200)).isFalse();
        }

        @Test
        @DisplayName("depositToBank allows unlimited when max is -1")
        void depositToBankUnlimited() throws Exception {
            config.setMaxBankBalance(-1);
            PlayerAccountEntity account = makeAccount(PLAYER_UUID, "Steve", 1000000, 999999);
            mockQueryReturns(PLAYER_UUID, account);
            assertThat(service.depositToBank(PLAYER_UUID, 500)).isTrue();
        }

        @Test
        @DisplayName("withdrawFromBank moves bank to cash")
        void withdrawFromBank() throws Exception {
            PlayerAccountEntity account = makeAccount(PLAYER_UUID, "Steve", 100, 500);
            mockQueryReturns(PLAYER_UUID, account);

            assertThat(service.withdrawFromBank(PLAYER_UUID, 200)).isTrue();
            assertThat(account.getCash()).isEqualTo(300.0);
            assertThat(account.getBank()).isEqualTo(300.0);
        }

        @Test
        @DisplayName("withdrawFromBank fails on insufficient bank")
        void withdrawInsufficient() {
            mockQueryReturns(PLAYER_UUID, makeAccount(PLAYER_UUID, "Steve", 0, 50));
            assertThat(service.withdrawFromBank(PLAYER_UUID, 100)).isFalse();
        }

        @Test
        @DisplayName("withdrawFromBank rejects zero or negative")
        void withdrawInvalid() {
            assertThat(service.withdrawFromBank(PLAYER_UUID, 0)).isFalse();
            assertThat(service.withdrawFromBank(PLAYER_UUID, -10)).isFalse();
        }

        @Test
        @DisplayName("setBank fails for non-existent account")
        void setBankNonExistent() {
            mockQueryReturns(PLAYER_UUID, null);
            assertThat(service.setBank(PLAYER_UUID, 100)).isFalse();
        }

        @Test
        @DisplayName("addBank rejects zero or negative")
        void addBankInvalid() {
            assertThat(service.addBank(PLAYER_UUID, 0)).isFalse();
            assertThat(service.addBank(PLAYER_UUID, -10)).isFalse();
        }

        @Test
        @DisplayName("addBank fails for non-existent account")
        void addBankNonExistent() {
            mockQueryReturns(PLAYER_UUID, null);
            assertThat(service.addBank(PLAYER_UUID, 100)).isFalse();
        }

        @Test
        @DisplayName("takeBank rejects zero or negative")
        void takeBankInvalid() {
            assertThat(service.takeBank(PLAYER_UUID, 0)).isFalse();
            assertThat(service.takeBank(PLAYER_UUID, -10)).isFalse();
        }

        @Test
        @DisplayName("depositToBank rejects zero or negative")
        void depositToBankInvalid() {
            assertThat(service.depositToBank(PLAYER_UUID, 0)).isFalse();
            assertThat(service.depositToBank(PLAYER_UUID, -10)).isFalse();
        }
    }

    @Nested
    @DisplayName("Transfer Operations")
    class TransferOps {

        @Test
        @DisplayName("transfer moves cash between players")
        void transferSuccess() throws Exception {
            PlayerAccountEntity sender = makeAccount(PLAYER_UUID, "Steve", 1000, 0);
            PlayerAccountEntity receiver = makeAccount(OTHER_UUID, "Alex", 200, 0);

            when(dataOperator.query()).thenReturn(query);
            when(query.where("uuid")).thenReturn(query);
            when(query.eq(PLAYER_UUID.toString())).thenReturn(query);
            when(query.eq(OTHER_UUID.toString())).thenReturn(query);
            when(query.list())
                    .thenReturn(Collections.singletonList(sender))
                    .thenReturn(Collections.singletonList(receiver));

            assertThat(service.transfer(PLAYER_UUID, OTHER_UUID, 300)).isTrue();
            assertThat(sender.getCash()).isEqualTo(700.0);
            assertThat(receiver.getCash()).isEqualTo(500.0);
        }

        @Test
        @DisplayName("transfer fails on insufficient funds")
        void transferInsufficient() {
            mockQueryReturns(PLAYER_UUID, makeAccount(PLAYER_UUID, "Steve", 100, 0));
            assertThat(service.transfer(PLAYER_UUID, OTHER_UUID, 500)).isFalse();
        }

        @Test
        @DisplayName("transfer rejects zero or negative")
        void transferInvalid() {
            assertThat(service.transfer(PLAYER_UUID, OTHER_UUID, 0)).isFalse();
            assertThat(service.transfer(PLAYER_UUID, OTHER_UUID, -50)).isFalse();
        }

        @Test
        @DisplayName("transfer rejects self-transfer")
        void transferSelf() {
            assertThat(service.transfer(PLAYER_UUID, PLAYER_UUID, 100)).isFalse();
        }

        @Test
        @DisplayName("transfer fails when receiver not found")
        void transferReceiverNotFound() {
            PlayerAccountEntity sender = makeAccount(PLAYER_UUID, "Steve", 1000, 0);

            when(dataOperator.query()).thenReturn(query);
            when(query.where("uuid")).thenReturn(query);
            when(query.eq(PLAYER_UUID.toString())).thenReturn(query);
            when(query.eq(OTHER_UUID.toString())).thenReturn(query);
            when(query.list())
                    .thenReturn(Collections.singletonList(sender))
                    .thenReturn(Collections.emptyList());

            assertThat(service.transfer(PLAYER_UUID, OTHER_UUID, 300)).isFalse();
        }

        @Test
        @DisplayName("transfer applies transaction tax when TaxService present")
        void transferWithTax() throws Exception {
            TaxService taxService = mock(TaxService.class);
            when(taxService.calculateTransactionTax(100.0)).thenReturn(5.0);
            service.setTaxService(taxService);

            PlayerAccountEntity sender = makeAccount(PLAYER_UUID, "Steve", 1000, 0);
            PlayerAccountEntity receiver = makeAccount(OTHER_UUID, "Alex", 0, 0);

            when(dataOperator.query()).thenReturn(query);
            when(query.where("uuid")).thenReturn(query);
            when(query.eq(PLAYER_UUID.toString())).thenReturn(query);
            when(query.eq(OTHER_UUID.toString())).thenReturn(query);
            when(query.list())
                    .thenReturn(Collections.singletonList(sender))
                    .thenReturn(Collections.singletonList(receiver));

            boolean result = service.transfer(PLAYER_UUID, OTHER_UUID, 100);
            assertThat(result).isTrue();
            assertThat(sender.getCash()).isEqualTo(900.0);
            assertThat(receiver.getCash()).isEqualTo(95.0);
            verify(taxService).depositToTreasury(5.0, "coins");
        }

        @Test
        @DisplayName("transfer with currency applies transaction tax")
        void transferWithCurrencyTax() throws Exception {
            TaxService taxService = mock(TaxService.class);
            when(taxService.calculateTransactionTax(200.0)).thenReturn(10.0);
            service.setTaxService(taxService);

            CurrencyBalanceEntity sender = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("gems").cash(500.0).bank(0.0).build();
            CurrencyBalanceEntity receiver = CurrencyBalanceEntity.builder()
                    .uuid(OTHER_UUID.toString()).currencyId("gems").cash(100.0).bank(0.0).build();

            when(currencyDataOperator.query()).thenReturn(currencyQuery);
            when(currencyQuery.where("uuid")).thenReturn(currencyQuery);
            when(currencyQuery.eq(PLAYER_UUID.toString())).thenReturn(currencyQuery);
            when(currencyQuery.eq(OTHER_UUID.toString())).thenReturn(currencyQuery);
            when(currencyQuery.and("currency_id")).thenReturn(currencyQuery);
            when(currencyQuery.eq("gems")).thenReturn(currencyQuery);
            when(currencyQuery.list())
                    .thenReturn(Collections.singletonList(sender))
                    .thenReturn(Collections.singletonList(receiver));

            boolean result = service.transfer(PLAYER_UUID, OTHER_UUID, 200.0, "gems");
            assertThat(result).isTrue();
            assertThat(sender.getCash()).isEqualTo(300.0);
            assertThat(receiver.getCash()).isEqualTo(290.0);
            verify(taxService).depositToTreasury(10.0, "gems");
        }
    }

    @Nested
    @DisplayName("Format Operations")
    class FormatOps {

        @Test
        @DisplayName("formatAmount uses currency symbol")
        void formatAmount() {
            config.setCurrencySymbol("$");
            assertThat(service.formatAmount(1234.56)).isEqualTo("$1,234.56");
        }

        @Test
        @DisplayName("formatAmount handles zero")
        void formatZero() {
            config.setCurrencySymbol("$");
            assertThat(service.formatAmount(0)).isEqualTo("$0.00");
        }

        @Test
        @DisplayName("formatAmount handles large numbers")
        void formatLarge() {
            config.setCurrencySymbol("$");
            assertThat(service.formatAmount(1000000.0)).isEqualTo("$1,000,000.00");
        }
    }

    @Nested
    @DisplayName("Total Wealth")
    class WealthOps {

        @Test
        @DisplayName("getTotalWealth sums cash and bank")
        void getTotalWealth() {
            mockQueryReturns(PLAYER_UUID, makeAccount(PLAYER_UUID, "Steve", 1000, 2000));
            assertThat(service.getTotalWealth(PLAYER_UUID)).isEqualTo(3000.0);
        }

        @Test
        @DisplayName("getTotalWealth returns 0 for non-existent")
        void getTotalWealthNonExistent() {
            mockQueryReturns(PLAYER_UUID, null);
            assertThat(service.getTotalWealth(PLAYER_UUID)).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Update Failure Handling")
    class UpdateFailures {

        @Test
        @DisplayName("setCash returns false on update exception")
        void setCashUpdateFails() throws Exception {
            PluginLogger logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);

            PlayerAccountEntity account = makeAccount(PLAYER_UUID, "Steve", 1000, 0);
            mockQueryReturns(PLAYER_UUID, account);
            doThrow(new IllegalAccessException("test")).when(dataOperator).update(any());

            assertThat(service.setCash(PLAYER_UUID, 2000)).isFalse();
            verify(logger).error(anyString());
        }
    }

    @Nested
    @DisplayName("Multi-Currency Operations")
    class MultiCurrencyOps {

        @Test
        @DisplayName("getCash with currencyId queries correct currency")
        void getCashWithCurrency() {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString())
                    .currencyId("gems")
                    .cash(250.0)
                    .bank(0.0)
                    .build();
            mockCurrencyQueryReturns(PLAYER_UUID, "gems", balance);

            assertThat(service.getCash(PLAYER_UUID, "gems")).isEqualTo(250.0);
        }

        @Test
        @DisplayName("getCash returns 0 for non-existent balance")
        void getCashNonExistent() {
            mockCurrencyQueryReturns(PLAYER_UUID, "gems", null);
            assertThat(service.getCash(PLAYER_UUID, "gems")).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getBank with currencyId queries correct currency")
        void getBankWithCurrency() {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString())
                    .currencyId("coins")
                    .cash(100.0)
                    .bank(500.0)
                    .build();
            mockCurrencyQueryReturns(PLAYER_UUID, "coins", balance);

            assertThat(service.getBank(PLAYER_UUID, "coins")).isEqualTo(500.0);
        }

        @Test
        @DisplayName("transfer with currencyId moves correct currency")
        void transferWithCurrency() throws Exception {
            CurrencyBalanceEntity sender = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("gems").cash(500.0).bank(0.0).build();
            CurrencyBalanceEntity receiver = CurrencyBalanceEntity.builder()
                    .uuid(OTHER_UUID.toString()).currencyId("gems").cash(100.0).bank(0.0).build();

            when(currencyDataOperator.query()).thenReturn(currencyQuery);
            when(currencyQuery.where("uuid")).thenReturn(currencyQuery);
            when(currencyQuery.eq(PLAYER_UUID.toString())).thenReturn(currencyQuery);
            when(currencyQuery.eq(OTHER_UUID.toString())).thenReturn(currencyQuery);
            when(currencyQuery.and("currency_id")).thenReturn(currencyQuery);
            when(currencyQuery.eq("gems")).thenReturn(currencyQuery);
            when(currencyQuery.list())
                    .thenReturn(Collections.singletonList(sender))
                    .thenReturn(Collections.singletonList(receiver));

            boolean result = service.transfer(PLAYER_UUID, OTHER_UUID, 200.0, "gems");
            assertThat(result).isTrue();
            assertThat(sender.getCash()).isEqualTo(300.0);
            assertThat(receiver.getCash()).isEqualTo(300.0);
        }

        @Test
        @DisplayName("getOrCreateBalance creates new balance for new currency")
        void getOrCreateNew() {
            mockCurrencyQueryReturns(PLAYER_UUID, "gems", null);

            CurrencyBalanceEntity result = service.getOrCreateBalance(PLAYER_UUID, "Steve", "gems");
            assertThat(result).isNotNull();
            assertThat(result.getCurrencyId()).isEqualTo("gems");
            verify(currencyDataOperator).insert(any(CurrencyBalanceEntity.class));
        }

        @Test
        @DisplayName("getOrCreateBalance returns existing balance")
        void getOrCreateExisting() {
            CurrencyBalanceEntity existing = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("gems").cash(250.0).bank(0.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "gems", existing);

            CurrencyBalanceEntity result = service.getOrCreateBalance(PLAYER_UUID, "Steve", "gems");
            assertThat(result.getCash()).isEqualTo(250.0);
            verify(currencyDataOperator, never()).insert(any());
        }

        @Test
        @DisplayName("hasBalance checks existence for currency")
        void hasBalance() {
            mockCurrencyQueryReturns(PLAYER_UUID, "gems", CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("gems").build());
            assertThat(service.hasBalance(PLAYER_UUID, "gems")).isTrue();
        }

        @Test
        @DisplayName("getTotalWealth with currencyId")
        void getTotalWealthCurrency() {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("coins").cash(300.0).bank(700.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "coins", balance);

            assertThat(service.getTotalWealth(PLAYER_UUID, "coins")).isEqualTo(1000.0);
        }

        @Test
        @DisplayName("formatAmount with currencyId uses currency symbol")
        void formatWithCurrency() {
            assertThat(service.formatAmount(1234.56, "gems")).isEqualTo("G1,234.56");
        }

        @Test
        @DisplayName("formatAmount with unknown currency falls back")
        void formatWithUnknown() {
            assertThat(service.formatAmount(100.0, "unknown")).isEqualTo("100.00");
        }

        @Test
        @DisplayName("getPrimaryCurrencyId returns primary")
        void getPrimaryId() {
            assertThat(service.getPrimaryCurrencyId()).isEqualTo("coins");
        }

        @Test
        @DisplayName("setCash with currencyId updates balance")
        void setCashWithCurrency() throws Exception {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("gems").cash(100.0).bank(0.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "gems", balance);

            assertThat(service.setCash(PLAYER_UUID, 500.0, "gems")).isTrue();
            assertThat(balance.getCash()).isEqualTo(500.0);
            verify(currencyDataOperator).update(balance);
        }

        @Test
        @DisplayName("addCash with currencyId increases balance")
        void addCashWithCurrency() throws Exception {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("gems").cash(100.0).bank(0.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "gems", balance);

            assertThat(service.addCash(PLAYER_UUID, 200.0, "gems")).isTrue();
            assertThat(balance.getCash()).isEqualTo(300.0);
            verify(currencyDataOperator).update(balance);
        }

        @Test
        @DisplayName("takeCash with currencyId decreases balance")
        void takeCashWithCurrency() throws Exception {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("gems").cash(500.0).bank(0.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "gems", balance);

            assertThat(service.takeCash(PLAYER_UUID, 200.0, "gems")).isTrue();
            assertThat(balance.getCash()).isEqualTo(300.0);
            verify(currencyDataOperator).update(balance);
        }

        @Test
        @DisplayName("takeCash with currencyId fails on insufficient funds")
        void takeCashInsufficientCurrency() {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("gems").cash(50.0).bank(0.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "gems", balance);

            assertThat(service.takeCash(PLAYER_UUID, 200.0, "gems")).isFalse();
        }

        @Test
        @DisplayName("depositToBank with currencyId")
        void depositToBankCurrency() throws Exception {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("coins").cash(1000.0).bank(500.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "coins", balance);

            assertThat(service.depositToBank(PLAYER_UUID, 300.0, "coins")).isTrue();
            assertThat(balance.getCash()).isEqualTo(700.0);
            assertThat(balance.getBank()).isEqualTo(800.0);
        }

        @Test
        @DisplayName("withdrawFromBank with currencyId")
        void withdrawFromBankCurrency() throws Exception {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("coins").cash(100.0).bank(500.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "coins", balance);

            assertThat(service.withdrawFromBank(PLAYER_UUID, 200.0, "coins")).isTrue();
            assertThat(balance.getCash()).isEqualTo(300.0);
            assertThat(balance.getBank()).isEqualTo(300.0);
        }

        @Test
        @DisplayName("setBank with currencyId updates bank balance")
        void setBankWithCurrency() throws Exception {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("coins").cash(100.0).bank(200.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "coins", balance);

            assertThat(service.setBank(PLAYER_UUID, 800.0, "coins")).isTrue();
            assertThat(balance.getBank()).isEqualTo(800.0);
            verify(currencyDataOperator).update(balance);
        }

        @Test
        @DisplayName("addBank with currencyId increases bank balance")
        void addBankWithCurrency() throws Exception {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("coins").cash(100.0).bank(300.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "coins", balance);

            assertThat(service.addBank(PLAYER_UUID, 200.0, "coins")).isTrue();
            assertThat(balance.getBank()).isEqualTo(500.0);
            verify(currencyDataOperator).update(balance);
        }

        @Test
        @DisplayName("takeBank with currencyId decreases bank balance")
        void takeBankWithCurrency() throws Exception {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("coins").cash(100.0).bank(500.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "coins", balance);

            assertThat(service.takeBank(PLAYER_UUID, 200.0, "coins")).isTrue();
            assertThat(balance.getBank()).isEqualTo(300.0);
            verify(currencyDataOperator).update(balance);
        }

        @Test
        @DisplayName("takeBank with currencyId fails on insufficient funds")
        void takeBankInsufficientCurrency() {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("coins").cash(100.0).bank(50.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "coins", balance);

            assertThat(service.takeBank(PLAYER_UUID, 200.0, "coins")).isFalse();
        }

        @Test
        @DisplayName("depositToBank with currencyId fails on insufficient cash")
        void depositToBankCurrencyInsufficient() {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("coins").cash(50.0).bank(0.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "coins", balance);

            assertThat(service.depositToBank(PLAYER_UUID, 200.0, "coins")).isFalse();
        }

        @Test
        @DisplayName("withdrawFromBank with currencyId fails on insufficient bank")
        void withdrawFromBankCurrencyInsufficient() {
            CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("coins").cash(100.0).bank(50.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "coins", balance);

            assertThat(service.withdrawFromBank(PLAYER_UUID, 200.0, "coins")).isFalse();
        }

        @Test
        @DisplayName("addCash for non-existent currency balance returns false")
        void addCashNonExistentCurrency() {
            mockCurrencyQueryReturns(PLAYER_UUID, "gems", null);

            assertThat(service.addCash(PLAYER_UUID, 100.0, "gems")).isFalse();
        }

        @Test
        @DisplayName("setCash for non-existent currency balance returns false")
        void setCashNonExistentCurrency() {
            mockCurrencyQueryReturns(PLAYER_UUID, "gems", null);

            assertThat(service.setCash(PLAYER_UUID, 100.0, "gems")).isFalse();
        }

        @Test
        @DisplayName("setCash with negative amount for currency returns false")
        void setCashNegativeCurrency() {
            assertThat(service.setCash(PLAYER_UUID, -50.0, "gems")).isFalse();
        }

        @Test
        @DisplayName("transfer with currencyId fails on insufficient funds")
        void transferCurrencyInsufficient() {
            CurrencyBalanceEntity sender = CurrencyBalanceEntity.builder()
                    .uuid(PLAYER_UUID.toString()).currencyId("gems").cash(50.0).bank(0.0).build();
            mockCurrencyQueryReturns(PLAYER_UUID, "gems", sender);

            assertThat(service.transfer(PLAYER_UUID, OTHER_UUID, 200.0, "gems")).isFalse();
        }

        @Test
        @DisplayName("transfer with currencyId rejects zero or negative")
        void transferCurrencyInvalid() {
            assertThat(service.transfer(PLAYER_UUID, OTHER_UUID, 0, "gems")).isFalse();
            assertThat(service.transfer(PLAYER_UUID, OTHER_UUID, -50.0, "gems")).isFalse();
        }

        @Test
        @DisplayName("transfer with currencyId rejects self-transfer")
        void transferCurrencySelf() {
            assertThat(service.transfer(PLAYER_UUID, PLAYER_UUID, 100.0, "gems")).isFalse();
        }
    }
}
