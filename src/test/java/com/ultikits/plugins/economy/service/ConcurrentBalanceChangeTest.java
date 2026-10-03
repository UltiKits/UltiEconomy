package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.entity.TreasuryEntity;
import com.ultikits.plugins.economy.testsupport.InMemoryDataOperator;
import com.ultikits.plugins.economy.testsupport.SteppedOperator;
import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.exceptions.ErrorCode;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockStatic;

/**
 * UltiKits/UltiEconomy#41, servers that share one database: every balance change used to be a
 * read-modify-write of absolute values, so a change another server made to the same row between this
 * server's read and its write was overwritten -- money created or destroyed. Maintainer decision
 * 2026-10-04: a balance change applies only if the row still holds what was read; otherwise it reads
 * again and retries a bounded number of times, then fails through the existing failure path.
 *
 * <p>One interleaving per class of balance-changing site. Two "servers" (two service instances) share
 * one relational store; server A's storage view runs server B's whole change once, right before A's
 * first write -- after A read the row, before A writes it. Every expected value is "both changes
 * applied"; before the fix, A's write overwrote B's change.
 */
@DisplayName("UltiEconomy#41: two servers changing the same balance at once lose neither change")
class ConcurrentBalanceChangeTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    /** Server A's view of {@code shared}: runs {@code racer} once, right before A's first write. */
    private static <T extends BaseDataEntity<String>> DataOperator<T> racing(String table, DataOperator<T> shared,
                                                                          Runnable racer) {
        AtomicBoolean ran = new AtomicBoolean();
        return new SteppedOperator<>(table, shared, call -> {
            if ((call.startsWith("update") || call.startsWith("insert")) && ran.compareAndSet(false, true)) {
                racer.run();
            }
        });
    }

    /** Server A on the world's store; server B is the world's own service. */
    private static EconomyServiceImpl serverA(EconomyTestWorld world, Runnable racer) {
        return EconomyServiceImpl.createForTest(world.plugin,
                racing("economy_accounts", world.accounts, racer), world.config,
                racing("currency_balances", world.balances, racer), world.currencies);
    }

    @Nested
    @DisplayName("account balance changes (set, give, take, add, deposit, withdraw; Vault and /eco go through these)")
    class AccountRow {

        @Test
        @DisplayName("A gives 100 while B takes 30 from the same account: 1000 + 100 - 30")
        void giveAndTakeBothApply() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 1000.0, 0.0);
            EconomyServiceImpl a = serverA(world, () -> assertThat(world.service.takeCash(STEVE, 30.0)).isTrue());

            assertThat(a.addCash(STEVE, 100.0)).isTrue();

            assertThat(world.account(STEVE).getCash()).isEqualTo(1070.0);
        }

        @Test
        @DisplayName("A deposits 200 into the bank while B adds 10 to the bank: cash 800, bank 210")
        void depositAndBankCreditBothApply() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 1000.0, 0.0);
            EconomyServiceImpl a = serverA(world, () -> assertThat(world.service.addBank(STEVE, 10.0)).isTrue());

            assertThat(a.depositToBank(STEVE, 200.0)).isTrue();

            assertThat(world.account(STEVE).getCash()).isEqualTo(800.0);
            assertThat(world.account(STEVE).getBank()).isEqualTo(210.0);
        }

        @Test
        @DisplayName("a take that the other server's change made unaffordable is refused on the re-read, not written on top")
        void aTakeThatBecameUnaffordableIsRefused() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 100.0, 0.0);
            EconomyServiceImpl a = serverA(world, () -> assertThat(world.service.takeCash(STEVE, 80.0)).isTrue());

            assertThat(a.takeCash(STEVE, 50.0)).isFalse();

            assertThat(world.account(STEVE).getCash()).isEqualTo(20.0);
        }
    }

    @Nested
    @DisplayName("non-primary currency balance changes")
    class CurrencyRow {

        @Test
        @DisplayName("A adds 100 gems while B adds 50 gems to the same wallet: 5 + 100 + 50")
        void bothCreditsApply() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.seedAccount(STEVE, "Steve", 0.0, 0.0);
            world.seedBalance(STEVE, "gems", 5.0, 0.0);
            EconomyServiceImpl a = serverA(world, () -> assertThat(world.service.addCash(STEVE, 50.0, "gems")).isTrue());

            assertThat(a.addCash(STEVE, 100.0, "gems")).isTrue();

            assertThat(world.balanceRows("gems").get(0).getCash()).isEqualTo(155.0);
        }
    }

    @Nested
    @DisplayName("transfers (two rows)")
    class Transfer {

        @Test
        @DisplayName("A pays Alex 100 from Steve while B gives Steve 40: Steve 1000 - 100 + 40, Alex 100")
        void transferAndCreditBothApply() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.config.setTaxEnabled(false);
            world.seedAccount(STEVE, "Steve", 1000.0, 0.0);
            world.seedAccount(ALEX, "Alex", 0.0, 0.0);
            EconomyServiceImpl a = serverA(world, () -> assertThat(world.service.addCash(STEVE, 40.0)).isTrue());

            assertThat(a.transfer(STEVE, ALEX, 100.0)).isTrue();

            assertThat(world.account(STEVE).getCash()).isEqualTo(940.0);
            assertThat(world.account(ALEX).getCash()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("a gems transfer while B gives Steve 40 gems: both apply")
        void currencyTransferAndCreditBothApply() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.config.setTaxEnabled(false);
            world.seedAccount(STEVE, "Steve", 0.0, 0.0);
            world.seedAccount(ALEX, "Alex", 0.0, 0.0);
            world.seedBalance(STEVE, "gems", 1000.0, 0.0);
            world.seedBalance(ALEX, "gems", 0.0, 0.0);
            EconomyServiceImpl a = serverA(world, () -> assertThat(world.service.addCash(STEVE, 40.0, "gems")).isTrue());

            assertThat(a.transfer(STEVE, ALEX, 100.0, "gems")).isTrue();

            double steve = 0;
            double alex = 0;
            for (CurrencyBalanceEntity row : world.balanceRows("gems")) {
                if (STEVE.toString().equals(row.getUuid())) {
                    steve = row.getCash();
                } else {
                    alex = row.getCash();
                }
            }
            assertThat(steve).isEqualTo(940.0);
            assertThat(alex).isEqualTo(100.0);
        }
    }

    /**
     * Gate-1 top-up F-E2 and F-E3 (maintainer decision 2026-10-04). A transfer debits first and credits
     * second. When the credit throws a storage error (not just is refused) after the debit committed, the
     * same conditional refund runs; and when the refund itself cannot be written, one error line names
     * both players, the amount and the currency, so an operator can restore it by hand.
     */
    @Nested
    @DisplayName("transfer faults after the debit")
    class TransferFaults {

        /** Server A's account view that runs {@code onNth} right before its n-th conditional write. */
        private DataOperator<PlayerAccountEntity> faultyAt(EconomyTestWorld world, int n, Runnable onNth) {
            int[] writes = new int[1];
            return new SteppedOperator<>("economy_accounts", world.accounts, call -> {
                if (call.startsWith("update") && ++writes[0] == n) {
                    onNth.run();
                }
            });
        }

        @Test
        @DisplayName("a credit that throws a storage error refunds the sender; the transfer is refused and no money moves")
        void aThrowingCreditRefundsTheSender() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.config.setTaxEnabled(false);
            world.seedAccount(STEVE, "Steve", 1000.0, 0.0);
            world.seedAccount(ALEX, "Alex", 0.0, 0.0);
            EconomyServiceImpl a = EconomyServiceImpl.createForTest(world.plugin, faultyAt(world, 2, () -> {
                throw new DataAccessException(ErrorCode.DATA_OPERATION_FAILED, "connection reset");
            }), world.config, world.balances, world.currencies);

            assertThat(a.transfer(STEVE, ALEX, 100.0)).isFalse();

            assertThat(world.account(STEVE).getCash()).isEqualTo(1000.0);
            assertThat(world.account(ALEX).getCash()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("when the credit and then the refund both cannot be written, one error line names both players, the amount and the currency")
        void aLostRefundIsNamedForTheOperator() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.config.setTaxEnabled(false);
            world.seedAccount(STEVE, "Steve", 1000.0, 0.0);
            world.seedAccount(ALEX, "Alex", 0.0, 0.0);
            // Before the credit, Alex's row is removed by another writer; before the refund, Steve's is.
            EconomyServiceImpl a = EconomyServiceImpl.createForTest(world.plugin, faultyAt(world, 2, () -> {
                world.accounts.delById(world.account(ALEX).getId());
                world.accounts.delById(world.account(STEVE).getId());
            }), world.config, world.balances, world.currencies);

            assertThat(a.transfer(STEVE, ALEX, 100.0)).isFalse();

            ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
            verify(world.logger, atLeastOnce()).error(line.capture());
            assertThat(line.getAllValues()).anySatisfy(l -> assertThat(l)
                    .contains("Steve").contains("Alex").contains("100").contains("coins"));
        }
    }

    @Nested
    @DisplayName("interest payments")
    class Interest {

        @Test
        @DisplayName("A pays 3% interest on Steve's bank while B deposits 1000 there: the deposit is kept and interest is paid once, on the balance re-read")
        void interestAndDepositBothApply() {
            EconomyTestWorld world = EconomyTestWorld.relational();
            world.config.setInterestRate(0.03);
            world.seedAccount(STEVE, "Steve", 0.0, 10000.0);
            InterestService a = InterestService.createForTest(world.plugin, world.service, world.config,
                    racing("economy_accounts", world.accounts, () -> assertThat(world.service.addBank(STEVE, 1000.0)).isTrue()),
                    world.balances, world.currencies);

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);
                a.distributeInterest();
            }

            assertThat(world.account(STEVE).getBank()).isCloseTo(11330.0, within(1e-6));
        }
    }

    @Nested
    @DisplayName("treasury writes")
    class Treasury {

        private InMemoryDataOperator<TreasuryEntity> treasury(EconomyTestWorld world) {
            return InMemoryDataOperator.relational("economy_treasury", TreasuryEntity.class, world.crash);
        }

        @Test
        @DisplayName("two tax deposits into the same treasury row at once: 100 + 5 + 7")
        void twoDepositsBothApply() throws Exception {
            EconomyTestWorld world = EconomyTestWorld.relational();
            InMemoryDataOperator<TreasuryEntity> shared = treasury(world);
            shared.seed(TreasuryEntity.builder().currencyId("coins").balance(100.0).build());
            TaxService b = new TaxService(world.config, shared);
            TaxService a = new TaxService(world.config, racing("economy_treasury", shared, () -> deposit(b, 7.0)));

            a.depositToTreasury(5.0, "coins");

            assertThat(rows(shared)).hasSize(1);
            assertThat(rows(shared).get(0).getBalance()).isEqualTo(112.0);
        }

        @Test
        @DisplayName("the first two tax deposits of a currency at once create one treasury row holding both")
        void twoFirstDepositsCreateOneRow() throws Exception {
            EconomyTestWorld world = EconomyTestWorld.relational();
            InMemoryDataOperator<TreasuryEntity> shared = treasury(world);
            TaxService b = new TaxService(world.config, shared);
            TaxService a = new TaxService(world.config, racing("economy_treasury", shared, () -> deposit(b, 7.0)));

            a.depositToTreasury(5.0, "coins");

            assertThat(rows(shared)).hasSize(1);
            assertThat(rows(shared).get(0).getBalance()).isEqualTo(12.0);
        }

        @Test
        @DisplayName("a withdrawal the other server's withdrawal made unaffordable is refused on the re-read: 100 - 80, not 50")
        void aWithdrawalThatBecameUnaffordableIsRefused() throws Exception {
            EconomyTestWorld world = EconomyTestWorld.relational();
            InMemoryDataOperator<TreasuryEntity> shared = treasury(world);
            shared.seed(TreasuryEntity.builder().currencyId("coins").balance(100.0).build());
            TaxService b = new TaxService(world.config, shared);
            TaxService a = new TaxService(world.config, racing("economy_treasury", shared, () -> {
                try {
                    assertThat(b.withdrawFromTreasury(80.0, "coins")).isTrue();
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }));

            assertThat(a.withdrawFromTreasury(50.0, "coins")).isFalse();

            assertThat(rows(shared).get(0).getBalance()).isEqualTo(20.0);
        }

        private void deposit(TaxService service, double amount) {
            try {
                service.depositToTreasury(amount, "coins");
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        private List<TreasuryEntity> rows(InMemoryDataOperator<TreasuryEntity> shared) {
            return shared.getAll();
        }
    }
}
