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
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {

    private EconomyConfig config;
    private DataOperator<PlayerAccountEntity> dataOperator;
    private DataOperator<CurrencyBalanceEntity> currencyDataOperator;
    private CurrencyManager currencyManager;
    private volatile List<LeaderboardEntry> cachedLeaderboard = Collections.emptyList();
    private volatile Map<String, List<LeaderboardEntry>> currencyLeaderboards = Collections.emptyMap();

    public LeaderboardService(UltiToolsPlugin plugin) {
        this.config = plugin.getConfig(EconomyConfig.class);
        this.dataOperator = plugin.getDataOperator(PlayerAccountEntity.class);
        this.currencyDataOperator = plugin.getDataOperator(CurrencyBalanceEntity.class);
        this.currencyManager = ((UltiEconomy) plugin).getCurrencyManager();
    }

    @SuppressWarnings("all")
    static LeaderboardService createForTest(EconomyConfig config,
                                            DataOperator<PlayerAccountEntity> dataOperator,
                                            DataOperator<CurrencyBalanceEntity> currencyDataOperator,
                                            CurrencyManager currencyManager) {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            LeaderboardService svc = (LeaderboardService) unsafe.allocateInstance(LeaderboardService.class);
            svc.config = config;
            svc.dataOperator = dataOperator;
            svc.currencyDataOperator = currencyDataOperator;
            svc.currencyManager = currencyManager;
            // Field initializers don't run with allocateInstance
            java.lang.reflect.Field cl = LeaderboardService.class.getDeclaredField("cachedLeaderboard");
            cl.setAccessible(true);
            cl.set(svc, java.util.Collections.emptyList());
            java.lang.reflect.Field clb = LeaderboardService.class.getDeclaredField("currencyLeaderboards");
            clb.setAccessible(true);
            clb.set(svc, java.util.Collections.emptyMap());
            return svc;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The scheduled refresh: rebuilds the primary leaderboard and every configured currency's own
     * leaderboard (UltiKits/UltiEconomy#15). Runs once as soon as the module has loaded, so the rank
     * and top-N placeholders are filled from the start, and then every
     * {@code leaderboard.update-interval} seconds, bound through the framework's config-bound
     * {@code @Scheduled} (UltiKits/UltiTools-Reborn#531). Before this
     * release nothing called either refresh method and every such placeholder read an empty cache.
     *
     * <p>Runs on the main thread, like the rest of this module's data access, and reads each table
     * once per run however many currencies are configured. It only reads and then swaps the two
     * volatile snapshots, so readers never see a half-built list.
     */
    @Scheduled(config = EconomyConfig.class, periodKey = "leaderboard.update-interval")
    public void refreshAll() {
        List<PlayerAccountEntity> accounts = dataOperator.getAll();
        cachedLeaderboard = primaryEntries(accounts);
        if (currencyManager == null || currencyDataOperator == null) {
            return;
        }
        Map<String, String> nameMap = nameMap(accounts);
        List<CurrencyBalanceEntity> balances = currencyDataOperator.getAll();
        Map<String, List<LeaderboardEntry>> updated = new HashMap<>(currencyLeaderboards);
        String primaryId = currencyManager.getPrimaryCurrencyId();
        for (CurrencyDefinition currency : currencyManager.getAllCurrencies()) {
            // The primary currency's one wallet is the account row, so its named board is the
            // primary board (UltiKits/UltiEconomy#25).
            updated.put(currency.getId(), primaryId.equals(currency.getId())
                    ? cachedLeaderboard
                    : currencyEntries(currency.getId(), balances, nameMap));
        }
        currencyLeaderboards = Collections.unmodifiableMap(updated);
    }

    /**
     * Refreshes the leaderboard cache from the database.
     */
    public void refreshLeaderboard() {
        cachedLeaderboard = primaryEntries(dataOperator.getAll());
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
        List<LeaderboardEntry> entries = currencyManager != null
                && currencyManager.getPrimaryCurrencyId().equals(currencyId)
                ? primaryEntries(dataOperator.getAll())
                : currencyEntries(currencyId, currencyDataOperator.getAll(), nameMap(dataOperator.getAll()));
        Map<String, List<LeaderboardEntry>> updated = new HashMap<>(currencyLeaderboards);
        updated.put(currencyId, entries);
        currencyLeaderboards = Collections.unmodifiableMap(updated);
    }

    private static List<LeaderboardEntry> primaryEntries(List<PlayerAccountEntity> accounts) {
        return accounts.stream()
                .map(a -> new LeaderboardEntry(a.getUuid(), a.getPlayerName(), a.getTotalWealth()))
                .sorted(Comparator.comparingDouble(LeaderboardEntry::getTotalWealth).reversed())
                .collect(Collectors.toList());
    }

    private static Map<String, String> nameMap(List<PlayerAccountEntity> accounts) {
        Map<String, String> nameMap = new HashMap<>();
        for (PlayerAccountEntity account : accounts) {
            nameMap.put(account.getUuid(), account.getPlayerName());
        }
        return nameMap;
    }

    private static List<LeaderboardEntry> currencyEntries(String currencyId,
                                                          List<CurrencyBalanceEntity> balances,
                                                          Map<String, String> nameMap) {
        return balances.stream()
                .filter(b -> currencyId.equals(b.getCurrencyId()))
                .map(b -> new LeaderboardEntry(
                        b.getUuid(),
                        nameMap.getOrDefault(b.getUuid(), b.getUuid()),
                        b.getTotalWealth()))
                .sorted(Comparator.comparingDouble(LeaderboardEntry::getTotalWealth).reversed())
                .collect(Collectors.toList());
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
