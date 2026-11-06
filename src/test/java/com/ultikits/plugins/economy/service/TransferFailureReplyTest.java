package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.commands.PayCommand;
import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.exceptions.ErrorCode;
import com.ultikits.ultitools.interfaces.impl.data.json.SimpleJsonDataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** #45: command feedback after a real framework transaction rolled back, with no refund writes. */
class TransferFailureReplyTest {
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    @TempDir Path directory;

    @Test
    void receiptHasNoUnreachableRefundState() {
        assertThat(TransferReceipt.class.getDeclaredMethods()).extracting(java.lang.reflect.Method::getName)
                .doesNotContain("refundFailed", "isRefundFailed");
        assertThat(TransferReceipt.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .doesNotContain("refundFailed", "REFUND_FAILED");
    }

    static Stream<Arguments> transferCases() {
        List<Arguments> cases = new ArrayList<>();
        for (String form : new String[]{"primary", "coins", "gems"}) {
            for (String language : new String[]{"en", "zh"}) {
                for (String outcome : new String[]{"success", "insufficient", "throw", "after", "removed", "unreadable", "busy"}) {
                    cases.add(Arguments.of(form, language, outcome));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}/{1}: {2}")
    @MethodSource("transferCases")
    void rolledBackCreditReportsOperationFailedOrBusy(String form, String language, String outcome) throws Exception {
        SimpleJsonDataOperator<PlayerAccountEntity> accounts = spy(new SimpleJsonDataOperator<>(
                directory.resolve("accounts").toString(), PlayerAccountEntity.class));
        SimpleJsonDataOperator<CurrencyBalanceEntity> balances = spy(new SimpleJsonDataOperator<>(
                directory.resolve("balances").toString(), CurrencyBalanceEntity.class));
        accounts.transaction((Callable<Void>) () -> {
            accounts.insert(PlayerAccountEntity.builder().uuid(STEVE.toString()).playerName("Steve").cash(1000).bank(0).build());
            accounts.insert(PlayerAccountEntity.builder().uuid(ALEX.toString()).playerName("Alex").cash(0).bank(0).build());
            return null;
        });
        balances.transaction((Callable<Void>) () -> {
            balances.insert(CurrencyBalanceEntity.builder().uuid(STEVE.toString()).currencyId("gems").cash(1000).bank(0).build());
            balances.insert(CurrencyBalanceEntity.builder().uuid(ALEX.toString()).currencyId("gems").cash(0).bank(0).build());
            return null;
        });
        UltiEconomy plugin = mock(UltiEconomy.class);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer(language));
        when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
        EconomyConfig config = new EconomyConfig();
        config.setTaxEnabled(false);
        CurrencyManager currencies = new CurrencyManager(YamlConfiguration.loadConfiguration(
                new StringReader(EconomyTestWorld.CURRENCIES_YAML)));
        when(plugin.getCurrencyManager()).thenReturn(currencies);
        EconomyServiceImpl service = spy(EconomyServiceImpl.createForTest(plugin, accounts, config, balances, currencies));
        TransferReceipt[] observed = {null};
        doAnswer(call -> observed[0] = (TransferReceipt) call.callRealMethod())
                .when(service).transferWithReceipt(eq(STEVE), eq(ALEX), anyDouble());
        doAnswer(call -> observed[0] = (TransferReceipt) call.callRealMethod())
                .when(service).transferWithReceipt(eq(STEVE), eq(ALEX), anyDouble(), anyString());
        boolean named = "gems".equals(form);
        int[] senderWrites = {0};
        if (named) intercept(balances, service.getBalance(ALEX, "gems").getId(), outcome, senderWrites);
        else intercept(accounts, service.getAccount(ALEX).getId(), outcome, senderWrites);
        Player steve = mock(Player.class);
        Player alex = mock(Player.class);
        when(steve.getUniqueId()).thenReturn(STEVE);
        when(alex.getUniqueId()).thenReturn(ALEX);
        lenient().when(steve.getName()).thenReturn("Steve");
        lenient().when(alex.getName()).thenReturn("Alex");
        PayCommand command = new PayCommand(plugin, service);
        double amount = "insufficient".equals(outcome) ? 1001 : 100;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer("Alex")).thenReturn(alex);
            bukkit.when(Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class));
            if ("primary".equals(form)) command.onPay(steve, "Alex", String.valueOf(amount));
            else command.onPayWithCurrency(steve, "Alex", String.valueOf(amount), form);
        }
        boolean success = "success".equals(outcome);
        boolean creditFailed = !success && !"insufficient".equals(outcome);
        String formatted = "primary".equals(form) ? service.formatAmount(100) : service.formatAmount(100, form);
        String expected = success
                ? ChatColor.GREEN + String.format(CatalogueText.text(language, "economy.pay.sent"), formatted, "Alex")
                : ChatColor.RED + CatalogueText.text(language, "busy".equals(outcome) ? "economy.error.busy"
                : creditFailed ? "economy.error.operation_failed" : "economy.error.insufficient_balance");
        // Assert persisted rollback before checking feedback, so RED demonstrates a message defect, not failed atomicity.
        assertThat(named ? service.getCash(STEVE, "gems") : service.getCash(STEVE)).isEqualTo(success ? 900 : 1000);
        assertThat(named ? service.getCash(ALEX, "gems") : service.getCash(ALEX)).isEqualTo(success ? 100 : 0);
        assertThat(senderWrites[0]).isEqualTo("insufficient".equals(outcome) ? 0 : 1);
        verify(steve).sendMessage(expected);
        if (success) verify(alex).sendMessage(ChatColor.GREEN
                + String.format(CatalogueText.text(language, "economy.pay.received"), "Steve", formatted));
        else verify(alex, never()).sendMessage(anyString());

        // The receipt records the failed phase without changing the transaction's rollback or success amounts.
        TransferReceipt receipt = observed[0];
        assertThat(receipt).isNotNull();
        assertThat(receipt.isSuccess()).isEqualTo(success);
        assertThat(receipt.isCreditFailed()).isEqualTo(creditFailed);
        assertThat(receipt.getCredited()).isEqualTo(success ? 100 : 0);
        assertThat(receipt.getTax()).isZero();
    }

    private static <T extends BaseDataEntity<String>> void intercept(SimpleJsonDataOperator<T> operator,
            String receiverId, String outcome, int[] senderWrites) {
        doAnswer(call -> {
            T row = call.getArgument(0);
            if (!receiverId.equals(row.getId())) {
                senderWrites[0]++;
                return call.callRealMethod();
            }
            if ("success".equals(outcome) || "insufficient".equals(outcome)) return call.callRealMethod();
            if ("busy".equals(outcome)) return false; // Lose each conditional attempt, retaining a readable row.
            if ("removed".equals(outcome)) {
                operator.delById(row.getId());
                return false;
            }
            if ("unreadable".equals(outcome)) throw new DataAccessException(ErrorCode.DATA_ENTITY_INVALID,
                    "Failed to access entity fields", new IllegalAccessException("unreadable cash"));
            if ("after".equals(outcome)) call.callRealMethod();
            throw new IllegalStateException("credit failed " + outcome);
        }).when(operator).updateIf(any(), any(WhereCondition[].class));
    }
}
