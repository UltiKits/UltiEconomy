package com.ultikits.plugins.economy.config;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import lombok.Getter;
import lombok.Setter;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Getter
@Setter
@ConfigEntity("config/config.yml")
public class EconomyConfig extends AbstractConfigEntity {

    /** The primary currency's name every earlier version shipped; the Java default of {@link #currencyName}. */
    static final String SHIPPED_CURRENCY_NAME = "Coins";

    /** The catalogue key of the primary currency's name in the server's language. */
    static final String CURRENCY_NAME_KEY = "economy.config.currency_name";

    /** Declared default of {@code interest.rate}, used while the file's value is outside 0 to 1. */
    static final double DEFAULT_INTEREST_RATE = 0.03;
    /** Declared default of {@code interest.max-interest}, used while the file's is neither -1 nor 0 and above. */
    static final double DEFAULT_MAX_INTEREST = 10000.0;
    /** Declared default of {@code tax.transaction-tax.rate}, used while the file's value is outside 0 to 1. */
    static final double DEFAULT_TRANSACTION_TAX_RATE = 0.05;
    /** Declared default of {@code bank.max-balance} (no cap), used while the file's is neither -1 nor above 0. */
    public static final double DEFAULT_MAX_BANK_BALANCE = -1;

    public EconomyConfig() {
        super("config/config.yml");
    }

    /**
     * Writes the primary currency's name in the server's language (maintainer decision 2026-09-25,
     * UltiKits/UltiEconomy#32): {@code currency-name} is replaced with {@code text}'s current text
     * when it is still built-in text -- the name an earlier version shipped, or this jar's text for it
     * in any language -- and differs from the current text. Any other value is the operator's and is
     * kept. Idempotent. Must run after the module's language is loaded (enable and
     * {@code onReload()}); the caller saves the file when this returns {@code true}.
     *
     * @param text the jar's catalogue for the server's language, as {@code getLocalizedText}
     * @return whether {@code currency-name} changed
     */
    public boolean materializeText(Function<String, String> text) {
        Map<String, Map<String, String>> jar = ConfigTextDefaults.jarCatalogues(EconomyConfig.class);
        String newName = ConfigTextDefaults.materialize(EconomyConfig.class, "currencyName", currencyName,
                ConfigTextDefaults.currentText(text, "", CURRENCY_NAME_KEY),
                ConfigTextDefaults.tracked(jar, "", CURRENCY_NAME_KEY, SHIPPED_CURRENCY_NAME));
        if (Objects.equals(newName, currencyName)) {
            return false;
        }
        currencyName = newName;
        return true;
    }

    /**
     * Every text that counts as the primary currency's built-in name: the name an earlier version
     * shipped, and this jar's text for it in every language.
     *
     * @return the built-in names
     */
    public static Set<String> builtInCurrencyNames() {
        return ConfigTextDefaults.tracked(ConfigTextDefaults.jarCatalogues(EconomyConfig.class), "",
                CURRENCY_NAME_KEY, SHIPPED_CURRENCY_NAME);
    }

    @ConfigEntry(path = "initial-cash", comment = "Initial cash for new players")
    private double initialCash = 1000.0;

    @ConfigEntry(path = "currency-name", comment = "Currency display name")
    private String currencyName = SHIPPED_CURRENCY_NAME;

    @ConfigEntry(path = "currency-symbol", comment = "Currency symbol prefix")
    private String currencySymbol = "$";

    @ConfigEntry(path = "bank.enabled", comment = "Enable bank feature")
    private boolean bankEnabled = true;

    @ConfigEntry(path = "bank.min-deposit", comment = "Minimum deposit amount")
    private double minDeposit = 100.0;

    @ConfigEntry(path = "bank.max-balance", comment = "Maximum bank balance (-1 = unlimited, otherwise above 0)")
    private double maxBankBalance = DEFAULT_MAX_BANK_BALANCE;

    @ConfigEntry(path = "interest.enabled", comment = "Pay bank interest every interest.interval seconds; read at each payment")
    private boolean interestEnabled = false;

    @ConfigEntry(path = "interest.rate", comment = "Interest rate per payment")
    private double interestRate = DEFAULT_INTEREST_RATE;

    /**
     * Seconds between interest payments, and before the first one after load. Bound to
     * {@code InterestService#payInterestIfEnabled} through the framework's config-bound
     * {@code @Scheduled} (UltiKits/UltiTools-Reborn#531): 1 to 107374182; an invalid value refuses the
     * module at load and is ignored, with a WARNING, at {@code /ul reload}.
     */
    @ConfigEntry(path = "interest.interval", comment = "Seconds between interest payments (1 to 107374182); /ul reload applies a change without moving the next payment earlier or later than the new interval allows")
    private int interestInterval = 1800;

    @ConfigEntry(path = "interest.max-interest", comment = "Max interest per payment")
    private double maxInterest = DEFAULT_MAX_INTEREST;

