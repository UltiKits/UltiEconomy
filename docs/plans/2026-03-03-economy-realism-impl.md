# UltiEconomy v2.0 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add multi-currency, money notes, and tax system to UltiEconomy for realistic RPG server economies.

**Architecture:** Layered build — multi-currency is the foundation (new entity + refactored service), money notes build on top (physical items tied to currency), tax system completes the loop (money sinks per currency). Each layer is independently shippable with its own commit.

**Tech Stack:** Java 8, UltiTools-API 6.2.1, Spigot API, Vault, PlaceholderAPI, Mockito 5, JUnit 5, AssertJ

**Project root:** `/home/wisdomme/Code-Folder/Minecraft/Ulti/Modules/UltiEconomy/`
**Source base package:** `com.ultikits.plugins.economy`
**Test base:** `src/test/java/com/ultikits/plugins/economy/`
**Main base:** `src/main/java/com/ultikits/plugins/economy/`

---

## Phase 1: Multi-Currency (Foundation)

### Task 1: CurrencyDefinition POJO

A simple data holder for currency properties loaded from config. NOT a config entity — currencies are loaded manually from YAML sections.

**Files:**
- Create: `src/main/java/com/ultikits/plugins/economy/model/CurrencyDefinition.java`
- Test: `src/test/java/com/ultikits/plugins/economy/model/CurrencyDefinitionTest.java`

**Step 1: Write the failing test**

```java
package com.ultikits.plugins.economy.model;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CurrencyDefinition Tests")
class CurrencyDefinitionTest {

    @Test
    @DisplayName("builder creates currency with all fields")
    void builderCreates() {
        CurrencyDefinition def = CurrencyDefinition.builder()
                .id("coins")
                .displayName("Coins")
                .symbol("$")
                .initialCash(1000.0)
                .bankEnabled(true)
                .minDeposit(100.0)
                .maxBankBalance(-1)
                .primary(true)
                .build();

        assertThat(def.getId()).isEqualTo("coins");
        assertThat(def.getDisplayName()).isEqualTo("Coins");
        assertThat(def.getSymbol()).isEqualTo("$");
        assertThat(def.getInitialCash()).isEqualTo(1000.0);
        assertThat(def.isBankEnabled()).isTrue();
        assertThat(def.getMinDeposit()).isEqualTo(100.0);
        assertThat(def.getMaxBankBalance()).isEqualTo(-1.0);
        assertThat(def.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("defaults for non-primary currency")
    void defaults() {
        CurrencyDefinition def = CurrencyDefinition.builder()
                .id("gems")
                .displayName("Gems")
                .symbol("G")
                .build();

        assertThat(def.getInitialCash()).isEqualTo(0.0);
        assertThat(def.isBankEnabled()).isFalse();
        assertThat(def.isPrimary()).isFalse();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /home/wisdomme/Code-Folder/Minecraft/Ulti/Modules/UltiEconomy && mvn test -Dtest=CurrencyDefinitionTest -pl .`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```java
package com.ultikits.plugins.economy.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CurrencyDefinition {
    private final String id;
    private final String displayName;
    private final String symbol;
    @Builder.Default private final double initialCash = 0.0;
    @Builder.Default private final boolean bankEnabled = false;
    @Builder.Default private final double minDeposit = 0.0;
    @Builder.Default private final double maxBankBalance = -1;
    @Builder.Default private final boolean primary = false;
}
```

**Step 4: Run test to verify it passes**

Run: `cd /home/wisdomme/Code-Folder/Minecraft/Ulti/Modules/UltiEconomy && mvn test -Dtest=CurrencyDefinitionTest -pl .`
Expected: PASS

**Step 5: Commit**

```bash
cd /home/wisdomme/Code-Folder/Minecraft/Ulti/Modules/UltiEconomy
git add src/main/java/com/ultikits/plugins/economy/model/CurrencyDefinition.java src/test/java/com/ultikits/plugins/economy/model/CurrencyDefinitionTest.java
git commit -m "feat(multi-currency): add CurrencyDefinition POJO"
```

---

### Task 2: CurrencyManager — load currencies from YAML

Reads `config/currencies.yml` and provides lookups. The file is a map of currency ID → properties. Uses Bukkit `YamlConfiguration` directly (not `@ConfigEntity`) because the config is a dynamic map, not fixed fields.

**Files:**
- Create: `src/main/java/com/ultikits/plugins/economy/service/CurrencyManager.java`
- Create: `src/main/resources/config/currencies.yml`
- Test: `src/test/java/com/ultikits/plugins/economy/service/CurrencyManagerTest.java`

**Step 1: Write the failing test**

