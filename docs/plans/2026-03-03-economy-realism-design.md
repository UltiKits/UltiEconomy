# UltiEconomy v2.0 — Realism & Depth Upgrade

**Date**: 2026-03-03
**Goal**: Add multi-currency, money notes, and tax system to create a realistic economy for RPG servers.
**Approach**: Layered Build — multi-currency first (core refactor), then money notes, then tax system. Each layer ships independently.

## Feature 1: Multi-Currency System

### Data Model

New entity `CurrencyBalanceEntity` stored in `currency_balances` table:

| Column | Type | Description |
|--------|------|-------------|
| `uuid` | VARCHAR | Player UUID |
| `currency_id` | VARCHAR | Currency identifier (e.g. "coins", "gems") |
| `cash` | DOUBLE | Wallet balance for this currency |
| `bank` | DOUBLE | Bank balance for this currency |

### Currency Configuration

New config file `config/currencies.yml`:

```yaml
currencies:
  coins:
    display-name: "Coins"
    symbol: "$"
    initial-cash: 1000.0
    bank-enabled: true
    min-deposit: 100.0
    max-bank-balance: -1
    primary: true          # Maps to Vault
  gems:
    display-name: "Gems"
    symbol: "G"
    initial-cash: 0.0
    bank-enabled: false
    primary: false
  credits:
    display-name: "Credits"
    symbol: "C"
    initial-cash: 50.0
    bank-enabled: true
    min-deposit: 10.0
    max-bank-balance: 100000
    primary: false
```

Only one currency may be `primary: true`. That currency is exposed via Vault.

### Service Changes

- `EconomyService` interface gains `currencyId` parameter on all methods. Methods without `currencyId` default to primary currency for backward compatibility.
- `EconomyServiceImpl` queries `currency_balances` filtered by `currency_id`.
- `VaultEconomyProvider` always delegates to primary currency — zero changes for Vault consumers.
- `LeaderboardService` tracks per-currency leaderboards.
- `InterestService` distributes interest per currency (only currencies with `bank-enabled: true`).

### Commands

Existing commands work unchanged for primary currency. Optional currency suffix for extras:

| Command | Example |
|---------|---------|
| `/money` | Shows primary currency (unchanged) |
| `/money gems` | Shows gems balance |
| `/pay <player> <amount> [currency]` | `/pay Steve 100 gems` |
| `/deposit <amount> [currency]` | `/deposit 500 credits` |
| `/withdraw <amount> [currency]` | `/withdraw 200 credits` |
| `/eco give <player> <amount> [currency]` | Admin give in any currency |
| `/eco take <player> <amount> [currency]` | Admin take from any currency |
| `/eco set <player> <amount> [currency]` | Admin set for any currency |

### PlaceholderAPI

Existing placeholders stay for primary currency. New pattern for extra currencies:

- `%ultieconomy_<currency>_cash%` — e.g. `%ultieconomy_gems_cash%`
- `%ultieconomy_<currency>_bank%`
- `%ultieconomy_<currency>_total%`
- `%ultieconomy_<currency>_cash_formatted%`
- `%ultieconomy_<currency>_rank%`
- `%ultieconomy_<currency>_top_name_N%`
- `%ultieconomy_<currency>_top_balance_N%`

### Migration

On first boot with multi-currency, migrate existing `economy_accounts` rows into `currency_balances` with `currency_id = <primary currency id>`. Old table preserved as backup.

---

## Feature 2: Money Notes

### Concept

Convert virtual currency into a physical Minecraft item (paper with custom lore/NBT). Can be traded, dropped, stored in chests, and redeemed back to virtual balance.

### Item Format

A money note is a `PAPER` item with:

**Display name**: `$500 Money Note` (or `G100 Gems Note`)

**Lore**:
```
Currency: Coins
Value: $500.00
Created by: Steve
---
Right-click to redeem
```

**PersistentDataContainer tags**:
- `ultieconomy:currency` → `"coins"`
- `ultieconomy:value` → `500.0`
- `ultieconomy:creator` → UUID string
- `ultieconomy:created_at` → timestamp

### Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/note <amount> [currency]` | `ultieconomy.note` | Create a money note (deducts from cash) |
| `/note redeem` | `ultieconomy.note` | Redeem held money note (adds to cash) |

### Redemption

Two ways to redeem:
1. Right-click a money note → auto-redeems
2. `/note redeem` while holding the note

Both validate NBT, add value to cash, destroy the item.

### Config

In `config/config.yml`:

```yaml
money-notes:
  enabled: true
  max-value: 1000000
  min-value: 1.0
  allowed-currencies:
    - coins
    - gems
```

### New Classes

