package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.interfaces.DataOperator;

import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;

@Service
public class EconomyServiceImpl implements EconomyService {

    private final UltiToolsPlugin plugin;
    private final DataOperator<PlayerAccountEntity> dataOperator;
    private final EconomyConfig config;
    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");

    // Test-friendly constructor
    public EconomyServiceImpl(UltiToolsPlugin plugin,
                              DataOperator<PlayerAccountEntity> dataOperator,
                              EconomyConfig config) {
        this.plugin = plugin;
        this.dataOperator = dataOperator;
        this.config = config;
    }

    @Autowired
    public EconomyServiceImpl(UltiToolsPlugin plugin) {
        this(plugin,
             plugin.getDataOperator(PlayerAccountEntity.class),
             plugin.getConfig(EconomyConfig.class));
    }

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
        sender.setCash(sender.getCash() - amount);
        receiver.setCash(receiver.getCash() + amount);
        if (!updateAccount(sender)) {
            sender.setCash(sender.getCash() + amount);
            return false;
        }
        if (!updateAccount(receiver)) {
            sender.setCash(sender.getCash() + amount);
            updateAccount(sender);
            return false;
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

    private boolean updateAccount(PlayerAccountEntity account) {
        try {
            dataOperator.update(account);
            return true;
        } catch (IllegalAccessException e) {
            plugin.getLogger().error("Failed to update account: " + e.getMessage());
            return false;
        }
    }
}
