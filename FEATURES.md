# UltiEconomy — Feature Inventory

This document catalogues every operator- or player-visible function, command, content item and
configuration key in this repository, as read directly from source. It is an internal reference
for UAT execution and issue reconciliation — the public description of these features lives on
<https://doc.ultikits.com/>. Update this file in the same pull request as any feature change.

## Conventions

- **ID grammar:** `<repo-slug>.<area>.<action>`, dot-separated, every segment lowercase ASCII
  drawn from `[a-z0-9-]`. `<repo-slug>` is the repository name lowercased with no separators —
  `ultieconomy` here, `ultitools`, `ultichat`, `ultibot`, and `ultitools-example` for
  `UltiTools-External-Example`. `<area>` is the feature section's slug. `<action>` is the verb.
  A `config` row is the one shape that exceeds three segments and is exempt from the
  lowercase-ASCII rule for its key-path suffix:
  `<repo-slug>.config.<file-stem>.<yml key path>`, the key path keeping its own dots and its own
  casing verbatim from the yml file — a config ID is a citation of the key, not a re-derived slug.
  An ID changes only when the feature's identity changes, never on rewording. IDs are unique
  within a repository.
- **Kind**, exactly these eight values: `command`, `config`, `event`, `gui`, `scheduled`,
  `placeholder`, `persistence`, `gate`. Each maps one-to-one onto a reconciliation-table line.
  This module has no `gui` rows (no GUI page class) and — despite the public documentation and
  this module's own `README.md` describing interest distribution and leaderboard refresh as
  periodic — no `scheduled` rows either: the reconciliation table below shows why (0 real
  `@Scheduled` sites, and nothing else drives either mechanism).
- **Tier**, exactly three: `player`, `admin`, `internal`. Judged from what the feature is for, not
  from whether it carries a permission string. A feature documented here as currently broken
  keeps the Tier its intended audience would have — brokenness is recorded in the Feature/Manual
  text and the linked issue, not by demoting a row to `internal`.
- **Manual**, exactly three: `detailed`, `brief`, `none`.
- **Target**, exactly four: `player`, `console`, `both`, or `n/a` — the first three read straight
  off `@CmdTarget` for a `command` row (a class or method carrying no `@CmdTarget` at all resolves
  to `both`, confirmed by reading `SenderTypeValidator`'s no-argument constructor in the
  framework); it is a property, not a tier. `n/a` is for every other Kind.
- **Permission:** the literal node string, `none`, or `n/a`. No `@CmdExecutor` in this module sets
  `requireOp = true`, so the `(requireOp=true)` suffix documented in the framework's own
  `FEATURES.md` never appears below.
- **Source:** `ClassName#member` — the class and member that actually reads or applies the
  feature — for every Kind, `config` included.
- **Row order:** by section, then by ID ascending within the section.
- **No manual prose:** no troubleshooting column, no explanatory paragraphs, no draft page text.
  Where a feature's actual runtime behaviour genuinely diverges from what the public doc page or
  this module's own `README.md` describes it as doing, that fact is itself part of "what the
  feature does" and is stated here as a plain, sourced observation, with the filed issue number —
  the same standard the framework's own `FEATURES.md` already applies to its documented `#432`
  defect.

### Reconciliation command family

The canonical form for counting an annotation site across this repository's real sources:

```bash
find <repo-root> -path '*/src/main/java/*' -name '*.java' -not -path '*/target/*' \
  -not -path '*/.worktrees/*' -print0 | xargs -0 grep -nE '^[[:space:]]*@AnnotationName\b' | wc -l
```

**This repository's own trap: a git worktree at `.worktrees/economy-v2/`.** That tree is a
checkout of a different branch, produced by `git worktree add`, not a build artifact — but its
`src/main/java` is a full second copy of this module's source, and a `find` without the
`-not -path '*/.worktrees/*'` exclusion above walks straight into it. Measured directly:

| Instrument | `@CmdMapping` count |
|---|---|
| Canonical form (worktree excluded) | **24** |
| Same command with the `-not -path '*/.worktrees/*'` clause removed | **48** |

Exactly double — `.worktrees/economy-v2` currently mirrors this branch's own source 1:1, so every
site is counted twice. **If a reconciliation line in this document or its companion pull request
ever reports a number at or near 48 rather than 24, the worktree leaked into the count.** Nothing
under `.worktrees/` was read, cited, or counted anywhere in this document.

