package com.ultikits.plugins.economy.config;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigEntity("config/config.yml")
public class EconomyConfig extends AbstractConfigEntity {

    public EconomyConfig() {
        super("config/config.yml");
    }

    @ConfigEntry(path = "initial-cash", comment = "Initial cash for new players")
    private double initialCash = 1000.0;

    @ConfigEntry(path = "currency-name", comment = "Currency display name")
    private String currencyName = "Coins";

    @ConfigEntry(path = "currency-symbol", comment = "Currency symbol prefix")
    private String currencySymbol = "$";

    @ConfigEntry(path = "bank.enabled", comment = "Enable bank feature")
    private boolean bankEnabled = true;

    @ConfigEntry(path = "bank.min-deposit", comment = "Minimum deposit amount")
    private double minDeposit = 100.0;

    @ConfigEntry(path = "bank.max-balance", comment = "Maximum bank balance (-1 = unlimited)")
    private double maxBankBalance = -1;

    @ConfigEntry(path = "interest.enabled", comment = "Enable interest")
    private boolean interestEnabled = true;

    @ConfigEntry(path = "interest.rate", comment = "Interest rate per interval")
    private double interestRate = 0.03;

    @ConfigEntry(path = "interest.interval", comment = "Interval in seconds")
    private int interestInterval = 1800;

    @ConfigEntry(path = "interest.max-interest", comment = "Max interest per payment")
    private double maxInterest = 10000.0;

    @ConfigEntry(path = "leaderboard.update-interval", comment = "Leaderboard update interval in seconds")
    private int leaderboardUpdateInterval = 60;

    @ConfigEntry(path = "leaderboard.display-count", comment = "Default leaderboard entries")
    private int leaderboardDisplayCount = 10;
}