```java
package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.model.CurrencyDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.Collection;

@DisplayName("CurrencyManager Tests")
class CurrencyManagerTest {

    private static final String YAML_CONTENT =
            "currencies:\n" +
            "  coins:\n" +
            "    display-name: 'Coins'\n" +
            "    symbol: '$'\n" +
            "    initial-cash: 1000.0\n" +
            "    bank-enabled: true\n" +
            "    min-deposit: 100.0\n" +
            "    max-bank-balance: -1\n" +
            "    primary: true\n" +
            "  gems:\n" +
            "    display-name: 'Gems'\n" +
            "    symbol: 'G'\n" +
            "    initial-cash: 0.0\n" +
            "    bank-enabled: false\n" +
            "    primary: false\n";

    private CurrencyManager manager;

    @BeforeEach
    void setUp() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader(YAML_CONTENT));
        manager = new CurrencyManager(yaml);
    }

    @Test
    @DisplayName("loads all currencies from YAML")
    void loadsAll() {
        Collection<CurrencyDefinition> all = manager.getAllCurrencies();
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("getCurrency returns correct definition")
    void getCurrency() {
        CurrencyDefinition coins = manager.getCurrency("coins");
        assertThat(coins).isNotNull();
        assertThat(coins.getDisplayName()).isEqualTo("Coins");
        assertThat(coins.getSymbol()).isEqualTo("$");
        assertThat(coins.getInitialCash()).isEqualTo(1000.0);
        assertThat(coins.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("getPrimaryCurrency returns the primary one")
    void getPrimary() {
        CurrencyDefinition primary = manager.getPrimaryCurrency();
        assertThat(primary.getId()).isEqualTo("coins");
    }

    @Test
    @DisplayName("getCurrency returns null for unknown")
    void unknownReturnsNull() {
        assertThat(manager.getCurrency("unknown")).isNull();
    }

    @Test
    @DisplayName("hasCurrency checks existence")
    void hasCurrency() {
        assertThat(manager.hasCurrency("coins")).isTrue();
        assertThat(manager.hasCurrency("nope")).isFalse();
    }

    @Test
    @DisplayName("throws if no primary currency defined")
    void noPrimary() {
        String yaml = "currencies:\n  gems:\n    display-name: 'Gems'\n    symbol: 'G'\n    primary: false\n";
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        assertThatThrownBy(() -> new CurrencyManager(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primary");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /home/wisdomme/Code-Folder/Minecraft/Ulti/Modules/UltiEconomy && mvn test -Dtest=CurrencyManagerTest -pl .`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```java
package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.model.CurrencyDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

public class CurrencyManager {

    private final Map<String, CurrencyDefinition> currencies = new LinkedHashMap<>();
    private final String primaryCurrencyId;

    public CurrencyManager(YamlConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection("currencies");
        if (section == null) {
            throw new IllegalStateException("No 'currencies' section in currencies.yml");
        }

        String foundPrimary = null;
        for (String id : section.getKeys(false)) {
            ConfigurationSection cs = section.getConfigurationSection(id);
            if (cs == null) continue;

            CurrencyDefinition def = CurrencyDefinition.builder()
                    .id(id)
                    .displayName(cs.getString("display-name", id))
                    .symbol(cs.getString("symbol", ""))
                    .initialCash(cs.getDouble("initial-cash", 0.0))
                    .bankEnabled(cs.getBoolean("bank-enabled", false))
                    .minDeposit(cs.getDouble("min-deposit", 0.0))
                    .maxBankBalance(cs.getDouble("max-bank-balance", -1))
                    .primary(cs.getBoolean("primary", false))
                    .build();

            currencies.put(id, def);
            if (def.isPrimary()) {
                if (foundPrimary != null) {
                    throw new IllegalStateException("Multiple primary currencies: " + foundPrimary + " and " + id);
                }
                foundPrimary = id;
            }
        }

        if (foundPrimary == null) {
            throw new IllegalStateException("No primary currency defined in currencies.yml");
        }
        this.primaryCurrencyId = foundPrimary;
    }

    public CurrencyDefinition getCurrency(String id) {
        return currencies.get(id);
    }

    public CurrencyDefinition getPrimaryCurrency() {
        return currencies.get(primaryCurrencyId);
    }

    public String getPrimaryCurrencyId() {
        return primaryCurrencyId;
    }

    public boolean hasCurrency(String id) {
        return currencies.containsKey(id);
    }

    public Collection<CurrencyDefinition> getAllCurrencies() {
        return Collections.unmodifiableCollection(currencies.values());
    }
}
```

Also create the default currencies YAML at `src/main/resources/config/currencies.yml`:

```yaml
# Currency definitions / 货币定义
# Each currency has its own wallet. One must be primary (maps to Vault).
# 每种货币有独立的钱包。必须有一个设为 primary（映射到 Vault）。

currencies:
  coins:
    display-name: 'Coins'
    symbol: '$'
    initial-cash: 1000.0
    bank-enabled: true
    min-deposit: 100.0
    max-bank-balance: -1
    primary: true
```

**Step 4: Run test to verify it passes**

Run: `cd /home/wisdomme/Code-Folder/Minecraft/Ulti/Modules/UltiEconomy && mvn test -Dtest=CurrencyManagerTest -pl .`
Expected: PASS

**Step 5: Commit**

```bash
cd /home/wisdomme/Code-Folder/Minecraft/Ulti/Modules/UltiEconomy
git add src/main/java/com/ultikits/plugins/economy/service/CurrencyManager.java src/test/java/com/ultikits/plugins/economy/service/CurrencyManagerTest.java src/main/resources/config/currencies.yml
git commit -m "feat(multi-currency): add CurrencyManager to load currency definitions from YAML"
```

---

### Task 3: CurrencyBalanceEntity — new data entity

Replace `PlayerAccountEntity` as the data model. Each row is one player + one currency.

**Files:**
- Create: `src/main/java/com/ultikits/plugins/economy/entity/CurrencyBalanceEntity.java`
- Test: `src/test/java/com/ultikits/plugins/economy/entity/CurrencyBalanceEntityTest.java`

