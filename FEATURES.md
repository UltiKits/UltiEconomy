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
  This module has no `gui` rows (no GUI page class) and, since 6.3.0, no `gate` rows (its one
  `@ConditionalOnConfig` site was removed with UltiKits/UltiEconomy#15). Its two `scheduled` rows
  match the reconciliation table's 2 real `@Scheduled` sites — interest payment and leaderboard
  refresh, both of which had no scheduler at all before this release (UltiEconomy 2.0.0 and earlier).
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
`@Scheduled` = 2, `@ConfigEntity` = 1, `@ConditionalOnConfig` = 0, `@ConfigEntry` = 19, `@Table` = 3 —
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

**Fixed (UltiKits/UltiEconomy#14):** the success lines of both rows below used to render
the raw Chinese source text in every language, because the key the source passed had no catalogue
entry. Every key is now an ASCII key present in both catalogues, so the lines follow `language`.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.bank.balance | View the sender's own primary-currency bank balance. Success line: `Your bank balance: <amount>` under `language: en` (UltiKits/UltiEconomy#14) | command | `/bank` | ultieconomy.bank | player | player | brief | BankCommand#onBank |
| ultieconomy.bank.balance-currency | View the sender's bank balance in a named currency; a non-primary currency's own per-currency row. Naming the primary currency (`coins` by default) uses the account wallet the row above shows — the primary currency has one wallet (UltiKits/UltiEconomy#25) | command | `/bank <currency>` | ultieconomy.bank | player | player | brief | BankCommand#onBankCurrency |

## Deposit

`DepositCommand` — class-level `@CmdExecutor(permission = "ultieconomy.deposit", alias =
{"deposit", "ck"})`. Gated at the method body by `EconomyConfig#isBankEnabled` for the
primary-currency mapping only — `onDepositCurrency` does **not** check `isBankEnabled` at all,
and separately does not check the currency's own `bank-enabled`/`min-deposit` flags either (both
of which `EconomyServiceImpl#depositToBank(uuid, amount, currencyId)` itself enforces one layer
down) — see the `.neg-*` checklist rows for the practical consequence.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.deposit.deposit | Move cash into the primary-currency bank balance, subject to the configured minimum deposit and maximum bank balance. Success line: `Successfully deposited <amount> to bank` under `language: en` (UltiKits/UltiEconomy#14) | command | `/deposit <amount>` | ultieconomy.deposit | player | player | brief | DepositCommand#onDeposit |
| ultieconomy.deposit.deposit-currency | Deposit into a named currency's bank balance. For a non-primary currency: that currency's own `bank-enabled`/`min-deposit`/`max-bank-balance`, enforced one call down in `EconomyServiceImpl`, and none of the `bank.enabled` (global) or `bank.min-deposit` (`EconomyConfig`) checks this row's sibling applies. Naming the primary currency deposits into the account wallet under `config/config.yml`'s `bank.enabled`, `bank.min-deposit` and `bank.max-balance` (the primary block of `currencies.yml` is not read for them), refusing with the currency form's generic `Insufficient balance` line rather than the sibling's specific ones (UltiKits/UltiEconomy#25). Success line as `ultieconomy.deposit.deposit` | command | `/deposit <amount> <currency>` | ultieconomy.deposit | player | player | brief | DepositCommand#onDepositCurrency |

## Withdraw

`WithdrawCommand` — class-level `@CmdExecutor(permission = "ultieconomy.withdraw", alias =
{"withdraw", "qk"})`. Gated at the method body by `EconomyConfig#isBankEnabled` for the
primary-currency mapping only, the same asymmetry as Deposit above.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.withdraw.withdraw | Move cash out of the primary-currency bank balance back into cash. Success line: `Successfully withdrew <amount> from bank` under `language: en` (UltiKits/UltiEconomy#14) | command | `/withdraw <amount>` | ultieconomy.withdraw | player | player | brief | WithdrawCommand#onWithdraw |
| ultieconomy.withdraw.withdraw-currency | Withdraw from a named currency's bank balance; for a non-primary currency no `bank.enabled` (global) gate applies. Naming the primary currency (`coins` by default) uses the account wallet the row above shows — the primary currency has one wallet — and obeys `config/config.yml`'s `bank.enabled` as the row above does, refusing with the generic `Insufficient balance` line (UltiKits/UltiEconomy#25). Success line as `ultieconomy.withdraw.withdraw` | command | `/withdraw <amount> <currency>` | ultieconomy.withdraw | player | player | brief | WithdrawCommand#onWithdrawCurrency |

## Pay

`PayCommand` — class-level `@CmdExecutor(permission = "ultieconomy.pay", alias = {"pay"})`.
Refuses a zero/negative amount, an offline target, and self-payment; the transfer itself is
atomic across sender-deduct and receiver-credit (see `EconomyServiceImpl#transfer`).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.pay.pay | Transfer primary-currency cash to another online player; the sender's cash always drops by the full requested amount, and the receiver's cash rises by that amount minus the transaction tax (if both `tax.enabled` and `tax.transaction-tax.enabled` are true; otherwise by the full amount) — but BOTH the sender's own success line and the receiver's own notification format the ORIGINAL gross `amount` variable, never the net figure actually credited, so a taxed transfer's chat text overstates what the receiver got by the tax amount (a distinct discrepancy from the language fix). Under `language: en` the lines read `Successfully transferred <amount> to <target>` and `<sender> transferred <amount> to you` (UltiKits/UltiEconomy#14) | command | `/pay <player> <amount>` | ultieconomy.pay | player | player | brief | PayCommand#onPay |
| ultieconomy.pay.pay-currency | Transfer cash in a named currency to another online player, same tax and atomicity behaviour, and the same two lines. Naming the primary currency (`coins` by default) uses the account wallet the row above shows — the primary currency has one wallet (UltiKits/UltiEconomy#25) | command | `/pay <player> <amount> <currency>` | ultieconomy.pay | player | player | brief | PayCommand#onPayWithCurrency |

## Money (balance display)

`MoneyCommand` — class-level `@CmdExecutor(permission = "ultieconomy.money", alias = {"money",
"bal"})`.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.money.balance | View the sender's own primary-currency cash, bank, and total-wealth figures in one block. Under `language: en`: `=== Economy System ===`, `Your balance: …`, `Your bank balance: …`, `Total wealth: …` (the three body lines rendered raw Chinese source text before this fix, UltiKits/UltiEconomy#14) | command | `/money` | ultieconomy.money | player | player | brief | MoneyCommand#onBalance |
| ultieconomy.money.balance-currency | Same three-figure view, scoped to a named currency. Same four lines as the row above, the header naming the currency. Naming the primary currency (`coins` by default) uses the account wallet the row above shows — the primary currency has one wallet (UltiKits/UltiEconomy#25), so `/money coins` shows the same three figures as `/money` | command | `/money <currency>` | ultieconomy.money | player | player | brief | MoneyCommand#onCurrencyBalance |

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
| ultieconomy.note.create-currency | Create a money note for a named currency, paid from that currency's wallet. Naming the primary currency (`coins` by default) uses the account wallet the row above shows — the primary currency has one wallet (UltiKits/UltiEconomy#25): `/note <amount> coins` is paid from the same cash `/note <amount>` is, and redeeming either note credits it back there | command | `/note <amount> <currency>` | ultieconomy.note | player | player | brief | NoteCommand#onCreateCurrencyNote |
| ultieconomy.note.redeem | Redeem the money note currently held in the main hand via command, crediting its face value in its own currency and removing one from the stack | command | `/note redeem` | ultieconomy.note | player | player | brief | NoteCommand#onRedeem |
| ultieconomy.note.redeem-onuse | Redeem a held money note by right-clicking (air or a block) instead of running a command — reads and credits the note identically to `ultieconomy.note.redeem`, then unconditionally cancels the interaction event regardless of whether the credit succeeded | event | right-click while holding a money-note item | n/a | n/a | player | brief | NoteRedeemListener#onInteract |

## Admin

`EcoAdminCommand` — class-level `@CmdExecutor(permission = "ultieconomy.admin", alias =
{"eco"})`, no `@CmdTarget` anywhere on the class (resolves to `both`). Console-compatible and
supports offline targets via `Bukkit#getOfflinePlayer`.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.admin.check | View a target player's primary-currency cash, bank, and total wealth. Under `language: en`: `<target>'s balance: …`, `<target>'s bank balance: …`, `Total wealth: …` (UltiKits/UltiEconomy#14) | command | `/eco check <player>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onCheck |
| ultieconomy.admin.check-currency | Same view scoped to a named currency. Same lines as the row above. Naming the primary currency (`coins` by default) uses the account wallet the row above shows — the primary currency has one wallet (UltiKits/UltiEconomy#25) | command | `/eco check <player> <currency>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onCheckCurrency |
| ultieconomy.admin.give | Add primary-currency cash to a target player's balance, online or offline. Success line: `Gave <target> <amount>` under `language: en` | command | `/eco give <player> <amount>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onGive |
| ultieconomy.admin.give-currency | Add cash in a named currency. Naming the primary currency (`coins` by default) uses the account wallet the row above shows — the primary currency has one wallet (UltiKits/UltiEconomy#25). Success line as `ultieconomy.admin.give` | command | `/eco give <player> <amount> <currency>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onGiveCurrency |
| ultieconomy.admin.set | Set a target player's primary-currency cash balance outright. Success line: `Set <target>'s balance to <amount>` under `language: en` | command | `/eco set <player> <amount>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onSet |
| ultieconomy.admin.set-currency | Set a named currency's cash balance outright. Naming the primary currency (`coins` by default) uses the account wallet the row above shows — the primary currency has one wallet (UltiKits/UltiEconomy#25). Success line as `ultieconomy.admin.set` | command | `/eco set <player> <amount> <currency>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onSetCurrency |
| ultieconomy.admin.take | Deduct primary-currency cash from a target player's balance, refusing if insufficient. Success line: `Took <amount> from <target>` under `language: en` | command | `/eco take <player> <amount>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onTake |
| ultieconomy.admin.take-currency | Deduct cash in a named currency. Naming the primary currency (`coins` by default) uses the account wallet the row above shows — the primary currency has one wallet (UltiKits/UltiEconomy#25). Success line as `ultieconomy.admin.take` | command | `/eco take <player> <amount> <currency>` | ultieconomy.admin | both | admin | brief | EcoAdminCommand#onTakeCurrency |
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
| ultieconomy.config.currencies.currencies | The full currency-definition map, each entry `{display-name, symbol, initial-cash, bank-enabled, min-deposit, max-bank-balance, primary}`; exactly one entry must set `primary: true`, and no id may start with `~merging-into-account:` (reserved for `ultieconomy.persistence.primary-wallet-merge`; either violation refuses the module at load). Every non-primary currency has its own wallet (a `currency_balances` row per player) governed by its own entry. The primary currency is the one Vault exposes, and its one wallet is the account (`economy_accounts`); its starting cash, bank switch, minimum deposit and bank cap come from `config/config.yml`, so the primary entry's `initial-cash`, `bank-enabled`, `min-deposit` and `max-bank-balance` are not read — its `display-name` and `symbol` still are. When one of those four is present in the primary entry with a value different from `config.yml`'s `initial-cash`, `bank.enabled`, `bank.min-deposit` or `bank.max-balance`, loading logs one WARN per setting naming both files, both keys and both values and saying the `config.yml` value applies; an absent or agreeing setting logs nothing (a bank cap of 0 or below means "no cap" in both files, so `0` and `-1` agree) (maintainer decision 2026-09-24, UltiKits/UltiEconomy#25) | config | `config/currencies.yml: currencies (default: 1 entry, "coins", primary)` | n/a | n/a | admin | detailed | CurrencyManager#CurrencyManager, StartupWarnings#logPrimaryCurrencyConflicts |

## Vault and PlaceholderAPI registration

Boot-time behaviour driven by `UltiEconomy#registerSelf()`, gated on the presence of a soft
dependency rather than on any config key — no `@ConditionalOnConfig` is involved in either
registration row — plus the matching unload-time Vault deregistration in
`UltiEconomy#onUnregister()`.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.vault.register | Register `VaultEconomyProvider` as the server's Vault `Economy` service at `ServicePriority.Normal`, only if the `Vault` plugin is present — the hard dependency declared in `plugin.yml`, so in practice this always runs | event | install/enable this module with Vault present | n/a | n/a | internal | none | UltiEconomy#registerSelf |
| ultieconomy.placeholder.register | Register `EconomyPlaceholderExpansion` with PlaceholderAPI, only if the `PlaceholderAPI` plugin is present (soft dependency) | event | install/enable this module with PlaceholderAPI present | n/a | n/a | internal | none | UltiEconomy#registerSelf |
| ultieconomy.vault.unregister | When this module unloads (for example `/upm uninstall UltiTools-Economy` at runtime), remove the `VaultEconomyProvider` it created from Bukkit's services manager for `Economy.class`; null-guarded, so a module whose `registerSelf` never created a provider makes no call. This module's own hook must do it: `ultieconomy.vault.register` names the `Vault` plugin as the registration's owner, and the framework's own unload steps (`UltiToolsPlugin#unregisterSelf`) only unregister this module's commands and listeners (UltiKits/UltiEconomy#22) | event | unload this module at runtime, e.g. `/upm uninstall UltiTools-Economy` | n/a | n/a | internal | none | UltiEconomy#onUnregister |

## Player join

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.player.join-init | On a player's first (or any) join, create their primary-currency account if absent (crediting `config/config.yml`'s `initial-cash`, once), then create a per-currency balance row for every currently-configured non-primary currency they do not already have one for. No per-currency row is created for the primary currency, whose one wallet is the account (UltiKits/UltiEconomy#25) | event | join the server as any player | n/a | n/a | internal | none | PlayerJoinListener#onPlayerJoin |

## Scheduled tasks

Both tasks are registered by the framework's `TaskManager` from a config-bound `@Scheduled`
method (UltiTools 6.3.0, UltiKits/UltiTools-Reborn#531), once when the module loads, on the main
thread (a binding is sync only). The interval is read in seconds from `interest.interval` /
`leaderboard.update-interval` in `config/config.yml`; the default lives only in the `EconomyConfig`
field. Each registration logs one INFO line, `Registered config-bound @Scheduled task:
<Class>.<method> (delay=<d>, period=<p>, async=false, config=EconomyConfig, periodKey=<key>=<n>s…)`.
`/ul reload UltiTools-Economy` does not register them again: it applies a changed interval to the
running task, keeping its place in its cycle (the next run is the last run -- or, before the first,
the load -- plus the new interval, or the next tick if that has passed), and logs one INFO line
`rescheduled config-bound @Scheduled task <Class>.<method> after reload`; an unchanged value leaves
the task alone. A value below 1 or above 107374182 seconds refuses the module at load, naming the
key; at reload it is not applied, the running value is kept and a WARNING names the key. Unloading
the module (`/upm uninstall UltiTools-Economy`, server shutdown) cancels both through
`PluginManager#unregister`. The binding needs `api-version: 630` in `plugin.yml`: an older framework
would drop it silently and run the method once at load, so it refuses the module instead. Before
this release neither task existed (UltiKits/UltiEconomy#15): nothing called `distributeInterest()` or
either refresh method, so no interest was ever paid and every leaderboard placeholder read an empty
cache.

`InterestService` is no longer `@ConditionalOnConfig` on `interest.enabled`: the service always
exists and the switch is read at every run instead, so turning interest on or off with
`/ul reload UltiTools-Economy` takes effect at the next payment.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.scheduled.interest-payment | Every `interest.interval` seconds (1800 by default, 36000 ticks; the first run one interval after the module loads, not at load), if `interest.enabled` is `true` at that moment: every positive primary-currency bank balance on the account row (`PlayerAccountEntity` — the balance `/bank`, `/money`, `/eco check <player>` and Vault show) and every positive bank balance in each other currency whose `bank-enabled` is `true` (`CurrencyBalanceEntity`) is credited `balance × interest.rate`, capped at `interest.max-interest` when that is above 0 and never taking the balance above its bank maximum (`bank.max-balance` for the primary account, the currency's own `max-bank-balance` otherwise, when above 0; a balance at the maximum gets nothing). Each row is read once by `getAll()` and written once by `update`, with no per-row lookup; the owner, if online, is sent a chat line only after that write succeeded, and a failed write is logged and leaves the row unchanged. If the switch is `false` the run reads no account and sends nothing. Every server that runs this task pays every balance in its database, so servers sharing one database must have interest on for exactly one of them (the boot warning says so). The account row is the primary currency's only wallet (UltiKits/UltiEconomy#25): no per-currency row is created for it; should one exist, it earns nothing, so a player is paid once for the primary currency and the per-payment cap is `interest.max-interest` (maintainer ruling 2026-09-23) | scheduled | automatic while the module is loaded; `interest.enabled` and `interest.interval` in `config/config.yml` | n/a | n/a | admin | detailed | InterestService#payInterestIfEnabled |
| ultieconomy.scheduled.leaderboard-refresh | Rebuild the cached wealth leaderboards: once as soon as the module has loaded (delay 0), then every `leaderboard.update-interval` seconds (60 by default, 1200 ticks) — the primary leaderboard from every account's cash + bank, and one leaderboard per configured currency: a non-primary currency's from its balance rows, the primary currency's the primary leaderboard itself (UltiKits/UltiEconomy#25). The rank and top-N placeholders read these caches, so they lag real balances by up to one interval | scheduled | automatic while the module is loaded; `leaderboard.update-interval` in `config/config.yml`, no on/off switch | n/a | n/a | internal | brief | LeaderboardService#refreshAll |

## Leaderboard placeholders

`EconomyPlaceholderExpansion` (identifier `ultieconomy`), registered per
`ultieconomy.placeholder.register` above. The rank and top-N placeholders read the caches
`ultieconomy.scheduled.leaderboard-refresh` rebuilds every `leaderboard.update-interval` seconds (60 by
default), so they lag real balances by up to that long; before this release nothing rebuilt them and they always read empty (UltiKits/UltiEconomy#15).

12 distinct placeholder-parameter branches exist across `onRequest`, `handleCurrencyPlaceholder`,
and `handleTopPlaceholder` (5 unscoped: `cash`/`bank`/`total`/`cash_formatted`/`rank`; 5 identical
branches re-dispatched per named currency via a `<currencyId>_<type>` prefix; 2 leaderboard-only:
`top_name_<N>`/`top_balance_<N>`), catalogued below as 8 rows — the 5 currency-scoped branches
share `handleCurrencyPlaceholder`'s logic exactly and are bundled into one row rather than
restated five times.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.placeholder.bank | Primary-currency bank balance, raw two-decimal number, no currency symbol | placeholder | `%ultieconomy_bank%` | n/a | n/a | admin | brief | EconomyPlaceholderExpansion#onRequest |
| ultieconomy.placeholder.cash | Primary-currency cash balance, raw two-decimal number, no currency symbol | placeholder | `%ultieconomy_cash%` | n/a | n/a | admin | brief | EconomyPlaceholderExpansion#onRequest |
| ultieconomy.placeholder.cash-formatted | Primary-currency cash balance with the configured currency symbol prefixed | placeholder | `%ultieconomy_cash_formatted%` | n/a | n/a | admin | brief | EconomyPlaceholderExpansion#onRequest |
| ultieconomy.placeholder.currency-scoped | Any of `cash`/`bank`/`total`/`cash_formatted`/`rank`, scoped to a named currency instead of the primary one, dispatched by a `<currencyId>_` prefix recognised only when `currencyManager.hasCurrency(...)` matches. `_rank` reads that currency's own leaderboard as of the last refresh (`ultieconomy.scheduled.leaderboard-refresh`) and falls back to the primary-currency rank while that currency's leaderboard is empty. Naming the primary currency (`%ultieconomy_coins_cash%` and the rest) reads the account wallet and the primary leaderboard, the same values as the unprefixed placeholders (UltiKits/UltiEconomy#25) | placeholder | `%ultieconomy_<currency>_cash%`, `%ultieconomy_<currency>_bank%`, `%ultieconomy_<currency>_total%`, `%ultieconomy_<currency>_cash_formatted%`, `%ultieconomy_<currency>_rank%` | n/a | n/a | admin | detailed | EconomyPlaceholderExpansion#handleCurrencyPlaceholder |
| ultieconomy.placeholder.rank | Sender's 1-based primary-currency wealth rank (cash + bank) as of the last leaderboard refresh, or `-` if the sender is not in it (for example an account created since that refresh) | placeholder | `%ultieconomy_rank%` | n/a | n/a | admin | brief | EconomyPlaceholderExpansion#onRequest |
| ultieconomy.placeholder.top-balance | Total wealth (cash + bank) of the Nth-ranked player, 1-based, as of the last leaderboard refresh, as a raw two-decimal number; `0.00` when fewer than N accounts exist | placeholder | `%ultieconomy_top_balance_<N>%` | n/a | n/a | admin | brief | EconomyPlaceholderExpansion#getTopBalance |
| ultieconomy.placeholder.top-name | Name of the Nth-ranked player, 1-based, as of the last leaderboard refresh; `-` when fewer than N accounts exist | placeholder | `%ultieconomy_top_name_<N>%` | n/a | n/a | admin | brief | EconomyPlaceholderExpansion#getTopName |
| ultieconomy.placeholder.total | Primary-currency total wealth (cash + bank), raw two-decimal number | placeholder | `%ultieconomy_total%` | n/a | n/a | admin | brief | EconomyPlaceholderExpansion#onRequest |

## Data persistence

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.persistence.balance-restart | A player's primary-currency account (`PlayerAccountEntity`, table `economy_accounts`, the primary currency's only wallet) and every non-primary per-currency balance row (`CurrencyBalanceEntity`, table `currency_balances`) survive a full server restart, in whichever ORM backend the framework is configured to use | persistence | change a balance via any command above, note both the before and after values, restart the server, then read the balance back via the same command | n/a | n/a | admin | none | EconomyServiceImpl#getAccount, EconomyServiceImpl#getBalance |
| ultieconomy.persistence.primary-wallet-merge | Once, when the module loads — first thing in `registerSelf`, so before its Vault provider and PlaceholderAPI expansion are registered, and before the framework registers its commands, listeners and scheduled tasks (all of which follow a successful `registerSelf`): every `currency_balances` row of the primary currency (the second wallet UltiEconomy 2.0.0 kept for it) is added to that player's account — cash to cash, bank to bank, amounts below zero not taken — and removed; a player with only such a row gets an account holding exactly its amounts. One INFO line per merged player (`Primary-currency wallet merge: added <cash> cash and <bank> bank from <player>'s second wallet to their account, which now holds <cash> cash and <bank> bank.` under `language: en`) and one total line; a WARN line for a negative amount, for a player whose merged balance cannot be held exactly — amounts are added as the decimals they print as (0.1 + 0.2 is 0.3), and a sum that overflows, has more significant digits than a balance keeps, or is not a number is not merged (nothing moves, the row stays) — and for a player whose earlier, interrupted merge no longer adds up (their rows are left for an operator). Each player's merge marks the rows first, credits the account with absolute values second and removes the rows last, each step durable before the next (the JSON backend is flushed between steps), and only marked rows are ever removed (a start that finds a player's marking interrupted finishes it first), so a start interrupted at any point is completed by the next without adding anything twice (a JSON file torn by a stop mid-rewrite excepted); a start that finds nothing to merge logs nothing. A storage failure logs an ERROR and refuses the module (maintainer decision 2026-09-24, confirmed 2026-09-25; UltiKits/UltiEconomy#25) | persistence | start the server after upgrading from 2.0.0 | n/a | n/a | admin | detailed | PrimaryWalletMerge#run |
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
`leaderboard.display-count`); the packaged file has no
`tax:` section at all. The remaining 7 — every `tax.*` key — are absent from the shipped resource
and are written to the on-disk `plugins/UltiTools/pluginConfig/UltiTools-Economy/config/config.yml`
on the plugin's first boot instead, by
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

**Two keys are bound to the scheduled tasks:** `interest.interval` and `leaderboard.update-interval`.
Before this release nothing read either (UltiKits/UltiEconomy#15); they now set the two tasks'
intervals through the framework's config-bound `@Scheduled` (UltiKits/UltiTools-Reborn#531), so a
value an operator kept in the file from 1.0.0 or 2.0.0 takes effect on upgrade. See
`## Scheduled tasks` for reload and invalid-value behaviour.

**Language:** every chat, GUI and console line this module writes goes through its
language catalogue with an ASCII key (`economy.money.cash`), so it follows the framework's
`language` setting. Before this fix, 23 of the 44 keys the source passed had no entry in either
catalogue and rendered their raw Chinese source text in every language (UltiKits/UltiEconomy#14),
and the help headers, the money note's name and lore, both startup warnings and three console
error lines were fixed English. Two JUnit guards (`UltiEconomyLanguageCatalogueTest`,
`UltiEconomyCjkLiteralScopeTest`) now fail the build on a missing key or on Chinese text outside a
catalogue. The exception messages `CurrencyManager` throws for a malformed `currencies.yml` stay
English: they are messages to code, reported by the framework, not lines this module shows. The
refusal texts the Vault bridge returns to a calling plugin (insufficient funds, a negative amount, a
failed deposit, shared banks not supported) follow the language, because shop and sign plugins show
them to the player; only the deprecated name-based Vault methods keep a fixed English text.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.config.config.bank.enabled | Enable the bank feature (primary-currency deposit/withdraw); gates `BankCommand`/`DepositCommand`/`WithdrawCommand`'s primary-currency mappings only, not their currency-aware siblings. It does not gate the interest payment: with `interest.enabled: true`, an existing positive primary bank balance keeps earning interest while this is `false` (`InterestService#distributeInterest` does not read it) | config | `config/config.yml: bank.enabled (default: true)` | n/a | n/a | admin | detailed | EconomyConfig#isBankEnabled |
| ultieconomy.config.config.bank.max-balance | Maximum primary-currency bank balance a deposit may not exceed, and that an interest payment never takes a balance above (a balance at the maximum earns nothing; one below it earns at most the room left); `-1` means unlimited | config | `config/config.yml: bank.max-balance (default: -1)` | n/a | n/a | admin | brief | DepositCommand#onDeposit |
| ultieconomy.config.config.bank.min-deposit | Minimum amount accepted by a single primary-currency deposit | config | `config/config.yml: bank.min-deposit (default: 100.0)` | n/a | n/a | admin | brief | DepositCommand#onDeposit |
| ultieconomy.config.config.currency-name | Currency display name used in Vault's `currencyNamePlural`/`currencyNameSingular` queries | config | `config/config.yml: currency-name (default: "Coins")` | n/a | n/a | admin | brief | VaultEconomyProvider#currencyNamePlural |
| ultieconomy.config.config.currency-symbol | Symbol prefixed to every primary-currency amount a path formats without naming a currency (`/money`, `/bank`, `/pay <player> <amount>`, `%ultieconomy_cash_formatted%`, …). A path that names the primary currency (`/money coins`, `%ultieconomy_coins_cash_formatted%`, …) formats the same wallet with the primary entry's `symbol` in `currencies.yml`; which file owns the primary currency's name and symbol is decided separately | config | `config/config.yml: currency-symbol (default: "$")` | n/a | n/a | admin | brief | EconomyServiceImpl#formatAmount |
| ultieconomy.config.config.initial-cash | Starting primary-currency cash balance for a newly-created account | config | `config/config.yml: initial-cash (default: 1000.0)` | n/a | n/a | admin | brief | EconomyServiceImpl#getOrCreateAccount |
| ultieconomy.config.config.interest.enabled | Whether each scheduled interest payment (`ultieconomy.scheduled.interest-payment`) pays anything, read at every payment, so a `/ul reload UltiTools-Economy` that changes it applies to the next one. While it is `true`, every boot logs one WARN line from `StartupWarnings#log` naming the rate, the configured `interest.interval`, the cap (or that there is none) and how to turn it off. Declared default and shipped value `false`; a file written by 1.0.0 or 2.0.0 holds `true` (their shipped value) unless edited, and on upgrade that value applies (UltiKits/UltiEconomy#15) | config | `config/config.yml: interest.enabled (default: false; a file written by 1.0.0 or 2.0.0 holds true)` | n/a | n/a | admin | detailed | InterestService#payInterestIfEnabled, StartupWarnings#log |
| ultieconomy.config.config.interest.interval | Seconds between scheduled interest payments, and before the first one after load, bound to `ultieconomy.scheduled.interest-payment`; 1 to 107374182. `/ul reload UltiTools-Economy` applies a change keeping the payment's place in its cycle (never paid early, never postponed); an invalid value refuses the whole economy module at load -- the Vault economy provider and every command included, even while `interest.enabled` is `false` -- and at reload is ignored with a WARNING naming the key. The value in an upgraded file applies: 1.0.0 and 2.0.0 shipped `1800` but nothing read it (UltiKits/UltiEconomy#15, UltiKits/UltiTools-Reborn#531) | config | `config/config.yml: interest.interval (default: 1800)` | n/a | n/a | admin | detailed | InterestService#payInterestIfEnabled |
| ultieconomy.config.config.interest.max-interest | Cap on a single interest payment to a single balance; a value of 0 or below means no cap | config | `config/config.yml: interest.max-interest (default: 10000.0)` | n/a | n/a | admin | brief | InterestService#distributeInterest |
| ultieconomy.config.config.interest.rate | Fraction of a positive bank balance credited at each scheduled payment (0.03 = 3% per payment; payments every `interest.interval` seconds) | config | `config/config.yml: interest.rate (default: 0.03)` | n/a | n/a | admin | brief | InterestService#distributeInterest |
| ultieconomy.config.config.leaderboard.display-count | Default number of top entries `LeaderboardService#getDefaultDisplayCount` reports; no command or placeholder in this module actually calls that accessor | config | `config/config.yml: leaderboard.display-count (default: 10)` | n/a | n/a | admin | none | LeaderboardService#getDefaultDisplayCount (declared, no caller) |
| ultieconomy.config.config.leaderboard.update-interval | Seconds between leaderboard refreshes, bound to `ultieconomy.scheduled.leaderboard-refresh` (the first refresh runs at load); 1 to 107374182, applied at `/ul reload` the same way as `interest.interval`; an invalid value refuses the whole economy module at load, Vault provider included (UltiKits/UltiEconomy#15, UltiKits/UltiTools-Reborn#531) | config | `config/config.yml: leaderboard.update-interval (default: 60)` | n/a | n/a | admin | brief | LeaderboardService#refreshAll |
| ultieconomy.config.config.tax.enabled | Master switch over all taxation, read at every transfer (UltiKits/UltiEconomy#16): while `false`, `TaxService#calculateTransactionTax` returns 0 before looking at `tax.transaction-tax.*`, so both `EconomyServiceImpl#transfer` overloads credit the receiver the full amount and deposit nothing into the treasury; while `true`, the transaction tax applies as `tax.transaction-tax.enabled`/`.rate` say. A `/ul reload UltiTools-Economy` that changes it applies to the next transfer. While it is `false`, every boot logs one WARN line from `StartupWarnings#log` saying no transaction tax and no wealth tax is collected and how to turn it back on. The treasury commands (`/eco treasury ...`) are not taxation and are not gated by it. The declared default is `true`, but it reaches only a file that lacks the key: every server that has run 2.0.0 (the release that added the tax settings) had `false` written into its file on first boot (the then-declared default) and keeps that value | config | `config/config.yml: tax.enabled (default: true; a file written by 2.0.0 holds false, see the Feature text)` | n/a | n/a | admin | detailed | TaxService#calculateTransactionTax, StartupWarnings#log |
| ultieconomy.config.config.tax.transaction-tax.enabled | Enable a transaction tax deducted from a `/pay` transfer only, deposited into the treasury; has an effect only while the master switch `tax.enabled` is `true` (UltiKits/UltiEconomy#16). `EcoAdminCommand#onGive`/`#onTake`/`#onSet` call `EconomyService#addCash`/`#takeCash`/`#setCash` directly, never `#transfer` — `TaxService#calculateTransactionTax` is invoked only from `EconomyServiceImpl#transfer`'s two overloads (the code path behind `/pay` and `/pay ... <currency>`), so the admin commands neither deduct tax nor deposit anything into the treasury regardless of this key | config | `config/config.yml: tax.transaction-tax.enabled (default: true)` | n/a | n/a | admin | detailed | TaxService#calculateTransactionTax, EconomyServiceImpl#transfer |
| ultieconomy.config.config.tax.transaction-tax.exempt-permission | Permission node that, if held, is intended to exempt a player from the transaction tax — declared but never referenced by any `hasPermission` check anywhere in this module's source (0 occurrences outside this config field and its default-value string), so holding it currently changes nothing | config | `config/config.yml: tax.transaction-tax.exempt-permission (default: "ultieconomy.tax.exempt", not enforced anywhere)` | n/a | n/a | admin | none | EconomyConfig#getTransactionTaxExemptPermission (declared, never checked) |
| ultieconomy.config.config.tax.transaction-tax.rate | Transaction tax rate applied to every taxed transfer | config | `config/config.yml: tax.transaction-tax.rate (default: 0.05)` | n/a | n/a | admin | brief | TaxService#calculateTransactionTax |
| ultieconomy.config.config.tax.wealth-tax.enabled | Declared switch for a periodic wealth tax; `TaxService#calculateWealthTax(double, List)` exists and is unit-tested but has zero production callers anywhere in this module's source, and no scheduled task or command ever invokes it — the entire wealth-tax mechanism is unbuilt beyond this one calculation method. Split out of UltiKits/UltiEconomy#16 into UltiKits/UltiEconomy#27, which records the design decisions needed first; `tax.enabled: false` would also stop it once built | config | `config/config.yml: tax.wealth-tax.enabled (default: false, unreachable, see UltiKits/UltiEconomy#27)` | n/a | n/a | admin | detailed | TaxService#calculateWealthTax (declared, no caller) |
| ultieconomy.config.config.tax.wealth-tax.exempt-permission | Declared exempt-permission for the unbuilt wealth tax (see the row above, UltiKits/UltiEconomy#27); not referenced by any `hasPermission` check | config | `config/config.yml: tax.wealth-tax.exempt-permission (default: "ultieconomy.wealthtax.exempt", not enforced anywhere)` | n/a | n/a | admin | none | EconomyConfig#getWealthTaxExemptPermission (declared, never checked) |
| ultieconomy.config.config.tax.wealth-tax.interval | Declared interval for the unbuilt wealth tax (see the row two above, UltiKits/UltiEconomy#27); not read by any code path since the feature it would drive was never built | config | `config/config.yml: tax.wealth-tax.interval (default: 3600, unreachable)` | n/a | n/a | admin | none | EconomyConfig#getWealthTaxInterval (declared, never read outside this class) |

## Language

Every chat, GUI and console line this module writes follows the framework-wide `language` setting
(`plugins/UltiTools/config.yml`); see the note under `## Configuration`.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultieconomy.i18n.language | All of this module's chat, GUI and console text in the server's language: `lang/en.json` under `language: en`, `lang/zh.json` under `language: zh`, including the 23 command messages that rendered raw Chinese source text in every language before this fix (UltiKits/UltiEconomy#14) | config | framework `config.yml: language` | n/a | both | admin | none | `lang/en.json`, `lang/zh.json`, every `i18n(...)` call |
