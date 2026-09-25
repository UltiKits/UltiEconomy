package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.model.CurrencyDefinition;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Scheduled;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Pays bank interest every {@code interest.interval} seconds while {@code interest.enabled} is true.
 *
 * <p>The service is created whatever {@code interest.enabled} says at boot. The switch is read at
 * every scheduled run instead, so a {@code /ul reload} that turns interest on or off takes effect at
 * the next payment without rescheduling anything (UltiKits/UltiEconomy#15). Before this release this class
 * was {@code @ConditionalOnConfig} on the same key but nothing ever scheduled it, so no interest was
 * paid on any server.
 */
@Service
public class InterestService {

    private UltiToolsPlugin plugin;
    private EconomyService economyService;
    private EconomyConfig config;
    private DataOperator<PlayerAccountEntity> dataOperator;
    private DataOperator<CurrencyBalanceEntity> currencyDataOperator;
    private CurrencyManager currencyManager;

    public InterestService(UltiToolsPlugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.config = plugin.getConfig(EconomyConfig.class);
        this.dataOperator = plugin.getDataOperator(PlayerAccountEntity.class);
        this.currencyDataOperator = plugin.getDataOperator(CurrencyBalanceEntity.class);
        this.currencyManager = ((UltiEconomy) plugin).getCurrencyManager();
    }

    @SuppressWarnings("all")
    static InterestService createForTest(UltiToolsPlugin plugin,
                                         EconomyService economyService,
                                         EconomyConfig config,
                                         DataOperator<PlayerAccountEntity> dataOperator,
                                         DataOperator<CurrencyBalanceEntity> currencyDataOperator,
                                         CurrencyManager currencyManager) {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            InterestService svc = (InterestService) unsafe.allocateInstance(InterestService.class);
            svc.plugin = plugin;
            svc.economyService = economyService;
            svc.config = config;
            svc.dataOperator = dataOperator;
            svc.currencyDataOperator = currencyDataOperator;
            svc.currencyManager = currencyManager;
            return svc;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The scheduled payment: pays interest if {@code interest.enabled} is true right now, and does
     * nothing at all otherwise -- no account is read and nobody is notified.
     *
     * <p>Timing is bound to {@code interest.interval} (seconds) through the framework's config-bound
     * {@code @Scheduled} (UltiKits/UltiTools-Reborn#531) -- the default lives only in
     * {@link EconomyConfig}. The same key is the first delay, so the first payment comes one full
     * interval after the module loads, not at load: paying at load would pay an extra time on every
     * server restart. {@code /ul reload} applies a changed interval keeping the task's place in its
     * cycle, so a reload never pays early and never postpones a payment. The binding is sync only,
     * and requires {@code api-version: 630} in {@code plugin.yml}.
     *
     * <p>Runs on the main thread. A payment is a read-modify-write of every bank balance, and this
     * module's own commands change balances on the main thread; running it there means no payment
     * can interleave with them and lose an update. (A third-party plugin calling Vault from another
     * thread is outside this module's control.)
     *
     * <p>Every server that runs this task pays the full rate on every balance in its database. If
     * several servers share one database, interest must be on for exactly one of them (the load-time
     * warning says so).
     */
    @Scheduled(config = EconomyConfig.class, periodKey = "interest.interval", delayKey = "interest.interval")
    public void payInterestIfEnabled() {
        if (!config.isInterestEnabled()) {
            return;
        }
        distributeInterest();
    }

    /**
     * Distributes interest to all accounts with positive bank balance.
     * Handles both primary currency (PlayerAccountEntity) and per-currency balances
     * (CurrencyBalanceEntity) for bank-enabled currencies.
     * Called by {@link #payInterestIfEnabled()} on the framework's schedule.
     *
     * <p>Which balances earn interest: the primary currency's bank balance on the account row
     * ({@link PlayerAccountEntity}, what {@code /bank}, {@code /money}, {@code /eco check} and Vault
     * show), once per player; and the bank balance of every other currency whose
     * {@code bank-enabled} is true. The account row is the primary currency's only wallet
     * (UltiKits/UltiEconomy#25): the module creates no per-currency row for it, and merges the ones
     * 2.0.0 created into the accounts at load. Should one exist anyway, it earns nothing, so a player
     * is paid once for the primary currency and the per-payment cap is
     * {@code interest.max-interest}, not twice it.
     *
     * <p>How a payment writes (found reviewing UltiKits/UltiEconomy#15):
     * <ul>
     *   <li>It credits the rows {@code getAll()} returned and writes each one once. It does not look a
     *       row up again: this runs on the main thread, and a per-row lookup by {@code uuid} (an
     *       unindexed column) made each payment cost accounts x rows. Nothing else can change a row
     *       between the read and the write, because both happen in the same tick on the main thread.</li>
     *   <li>A payment never takes a bank balance above its cap -- {@code bank.max-balance} for the
     *       primary currency, the currency's own {@code max-bank-balance} otherwise, each only when
     *       above 0, the same caps a deposit obeys. The credit is the smallest of rate x balance,
     *       {@code interest.max-interest} (when above 0) and the room left under the cap; a balance
     *       with no room gets nothing.</li>
     *   <li>The owner is told only after the write succeeded. A write that fails is logged, the row
     *       keeps its old balance, and the payment carries on with the next row.</li>
     * </ul>
     */
    public void distributeInterest() {
        // Primary currency interest
        List<PlayerAccountEntity> accounts = dataOperator.getAll();
        for (PlayerAccountEntity account : accounts) {
            double interest = creditFor(account.getBank(), config.getMaxBankBalance());
            if (interest <= 0) {
                continue;
            }
            double before = account.getBank();
            account.setBank(before + interest);
            if (!write(dataOperator, account)) {
                account.setBank(before);
                continue;
            }
            notifyPlayer(account.getUuid(), interest);
        }

        // Per-currency interest
        if (currencyDataOperator == null || currencyManager == null) {
            return;
        }

        List<CurrencyBalanceEntity> currencyBalances = currencyDataOperator.getAll();
        for (CurrencyBalanceEntity balance : currencyBalances) {
            // The primary currency is paid once, above, on the account row -- its only wallet, the
            // bank balance /bank, /money, /eco check and Vault show. A per-currency row for it is no
            // longer created (UltiKits/UltiEconomy#25); should one exist it earns nothing.
            if (currencyManager.getPrimaryCurrencyId().equals(balance.getCurrencyId())) {
                continue;
            }
            CurrencyDefinition def = currencyManager.getCurrency(balance.getCurrencyId());
            if (def == null || !def.isBankEnabled()) {
                continue;
            }
            double interest = creditFor(balance.getBank(), def.getMaxBankBalance());
            if (interest <= 0) {
                continue;
            }
            double before = balance.getBank();
            balance.setBank(before + interest);
            if (!write(currencyDataOperator, balance)) {
                balance.setBank(before);
                continue;
            }
            notifyPlayer(balance.getUuid(), interest, balance.getCurrencyId());
        }
    }

    /**
     * The interest one payment credits to a bank balance: {@link #calculateInterest(double)}, then
     * limited to the room left under {@code maxBankBalance} when that is above 0. Zero or less means
     * "credit nothing".
     */
    private double creditFor(double bankBalance, double maxBankBalance) {
        double interest = calculateInterest(bankBalance);
        if (maxBankBalance > 0) {
            interest = Math.min(interest, maxBankBalance - bankBalance);
        }
        return interest;
    }

    /** Writes one row; returns false, having logged why, if the write failed. */
    private <T extends com.ultikits.ultitools.abstracts.data.BaseDataEntity<String>> boolean write(
            DataOperator<T> operator, T row) {
        try {
            operator.update(row);
            return true;
        } catch (IllegalAccessException | RuntimeException e) {
            plugin.getLogger().error(String.format(plugin.i18n("economy.log.interest_write_failed"), e.getMessage()));
            return false;
        }
    }

    /**
     * Calculates interest for a specific bank balance.
     * Visible for testing.
     */
    double calculateInterest(double bankBalance) {
        if (bankBalance <= 0) {
            return 0.0;
        }
        double interest = bankBalance * config.getInterestRate();
        double maxInterest = config.getMaxInterest();
        if (maxInterest > 0 && interest > maxInterest) {
            interest = maxInterest;
        }
        return interest;
    }

    private void notifyPlayer(String uuid, double interest) {
        try {
            Player player = Bukkit.getPlayer(UUID.fromString(uuid));
            if (player != null && player.isOnline()) {
                String formatted = economyService.formatAmount(interest);
                player.sendMessage(ChatColor.GREEN + String.format(
                        plugin.i18n("economy.interest.received"), formatted));
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid UUID — skip notification
        }
    }

    private void notifyPlayer(String uuid, double interest, String currencyId) {
        try {
            Player player = Bukkit.getPlayer(UUID.fromString(uuid));
            if (player != null && player.isOnline()) {
                String formatted = economyService.formatAmount(interest, currencyId);
                CurrencyDefinition def = currencyManager.getCurrency(currencyId);
                String currencyName = def != null ? def.getDisplayName() : currencyId;
                player.sendMessage(ChatColor.GREEN + String.format(
                        plugin.i18n("economy.interest.received_currency"), currencyName, formatted));
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid UUID — skip notification
        }
    }
}
