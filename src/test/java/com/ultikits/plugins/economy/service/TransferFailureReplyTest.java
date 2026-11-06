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
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.function.Consumer;

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
}