    /**
     * Seconds between leaderboard refreshes; the first runs at load. Bound to
     * {@code LeaderboardService#refreshAll} (UltiKits/UltiTools-Reborn#531): 1 to 107374182.
     */
    @ConfigEntry(path = "leaderboard.update-interval", comment = "Seconds between leaderboard refreshes (1 to 107374182)")
    private int leaderboardUpdateInterval = 60;

    // leaderboard.display-count was deleted in 6.3.0: nothing read it, and each top-N placeholder names
    // its own N (UltiKits/UltiEconomy#36). A file that still holds it is told so by RemovedConfigKeys.

    @ConfigEntry(path = "tax.enabled", comment = "Master switch for taxation: false collects no transaction tax")
    private boolean taxEnabled = true;

    @ConfigEntry(path = "tax.transaction-tax.enabled", comment = "Enable transaction tax on transfers")
    private boolean transactionTaxEnabled = true;

    @ConfigEntry(path = "tax.transaction-tax.rate", comment = "Transaction tax rate (0.05 = 5%)")
    private double transactionTaxRate = DEFAULT_TRANSACTION_TAX_RATE;

    @ConfigEntry(path = "tax.transaction-tax.exempt-permission", comment = "Permission to exempt from transaction tax")
    private String transactionTaxExemptPermission = "ultieconomy.tax.exempt";

    // tax.wealth-tax.enabled, .interval and .exempt-permission were deleted in 6.3.0: nothing ever
    // collected a wealth tax (UltiKits/UltiEconomy#27; the feature request is UltiKits/UltiEconomy#38).
    // A file that still holds them is told so by RemovedConfigKeys.

    // The three ranged values (UltiKits/UltiEconomy#29, maintainer decision 2026-09-27). The field keeps
    // what the file holds, so any save of this file - the module writes it when it puts the currency
    // name in the server's language - writes the operator's value back unchanged; the getter is what
    // the module reads, and it answers the declared default while the value is outside its range.
    // ConfigRanges warns about each one at load and after every reload.

    /** The interest rate the module uses: the file's value when it is from 0 to 1, else the default. */
    public double getInterestRate() {
        return isUsableFraction(interestRate) ? interestRate : DEFAULT_INTEREST_RATE;
    }

    /** The interest cap the module uses: the file's value when it is -1 or 0 and above, else the default. */
    public double getMaxInterest() {
        return isUsableCap(maxInterest) ? maxInterest : DEFAULT_MAX_INTEREST;
    }

    /** The transaction tax rate the module uses: the file's value when it is from 0 to 1, else the default. */
    public double getTransactionTaxRate() {
        return isUsableFraction(transactionTaxRate) ? transactionTaxRate : DEFAULT_TRANSACTION_TAX_RATE;
    }

    /**
     * The primary currency's bank cap the module uses: the file's value when it is -1 (no cap) or above
     * 0, else the default -1 (UltiKits/UltiEconomy#35). Every reader checks the cap as "above 0", so
     * before this 0 and every other negative also meant "no cap" while the setting documents only -1;
     * now such a value is refused and named at load and after every reload ({@link ConfigRanges}).
     */
    public double getMaxBankBalance() {
        return isUsableBankCap(maxBankBalance) ? maxBankBalance : DEFAULT_MAX_BANK_BALANCE;
    }

    /** {@code bank.max-balance} as the file holds it. */
    double writtenMaxBankBalance() {
        return maxBankBalance;
    }

    /** {@code interest.rate} as the file holds it. */
    double writtenInterestRate() {
        return interestRate;
    }

    /** {@code interest.max-interest} as the file holds it. */
    double writtenMaxInterest() {
        return maxInterest;
    }

    /** {@code tax.transaction-tax.rate} as the file holds it. */
    double writtenTransactionTaxRate() {
        return transactionTaxRate;
    }

    /** A fraction per payment or transfer: 0 to 1. */
    static boolean isUsableFraction(double value) {
        return value >= 0 && value <= 1;
    }

    /**
     * A cap: -1 for none, or a finite 0 and above. An infinite value (YAML {@code .inf}, or a number too
     * large for a double) is refused like any other value outside the range: "no cap" is written -1.
     */
    static boolean isUsableCap(double value) {
        return value == -1 || (value >= 0 && !Double.isInfinite(value));
    }

    /**
     * A bank cap: -1 for none, or a finite value above 0 (UltiKits/UltiEconomy#35). Unlike the interest
     * cap, 0 is refused: it reads as "no deposits" but every reader treated it as "no cap". The same
     * rule applies to a currency's own {@code max-bank-balance} in {@code currencies.yml}.
     *
     * @param value the cap as written
     * @return whether the module can use it as written
     */
    public static boolean isUsableBankCap(double value) {
        return value == -1 || (value > 0 && !Double.isInfinite(value));
    }
}