**Step 1: Write the failing test**

```java
package com.ultikits.plugins.economy.entity;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CurrencyBalanceEntity Tests")
class CurrencyBalanceEntityTest {

    @Test
    @DisplayName("builder creates entity with all fields")
    void builderCreates() {
        CurrencyBalanceEntity entity = CurrencyBalanceEntity.builder()
                .uuid("550e8400-e29b-41d4-a716-446655440000")
                .currencyId("coins")
                .cash(1000.0)
                .bank(500.0)
                .build();

        assertThat(entity.getUuid()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(entity.getCurrencyId()).isEqualTo("coins");
        assertThat(entity.getCash()).isEqualTo(1000.0);
        assertThat(entity.getBank()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("getTotalWealth returns cash + bank")
    void totalWealth() {
        CurrencyBalanceEntity entity = CurrencyBalanceEntity.builder()
                .uuid("test-uuid")
                .currencyId("coins")
                .cash(300.0)
                .bank(700.0)
                .build();

        assertThat(entity.getTotalWealth()).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("defaults to zero balances")
    void defaults() {
        CurrencyBalanceEntity entity = CurrencyBalanceEntity.builder()
                .uuid("test-uuid")
                .currencyId("gems")
                .build();

        assertThat(entity.getCash()).isEqualTo(0.0);
        assertThat(entity.getBank()).isEqualTo(0.0);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=CurrencyBalanceEntityTest -pl .`
Expected: FAIL

**Step 3: Write minimal implementation**

```java
package com.ultikits.plugins.economy.entity;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.annotations.Column;
import com.ultikits.ultitools.annotations.Table;
import lombok.*;

@Table("currency_balances")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyBalanceEntity extends BaseDataEntity<String> {

    @Column("uuid")
    private String uuid;

    @Column("currency_id")
    private String currencyId;

    @Column(value = "cash", type = "DOUBLE")
    @Builder.Default
    private double cash = 0.0;

    @Column(value = "bank", type = "DOUBLE")
    @Builder.Default
    private double bank = 0.0;

    public double getTotalWealth() {
        return cash + bank;
    }
}
```

**Step 4: Run test, verify pass**

Run: `mvn test -Dtest=CurrencyBalanceEntityTest -pl .`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/entity/CurrencyBalanceEntity.java src/test/java/com/ultikits/plugins/economy/entity/CurrencyBalanceEntityTest.java
git commit -m "feat(multi-currency): add CurrencyBalanceEntity for per-currency balances"
```

---

### Task 4: Refactor EconomyService interface — add currency-aware methods

Add overloads with `currencyId` parameter. Keep existing single-currency methods as defaults that delegate to primary currency.

**Files:**
- Modify: `src/main/java/com/ultikits/plugins/economy/service/EconomyService.java`

**Step 1: Write the updated interface**

Add these new methods alongside the existing ones. The existing methods become the "primary currency" convenience API.

```java
// Add to EconomyService.java — new currency-aware methods:

CurrencyBalanceEntity getBalance(UUID playerUuid, String currencyId);

CurrencyBalanceEntity getOrCreateBalance(UUID playerUuid, String playerName, String currencyId);

boolean hasBalance(UUID playerUuid, String currencyId);

double getCash(UUID playerUuid, String currencyId);

double getBank(UUID playerUuid, String currencyId);

double getTotalWealth(UUID playerUuid, String currencyId);

boolean setCash(UUID playerUuid, double amount, String currencyId);

boolean setBank(UUID playerUuid, double amount, String currencyId);

boolean addCash(UUID playerUuid, double amount, String currencyId);

boolean addBank(UUID playerUuid, double amount, String currencyId);

boolean takeCash(UUID playerUuid, double amount, String currencyId);

boolean takeBank(UUID playerUuid, double amount, String currencyId);

boolean transfer(UUID from, UUID to, double amount, String currencyId);

boolean depositToBank(UUID playerUuid, double amount, String currencyId);

boolean withdrawFromBank(UUID playerUuid, double amount, String currencyId);

String formatAmount(double amount, String currencyId);

// New method to get the primary currency ID
String getPrimaryCurrencyId();
```

Note: import `CurrencyBalanceEntity` at the top.

**Step 2: Verify compilation fails (tests use the interface)**

Run: `mvn compile -pl .`
Expected: FAIL — `EconomyServiceImpl` doesn't implement the new methods yet. This is expected; Task 5 implements them.

**Step 3: Commit the interface change alone**

```bash
git add src/main/java/com/ultikits/plugins/economy/service/EconomyService.java
git commit -m "feat(multi-currency): add currency-aware method overloads to EconomyService interface"
```

---

### Task 5: Refactor EconomyServiceImpl — implement currency-aware methods

The big refactor. `EconomyServiceImpl` now uses `CurrencyBalanceEntity` + `CurrencyManager` internally. Existing no-currency methods delegate to primary.

**Files:**
- Modify: `src/main/java/com/ultikits/plugins/economy/service/EconomyServiceImpl.java`
- Modify: `src/test/java/com/ultikits/plugins/economy/service/EconomyServiceImplTest.java`

**Step 1: Write failing tests for new currency-aware methods**

Add a new `@Nested` test class inside `EconomyServiceImplTest`:

```java
@Nested
@DisplayName("Multi-Currency Operations")
class MultiCurrencyOps {

