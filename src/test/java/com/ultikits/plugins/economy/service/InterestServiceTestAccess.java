package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Builds this module's two {@code @Scheduled} services for tests outside this package, through the
 * package-private {@code createForTest} factories, with mocked data operators.
 */
public final class InterestServiceTestAccess {

    private InterestServiceTestAccess() {
    }

    /** The interest and leaderboard services, in that order. */
    @SuppressWarnings("unchecked")
    public static List<Object> scheduledBeans(UltiToolsPlugin plugin, EconomyConfig config) {
        CurrencyManager currencies = new CurrencyManager(YamlConfiguration.loadConfiguration(new StringReader(
                "currencies:\n  coins:\n    display-name: 'Coins'\n    symbol: '$'\n    primary: true\n")));
        DataOperator<PlayerAccountEntity> accounts = mock(DataOperator.class);
        DataOperator<CurrencyBalanceEntity> balances = mock(DataOperator.class);
        return Arrays.<Object>asList(
                InterestService.createForTest(plugin, mock(EconomyService.class), config, accounts, balances, currencies),
                LeaderboardService.createForTest(config, accounts, balances, currencies));
    }
}
