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

    // --- Legacy single-currency methods (delegate to primary) ---

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

    @Override
    public CurrencyBalanceEntity getBalance(UUID playerUuid, String currencyId) {
        List<CurrencyBalanceEntity> results = currencyDataOperator.query()
                .where("uuid").eq(playerUuid.toString())
                .and("currency_id").eq(currencyId)
                .list();
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public CurrencyBalanceEntity getOrCreateBalance(UUID playerUuid, String playerName, String currencyId) {
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
        return getBalance(playerUuid, currencyId) != null;
    }

    @Override
    public double getCash(UUID playerUuid, String currencyId) {
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        return balance != null ? balance.getCash() : 0.0;
    }

    @Override
    public double getBank(UUID playerUuid, String currencyId) {
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        return balance != null ? balance.getBank() : 0.0;
    }

    @Override
    public double getTotalWealth(UUID playerUuid, String currencyId) {
        CurrencyBalanceEntity balance = getBalance(playerUuid, currencyId);
        return balance != null ? balance.getTotalWealth() : 0.0;
    }

    @Override
    public boolean setCash(UUID playerUuid, double amount, String currencyId) {
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
            plugin.getLogger().error("Failed to update account: " + e.getMessage());
            return false;
        }
    }

    private boolean updateBalance(CurrencyBalanceEntity balance) {
        try {
            currencyDataOperator.update(balance);
            return true;
        } catch (IllegalAccessException e) {
            plugin.getLogger().error("Failed to update balance: " + e.getMessage());
            return false;
        }
    }
}