    @Test
    @DisplayName("getCash with currencyId queries correct currency")
    void getCashWithCurrency() {
        CurrencyBalanceEntity balance = CurrencyBalanceEntity.builder()
                .uuid(PLAYER_UUID.toString())
                .currencyId("gems")
                .cash(250.0)
                .bank(0.0)
                .build();
        mockCurrencyQueryReturns(PLAYER_UUID, "gems", balance);

        assertThat(service.getCash(PLAYER_UUID, "gems")).isEqualTo(250.0);
    }

    @Test
    @DisplayName("transfer with currencyId moves correct currency")
    void transferWithCurrency() throws Exception {
        CurrencyBalanceEntity sender = CurrencyBalanceEntity.builder()
                .uuid(PLAYER_UUID.toString()).currencyId("gems").cash(500.0).bank(0.0).build();
        CurrencyBalanceEntity receiver = CurrencyBalanceEntity.builder()
                .uuid(OTHER_UUID.toString()).currencyId("gems").cash(100.0).bank(0.0).build();

        mockCurrencyQueryReturns(PLAYER_UUID, "gems", sender);
        mockCurrencyQueryReturns(OTHER_UUID, "gems", receiver);

        boolean result = service.transfer(PLAYER_UUID, OTHER_UUID, 200.0, "gems");
        assertThat(result).isTrue();
        assertThat(sender.getCash()).isEqualTo(300.0);
        assertThat(receiver.getCash()).isEqualTo(300.0);
    }

    @Test
    @DisplayName("getOrCreateBalance creates new balance for new currency")
    void getOrCreateNew() {
        mockCurrencyQueryReturns(PLAYER_UUID, "credits", null);

        CurrencyBalanceEntity result = service.getOrCreateBalance(PLAYER_UUID, "Steve", "credits");
        assertThat(result).isNotNull();
        assertThat(result.getCurrencyId()).isEqualTo("credits");
        verify(currencyDataOperator).insert(any(CurrencyBalanceEntity.class));
    }
}
```

This requires adding `@Mock DataOperator<CurrencyBalanceEntity> currencyDataOperator` and a `mockCurrencyQueryReturns` helper to the test class setUp. The service constructor now takes both the old `dataOperator` (for migration) and `currencyDataOperator` + `CurrencyManager`.

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=EconomyServiceImplTest -pl .`
Expected: FAIL — new methods not implemented

**Step 3: Implement**

Refactor `EconomyServiceImpl`:
- Add `CurrencyManager` and `DataOperator<CurrencyBalanceEntity>` fields
- New constructor that accepts both operators + manager
- Existing no-currencyId methods delegate: `getCash(uuid) → getCash(uuid, getPrimaryCurrencyId())`
- Currency-aware methods query `currencyDataOperator` with `where("uuid").eq(uuid).where("currency_id").eq(currencyId)`
- `formatAmount(amount, currencyId)` uses `CurrencyManager.getCurrency(currencyId).getSymbol()`
- `getPrimaryCurrencyId()` delegates to `CurrencyManager`

**Step 4: Run ALL existing + new tests**

Run: `mvn test -pl .`
Expected: ALL PASS — existing tests still work because no-arg methods delegate to primary

**Step 5: Commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/service/EconomyServiceImpl.java src/test/java/com/ultikits/plugins/economy/service/EconomyServiceImplTest.java
git commit -m "feat(multi-currency): implement currency-aware EconomyServiceImpl"
```

---

### Task 6: Update commands — add optional currency argument

All commands (`MoneyCommand`, `PayCommand`, `DepositCommand`, `WithdrawCommand`, `BankCommand`, `EcoAdminCommand`) get an optional trailing `[currency]` argument. When omitted, defaults to primary.

**Files:**
- Modify: All 6 command files
- Modify: All 6 command test files

**Step 1: Update MoneyCommand first (pattern for others)**

Add a new `@CmdMapping(format = "<currency>")` method that shows balance for a specific currency. Update existing tests + add new test for currency arg.

Test addition in `MoneyCommandTest`:
```java
@Test
@DisplayName("shows balance for specific currency")
void showsCurrencyBalance() {
    when(economyService.getCash(PLAYER_UUID, "gems")).thenReturn(250.0);
    when(economyService.getBank(PLAYER_UUID, "gems")).thenReturn(0.0);
    when(economyService.getTotalWealth(PLAYER_UUID, "gems")).thenReturn(250.0);
    when(economyService.formatAmount(anyDouble(), eq("gems"))).thenAnswer(inv -> "G" + String.format("%.2f", (double) inv.getArgument(0)));

    command.onCurrencyBalance(player, "gems");

    verify(player, atLeastOnce()).sendMessage(contains("250"));
}
```

**Step 2: Repeat the pattern for all 6 commands**

- `PayCommand`: add `@CmdMapping(format = "<player> <amount> <currency>")` → `onPayWithCurrency()`
- `DepositCommand`: add `@CmdMapping(format = "<amount> <currency>")` → `onDepositCurrency()`
- `WithdrawCommand`: add `@CmdMapping(format = "<amount> <currency>")` → `onWithdrawCurrency()`
- `BankCommand`: add `@CmdMapping(format = "<currency>")` → `onBankCurrency()`
- `EcoAdminCommand`: add currency param to give/take/set/check mappings

**Step 3: Run ALL tests**

Run: `mvn test -pl .`
Expected: ALL PASS

**Step 4: Commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/commands/ src/test/java/com/ultikits/plugins/economy/commands/
git commit -m "feat(multi-currency): add optional currency argument to all commands"
```