**Positive control:** the canonical (worktree-excluded) line-start form returns `@CmdExecutor` = 7,
`@CmdMapping` = 24, `@EventListener` = 2 (classes, 2 handler methods total: one each),
`@Scheduled` = 0, `@ConfigEntity` = 1, `@ConditionalOnConfig` = 1, `@ConfigEntry` = 19, `@Table` = 3 —
confirmed by reading `EcoAdminCommand.java` directly: it alone declares 11 of the 24
`@CmdMapping` sites (`give`, `take`, `set`, `check`, `give <player> <amount> <currency>`,
`take <player> <amount> <currency>`, `set <player> <amount> <currency>`,
`check <player> <currency>`, `treasury`, `treasury withdraw <amount>`,
`treasury withdraw <amount> <currency>`), each confirmed at its own line. This document's own
24 `command`-Kind rows below match that 24 exactly, 1:1 — no bare-`help` short-circuit row exists
here the way the framework's own `/upm help`/`/ulticloud help` do, because every one of this
module's seven `@CmdExecutor` classes overrides `handleHelp` as its own dispatch target and none
of the seven has a `format = "help"`/bare-argument `@CmdMapping` site to begin with.

## Bank

`BankCommand` — class-level `@CmdExecutor(permission = "ultieconomy.bank", alias = {"bank"})`, no
class-level `@CmdTarget` (both mapped methods carry their own method-level
`@CmdTarget(PLAYER)`). Gated at the method body by `EconomyConfig#isBankEnabled` (shipped
default: `true`).

**Confirmed defect (filed as issue, D-20): the success-path message on both rows below never
localizes.** `BankCommand#onBank` (line 66) and `#onBankCurrency` (line 80) both call
`plugin.i18n(...)` with a Chinese-literal key carrying exactly one `%s` placeholder — read the
cited source lines for the literal characters, per this document's own English-only rule (D-02;
see the `UAT-CHECKLIST.md` Conventions for why no Chinese literal is reproduced here). Both
`lang/en.json` and `lang/zh.json` key their corresponding entry with an otherwise-identical
literal carrying TWO consecutive `%s` placeholders instead of one, and the framework's
`Language#getLocalizedText` (`dictionary.get(str)`, falling back to the raw key on a miss) does an
**exact string match**. The one-`%s` key the source calls with is absent from both dictionaries,
so the call falls all the way through to returning the raw Chinese key unchanged — under
`language: en` exactly as under the shipped `language: zh` default, since neither file contains
the key the source actually passes. See the `## Configuration` section's note below for the full
23-of-44-key inventory; filed as UltiKits/UltiEconomy#14.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.bank.balance | View the sender's own primary-currency bank balance. The success line never localizes (see note above; UltiKits/UltiEconomy#14) — it renders the raw Chinese source string under both `language: en` and `language: zh` | command | `/bank` | ultieconomy.bank | player | player | brief | BankCommand#onBank |
| ultieconomy.bank.balance-currency | View the sender's bank balance in a named non-primary currency | command | `/bank <currency>` | ultieconomy.bank | player | player | brief | BankCommand#onBankCurrency |

## Deposit

