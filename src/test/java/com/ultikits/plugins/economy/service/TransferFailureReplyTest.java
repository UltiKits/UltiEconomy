package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.commands.PayCommand;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.testsupport.SteppedOperator;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** #45: report the observed transfer phase, not a guessed insufficient balance. */
class TransferFailureReplyTest {
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @ParameterizedTest
    @CsvSource({"false,throw", "true,throw", "false,removed", "true,removed",
            "false,unreadable", "true,unreadable", "false,busy", "true,busy"})
    void failedCreditReportsOperationFailedOrBusyAndRefunds(boolean namedCurrency, String fault) {
        EconomyTestWorld world = EconomyTestWorld.relational();
        world.config.setTaxEnabled(false);
        world.seedAccount(STEVE, "Steve", 1000.0, 0.0);
        world.seedAccount(ALEX, "Alex", 0.0, 0.0);
        CurrencyBalanceEntity sender = world.seedBalance(STEVE, "gems", 1000.0, 0.0);
        CurrencyBalanceEntity receiver = world.seedBalance(ALEX, "gems", 0.0, 0.0);
        int[] writes = {0};
        Consumer<String> failCredit = call -> {
            if (!call.startsWith("update")) return;
            int write = ++writes[0];
            if (write == 2 && "throw".equals(fault)) {
                throw new IllegalStateException("connection reset before credit");
            }
            if (write == 2 && "unreadable".equals(fault)) {
                throw new com.ultikits.ultitools.exceptions.DataAccessException(
                        com.ultikits.ultitools.exceptions.ErrorCode.DATA_ENTITY_INVALID,
                        "Failed to access entity fields", new IllegalAccessException("unreadable cash"));
            }
            if (write == 2 && "removed".equals(fault)) {
                if (namedCurrency) world.balances.delById(receiver.getId());
                else world.accounts.delById(world.account(ALEX).getId());
            }
            if ("busy".equals(fault) && write >= 2 && write <= 4) {
                if (namedCurrency) {
                    CurrencyBalanceEntity row = world.balances.getById(receiver.getId());
                    row.setCash(row.getCash() + 1);
                    world.balances.updateIf(row);
                } else {
                    PlayerAccountEntity row = world.account(ALEX);
                    row.setCash(row.getCash() + 1);
                    world.accounts.updateIf(row);
                }
            }
        };
        DataOperator<PlayerAccountEntity> accounts = namedCurrency ? world.accounts
                : new SteppedOperator<>("economy_accounts", world.accounts, failCredit);
        DataOperator<CurrencyBalanceEntity> balances = namedCurrency
                ? new SteppedOperator<>("currency_balances", world.balances, failCredit) : world.balances;
        EconomyServiceImpl service = EconomyServiceImpl.createForTest(world.plugin, accounts,
                world.config, balances, world.currencies);
        UltiEconomy plugin = mock(UltiEconomy.class);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        lenient().when(plugin.getCurrencyManager()).thenReturn(world.currencies);
        Player steve = mock(Player.class);
        Player alex = mock(Player.class);
        when(steve.getUniqueId()).thenReturn(STEVE);
        when(alex.getUniqueId()).thenReturn(ALEX);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer("Alex")).thenReturn(alex);
            bukkit.when(Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class));
            PayCommand pay = new PayCommand(plugin, service);
            if (namedCurrency) pay.onPayWithCurrency(steve, "Alex", "100", "gems");
            else pay.onPay(steve, "Alex", "100");
        }
        verify(steve).sendMessage(ChatColor.RED + ("busy".equals(fault)
                ? "The balance is being changed on another server right now; please try again." : "Operation failed"));
        verify(alex, never()).sendMessage(anyString());
        assertThat(namedCurrency ? world.balances.getById(sender.getId()).getCash()
                : world.account(STEVE).getCash()).isEqualTo(1000.0);
        if (!"removed".equals(fault)) {
            assertThat(namedCurrency ? world.balances.getById(receiver.getId()).getCash()
                    : world.account(ALEX).getCash()).isEqualTo("busy".equals(fault) ? 3.0 : 0.0);
        }
    }

    static Stream<Arguments> failedRefundCases() {
        List<Arguments> cases = new ArrayList<>();
        for (String form : new String[]{"primary", "coins", "gems"}) {
            for (String language : new String[]{"en", "zh"}) {
                for (String creditFault : new String[]{"throw", "busy"}) {
                    for (String refundFault : new String[]{"removed", "throw", "busy"}) {
                        cases.add(Arguments.of(form, language, creditFault, refundFault));
                    }
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}/{1}: credit={2}, refund={3}")
    @MethodSource("failedRefundCases")
    void failedRefundAlwaysWarnsInsteadOfBusy(String form, String language, String creditFault, String refundFault) {
        TransferScenario scenario = new TransferScenario(form, language, creditFault, refundFault);
        scenario.pay("100");

        String warning = "en".equals(language)
                ? "Transfer not completed. Please contact an administrator to check your balance."
                : "转账未完成。请联系管理员核查你的余额。";
        verify(scenario.steve).sendMessage(ChatColor.RED + warning);
        verify(scenario.alex, never()).sendMessage(anyString());
        assertThat(scenario.receipt.isSuccess()).isFalse();
        assertThat(scenario.receipt.isCreditFailed()).isTrue();
        assertThat(scenario.receipt.isRefundFailed()).isTrue();
        assertThat(scenario.receipt.getCredited()).isZero();
        assertThat(scenario.receipt.getTax()).isZero();
        if ("removed".equals(refundFault)) {
            assertThat(scenario.senderCash()).isNull();
        } else {
            assertThat(scenario.senderCash()).isEqualTo("busy".equals(refundFault) ? 903.0 : 900.0);
        }
        assertThat(scenario.receiverCash()).isEqualTo("busy".equals(creditFault) ? 3.0 : 0.0);

        org.mockito.ArgumentCaptor<String> lines = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(scenario.world.logger, atLeastOnce()).error(lines.capture());
        assertThat(lines.getAllValues()).anySatisfy(line -> assertThat(line)
                .contains("Steve").contains(STEVE.toString()).contains("Alex").contains(ALEX.toString())
                .contains("100.00").contains(scenario.namedCurrency ? "gems" : "coins")
                .contains("en".equals(language) ? "refund could not be written either" : "退款也未能写入"));
    }

    static Stream<Arguments> unchangedReplyCases() {
        List<Arguments> cases = new ArrayList<>();
        for (String form : new String[]{"primary", "coins", "gems"}) {
            for (String language : new String[]{"en", "zh"}) {
                for (String outcome : new String[]{"success", "insufficient", "throw", "busy"}) {
                    cases.add(Arguments.of(form, language, outcome));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}/{1}: unchanged {2}")
    @MethodSource("unchangedReplyCases")
    void refundedAndOtherTransfersKeepExistingReplies(String form, String language, String outcome) {
        TransferScenario scenario = new TransferScenario(form, language, outcome, "success");
        scenario.pay("insufficient".equals(outcome) ? "1001" : "100");

        boolean success = "success".equals(outcome);
        String formatted = "primary".equals(form) ? scenario.service.formatAmount(100.0)
                : scenario.service.formatAmount(100.0, form);
        String expected = success
                ? ChatColor.GREEN + String.format(CatalogueText.text(language, "economy.pay.sent"), formatted, "Alex")
                : ChatColor.RED + CatalogueText.text(language, "busy".equals(outcome) ? "economy.error.busy"
                : "throw".equals(outcome) ? "economy.error.operation_failed" : "economy.error.insufficient_balance");
        verify(scenario.steve).sendMessage(expected);
        if (success) {
            verify(scenario.alex).sendMessage(ChatColor.GREEN
                    + String.format(CatalogueText.text(language, "economy.pay.received"), "Steve", formatted));
        } else {
            verify(scenario.alex, never()).sendMessage(anyString());
        }
        assertThat(scenario.receipt.isSuccess()).isEqualTo(success);
        assertThat(scenario.receipt.isRefundFailed()).isFalse();
        assertThat(scenario.receipt.isCreditFailed()).isEqualTo("throw".equals(outcome) || "busy".equals(outcome));
        assertThat(scenario.senderCash()).isEqualTo(success ? 900.0 : 1000.0);
        assertThat(scenario.receiverCash()).isEqualTo(success ? 100.0 : "busy".equals(outcome) ? 3.0 : 0.0);
        verify(scenario.world.logger, never()).error(contains("en".equals(language)
                ? "refund could not be written either" : "退款也未能写入"));
    }

    /** Deterministic pre-write failures; never simulates the documented lost-acknowledgement case. */
    private static final class TransferScenario {
        final EconomyTestWorld world = EconomyTestWorld.relational();
        final boolean namedCurrency;
        final String form;
        final CurrencyBalanceEntity sender;
        final CurrencyBalanceEntity receiver;
        final EconomyServiceImpl service;
        final PayCommand command;
        final Player steve = mock(Player.class);
        final Player alex = mock(Player.class);
        TransferReceipt receipt;

        TransferScenario(String form, String language, String creditFault, String refundFault) {
            this.form = form;
            this.namedCurrency = "gems".equals(form);
            world.config.setTaxEnabled(false);
            world.seedAccount(STEVE, "Steve", 1000.0, 0.0);
            world.seedAccount(ALEX, "Alex", 0.0, 0.0);
            sender = world.seedBalance(STEVE, "gems", 1000.0, 0.0);
            receiver = world.seedBalance(ALEX, "gems", 0.0, 0.0);
            when(world.plugin.i18n(anyString())).thenAnswer(CatalogueText.answer(language));
            int[] writes = {0};
            int refundStart = "busy".equals(creditFault) ? 5 : 3;
            Consumer<String> failWrites = call -> {
                if (!call.startsWith("update")) return;
                int write = ++writes[0];
                if (write == 2 && "throw".equals(creditFault)) {
                    throw new IllegalStateException("connection reset before credit");
                }
                if (write >= 2 && write <= 4 && "busy".equals(creditFault)) {
                    bumpCash(false);
                }
                if (write >= refundStart) {
                    if ("throw".equals(refundFault)) {
                        throw new IllegalStateException("connection reset before refund");
                    }
                    if ("removed".equals(refundFault) && write == refundStart) {
                        if (namedCurrency) world.balances.delById(sender.getId());
                        else world.accounts.delById(world.account(STEVE).getId());
                    }
                    if ("busy".equals(refundFault)) bumpCash(true);
                }
            };
            DataOperator<PlayerAccountEntity> accounts = namedCurrency ? world.accounts
                    : new SteppedOperator<>("economy_accounts", world.accounts, failWrites);
            DataOperator<CurrencyBalanceEntity> balances = namedCurrency
                    ? new SteppedOperator<>("currency_balances", world.balances, failWrites) : world.balances;
            service = spy(EconomyServiceImpl.createForTest(world.plugin, accounts, world.config, balances, world.currencies));
            if ("primary".equals(form)) {
                doAnswer(inv -> receipt = (TransferReceipt) inv.callRealMethod())
                        .when(service).transferWithReceipt(STEVE, ALEX, 100.0);
                if ("insufficient".equals(creditFault)) {
                    doAnswer(inv -> receipt = (TransferReceipt) inv.callRealMethod())
                            .when(service).transferWithReceipt(STEVE, ALEX, 1001.0);
                }
            } else {
                doAnswer(inv -> receipt = (TransferReceipt) inv.callRealMethod())
                        .when(service).transferWithReceipt(STEVE, ALEX,
                                "insufficient".equals(creditFault) ? 1001.0 : 100.0, form);
            }
            UltiEconomy plugin = mock(UltiEconomy.class);
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer(language));
            lenient().when(plugin.getCurrencyManager()).thenReturn(world.currencies);
            when(steve.getUniqueId()).thenReturn(STEVE);
            when(alex.getUniqueId()).thenReturn(ALEX);
            lenient().when(steve.getName()).thenReturn("Steve");
            lenient().when(alex.getName()).thenReturn("Alex");
            command = new PayCommand(plugin, service);
        }

        void pay(String amount) {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer("Alex")).thenReturn(alex);
                bukkit.when(Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class));
                if ("primary".equals(form)) command.onPay(steve, "Alex", amount);
                else command.onPayWithCurrency(steve, "Alex", amount, form);
            }
        }

        Double senderCash() {
            if (namedCurrency) {
                CurrencyBalanceEntity row = world.balances.getById(sender.getId());
                return row == null ? null : row.getCash();
            }
            PlayerAccountEntity row = world.account(STEVE);
            return row == null ? null : row.getCash();
        }

        double receiverCash() {
            return namedCurrency ? world.balances.getById(receiver.getId()).getCash() : world.account(ALEX).getCash();
        }

        private void bumpCash(boolean refund) {
            if (namedCurrency) {
                CurrencyBalanceEntity row = world.balances.getById(refund ? sender.getId() : receiver.getId());
                row.setCash(row.getCash() + 1);
                world.balances.updateIf(row);
            } else {
                PlayerAccountEntity row = world.account(refund ? STEVE : ALEX);
                row.setCash(row.getCash() + 1);
                world.accounts.updateIf(row);
            }
        }
    }
}