---

### Task 7: Update PlaceholderAPI — per-currency placeholders

**Files:**
- Modify: `src/main/java/com/ultikits/plugins/economy/placeholder/EconomyPlaceholderExpansion.java`
- Modify: `src/test/java/com/ultikits/plugins/economy/placeholder/EconomyPlaceholderExpansionTest.java`

**Step 1: Write failing test**

```java
@Test
@DisplayName("currency-specific cash placeholder")
void currencySpecificCash() {
    when(economyService.getCash(player.getUniqueId(), "gems")).thenReturn(250.0);
    String result = expansion.onRequest(player, "gems_cash");
    assertThat(result).isEqualTo("250.00");
}

@Test
@DisplayName("currency-specific rank placeholder")
void currencySpecificRank() {
    when(leaderboardService.getPlayerRank(player.getUniqueId(), "gems")).thenReturn(3);
    String result = expansion.onRequest(player, "gems_rank");
    assertThat(result).isEqualTo("3");
}
```

**Step 2: Implement**

In `onRequest()`, check if the param contains an underscore prefix matching a known currency ID. Pattern: if `param` starts with `<currencyId>_`, extract the suffix and dispatch to the currency-aware service method. Otherwise, fall through to existing primary-currency logic.

**Step 3: Run tests, commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/placeholder/ src/test/java/com/ultikits/plugins/economy/placeholder/
git commit -m "feat(multi-currency): add per-currency PlaceholderAPI placeholders"
```

---

### Task 8: Update InterestService + LeaderboardService for multi-currency

**Files:**
- Modify: `src/main/java/com/ultikits/plugins/economy/service/InterestService.java`
- Modify: `src/main/java/com/ultikits/plugins/economy/service/LeaderboardService.java`
- Modify: Both test files

**Step 1: InterestService — iterate all bank-enabled currencies**

`distributeInterest()` now queries `CurrencyBalanceEntity` grouped by currency, only processing currencies where `bankEnabled == true`. Test that interest is paid for each bank-enabled currency independently.

**Step 2: LeaderboardService — per-currency leaderboards**

Add `refreshLeaderboard(String currencyId)` and `getTopPlayers(int count, String currencyId)`. Cache becomes `Map<String, List<LeaderboardEntry>>`. Existing no-arg methods delegate to primary.

**Step 3: Run tests, commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/service/InterestService.java src/main/java/com/ultikits/plugins/economy/service/LeaderboardService.java src/test/java/com/ultikits/plugins/economy/service/
git commit -m "feat(multi-currency): update InterestService and LeaderboardService for per-currency support"
```

---

### Task 9: Update PlayerJoinListener + VaultEconomyProvider

**Files:**
- Modify: `src/main/java/com/ultikits/plugins/economy/listener/PlayerJoinListener.java`
- Modify: `src/main/java/com/ultikits/plugins/economy/vault/VaultEconomyProvider.java`
- Modify: Both test files

**Step 1: PlayerJoinListener**

On join, call `economyService.getOrCreateBalance(uuid, name, currencyId)` for EACH currency defined in `CurrencyManager`.

**Step 2: VaultEconomyProvider**

Explicitly pass primary currency ID in all delegated calls. E.g.:
```java
public double getBalance(OfflinePlayer player) {
    return economyService.getCash(player.getUniqueId(), economyService.getPrimaryCurrencyId());
}
```

**Step 3: Run tests, commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/listener/ src/main/java/com/ultikits/plugins/economy/vault/ src/test/java/com/ultikits/plugins/economy/listener/ src/test/java/com/ultikits/plugins/economy/vault/
git commit -m "feat(multi-currency): update PlayerJoinListener and VaultEconomyProvider for multi-currency"
```

---

### Task 10: Update UltiEconomy main class + migration + i18n

**Files:**
- Modify: `src/main/java/com/ultikits/plugins/economy/UltiEconomy.java`
- Modify: `src/main/resources/lang/en.json`
- Modify: `src/main/resources/lang/zh.json`
- Modify: `src/main/java/com/ultikits/plugins/economy/config/EconomyConfig.java`

**Step 1: UltiEconomy.registerSelf()**

Load `CurrencyManager` from `config/currencies.yml`. Pass it to `EconomyServiceImpl`. Register `DataOperator<CurrencyBalanceEntity>`. On first boot, run migration: query all `PlayerAccountEntity` rows, insert as `CurrencyBalanceEntity` with `currency_id = primaryCurrencyId`.

**Step 2: i18n — add new keys**

Add to both `en.json` and `zh.json`:
```json
"货币不存在": "Currency not found",
"当前货币: %s": "Currency: %s",
"该货币不支持银行功能": "This currency does not support banking"
```

**Step 3: Run ALL tests**

Run: `mvn test -pl .`
Expected: ALL PASS

**Step 4: Commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/UltiEconomy.java src/main/resources/lang/ src/main/java/com/ultikits/plugins/economy/config/EconomyConfig.java
git commit -m "feat(multi-currency): wire CurrencyManager into main plugin, add migration, update i18n"
```

---

### Task 11: Run full test suite + integration check

**Step 1: Run all tests**