`DepositCommand` — class-level `@CmdExecutor(permission = "ultieconomy.deposit", alias =
{"deposit", "ck"})`. Gated at the method body by `EconomyConfig#isBankEnabled` for the
primary-currency mapping only — `onDepositCurrency` does **not** check `isBankEnabled` at all,
and separately does not check the currency's own `bank-enabled`/`min-deposit` flags either (both
of which `EconomyServiceImpl#depositToBank(uuid, amount, currencyId)` itself enforces one layer
down) — see the `.neg-*` checklist rows for the practical consequence.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.deposit.deposit | Move cash into the primary-currency bank balance, subject to the configured minimum deposit and maximum bank balance. Success message never localizes (UltiKits/UltiEconomy#14, same root cause as `ultieconomy.bank.balance`) | command | `/deposit <amount>` | ultieconomy.deposit | player | player | brief | DepositCommand#onDeposit |
| ultieconomy.deposit.deposit-currency | Deposit into a named non-primary currency's bank balance, enforcing that currency's own `bank-enabled`/`min-deposit`/`max-bank-balance` one call down in `EconomyServiceImpl`, but with none of the `bank.enabled` (global) or `bank.min-deposit` (`EconomyConfig`) checks this row's sibling applies. Success message never localizes (UltiKits/UltiEconomy#14) | command | `/deposit <amount> <currency>` | ultieconomy.deposit | player | player | brief | DepositCommand#onDepositCurrency |

## Withdraw

`WithdrawCommand` — class-level `@CmdExecutor(permission = "ultieconomy.withdraw", alias =
{"withdraw", "qk"})`. Gated at the method body by `EconomyConfig#isBankEnabled` for the
primary-currency mapping only, the same asymmetry as Deposit above.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.withdraw.withdraw | Move cash out of the primary-currency bank balance back into cash. Success message never localizes (UltiKits/UltiEconomy#14) | command | `/withdraw <amount>` | ultieconomy.withdraw | player | player | brief | WithdrawCommand#onWithdraw |
| ultieconomy.withdraw.withdraw-currency | Withdraw from a named non-primary currency's bank balance; no `bank.enabled` (global) gate applies to this mapping. Success message never localizes (UltiKits/UltiEconomy#14) | command | `/withdraw <amount> <currency>` | ultieconomy.withdraw | player | player | brief | WithdrawCommand#onWithdrawCurrency |

## Pay

`PayCommand` — class-level `@CmdExecutor(permission = "ultieconomy.pay", alias = {"pay"})`.
Refuses a zero/negative amount, an offline target, and self-payment; the transfer itself is
atomic across sender-deduct and receiver-credit (see `EconomyServiceImpl#transfer`).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.pay.pay | Transfer primary-currency cash to another online player; the sender's cash always drops by the full requested amount, and the receiver's cash rises by that amount minus the transaction tax (if `tax.transaction-tax.enabled`) — but BOTH the sender's own success line and the receiver's own notification format the ORIGINAL gross `amount` variable, never the net figure actually credited, so a taxed transfer's chat text overstates what the receiver got by the tax amount (a distinct discrepancy from the i18n breakage below). BOTH lines also never localize (UltiKits/UltiEconomy#14) — the same broken source string is reused for both messages | command | `/pay <player> <amount>` | ultieconomy.pay | player | player | brief | PayCommand#onPay |
| ultieconomy.pay.pay-currency | Transfer cash in a named non-primary currency to another online player, same tax and atomicity behaviour. Both messages never localize (UltiKits/UltiEconomy#14) | command | `/pay <player> <amount> <currency>` | ultieconomy.pay | player | player | brief | PayCommand#onPayWithCurrency |

## Money (balance display)

`MoneyCommand` — class-level `@CmdExecutor(permission = "ultieconomy.money", alias = {"money",
"bal"})`.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.money.balance | View the sender's own primary-currency cash, bank, and total-wealth figures in one block. The header line (`MoneyCommand.java:63`'s i18n call) DOES localize correctly; all three balance lines beneath it (lines 64-66) do not (UltiKits/UltiEconomy#14) — a single command output is a working header over three broken body lines | command | `/money` | ultieconomy.money | player | player | brief | MoneyCommand#onBalance |
| ultieconomy.money.balance-currency | Same three-figure view, scoped to a named non-primary currency. Same working-header/broken-body split as the row above (UltiKits/UltiEconomy#14) | command | `/money <currency>` | ultieconomy.money | player | player | brief | MoneyCommand#onCurrencyBalance |

## Money Notes

`NoteCommand` — class-level `@CmdExecutor(permission = "ultieconomy.note", alias = {"note"})` —
plus `NoteRedeemListener`, an `@EventListener` reacting to a right-click while holding a note.
A note is a `Material.PAPER` `ItemStack` carrying its currency id, face value, creator UUID, and
creation timestamp in its `PersistentDataContainer` (`MoneyNoteFactory#applyNoteData`); redemption
reads that data back and credits the redeeming player, decrementing the stack by one. Unlike the
sections above, this section's messages **do** localize correctly — `NoteCommand`'s own six i18n
key literals (`NoteCommand.java:66,73,87,100,108,129,136-138`, describing note creation, redemption,
and their failure/help text) all have exact matches in both `lang/en.json` and `lang/zh.json`.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.note.create | Convert primary-currency cash into a physical, tradeable money-note item, deducting the cash first and refusing to create the item if the deduction fails | command | `/note <amount>` | ultieconomy.note | player | player | brief | NoteCommand#onCreateNote |
| ultieconomy.note.create-currency | Create a money note for a named non-primary currency | command | `/note <amount> <currency>` | ultieconomy.note | player | player | brief | NoteCommand#onCreateCurrencyNote |
| ultieconomy.note.redeem | Redeem the money note currently held in the main hand via command, crediting its face value in its own currency and removing one from the stack | command | `/note redeem` | ultieconomy.note | player | player | brief | NoteCommand#onRedeem |
| ultieconomy.note.redeem-onuse | Redeem a held money note by right-clicking (air or a block) instead of running a command — reads and credits the note identically to `ultieconomy.note.redeem`, then unconditionally cancels the interaction event regardless of whether the credit succeeded | event | right-click while holding a money-note item | n/a | n/a | player | brief | NoteRedeemListener#onInteract |

## Admin

`EcoAdminCommand` — class-level `@CmdExecutor(permission = "ultieconomy.admin", alias =
{"eco"})`, no `@CmdTarget` anywhere on the class (resolves to `both`). Console-compatible and
supports offline targets via `Bukkit#getOfflinePlayer`.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.admin.check | View a target player's primary-currency cash, bank, and total wealth. Both figure lines and the total-wealth line never localize (UltiKits/UltiEconomy#14, same broken keys as `ultieconomy.money.balance`) | command | `/eco check <player>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onCheck |
| ultieconomy.admin.check-currency | Same view scoped to a named non-primary currency. Same broken lines as the row above | command | `/eco check <player> <currency>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onCheckCurrency |
| ultieconomy.admin.give | Add primary-currency cash to a target player's balance, online or offline. Success message never localizes (UltiKits/UltiEconomy#14) | command | `/eco give <player> <amount>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onGive |
| ultieconomy.admin.give-currency | Add cash in a named non-primary currency. Success message never localizes | command | `/eco give <player> <amount> <currency>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onGiveCurrency |
| ultieconomy.admin.set | Set a target player's primary-currency cash balance outright. Success message never localizes | command | `/eco set <player> <amount>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onSet |
| ultieconomy.admin.set-currency | Set a named non-primary currency's cash balance outright. Success message never localizes | command | `/eco set <player> <amount> <currency>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onSetCurrency |
| ultieconomy.admin.take | Deduct primary-currency cash from a target player's balance, refusing if insufficient. Success message never localizes | command | `/eco take <player> <amount>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onTake |
| ultieconomy.admin.take-currency | Deduct cash in a named non-primary currency. Success message never localizes | command | `/eco take <player> <amount> <currency>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onTakeCurrency |
| ultieconomy.admin.treasury | List the tax treasury's balance for every configured currency in one pass. This row's own messages localize correctly | command | `/eco treasury` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onTreasury |
| ultieconomy.admin.treasury-withdraw | Withdraw from the primary-currency treasury balance built up by transaction-tax collection. Refuses if `TaxService` was not constructed (never actually null in this class's own constructor, so this branch is presently unreachable through this command). This row's messages localize correctly | command | `/eco treasury withdraw <amount>` | ultieconomy.admin | both | admin | detailed | EcoAdminCommand#onTreasuryWithdraw |
| ultieconomy.admin.treasury-withdraw-currency | Withdraw from a named non-primary currency's treasury balance. Messages localize correctly | command | `/eco treasury withdraw <amount> <currency>` | ultieconomy.admin | both | admin | detailed | EcoAdminCommand#onTreasuryWithdrawCurrency |

## Currencies

`CurrencyManager`, constructed once per `UltiEconomy` instance from `config/currencies.yml`
(loaded directly via `YamlConfiguration`, **not** an `@ConfigEntity`/`@ConfigEntry`-bound class —
the module's only `@ConfigEntity` is `EconomyConfig`, bound to `config/config.yml`). Every
currency other than the shipped default (`coins`) is added by hand-editing this file; there is no
in-game command to define, edit, or remove a currency. Exactly one currency must be marked
`primary: true` — the constructor throws `IllegalStateException` at plugin load if zero or more
than one currency claims it, which surfaces as a failed module load, not a graceful runtime
refusal.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.config.currencies.currencies | The full currency-definition map — one entry per wallet, each `{display-name, symbol, initial-cash, bank-enabled, min-deposit, max-bank-balance, primary}`; exactly one entry must set `primary: true`, mapping that wallet to Vault | config | `config/currencies.yml: currencies (default: 1 entry, "coins", primary)` | n/a | n/a | admin | detailed | CurrencyManager#CurrencyManager |

## Vault and PlaceholderAPI registration

Boot-time behaviour driven by `UltiEconomy#registerSelf()`, gated on the presence of a soft
dependency rather than on any config key — no `@ConditionalOnConfig` is involved in either row.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.vault.register | Register `VaultEconomyProvider` as the server's Vault `Economy` service at `ServicePriority.Normal`, only if the `Vault` plugin is present — the hard dependency declared in `plugin.yml`, so in practice this always runs | event | install/enable this module with Vault present | n/a | n/a | internal | none | UltiEconomy#registerSelf |
| ultieconomy.placeholder.register | Register `EconomyPlaceholderExpansion` with PlaceholderAPI, only if the `PlaceholderAPI` plugin is present (soft dependency) | event | install/enable this module with PlaceholderAPI present | n/a | n/a | internal | none | UltiEconomy#registerSelf |

## Player join

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.player.join-init | On a player's first (or any) join, create their primary-currency account if absent (crediting `initial-cash`), then create a per-currency balance row for every currently-configured currency they do not already have one for | event | join the server as any player | n/a | n/a | internal | none | PlayerJoinListener#onPlayerJoin |

## Interest (gate)

`InterestService` — `@Service` `@ConditionalOnConfig(value = "config/config.yml", path =
"interest.enabled")`, the module's only `@ConditionalOnConfig` site (matches the reconciliation
table's `@ConditionalOnConfig` = 1 exactly).

**Confirmed defect, filed as UltiKits/UltiEconomy#15: the entire interest subsystem this gate
controls is inert regardless of the gate's own state.** `InterestService#distributeInterest()` is
unit-tested (`InterestServiceTest`) but has **zero production callers** — grepped across every
`.java` file under `src/main/java` with the worktree and `target/` excluded, the only two
occurrences of the method name in this module's shipped source are its own declaration and its
own javadoc, which itself claims (inaccurately) that the method is "Called periodically by the
scheduled task" — no such task exists anywhere in this module: `@Scheduled` = 0
(reconciliation table), and no `Bukkit.getScheduler()`/`BukkitRunnable`/`runTaskTimer` call
appears anywhere in `src/main/java` either. This means `interest.enabled: true` (the shipped
default) and the module's own `README.md`/public doc page both describe a feature — "bank savings
earn interest over time," "distributed at fixed intervals (default: every 30 minutes)" — that
never actually fires, on any server, regardless of configuration. The `interest.rate`,
`interest.interval`, and `interest.max-interest` config keys are consequently also unreachable —
see the `## Configuration` note on `interest.interval` below.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.gate.interest | Register `InterestService` as an IoC-managed bean only if `interest.enabled` is `true` at component-scan time (evaluated once, at boot — a later config change needs a restart, `/ul reload` alone does not re-evaluate it). No production code ever calls the bean's own `distributeInterest()` method regardless of whether this gate lets it register at all — see the section note above (UltiKits/UltiEconomy#15) | gate | `interest.enabled` in `plugins/UltiTools/UltiEconomy/config/config.yml`, applied only on a full server restart | n/a | n/a | admin | detailed | InterestService#InterestService |

## Leaderboard placeholders

`EconomyPlaceholderExpansion` (identifier `ultieconomy`), registered per
`ultieconomy.placeholder.register` above. `LeaderboardService#refreshLeaderboard()` and
`#refreshCurrencyLeaderboard(String)` populate the cached rank/top-N data these placeholders read
— **and, for the same reason as the interest gate above, neither refresh method has any
production caller anywhere in this module's source** (grepped identically, 0 call sites besides
their own declarations and their own javadoc's identical "Called periodically by the scheduled
task" claim). `LeaderboardService`'s two cache fields (`cachedLeaderboard`,
`currencyLeaderboards`) are therefore permanently empty/default on every running server, for the
life of the process. Filed together with the interest defect above as UltiKits/UltiEconomy#15,
since both are the identical root cause (a javadoc-promised scheduled task that was never wired
to an actual Bukkit scheduler call anywhere in this module).

12 distinct placeholder-parameter branches exist across `onRequest`, `handleCurrencyPlaceholder`,
and `handleTopPlaceholder` (5 unscoped: `cash`/`bank`/`total`/`cash_formatted`/`rank`; 5 identical
branches re-dispatched per named currency via a `<currencyId>_<type>` prefix; 2 leaderboard-only:
`top_name_<N>`/`top_balance_<N>`), catalogued below as 7 rows — the 5 currency-scoped branches
share `handleCurrencyPlaceholder`'s logic exactly and are bundled into one row rather than
restated five times.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.placeholder.bank | Primary-currency bank balance, raw two-decimal number, no currency symbol | placeholder | `%ultieconomy_bank%` | n/a | n/a | admin | brief | EconomyPlaceholderExpansion#onRequest |
| ultieconomy.placeholder.cash | Primary-currency cash balance, raw two-decimal number, no currency symbol | placeholder | `%ultieconomy_cash%` | n/a | n/a | admin | brief | EconomyPlaceholderExpansion#onRequest |
| ultieconomy.placeholder.cash-formatted | Primary-currency cash balance with the configured currency symbol prefixed | placeholder | `%ultieconomy_cash_formatted%` | n/a | n/a | admin | brief | EconomyPlaceholderExpansion#onRequest |
| ultieconomy.placeholder.currency-scoped | Any of `cash`/`bank`/`total`/`cash_formatted`/`rank`, scoped to a named currency instead of the primary one, dispatched by a `<currencyId>_` prefix recognised only when `currencyManager.hasCurrency(...)` matches | placeholder | `%ultieconomy_<currency>_cash%`, `%ultieconomy_<currency>_bank%`, `%ultieconomy_<currency>_total%`, `%ultieconomy_<currency>_cash_formatted%`, `%ultieconomy_<currency>_rank%` | n/a | n/a | admin | detailed | EconomyPlaceholderExpansion#handleCurrencyPlaceholder |
| ultieconomy.placeholder.rank | Sender's 1-based primary-currency wealth rank, or `-` if unranked. Always returns `-` regardless of the sender's actual wealth — `LeaderboardService#getPlayerRank` reads an always-empty cache (UltiKits/UltiEconomy#15) | placeholder | `%ultieconomy_rank%` | n/a | n/a | admin | none | EconomyPlaceholderExpansion#onRequest |
| ultieconomy.placeholder.top-balance | Total wealth of the Nth-ranked player, 1-based. Always returns `0.00` regardless of server state (UltiKits/UltiEconomy#15) | placeholder | `%ultieconomy_top_balance_<N>%` | n/a | n/a | admin | none | EconomyPlaceholderExpansion#getTopBalance |
| ultieconomy.placeholder.top-name | Name of the Nth-ranked player, 1-based. Always returns `-` regardless of server state (UltiKits/UltiEconomy#15) | placeholder | `%ultieconomy_top_name_<N>%` | n/a | n/a | admin | none | EconomyPlaceholderExpansion#getTopName |
| ultieconomy.placeholder.total | Primary-currency total wealth (cash + bank), raw two-decimal number | placeholder | `%ultieconomy_total%` | n/a | n/a | admin | brief | EconomyPlaceholderExpansion#onRequest |

## Data persistence

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.persistence.balance-restart | A player's primary-currency account (`PlayerAccountEntity`, table `economy_accounts`) and every per-currency balance row (`CurrencyBalanceEntity`, table `currency_balances`) survive a full server restart, in whichever ORM backend the framework is configured to use | persistence | change a balance via any command above, note both the before and after values, restart the server, then read the balance back via the same command | n/a | n/a | admin | none | EconomyServiceImpl#getAccount, EconomyServiceImpl#getBalance |
| ultieconomy.persistence.treasury-restart | The tax treasury's per-currency balance (`TreasuryEntity`, table `economy_treasury`) survives a full server restart | persistence | collect at least one transaction tax (or run `/eco treasury withdraw`), note the treasury balance via `/eco treasury`, restart the server, then run `/eco treasury` again | n/a | n/a | admin | none | TaxService#getTreasuryBalance |

## Configuration

All 19 `@ConfigEntry` fields declared on the module's one `@ConfigEntity` class,
`EconomyConfig` (confirmed by reading the class field by field, matching the reconciliation
table's `@ConfigEntry` = 19 exactly). **The packaged default resource,
`src/main/resources/config/config.yml`, ships only 12 of these 19 as literal YAML** —
`grep -cE '^[[:space:]]*[a-zA-Z][a-zA-Z0-9_-]*:[[:space:]]*[^[:space:]#]'
src/main/resources/config/config.yml` returns 12 (`initial-cash`, `currency-name`,
`currency-symbol`, `bank.enabled`, `bank.min-deposit`, `bank.max-balance`, `interest.enabled`,
`interest.rate`, `interest.interval`, `interest.max-interest`, `leaderboard.update-interval`,
`leaderboard.display-count`); the packaged file has no `tax:` section at all. The remaining 7 —
every `tax.*` key — are absent from the shipped resource and are written to the on-disk
`plugins/UltiTools/UltiEconomy/config/config.yml` on the plugin's first boot instead, by
`AbstractConfigEntity#init()`'s own field-reflection loop (it calls `config.save(file)` after
writing each `@ConfigEntry` field's Java-declared default for any key the on-disk file lacks) —
the same "migrated onto disk on first boot if absent" mechanism the framework's own `FEATURES.md`
documents for `ultipanel.commands.blocklist`/`ultipanel.files.editable-roots`. A server that has
booted this module at least once therefore has all 19 keys on disk even though the packaged jar's
default resource ships only 12. `config/currencies.yml` is this module's second shipped yml
resource; it is **not** `@ConfigEntity`-bound (see `## Currencies` above for its own single row)
and — per this plan's own instruction that this module carries exactly one config-per-file
checklist row, for the single `@ConfigEntity` class — its loading is folded into that same
checklist row rather than given a second one; see that row's own Preconditions/Steps for how.

**Two keys are declared, validated, and unit-tested, but have no reachable production effect at
all:** `interest.interval` and `leaderboard.update-interval`. Both are read only by their own
generated Lombok getter (confirmed: `getInterestInterval`/`getLeaderboardUpdateInterval` have
zero production callers outside `EconomyConfigTest`, grepped with `-i` across `src/main/java`).
This is not merely "no code path currently reads the key" — it is a direct consequence of
UltiKits/UltiEconomy#15: since nothing anywhere schedules `distributeInterest()` or
`refreshLeaderboard()`/`refreshCurrencyLeaderboard()` on any interval at all, there is no interval
value for either key to supply in the first place. Each row below states this rather than a
default-flip Steps sequence, per the same "do not test a key with no effect" convention the
framework's own `UAT-CHECKLIST.md` applies to `ultipanel.logging.batch.interval`.

**Also confirmed while reading for this section: 23 of the 44 distinct `plugin.i18n(...)` key
literals used across this module's source have no exact match in either `lang/en.json` or
`lang/zh.json`.** Both language files ship an *identical* key set (43 keys each) that differs from
what the source actually calls with — most commonly by one extra trailing `%s` format placeholder
(e.g. `MoneyCommand.java:64`'s i18n call carries one `%s`; both lang files key the near-identical
entry with two), and in a smaller number of cases (all six commands' own `handleHelp()` short
one-line descriptions bar `NoteCommand`'s) the dictionary simply has no entry for the exact
literal at all. Since `Language#getLocalizedText` does an exact-string dictionary lookup and falls
back to returning its own input unchanged on a miss, **every one of these 23 calls renders the
raw Chinese source literal verbatim, under `language: en` exactly as under the shipped
`language: zh` default** — there is no way to reach the English translation for any of them
through this module's own `config.yml`. Filed as UltiKits/UltiEconomy#14; the affected call sites
are cited individually on their own `command`/`event` rows above (every row whose Feature text
ends "never localizes"), not repeated here.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.config.config.bank.enabled | Enable the bank feature (primary-currency deposit/withdraw/interest); gates `BankCommand`/`DepositCommand`/`WithdrawCommand`'s primary-currency mappings only, not their currency-aware siblings | config | `config/config.yml: bank.enabled (default: true)` | n/a | n/a | admin | detailed | EconomyConfig#isBankEnabled |
| ultieconomy.config.config.bank.max-balance | Maximum primary-currency bank balance a deposit may not exceed; `-1` means unlimited | config | `config/config.yml: bank.max-balance (default: -1)` | n/a | n/a | admin | brief | DepositCommand#onDeposit |
| ultieconomy.config.config.bank.min-deposit | Minimum amount accepted by a single primary-currency deposit | config | `config/config.yml: bank.min-deposit (default: 100.0)` | n/a | n/a | admin | brief | DepositCommand#onDeposit |
| ultieconomy.config.config.currency-name | Currency display name used in Vault's `currencyNamePlural`/`currencyNameSingular` queries | config | `config/config.yml: currency-name (default: "Coins")` | n/a | n/a | admin | brief | VaultEconomyProvider#currencyNamePlural |
| ultieconomy.config.config.currency-symbol | Symbol prefixed to every formatted primary-currency amount | config | `config/config.yml: currency-symbol (default: "$")` | n/a | n/a | admin | brief | EconomyServiceImpl#formatAmount |
| ultieconomy.config.config.initial-cash | Starting primary-currency cash balance for a newly-created account | config | `config/config.yml: initial-cash (default: 1000.0)` | n/a | n/a | admin | brief | EconomyServiceImpl#getOrCreateAccount |
| ultieconomy.config.config.interest.enabled | Whether `InterestService` registers as a bean at all; see `## Interest (gate)` above for why registering it currently has no further effect | config | `config/config.yml: interest.enabled (default: true)` | n/a | n/a | admin | detailed | InterestService#InterestService |
| ultieconomy.config.config.interest.interval | Declared as the interest-distribution interval in seconds; unreachable — nothing schedules `InterestService#distributeInterest()` at all (UltiKits/UltiEconomy#15) | config | `config/config.yml: interest.interval (default: 1800, no effect, see UltiKits/UltiEconomy#15)` | n/a | n/a | admin | none | InterestService#calculateInterest (declared, unreachable) |
| ultieconomy.config.config.interest.max-interest | Cap on a single interest payment; `-1` means unlimited. Read by `distributeInterest()`/`calculateInterest()`, both themselves unreachable (UltiKits/UltiEconomy#15) | config | `config/config.yml: interest.max-interest (default: 10000.0, unreachable, see UltiKits/UltiEconomy#15)` | n/a | n/a | admin | none | InterestService#calculateInterest |
| ultieconomy.config.config.interest.rate | Interest rate applied per distribution. Read by `distributeInterest()`/`calculateInterest()`, both themselves unreachable (UltiKits/UltiEconomy#15) | config | `config/config.yml: interest.rate (default: 0.03, unreachable, see UltiKits/UltiEconomy#15)` | n/a | n/a | admin | none | InterestService#calculateInterest |
| ultieconomy.config.config.leaderboard.display-count | Default number of top entries `LeaderboardService#getDefaultDisplayCount` reports; no command or placeholder in this module actually calls that accessor | config | `config/config.yml: leaderboard.display-count (default: 10)` | n/a | n/a | admin | none | LeaderboardService#getDefaultDisplayCount (declared, no caller) |
| ultieconomy.config.config.leaderboard.update-interval | Declared as the leaderboard cache refresh interval in seconds; unreachable — nothing schedules `LeaderboardService#refreshLeaderboard()`/`#refreshCurrencyLeaderboard(String)` at all (UltiKits/UltiEconomy#15) | config | `config/config.yml: leaderboard.update-interval (default: 60, no effect, see UltiKits/UltiEconomy#15)` | n/a | n/a | admin | none | LeaderboardService#refreshLeaderboard (declared, unreachable) |
| ultieconomy.config.config.tax.enabled | Declared as the master tax-system switch; `EconomyConfig#isTaxEnabled()` has **zero callers anywhere in this module's production source** — both `EcoAdminCommand` and `EconomyServiceImpl` construct their own `TaxService` unconditionally in their constructors, so `EcoAdminCommand#onTreasury`'s own `taxService == null` guard (the only place that would report "tax system not enabled") is permanently `false` in production and reachable only through the test-only `createForTest` helpers. This key currently has no effect in either direction: the tax subsystem (transaction tax, treasury) is always constructed and always active, gated only by the separate `tax.transaction-tax.enabled` key below. Filed as UltiKits/UltiEconomy#16 | config | `config/config.yml: tax.enabled (default: false, no effect, see UltiKits/UltiEconomy#16)` | n/a | n/a | admin | detailed | EcoAdminCommand#onTreasury (guard unreachable in production) |
| ultieconomy.config.config.tax.transaction-tax.enabled | Enable a transaction tax deducted from a `/pay` transfer only, deposited into the treasury. `EcoAdminCommand#onGive`/`#onTake`/`#onSet` call `EconomyService#addCash`/`#takeCash`/`#setCash` directly, never `#transfer` — `TaxService#calculateTransactionTax` is invoked only from `EconomyServiceImpl#transfer`'s two overloads (the code path behind `/pay` and `/pay ... <currency>`), so the admin commands neither deduct tax nor deposit anything into the treasury regardless of this key | config | `config/config.yml: tax.transaction-tax.enabled (default: true)` | n/a | n/a | admin | detailed | TaxService#calculateTransactionTax, EconomyServiceImpl#transfer |
| ultieconomy.config.config.tax.transaction-tax.exempt-permission | Permission node that, if held, is intended to exempt a player from the transaction tax — declared but never referenced by any `hasPermission` check anywhere in this module's source (0 occurrences outside this config field and its default-value string), so holding it currently changes nothing | config | `config/config.yml: tax.transaction-tax.exempt-permission (default: "ultieconomy.tax.exempt", not enforced anywhere)` | n/a | n/a | admin | none | EconomyConfig#getTransactionTaxExemptPermission (declared, never checked) |
| ultieconomy.config.config.tax.transaction-tax.rate | Transaction tax rate applied to every taxed transfer | config | `config/config.yml: tax.transaction-tax.rate (default: 0.05)` | n/a | n/a | admin | brief | TaxService#calculateTransactionTax |
| ultieconomy.config.config.tax.wealth-tax.enabled | Declared switch for a periodic wealth tax; `TaxService#calculateWealthTax(double, List)` exists and is unit-tested but has zero production callers anywhere in this module's source, and no scheduled task or command ever invokes it — the entire wealth-tax mechanism is unbuilt beyond this one calculation method. Filed as UltiKits/UltiEconomy#16 | config | `config/config.yml: tax.wealth-tax.enabled (default: false, unreachable, see UltiKits/UltiEconomy#16)` | n/a | n/a | admin | detailed | TaxService#calculateWealthTax (declared, no caller) |
| ultieconomy.config.config.tax.wealth-tax.exempt-permission | Declared exempt-permission for the unbuilt wealth tax (see the row above, UltiKits/UltiEconomy#16); not referenced by any `hasPermission` check | config | `config/config.yml: tax.wealth-tax.exempt-permission (default: "ultieconomy.wealthtax.exempt", not enforced anywhere)` | n/a | n/a | admin | none | EconomyConfig#getWealthTaxExemptPermission (declared, never checked) |
| ultieconomy.config.config.tax.wealth-tax.interval | Declared interval for the unbuilt wealth tax (see the row two above, UltiKits/UltiEconomy#16); not read by any code path since the feature it would drive was never built | config | `config/config.yml: tax.wealth-tax.interval (default: 3600, unreachable)` | n/a | n/a | admin | none | EconomyConfig#getWealthTaxInterval (declared, never read outside this class) |
