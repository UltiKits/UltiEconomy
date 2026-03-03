package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.interfaces.DataOperator;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {

    private final EconomyConfig config;
    private final DataOperator<PlayerAccountEntity> dataOperator;
    private final DataOperator<CurrencyBalanceEntity> currencyDataOperator;
    private final CurrencyManager currencyManager;
    private volatile List<LeaderboardEntry> cachedLeaderboard = Collections.emptyList();
    private volatile Map<String, List<LeaderboardEntry>> currencyLeaderboards = Collections.emptyMap();

    public LeaderboardService(EconomyConfig config,
                              DataOperator<PlayerAccountEntity> dataOperator,
                              DataOperator<CurrencyBalanceEntity> currencyDataOperator,
                              CurrencyManager currencyManager) {
        this.config = config;
        this.dataOperator = dataOperator;
        this.currencyDataOperator = currencyDataOperator;
        this.currencyManager = currencyManager;
    }

    // Backward-compatible constructor
    public LeaderboardService(EconomyConfig config,
                              DataOperator<PlayerAccountEntity> dataOperator) {
        this(config, dataOperator, null, null);
    }

    /**
     * Refreshes the leaderboard cache from the database.
     * Called periodically by the scheduled task in the main plugin.
     */
    public void refreshLeaderboard() {
        List<PlayerAccountEntity> accounts = dataOperator.getAll();
        cachedLeaderboard = accounts.stream()
                .map(a -> new LeaderboardEntry(a.getUuid(), a.getPlayerName(), a.getTotalWealth()))
                .sorted(Comparator.comparingDouble(LeaderboardEntry::getTotalWealth).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Refreshes the leaderboard cache for a specific currency.
     * Builds a UUID→playerName map from primary accounts, then filters
     * currency balances by currencyId and sorts by total wealth.
     */
    public void refreshCurrencyLeaderboard(String currencyId) {
        if (currencyDataOperator == null) {
            return;
        }

        // Build UUID → playerName map from primary accounts
        Map<String, String> nameMap = new HashMap<>();
        for (PlayerAccountEntity account : dataOperator.getAll()) {
            nameMap.put(account.getUuid(), account.getPlayerName());
        }

        List<CurrencyBalanceEntity> balances = currencyDataOperator.getAll();
        List<LeaderboardEntry> entries = balances.stream()
                .filter(b -> currencyId.equals(b.getCurrencyId()))
                .map(b -> new LeaderboardEntry(
                        b.getUuid(),
                        nameMap.getOrDefault(b.getUuid(), b.getUuid()),
                        b.getTotalWealth()))
                .sorted(Comparator.comparingDouble(LeaderboardEntry::getTotalWealth).reversed())
                .collect(Collectors.toList());

        Map<String, List<LeaderboardEntry>> updated = new HashMap<>(currencyLeaderboards);
        updated.put(currencyId, entries);
        currencyLeaderboards = Collections.unmodifiableMap(updated);
    }

    /**
     * Returns the top N players from the cached leaderboard.
     */
    public List<LeaderboardEntry> getTopPlayers(int count) {
        List<LeaderboardEntry> snapshot = cachedLeaderboard;
        if (count >= snapshot.size()) {
            return new ArrayList<>(snapshot);
        }
        return new ArrayList<>(snapshot.subList(0, count));
    }

    /**
     * Returns a player's 1-based rank, or -1 if not found.
     */
    public int getPlayerRank(UUID playerUuid) {
        String uuidStr = playerUuid.toString();
        List<LeaderboardEntry> snapshot = cachedLeaderboard;
        for (int i = 0; i < snapshot.size(); i++) {
            if (uuidStr.equals(snapshot.get(i).getUuid())) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Returns the top N players for a specific currency from the cached leaderboard.
     * Falls back to primary leaderboard if no per-currency data available.
     */
    public List<LeaderboardEntry> getTopPlayers(int count, String currencyId) {
        List<LeaderboardEntry> snapshot = currencyLeaderboards.getOrDefault(currencyId, Collections.emptyList());
        if (snapshot.isEmpty()) {
            return getTopPlayers(count);
        }
        if (count >= snapshot.size()) {
            return new ArrayList<>(snapshot);
        }
        return new ArrayList<>(snapshot.subList(0, count));
    }

    /**
     * Returns a player's 1-based rank for a specific currency, or -1 if not found.
     */
    public int getPlayerRank(UUID playerUuid, String currencyId) {
        String uuidStr = playerUuid.toString();
        List<LeaderboardEntry> snapshot = currencyLeaderboards.getOrDefault(currencyId, Collections.emptyList());
        if (snapshot.isEmpty()) {
            return getPlayerRank(playerUuid);
        }
        for (int i = 0; i < snapshot.size(); i++) {
            if (uuidStr.equals(snapshot.get(i).getUuid())) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Returns the configured default display count.
     */
    public int getDefaultDisplayCount() {
        return config.getLeaderboardDisplayCount();
    }

    @Getter
    public static class LeaderboardEntry {
        private final String uuid;
        private final String playerName;
        private final double totalWealth;

        public LeaderboardEntry(String uuid, String playerName, double totalWealth) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.totalWealth = totalWealth;
        }
    }
}