```bash
cd /home/wisdomme/Code-Folder/Minecraft/Ulti/Modules/UltiEconomy && mvn clean test -pl .
```

Expected: ALL PASS, >80% coverage

**Step 2: Build the JAR**

```bash
mvn package -DskipTests -pl .
```

Expected: BUILD SUCCESS

**Step 3: Commit if any fixes needed, then tag Phase 1 complete**

```bash
git commit -m "feat(multi-currency): Phase 1 complete — multi-currency support"
```

---

## Phase 2: Money Notes

### Task 12: MoneyNoteFactory — creates and validates note items

**Files:**
- Create: `src/main/java/com/ultikits/plugins/economy/factory/MoneyNoteFactory.java`
- Test: `src/test/java/com/ultikits/plugins/economy/factory/MoneyNoteFactoryTest.java`

**Step 1: Write failing test**

Test that `createNote(currencyId, amount, creatorUuid, creatorName)` returns an `ItemStack` of `Material.PAPER` with correct display name and lore. Test that `isMoneyNote(itemStack)` returns true for notes, false for regular paper. Test that `getNoteValue(itemStack)` extracts the stored value. Test that `getNoteCurrency(itemStack)` extracts the currency ID.

Note: PersistentDataContainer needs a mock or test helper. Since UltiTools targets MC 1.21+, PersistentDataContainer is available. For unit tests, mock `ItemMeta` and `PersistentDataContainer`.

**Step 2: Implement MoneyNoteFactory**

Uses `NamespacedKey` with plugin instance from `Bukkit.getPluginManager().getPlugin("UltiTools")`. Stores data in `PersistentDataContainer`:
- `new NamespacedKey(plugin, "currency")` → `PersistentDataType.STRING`
- `new NamespacedKey(plugin, "value")` → `PersistentDataType.DOUBLE`
- `new NamespacedKey(plugin, "creator")` → `PersistentDataType.STRING`
- `new NamespacedKey(plugin, "created_at")` → `PersistentDataType.LONG`

**Step 3: Run tests, commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/factory/ src/test/java/com/ultikits/plugins/economy/factory/
git commit -m "feat(money-notes): add MoneyNoteFactory for creating and validating note items"
```

---

### Task 13: NoteCommand — /note create and redeem

**Files:**
- Create: `src/main/java/com/ultikits/plugins/economy/commands/NoteCommand.java`
- Test: `src/test/java/com/ultikits/plugins/economy/commands/NoteCommandTest.java`

**Step 1: Write failing test**

Test `/note 500` creates a note and deducts cash. Test `/note 500 gems` uses gems currency. Test `/note redeem` while holding a note adds cash and removes item. Test error cases: insufficient balance, invalid amount, bank-only currency, holding non-note.

**Step 2: Implement**

```java
@CmdExecutor(permission = "ultieconomy.note", description = "创建/兑换纸币", alias = {"note"})
public class NoteCommand extends AbstractCommandExecutor {
    // @CmdMapping(format = "<amount>") for primary currency
    // @CmdMapping(format = "<amount> <currency>") for specific currency
    // @CmdMapping(format = "redeem") for redemption
}
```

**Step 3: Run tests, commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/commands/NoteCommand.java src/test/java/com/ultikits/plugins/economy/commands/NoteCommandTest.java
git commit -m "feat(money-notes): add NoteCommand for /note create and redeem"
```

---

### Task 14: NoteRedeemListener — right-click redemption

**Files:**
- Create: `src/main/java/com/ultikits/plugins/economy/listener/NoteRedeemListener.java`
- Test: `src/test/java/com/ultikits/plugins/economy/listener/NoteRedeemListenerTest.java`

**Step 1: Write failing test**

Test that `PlayerInteractEvent` with `Action.RIGHT_CLICK_AIR` or `RIGHT_CLICK_BLOCK` while holding a money note calls `economyService.addCash()` and removes the item. Test that non-note items are ignored. Test that the event is cancelled.

**Step 2: Implement**

```java
@EventListener
public class NoteRedeemListener implements Listener {
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Check action is right-click
        // Check held item is money note via MoneyNoteFactory.isMoneyNote()
        // Extract value and currency, add to player cash
        // Remove one item from hand
        // Cancel event
    }
}
```

**Step 3: Run tests, commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/listener/NoteRedeemListener.java src/test/java/com/ultikits/plugins/economy/listener/NoteRedeemListenerTest.java
git commit -m "feat(money-notes): add right-click redemption listener"
```

---

### Task 15: Wire money notes + config + i18n

**Files:**
- Modify: `src/main/java/com/ultikits/plugins/economy/config/EconomyConfig.java` — add money-notes config fields
- Modify: `src/main/java/com/ultikits/plugins/economy/UltiEconomy.java` — register NoteCommand + NoteRedeemListener
- Modify: `src/main/resources/lang/en.json` + `zh.json` — money note i18n keys
- Modify: `src/main/resources/config/config.yml` — add money-notes section

**New i18n keys:**
```json
"纸币已创建: %s": "Money note created: %s",
"纸币已兑换: %s": "Money note redeemed: %s",
"纸币功能未启用": "Money notes are not enabled",
"该货币不支持纸币": "This currency does not support money notes",
"纸币金额超出范围": "Note amount out of range",
"手中没有纸币": "You are not holding a money note"
```

**Run ALL tests, commit:**
```bash
mvn clean test -pl .
git add -A
git commit -m "feat(money-notes): Phase 2 complete — money notes with create, redeem, and right-click"
```

---

## Phase 3: Tax System

### Task 16: TaxConfig — tax settings config entity

**Files:**
- Create: `src/main/java/com/ultikits/plugins/economy/config/TaxConfig.java`
- Test: `src/test/java/com/ultikits/plugins/economy/config/TaxConfigTest.java`

**Step 1: Write failing test**

Test default values: `transactionTaxEnabled=true`, `transactionTaxRate=0.05`, `wealthTaxEnabled=true`, `wealthTaxInterval=3600`.

**Step 2: Implement**

```java
@Getter @Setter
@ConfigEntity("config/config.yml")
public class TaxConfig extends AbstractConfigEntity {
    public TaxConfig() { super("config/config.yml"); }

