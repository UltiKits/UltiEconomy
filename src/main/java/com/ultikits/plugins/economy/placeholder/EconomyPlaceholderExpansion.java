package com.ultikits.plugins.economy.placeholder;

import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.service.LeaderboardService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EconomyPlaceholderExpansion extends PlaceholderExpansion {

    private final EconomyService economyService;
    private final LeaderboardService leaderboardService;

    public EconomyPlaceholderExpansion(EconomyService economyService,
                                      LeaderboardService leaderboardService) {
        this.economyService = economyService;
        this.leaderboardService = leaderboardService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ultieconomy";
    }

    @Override
    public @NotNull String getAuthor() {
        return "wisdomme";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        String param = params.toLowerCase();

        switch (param) {
            case "cash":
                return String.format("%.2f", economyService.getCash(player.getUniqueId()));
            case "bank":
                return String.format("%.2f", economyService.getBank(player.getUniqueId()));
            case "total":
                return String.format("%.2f", economyService.getTotalWealth(player.getUniqueId()));
            case "cash_formatted":
                return economyService.formatAmount(economyService.getCash(player.getUniqueId()));
            case "rank":
                int rank = leaderboardService.getPlayerRank(player.getUniqueId());
                return rank > 0 ? String.valueOf(rank) : "-";
            default:
                return handleTopPlaceholder(param);
        }
    }

    private String handleTopPlaceholder(String param) {
        if (param.startsWith("top_name_")) {
            return getTopName(param.substring("top_name_".length()));
        }
        if (param.startsWith("top_balance_")) {
            return getTopBalance(param.substring("top_balance_".length()));
        }
        return null;
    }

    private String getTopName(String indexStr) {
        int index = parseIndex(indexStr);
        if (index < 1) {
            return "-";
        }
        List<LeaderboardService.LeaderboardEntry> top = leaderboardService.getTopPlayers(index);
        if (top.size() < index) {
            return "-";
        }
        return top.get(index - 1).getPlayerName();
    }

    private String getTopBalance(String indexStr) {
        int index = parseIndex(indexStr);
        if (index < 1) {
            return "0.00";
        }
        List<LeaderboardService.LeaderboardEntry> top = leaderboardService.getTopPlayers(index);
        if (top.size() < index) {
            return "0.00";
        }
        return String.format("%.2f", top.get(index - 1).getTotalWealth());
    }

    private int parseIndex(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
