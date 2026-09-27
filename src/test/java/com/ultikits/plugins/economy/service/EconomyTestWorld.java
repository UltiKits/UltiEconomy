package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.entity.CurrencyBalanceEntity;
import com.ultikits.plugins.economy.entity.PlayerAccountEntity;
import com.ultikits.plugins.economy.entity.WalletMergeClaimEntity;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.testsupport.InMemoryDataOperator;
import com.ultikits.plugins.economy.testsupport.InMemoryDataOperator.CrashSwitch;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Test support: one module's storage and services wired together over in-memory operators that keep
 * real state ({@link InMemoryDataOperator}), so a test asserts what ends up in each wallet rather
 * than which method was called. Public so the command and listener tests in other packages can use
 * the package-private test factories behind it.
 */
public final class EconomyTestWorld {

    /**
     * The shipped primary currency, plus a second one. The primary currency's block deliberately
     * disagrees with {@code config.yml}'s defaults (initial cash 1000, minimum deposit 100, unlimited
     * bank, bank enabled), so a test can tell which file a primary-currency path obeys.
     */
    public static final String CURRENCIES_YAML =
            "currencies:\n"
                    + "  coins:\n"
                    + "    display-name: 'Coins'\n"
                    + "    symbol: '$'\n"
                    + "    initial-cash: 250.0\n"
                    + "    bank-enabled: false\n"
                    + "    min-deposit: 500.0\n"
                    + "    max-bank-balance: 1000.0\n"
                    + "    primary: true\n"
                    + "  gems:\n"
                    + "    display-name: 'Gems'\n"
                    + "    symbol: 'G'\n"
                    + "    initial-cash: 5.0\n"
                    + "    bank-enabled: true\n"
                    + "    min-deposit: 1.0\n"
                    + "    max-bank-balance: -1\n"
                    + "    primary: false\n";

    public final CrashSwitch crash = new CrashSwitch();
    public final InMemoryDataOperator<PlayerAccountEntity> accounts;
    public final InMemoryDataOperator<CurrencyBalanceEntity> balances;
    public final InMemoryDataOperator<WalletMergeClaimEntity> claims;
    public final EconomyConfig config = new EconomyConfig();
    public final CurrencyManager currencies;
    public final UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
    public final PluginLogger logger = mock(PluginLogger.class);
    public final EconomyServiceImpl service;

    private EconomyTestWorld(boolean cachedBackend, String currenciesYaml) {
        this.accounts = cachedBackend
                ? InMemoryDataOperator.cached("economy_accounts", PlayerAccountEntity.class, crash)
                : InMemoryDataOperator.relational("economy_accounts", PlayerAccountEntity.class, crash);
        this.balances = cachedBackend
                ? InMemoryDataOperator.cached("currency_balances", CurrencyBalanceEntity.class, crash)
                : InMemoryDataOperator.relational("currency_balances", CurrencyBalanceEntity.class, crash);
        this.claims = cachedBackend
                ? InMemoryDataOperator.cached("economy_wallet_merge_claim", WalletMergeClaimEntity.class, crash)
                : InMemoryDataOperator.relational("economy_wallet_merge_claim", WalletMergeClaimEntity.class, crash);
        this.currencies = new CurrencyManager(
                YamlConfiguration.loadConfiguration(new StringReader(currenciesYaml)));
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        lenient().when(plugin.getLogger()).thenReturn(logger);
        this.service = EconomyServiceImpl.createForTest(plugin, accounts, config, balances, currencies);
    }

    /** Storage like SQLite or MySQL: every write is durable when it returns. */
    public static EconomyTestWorld relational() {
        return new EconomyTestWorld(false, CURRENCIES_YAML);
    }

    /** Storage like the JSON backend: writes reach disk only through a flush. */
    public static EconomyTestWorld cached() {
        return new EconomyTestWorld(true, CURRENCIES_YAML);
    }

    public LeaderboardService leaderboard() {
        return LeaderboardService.createForTest(config, accounts, balances, currencies);
    }

    public PlayerAccountEntity seedAccount(UUID uuid, String name, double cash, double bank) {
        return accounts.seed(PlayerAccountEntity.builder()
                .uuid(uuid.toString()).playerName(name).cash(cash).bank(bank).build());
    }

    public CurrencyBalanceEntity seedBalance(UUID uuid, String currencyId, double cash, double bank) {
        return balances.seed(CurrencyBalanceEntity.builder()
                .uuid(uuid.toString()).currencyId(currencyId).cash(cash).bank(bank).build());
    }

    /** Seeds the claim a server holding (or having held) the merge left in the claim table. */
    public WalletMergeClaimEntity seedClaim(String owner, String heartbeat, String claimedAt) {
        WalletMergeClaimEntity claim = new WalletMergeClaimEntity(owner, heartbeat, claimedAt);
        claim.setId(MergeClaim.CLAIM_ID);
        return claims.seed(claim);
    }

    /** What a server restarted now would read, in all three tables. */
    public void restartFromDisk() {
        accounts.restartFromDisk();
        balances.restartFromDisk();
        claims.restartFromDisk();
    }

    /** The stored account row for {@code uuid}, or null. */
    public PlayerAccountEntity account(UUID uuid) {
        for (PlayerAccountEntity a : accounts.getAll()) {
            if (uuid.toString().equals(a.getUuid())) {
                return a;
            }
        }
        return null;
    }

    /** Every stored {@code currency_balances} row whose currency is {@code currencyId}. */
    public List<CurrencyBalanceEntity> balanceRows(String currencyId) {
        List<CurrencyBalanceEntity> out = new ArrayList<>();
        for (CurrencyBalanceEntity b : balances.getAll()) {
            if (currencyId.equals(b.getCurrencyId())) {
                out.add(b);
            }
        }
        return out;
    }
}