    @ConfigEntry(path = "tax.enabled") private boolean taxEnabled = true;
    @ConfigEntry(path = "tax.transaction-tax.enabled") private boolean transactionTaxEnabled = true;
    @ConfigEntry(path = "tax.transaction-tax.rate") private double transactionTaxRate = 0.05;
    @ConfigEntry(path = "tax.transaction-tax.exempt-permission") private String transactionTaxExemptPermission = "ultieconomy.tax.exempt";
    @ConfigEntry(path = "tax.wealth-tax.enabled") private boolean wealthTaxEnabled = true;
    @ConfigEntry(path = "tax.wealth-tax.interval") private int wealthTaxInterval = 3600;
    @ConfigEntry(path = "tax.wealth-tax.exempt-permission") private String wealthTaxExemptPermission = "ultieconomy.wealthtax.exempt";
}
```

Note: Progressive brackets are loaded from YAML section directly (not `@ConfigEntry`), same pattern as currencies.

**Step 3: Run tests, commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/config/TaxConfig.java src/test/java/com/ultikits/plugins/economy/config/TaxConfigTest.java
git commit -m "feat(tax): add TaxConfig entity"
```

---

### Task 17: TreasuryEntity — treasury balance storage

**Files:**
- Create: `src/main/java/com/ultikits/plugins/economy/entity/TreasuryEntity.java`
- Test: `src/test/java/com/ultikits/plugins/economy/entity/TreasuryEntityTest.java`

**Step 1: Implement**

```java
@Table("economy_treasury")
@Data @EqualsAndHashCode(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class TreasuryEntity extends BaseDataEntity<String> {
    @Column("currency_id") private String currencyId;
    @Column(value = "balance", type = "DOUBLE") @Builder.Default private double balance = 0.0;
}
```

**Step 2: Test, commit**

---

### Task 18: TaxService — transaction tax + wealth tax

**Files:**
- Create: `src/main/java/com/ultikits/plugins/economy/service/TaxService.java`
- Test: `src/test/java/com/ultikits/plugins/economy/service/TaxServiceTest.java`

**Step 1: Write failing tests**

```java
@Nested
@DisplayName("Transaction Tax")
class TransactionTax {

    @Test
    @DisplayName("calculates 5% transaction tax")
    void calculates5Percent() {
        when(taxConfig.getTransactionTaxRate()).thenReturn(0.05);
        double tax = taxService.calculateTransactionTax(100.0, "coins");
        assertThat(tax).isEqualTo(5.0);
    }

    @Test
    @DisplayName("returns 0 when transaction tax disabled")
    void disabledReturnsZero() {
        when(taxConfig.isTransactionTaxEnabled()).thenReturn(false);
        double tax = taxService.calculateTransactionTax(100.0, "coins");
        assertThat(tax).isEqualTo(0.0);
    }
}

@Nested
@DisplayName("Wealth Tax")
class WealthTax {

    @Test
    @DisplayName("progressive brackets: 0% below 10k, 1% on 10k-100k, 2% above")
    void progressiveBrackets() {
        // Player has 150,000 total wealth
        // Bracket 1: 0-10,000 @ 0% = 0
        // Bracket 2: 10,000-100,000 @ 1% = 900
        // Bracket 3: 100,000-150,000 @ 2% = 1000
        // Total tax = 1900
        double tax = taxService.calculateWealthTax(150000.0, brackets);
        assertThat(tax).isCloseTo(1900.0, within(0.01));
    }

    @Test
    @DisplayName("no tax below first bracket")
    void belowFirstBracket() {
        double tax = taxService.calculateWealthTax(5000.0, brackets);
        assertThat(tax).isEqualTo(0.0);
    }
}
```

**Step 2: Implement TaxService**

```java
@Service
@ConditionalOnConfig(value = "config/config.yml", path = "tax.enabled")
public class TaxService {
    // collectTransactionTax(UUID sender, double amount, String currencyId) → deducts tax, adds to treasury
    // collectWealthTax() → iterates all balances, applies brackets, collects to treasury
    // calculateTransactionTax(double amount, String currencyId) → pure calculation (testable)
    // calculateWealthTax(double totalWealth, List<TaxBracket> brackets) → pure calculation (testable)
    // getTreasuryBalance(String currencyId) → reads from TreasuryEntity
    // withdrawFromTreasury(double amount, String currencyId) → admin withdrawal
}
```