- `NoteCommand` — command executor for `/note`
- `NoteRedeemListener` — `PlayerInteractEvent` handler for right-click redemption
- `MoneyNoteFactory` — creates/validates money note ItemStacks

---

## Feature 3: Tax System

### Tax Types

**Transaction Tax**: A percentage deducted from every `/pay` transfer.
- Sender pays full amount, receiver gets amount minus tax, tax goes to treasury.
- Example: `/pay Steve 100` at 5% → sender -100, Steve +95, treasury +5.

**Wealth Tax**: Periodic tax on total wealth above configurable thresholds.
- Progressive brackets: different rates for different wealth levels.
- Only taxes the portion above each bracket threshold.
- Example: 0% on first 10k, 1% on 10k-100k, 2% on 100k+.

### Config

In `config/config.yml`:

```yaml
tax:
  enabled: true
  treasury-account: "treasury"

  transaction-tax:
    enabled: true
    rate: 0.05
    exempt-permission: "ultieconomy.tax.exempt"
    currencies:
      gems: 0.0              # No tax on gems

  wealth-tax:
    enabled: true
    interval: 3600
    brackets:
      - threshold: 10000
        rate: 0.0
      - threshold: 100000
        rate: 0.01
      - threshold: -1
        rate: 0.02
    exempt-permission: "ultieconomy.wealthtax.exempt"
    currencies:
      - coins
```

### Treasury Account

System account that accumulates tax. Admin commands:

| Command | Permission | Description |
|---------|------------|-------------|
| `/eco treasury` | `ultieconomy.admin` | View treasury balance per currency |
| `/eco treasury withdraw <amount> [currency]` | `ultieconomy.admin` | Withdraw from treasury |

### New Classes

- `TaxService` — `@Service` + `@ConditionalOnConfig(value = "config/config.yml", path = "tax.enabled")`
  - `collectTransactionTax(UUID sender, double amount, String currencyId)` → returns tax amount
  - `collectWealthTax()` → scheduled, iterates all accounts, applies brackets
  - Notifies online players when wealth tax is collected
- `TaxConfig` — config entity for tax settings
- `TreasuryEntity` — data entity for treasury balances per currency

### Integration

- `EconomyServiceImpl.transfer()` calls `TaxService.collectTransactionTax()` before completing transfer
- Wealth tax runs on separate scheduled task (same pattern as `InterestService`)

---

## Implementation Order

1. **Multi-Currency** — foundation refactor (entity, service, config, migration, commands, PAPI)
2. **Money Notes** — builds on multi-currency (note tied to currency_id)
3. **Tax System** — builds on multi-currency (per-currency rates, treasury per currency)

Each layer is independently shippable.

## New Files Summary

| File | Purpose |
|------|---------|
| `entity/CurrencyBalanceEntity.java` | New multi-currency data entity |
| `config/CurrencyConfig.java` | Currency definitions config |
| `config/TaxConfig.java` | Tax settings config |
| `service/TaxService.java` | Tax collection service |
| `entity/TreasuryEntity.java` | Treasury balance per currency |
| `commands/NoteCommand.java` | Money note create/redeem command |
| `listener/NoteRedeemListener.java` | Right-click note redemption |
| `factory/MoneyNoteFactory.java` | Creates/validates note items |

## Modified Files

| File | Changes |
|------|---------|
| `entity/PlayerAccountEntity.java` | Kept for migration; new code uses CurrencyBalanceEntity |
| `service/EconomyService.java` | Add currencyId-aware overloads |
| `service/EconomyServiceImpl.java` | Refactor to use CurrencyBalanceEntity, add currency routing |
| `service/InterestService.java` | Per-currency interest distribution |
| `service/LeaderboardService.java` | Per-currency leaderboards |
| `config/EconomyConfig.java` | Add tax + money-notes sections |
| `commands/MoneyCommand.java` | Add optional currency argument |
| `commands/PayCommand.java` | Add optional currency argument, transaction tax hook |
| `commands/DepositCommand.java` | Add optional currency argument |
| `commands/WithdrawCommand.java` | Add optional currency argument |
| `commands/EcoAdminCommand.java` | Add currency argument + treasury subcommands |
| `commands/BankCommand.java` | Add optional currency argument |
| `placeholder/EconomyPlaceholderExpansion.java` | Add per-currency placeholders |
| `vault/VaultEconomyProvider.java` | Delegate to primary currency explicitly |
| `listener/PlayerJoinListener.java` | Create accounts for all currencies on join |
| `UltiEconomy.java` | Register new services, commands, listeners |
| `resources/lang/en.json` | New i18n keys |
| `resources/lang/zh.json` | New i18n keys |
| `resources/config/currencies.yml` | New currency definitions |
