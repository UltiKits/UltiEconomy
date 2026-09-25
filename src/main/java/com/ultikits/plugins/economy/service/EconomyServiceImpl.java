package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.entity.TreasuryEntity;
import com.ultikits.plugins.economy.model.CurrencyDefinition;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.interfaces.DataOperator;

import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;

@Service
public class EconomyServiceImpl implements EconomyService {

    private UltiToolsPlugin plugin;
    private DataOperator<PlayerAccountEntity> dataOperator;
    private EconomyConfig config;
    private DataOperator<CurrencyBalanceEntity> currencyDataOperator;
    private CurrencyManager currencyManager;
    private TaxService taxService;
    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");

    public EconomyServiceImpl(UltiToolsPlugin plugin) {
        this.plugin = plugin;
        this.dataOperator = plugin.getDataOperator(PlayerAccountEntity.class);
        this.config = plugin.getConfig(EconomyConfig.class);
        this.currencyDataOperator = plugin.getDataOperator(CurrencyBalanceEntity.class);
        this.currencyManager = ((UltiEconomy) plugin).getCurrencyManager();
        this.taxService = new TaxService(
                plugin.getConfig(EconomyConfig.class),
                plugin.getDataOperator(TreasuryEntity.class));
    }

    @SuppressWarnings("all")
    static EconomyServiceImpl createForTest(UltiToolsPlugin plugin,
                                            DataOperator<PlayerAccountEntity> dataOperator,
                                            EconomyConfig config,
                                            DataOperator<CurrencyBalanceEntity> currencyDataOperator,
                                            CurrencyManager currencyManager) {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            EconomyServiceImpl instance = (EconomyServiceImpl) unsafe.allocateInstance(EconomyServiceImpl.class);
            instance.plugin = plugin;
            instance.dataOperator = dataOperator;
            instance.config = config;
            instance.currencyDataOperator = currencyDataOperator;
            instance.currencyManager = currencyManager;
            // Field initializers don't run with allocateInstance
            java.lang.reflect.Field df = EconomyServiceImpl.class.getDeclaredField("decimalFormat");
            df.setAccessible(true);
            df.set(instance, new DecimalFormat("#,##0.00"));
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setTaxService(TaxService taxService) {
        this.taxService = taxService;
    }

    // --- Account-wallet methods ---
    // The primary currency's one and only wallet: the account row (table economy_accounts) that
    // Vault, /money, /pay, /bank and /eco use. The currency-aware methods below route the primary
    // currency's id here, so the primary currency is never kept anywhere else
    // (UltiKits/UltiEconomy#25).

    @Override
    public PlayerAccountEntity getAccount(UUID playerUuid) {
        List<PlayerAccountEntity> results = dataOperator.query()
                .where("uuid").eq(playerUuid.toString())
                .list();
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public PlayerAccountEntity getOrCreateAccount(UUID playerUuid, String playerName) {
        PlayerAccountEntity account = getAccount(playerUuid);
        if (account != null) {
            return account;
        }
        account = PlayerAccountEntity.builder()
                .uuid(playerUuid.toString())
                .playerName(playerName)
                .cash(config.getInitialCash())
                .bank(0.0)
                .build();
        dataOperator.insert(account);
        return account;
    }

    @Override
    public boolean hasAccount(UUID playerUuid) {
        return getAccount(playerUuid) != null;
    }

    @Override
    public double getCash(UUID playerUuid) {
        PlayerAccountEntity account = getAccount(playerUuid);
        return account != null ? account.getCash() : 0.0;
    }

    @Override
    public double getBank(UUID playerUuid) {
        PlayerAccountEntity account = getAccount(playerUuid);
        return account != null ? account.getBank() : 0.0;
    }

    @Override
    public double getTotalWealth(UUID playerUuid) {
        PlayerAccountEntity account = getAccount(playerUuid);
        return account != null ? account.getTotalWealth() : 0.0;
    }

    @Override
    public boolean setCash(UUID playerUuid, double amount) {
        if (amount < 0) {
            return false;
        }
        PlayerAccountEntity account = getAccount(playerUuid);
        if (account == null) {
            return false;
        }
        account.setCash(amount);
        return updateAccount(account);
    }

    @Override
    public boolean setBank(UUID playerUuid, double amount) {
        if (amount < 0) {
            return false;
        }
        PlayerAccountEntity account = getAccount(playerUuid);
        if (account == null) {
            return false;
        }
        account.setBank(amount);
        return updateAccount(account);
    }

    @Override
    public boolean addCash(UUID playerUuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerAccountEntity account = getAccount(playerUuid);
        if (account == null) {
            return false;
        }
        account.setCash(account.getCash() + amount);
        return updateAccount(account);
    }

    @Override
    public boolean addBank(UUID playerUuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerAccountEntity account = getAccount(playerUuid);
        if (account == null) {
            return false;
        }
        account.setBank(account.getBank() + amount);
        return updateAccount(account);
    }

    @Override
    public boolean takeCash(UUID playerUuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerAccountEntity account = getAccount(playerUuid);
        if (account == null || account.getCash() < amount) {
            return false;
        }
        account.setCash(account.getCash() - amount);
        return updateAccount(account);
    }

    @Override
    public boolean takeBank(UUID playerUuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerAccountEntity account = getAccount(playerUuid);
        if (account == null || account.getBank() < amount) {
            return false;
        }
        account.setBank(account.getBank() - amount);
        return updateAccount(account);
    }

    @Override
    public boolean transfer(UUID from, UUID to, double amount) {
        if (amount <= 0 || from.equals(to)) {
            return false;
        }
        PlayerAccountEntity sender = getAccount(from);
        if (sender == null || sender.getCash() < amount) {
            return false;
        }
        PlayerAccountEntity receiver = getAccount(to);
        if (receiver == null) {
            return false;
        }
        double tax = (taxService != null) ? taxService.calculateTransactionTax(amount) : 0.0;
        double received = amount - tax;
        sender.setCash(sender.getCash() - amount);
        receiver.setCash(receiver.getCash() + received);
        if (!updateAccount(sender)) {
            sender.setCash(sender.getCash() + amount);
            return false;
        }
        if (!updateAccount(receiver)) {
            sender.setCash(sender.getCash() + amount);
            updateAccount(sender);
            return false;
        }
        if (tax > 0 && taxService != null) {
            try {
                taxService.depositToTreasury(tax, currencyManager.getPrimaryCurrency().getId());
            } catch (IllegalAccessException ignored) {
            }
        }
        return true;
    }

    @Override
    public boolean depositToBank(UUID playerUuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        if (amount < config.getMinDeposit()) {
            return false;
        }
        PlayerAccountEntity account = getAccount(playerUuid);
        if (account == null || account.getCash() < amount) {
            return false;
        }
        double maxBalance = config.getMaxBankBalance();
        if (maxBalance > 0 && account.getBank() + amount > maxBalance) {
            return false;
        }
        account.setCash(account.getCash() - amount);
        account.setBank(account.getBank() + amount);
        return updateAccount(account);
    }

    @Override
    public boolean withdrawFromBank(UUID playerUuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerAccountEntity account = getAccount(playerUuid);
        if (account == null || account.getBank() < amount) {
            return false;
        }
        account.setBank(account.getBank() - amount);
        account.setCash(account.getCash() + amount);
        return updateAccount(account);
    }

    @Override
    public String formatAmount(double amount) {
        return config.getCurrencySymbol() + decimalFormat.format(amount);
    }

    // --- Currency-aware methods ---
    // A non-primary currency keeps its own row in currency_balances. The primary currency's id is
    // routed to the account-wallet methods above, whichever path names it (/money coins,
    // /pay ... coins, /eco ... coins, /note ... coins, %ultieconomy_coins_*%), so the primary
    // currency has exactly one wallet. Its limits come from config.yml (maintainer decision
    // 2026-09-24, UltiKits/UltiEconomy#25).

    /** Whether {@code currencyId} names the primary currency, whose one wallet is the account row. */
    private boolean isPrimary(String currencyId) {
        return currencyManager != null && currencyManager.getPrimaryCurrencyId().equals(currencyId);
    }

    /**
     * The primary currency's balance as a detached snapshot of the account row, or null when the
     * player has no account. Writing to the returned object changes nothing; use the service methods.
     */
    private static CurrencyBalanceEntity accountView(PlayerAccountEntity account, String currencyId) {
        if (account == null) {
            return null;
        }
        return CurrencyBalanceEntity.builder()
                .uuid(account.getUuid())
                .currencyId(currencyId)
                .cash(account.getCash())
                .bank(account.getBank())
                .build();
    }

    @Override
    public CurrencyBalanceEntity getBalance(UUID playerUuid, String currencyId) {
        if (isPrimary(currencyId)) {
            return accountView(getAccount(playerUuid), currencyId);
        }
        List<CurrencyBalanceEntity> results = currencyDataOperator.query()
                .where("uuid").eq(playerUuid.toString())
                .and("currency_id").eq(currencyId)
                .list();
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public CurrencyBalanceEntity getOrCreateBalance(UUID playerUuid, String playerName, String currencyId) {
        if (isPrimary(currencyId)) {
            // Never a second primary-currency row: the account, created with config.yml's initial-cash.
            return accountView(getOrCreateAccount(playerUuid, playerName), currencyId);
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        if (balance != null) {
            return balance;
        }
        double initialCash = 0.0;
        if (currencyManager != null) {
            CurrencyDefinition def = currencyManager.getCurrency(currencyId);
            if (def != null) {
                initialCash = def.getInitialCash();
            }
        }
        balance = CurrencyBalanceEntity.builder()
                .uuid(playerUuid.toString())
                .currencyId(currencyId)
                .cash(initialCash)
                .bank(0.0)
                .build();
        currencyDataOperator.insert(balance);
        return balance;
    }

    @Override
    public boolean hasBalance(UUID playerUuid, String currencyId) {
        if (isPrimary(currencyId)) {
            return hasAccount(playerUuid);
        }
        return getBalance(playerUuid, currencyId) != null;
    }

    @Override
    public double getCash(UUID playerUuid, String currencyId) {
        if (isPrimary(currencyId)) {
            return getCash(playerUuid);
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        return balance != null ? balance.getCash() : 0.0;
    }

    @Override
    public double getBank(UUID playerUuid, String currencyId) {
        if (isPrimary(currencyId)) {
            return getBank(playerUuid);
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        return balance != null ? balance.getBank() : 0.0;
    }

    @Override
    public double getTotalWealth(UUID playerUuid, String currencyId) {
        if (isPrimary(currencyId)) {
            return getTotalWealth(playerUuid);
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        return balance != null ? balance.getTotalWealth() : 0.0;
    }

    @Override
    public boolean setCash(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return setCash(playerUuid, amount);
        }
        if (amount < 0) {
            return false;
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        if (balance == null) {
            return false;
        }
        balance.setCash(amount);
        return updateBalance(balance);
    }

    @Override
    public boolean setBank(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return setBank(playerUuid, amount);
        }
        if (amount < 0) {
            return false;
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        if (balance == null) {
            return false;
        }
        balance.setBank(amount);
        return updateBalance(balance);
    }

    @Override
    public boolean addCash(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return addCash(playerUuid, amount);
        }
        if (amount <= 0) {
            return false;
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        if (balance == null) {
            return false;
        }
        balance.setCash(balance.getCash() + amount);
        return updateBalance(balance);
    }

    @Override
    public boolean addBank(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return addBank(playerUuid, amount);
        }
        if (amount <= 0) {
            return false;
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        if (balance == null) {
            return false;
        }
        balance.setBank(balance.getBank() + amount);
        return updateBalance(balance);
    }

    @Override
    public boolean takeCash(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return takeCash(playerUuid, amount);
        }
        if (amount <= 0) {
            return false;
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        if (balance == null || balance.getCash() < amount) {
            return false;
        }
        balance.setCash(balance.getCash() - amount);
        return updateBalance(balance);
    }

    @Override
    public boolean takeBank(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return takeBank(playerUuid, amount);
        }
        if (amount <= 0) {
            return false;
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        if (balance == null || balance.getBank() < amount) {
            return false;
        }
        balance.setBank(balance.getBank() - amount);
        return updateBalance(balance);
    }

    @Override
    public boolean transfer(UUID from, UUID to, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            return transfer(from, to, amount);
        }
        if (amount <= 0 || from.equals(to)) {
            return false;
        }
        CurrencyBalanceEntity sender = getBalance(from, currencyId);
        if (sender == null || sender.getCash() < amount) {
            return false;
        }
        CurrencyBalanceEntity receiver = getBalance(to, currencyId);
        if (receiver == null) {
            return false;
        }
        double tax = (taxService != null) ? taxService.calculateTransactionTax(amount) : 0.0;
        double received = amount - tax;
        sender.setCash(sender.getCash() - amount);
        receiver.setCash(receiver.getCash() + received);
        if (!updateBalance(sender)) {
            sender.setCash(sender.getCash() + amount);
            return false;
        }
        if (!updateBalance(receiver)) {
            sender.setCash(sender.getCash() + amount);
            updateBalance(sender);
            return false;
        }
        if (tax > 0 && taxService != null) {
            try {
                taxService.depositToTreasury(tax, currencyId);
            } catch (IllegalAccessException ignored) {
            }
        }
        return true;
    }

    @Override
    public boolean depositToBank(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            // config.yml governs the primary currency: bank.enabled here, bank.min-deposit and
            // bank.max-balance in depositToBank(UUID, double).
            return config.isBankEnabled() && depositToBank(playerUuid, amount);
        }
        if (amount <= 0) {
            return false;
        }
        CurrencyDefinition def = currencyManager != null ? currencyManager.getCurrency(currencyId) : null;
        if (def != null) {
            if (!def.isBankEnabled()) {
                return false;
            }
            if (amount < def.getMinDeposit()) {
                return false;
            }
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        if (balance == null || balance.getCash() < amount) {
            return false;
        }
        if (def != null) {
            double maxBalance = def.getMaxBankBalance();
            if (maxBalance > 0 && balance.getBank() + amount > maxBalance) {
                return false;
            }
        }
        balance.setCash(balance.getCash() - amount);
        balance.setBank(balance.getBank() + amount);
        return updateBalance(balance);
    }

    @Override
    public boolean withdrawFromBank(UUID playerUuid, double amount, String currencyId) {
        if (isPrimary(currencyId)) {
            // config.yml's bank.enabled governs the primary currency, as /withdraw <amount> applies it.
            return config.isBankEnabled() && withdrawFromBank(playerUuid, amount);
        }
        if (amount <= 0) {
            return false;
        }
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        if (balance == null || balance.getBank() < amount) {
            return false;
        }
        balance.setBank(balance.getBank() - amount);
        balance.setCash(balance.getCash() + amount);
        return updateBalance(balance);
    }

    @Override
    public String formatAmount(double amount, String currencyId) {
        if (currencyManager != null) {
            CurrencyDefinition def = currencyManager.getCurrency(currencyId);
            if (def != null) {
                return def.getSymbol() + decimalFormat.format(amount);
            }
        }
        return decimalFormat.format(amount);
    }

    @Override
    public String getPrimaryCurrencyId() {
        return currencyManager != null ? currencyManager.getPrimaryCurrencyId() : "coins";
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    private boolean updateAccount(PlayerAccountEntity account) {
        try {
            dataOperator.update(account);
            return true;
        } catch (IllegalAccessException e) {
            plugin.getLogger().error(String.format(plugin.i18n("economy.log.account_update_failed"), e.getMessage()));
            return false;
        }
    }

    private boolean updateBalance(CurrencyBalanceEntity balance) {
        try {
            currencyDataOperator.update(balance);
            return true;
        } catch (IllegalAccessException e) {
            plugin.getLogger().error(String.format(plugin.i18n("economy.log.balance_update_failed"), e.getMessage()));
            return false;
        }
    }
}