**Step 3: Run tests, commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/service/TaxService.java src/test/java/com/ultikits/plugins/economy/service/TaxServiceTest.java
git commit -m "feat(tax): implement TaxService with transaction and wealth tax"
```

---

### Task 19: Hook transaction tax into EconomyServiceImpl.transfer()

**Files:**
- Modify: `src/main/java/com/ultikits/plugins/economy/service/EconomyServiceImpl.java`
- Modify: `src/test/java/com/ultikits/plugins/economy/service/EconomyServiceImplTest.java`

**Step 1: Write failing test**

```java
@Test
@DisplayName("transfer applies transaction tax when TaxService present")
void transferWithTax() {
    // Set up: sender has 1000, receiver has 0
    // TaxService returns 5% tax on 100 = 5.0
    // After transfer: sender has 900, receiver has 95, treasury gets 5
    when(taxService.calculateTransactionTax(100.0, "coins")).thenReturn(5.0);
    boolean result = service.transfer(PLAYER_UUID, OTHER_UUID, 100.0, "coins");
    assertThat(result).isTrue();
    assertThat(senderAccount.getCash()).isEqualTo(900.0);
    assertThat(receiverAccount.getCash()).isEqualTo(95.0);
    verify(taxService).depositToTreasury(5.0, "coins");
}
```

**Step 2: Implement**

In `transfer()`, if `taxService != null` (it's `@ConditionalOnConfig` so may not exist), calculate tax, reduce receiver amount, deposit tax to treasury.

**Step 3: Run tests, commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/service/EconomyServiceImpl.java src/test/java/com/ultikits/plugins/economy/service/EconomyServiceImplTest.java
git commit -m "feat(tax): hook transaction tax into transfer()"
```

---

### Task 20: EcoAdminCommand — treasury subcommands

**Files:**
- Modify: `src/main/java/com/ultikits/plugins/economy/commands/EcoAdminCommand.java`
- Modify: `src/test/java/com/ultikits/plugins/economy/commands/EcoAdminCommandTest.java`

**Step 1: Add treasury subcommands**

```java
@CmdMapping(format = "treasury")
public void onTreasury(@CmdSender CommandSender sender) {
    // Show treasury balance for all currencies
}

@CmdMapping(format = "treasury withdraw <amount>")
public void onTreasuryWithdraw(@CmdSender CommandSender sender, @CmdParam("amount") String amountStr) {
    // Withdraw from primary currency treasury
}

@CmdMapping(format = "treasury withdraw <amount> <currency>")
public void onTreasuryWithdrawCurrency(@CmdSender CommandSender sender, @CmdParam("amount") String amountStr, @CmdParam("currency") String currencyId) {
    // Withdraw from specific currency treasury
}
```

**Step 2: Run tests, commit**

```bash
git add src/main/java/com/ultikits/plugins/economy/commands/EcoAdminCommand.java src/test/java/com/ultikits/plugins/economy/commands/EcoAdminCommandTest.java
git commit -m "feat(tax): add treasury subcommands to /eco"
```

---

### Task 21: Wire tax system + config + i18n + final integration

**Files:**
- Modify: `UltiEconomy.java` — register TaxService, load tax brackets from YAML
- Modify: `config/config.yml` — add tax section
- Modify: `lang/en.json` + `zh.json` — tax i18n keys

**New i18n keys:**
```json
"交易税: %s (%.1f%%)": "Transaction tax: %s (%.1f%%)",
"财富税已收取: %s": "Wealth tax collected: %s",
"财富税豁免": "You are exempt from wealth tax",
"国库余额": "Treasury Balance",
"国库 %s: %s": "Treasury %s: %s",
"已从国库取出: %s": "Withdrew from treasury: %s",
"国库余额不足": "Insufficient treasury balance"
```

**Run ALL tests:**
```bash
cd /home/wisdomme/Code-Folder/Minecraft/Ulti/Modules/UltiEconomy && mvn clean test -pl .
```

Expected: ALL PASS, >80% coverage

**Commit:**
```bash
git add -A
git commit -m "feat(tax): Phase 3 complete — tax system with transaction tax, wealth tax, and treasury"
```

---

## Phase 4: Final Polish

### Task 22: Full test suite + build verification

```bash
cd /home/wisdomme/Code-Folder/Minecraft/Ulti/Modules/UltiEconomy
mvn clean test -pl .         # All tests pass
mvn clean package -pl .      # JAR builds
```

### Task 23: Update plugin.yml if needed

Check if new dependencies (none expected) or permissions need adding.

### Task 24: Final commit + version bump

```bash
cd /home/wisdomme/Code-Folder/Minecraft/Ulti/Modules/UltiEconomy
# Bump version in pom.xml to 2.0.0
git add pom.xml
git commit -m "chore: bump version to 2.0.0 for multi-currency + money notes + tax"
```

---

## Summary

| Phase | Tasks | Key Deliverable |
|-------|-------|-----------------|
| 1: Multi-Currency | 1-11 | CurrencyDefinition, CurrencyManager, CurrencyBalanceEntity, refactored service/commands/PAPI |
| 2: Money Notes | 12-15 | MoneyNoteFactory, NoteCommand, NoteRedeemListener |
| 3: Tax System | 16-21 | TaxConfig, TreasuryEntity, TaxService, treasury commands, transfer hook |
| 4: Polish | 22-24 | Full test suite, build, version bump |

Total: 24 tasks, ~162 tests expected (existing 162 + ~80 new).
