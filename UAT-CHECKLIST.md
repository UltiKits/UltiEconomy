# UltiEconomy — UAT Checklist

This document is the executable companion to `FEATURES.md`: one row per feature stating the
steps to exercise it and the observable truth that proves it works. It is an internal reference
for real-machine verification, not user-facing documentation.

> Batches are dispatched at 60 rows or fewer, and a batch never spans two repositories. There are
> exactly two legitimate exits to `human-uat-pending`: a row needing the pixel layer while the
> real-client harness is not ready, and a row needing personal credentials. Every other row must
> reach `pass`, `fail`, or `blocked`.

## Conventions

- **Columns:** `ID`, `Preconditions`, `Steps`, `Expected`, `Layer`, `Covers`.
- **ID:** cites its `FEATURES.md` ID verbatim. A negative case suffixes the checklist ID only, as
  `.neg-<slug>` — a negative case still tests the same feature, so the base ID is unchanged.
- **Layer**, copied verbatim from Laojun's own `ultitools-real-client-uat` skill: `protocol`,
  `java-client`, `os-input`, `pixel`, `server`, `human`. This module has no panel-facing surface,
  so no row below carries `human`.
- **Covers** back-references a Phase 9 GUI-excluded class name; left blank when no such class
  applies. UltiEconomy is not one of the nine modules in Phase 9's GUI-exclusion register (no
  `UltiEconomy.md` file exists under `.planning/phases/09-module-ecosystem-readiness-and-test-coverage/gui-exclusions/`),
  so every row below leaves `Covers` blank.
- A row whose Preconditions cite a prior row's checklist ID must appear after that row in file
  order — asserted mechanically: for every row, every checklist ID literally cited in its
  Preconditions cell must have a strictly smaller line number in this file than the row citing it
  (sweep class 8, D-27a). A Preconditions cell that merely *describes* a state to set up (e.g. "a
  second, non-primary currency configured") without citing another row's ID by name is not
  subject to this check.
- **Expected** must name an observable truth — an exact chat line, a log line, a database row —
  and never the words "it works". **This document is English-only (D-02) — a raw Chinese message
  literal is never reproduced inline, even when the row's whole point is that the handler emits
  one instead of the English translation.** Where the linked `FEATURES.md` row documents a broken
  i18n key (UltiKits/UltiEconomy#14), this document's Expected states that the line renders as the
  untranslated Chinese source literal and cites the exact source file and line the executing agent
  should read to know the literal characters, rather than quoting them here — the same convention
  the framework's own `UAT-CHECKLIST.md` uses for a hardcoded-Chinese defect
  (`ultichat.chat.pipeline.neg-antispam-block`, `UltiChat#18`).
- **Multi-currency precondition:** this module ships exactly one currency by default
  (`config/currencies.yml`'s single `coins` entry, marked `primary: true`). Every row below whose
  ID ends `-currency` needs a SECOND, non-primary currency added to `config/currencies.yml` before
  it can be exercised at all — stated in that row's own Preconditions, not cited from a shared
  row, since adding a currency is a file edit, not a feature this document gives its own
  checklist row (see `## Configuration` below for why).
- **Config-per-file rule (D-06), narrowed for this module's own layout:** UltiEconomy ships two
  yml resources — `config/config.yml` (the module's one `@ConfigEntity`-bound file) and
  `config/currencies.yml` (read directly via `YamlConfiguration`, not `@ConfigEntity`-bound). Per
  this plan's own explicit instruction ("exactly one config row for the single `@ConfigEntity`
  class"), this document carries exactly ONE config-per-file row, `ultieconomy.config.config-yml`,
  whose Steps exercise BOTH shipped files in the same pass — they load during the same server
  startup with no interdependent ordering, and `currencies.yml`'s own single row in `FEATURES.md`
  (`ultieconomy.config.currencies.currencies`) has no command surface of its own to test
  independently; the only way to exercise it at all is by editing the file and observing a
  currency-aware command, which the config-per-file row already does.

## Bank

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultieconomy.bank.balance | `language: en` in config.yml; `bank.enabled: true` (shipped default); the sender has a nonzero bank balance (deposit first, or use a fresh account's `0.00`) | Run `/bank` | The chat line renders as the untranslated Chinese source literal `plugin.i18n` is called with at `BankCommand.java:66`, immediately followed by the currency symbol and two-decimal amount (e.g. `$0.00`) — never the English "Your bank balance: ..." translation (UltiKits/UltiEconomy#14) | server | |
| ultieconomy.bank.balance.neg-disabled | `bank.enabled: false` in config.yml | Run `/bank` | Chat line is the RED-colored message at `BankCommand.java:60` — this key DOES have an exact dictionary match, so under `language: en` it reads `Bank feature is not enabled` | server | |
| ultieconomy.bank.balance-currency | `language: en` in config.yml; a second, non-primary currency (e.g. `gems`) added to `config/currencies.yml`, server restarted after the edit | Run `/bank gems` | Same untranslated-literal behaviour as `ultieconomy.bank.balance` above (`BankCommand.java:80`, UltiKits/UltiEconomy#14), formatted with `gems`'s own symbol, NOT the primary currency's `$` | server | |
| ultieconomy.bank.balance-currency.neg-unknown | `language: en` in config.yml | Run `/bank does-not-exist` | Chat line is the RED-colored message at `BankCommand.java:74` — this key DOES localize; under `language: en` it reads `Currency not found` | server | |

## Deposit

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultieconomy.deposit.deposit | `language: en` in config.yml; `bank.enabled: true`; sender's cash ≥ `bank.min-deposit` (default 100.0) | Run `/deposit 500` | Chat line renders as the untranslated Chinese source literal at `DepositCommand.java:59`, followed by the formatted amount (`$500.00`) — never the English "Successfully deposited..." translation (UltiKits/UltiEconomy#14); `/bank` immediately afterward shows the increased balance | server | |
| ultieconomy.deposit.deposit.neg-below-minimum | `language: en` in config.yml; `bank.enabled: true`; `bank.min-deposit: 100.0` (shipped default) | Run `/deposit 50` | Chat line is the RED-colored message at `DepositCommand.java:52` — this key DOES localize; under `language: en` it reads `Minimum deposit: $100.00` | server | |
| ultieconomy.deposit.deposit.neg-insufficient-cash | `language: en` in config.yml; `bank.enabled: true`; sender's cash < the amount requested but ≥ `bank.min-deposit` | Run `/deposit <amount greater than current cash>` | Chat line is the RED-colored "insufficient balance" message (`DepositCommand.java:67`) — localizes correctly to `Insufficient balance` under `language: en` | server | |
| ultieconomy.deposit.deposit.neg-max-balance | `language: en` in config.yml; `bank.enabled: true`; `bank.max-balance` set to a small positive value (NOT the shipped `-1` unlimited default) with the sender's current bank balance already at or near it | Run `/deposit <amount that would exceed bank.max-balance>` | Chat line is the RED-colored message at `DepositCommand.java:65` — localizes correctly to `Bank balance has reached the limit` | server | |
| ultieconomy.deposit.deposit-currency | `language: en` in config.yml; a second, non-primary currency (e.g. `gems`) configured with `bank-enabled: true`, `min-deposit` set to 200 OR LESS, and `max-bank-balance` either `-1` (unlimited) or at least 200 above the sender's current `gems` bank balance; sender's `gems` CASH balance specifically ≥ 200 (not merely ≥ `min-deposit` — a cash balance between `min-deposit` and 200 would refuse with `Insufficient balance` before this row ever observes a success) | Run `/deposit 200 gems` | Same untranslated-literal success pattern (`DepositCommand.java:95`, UltiKits/UltiEconomy#14), formatted in `gems` | server | |
| ultieconomy.deposit.deposit-currency.neg-bank-globally-disabled | `bank.enabled: false` in config.yml (the GLOBAL switch); a second currency (e.g. `gems`) configured with its OWN `bank-enabled: true`, `min-deposit` set to 200 or less, and `max-bank-balance` either `-1` or at least 200 above the sender's current `gems` bank balance; sender's `gems` cash ≥ 200 — the same three constraints as the row above, restated here rather than inherited, since this row's whole point is that `bank.enabled` makes no difference and every OTHER gate must therefore already be satisfied for the success to be observable | Run `/deposit 200 gems` | The deposit SUCCEEDS (same untranslated-literal success line as the row above, UltiKits/UltiEconomy#14) despite the global `bank.enabled` switch being off — `DepositCommand#onDepositCurrency` never calls `EconomyConfig#isBankEnabled()` at all, unlike its primary-currency sibling exercised by `ultieconomy.deposit.deposit.neg-below-minimum` above; this divergent-path row exists specifically because the two mappings are NOT symmetrically gated | server | |

## Withdraw

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultieconomy.withdraw.withdraw | `language: en` in config.yml; `bank.enabled: true`; sender's bank balance ≥ the amount requested (deposit first if needed) | Run `/withdraw 100` | Chat line renders as the untranslated Chinese source literal at `WithdrawCommand.java:53` followed by `$100.00` (UltiKits/UltiEconomy#14); `/bank` immediately afterward shows the decreased bank balance and `/money` shows the increased cash | server | |
| ultieconomy.withdraw.withdraw.neg-insufficient | `language: en` in config.yml; `bank.enabled: true`; sender's bank balance < the amount requested | Run `/withdraw <amount greater than current bank balance>` | Chat line is the RED-colored message at `WithdrawCommand.java:55` — localizes correctly to `Insufficient bank balance` | server | |
| ultieconomy.withdraw.withdraw-currency | `language: en` in config.yml; a second, non-primary currency (e.g. `gems`) configured, sender's `gems` bank balance ≥ the amount requested | Run `/withdraw 50 gems` | Same untranslated-literal success pattern (`WithdrawCommand.java:82`, UltiKits/UltiEconomy#14), formatted in `gems`; no `bank.enabled` (global) gate applies to this mapping | server | |

## Pay

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultieconomy.pay.pay | `language: en` in config.yml; `tax.transaction-tax.enabled: false` (NOT the shipped default — isolates this row to the i18n defect alone; see `.neg-tax-message-mismatch` below for the tax-and-message interaction); sender's cash ≥ the amount; a second online player as the target, not the sender | Run `/pay <target> 100` | BOTH the sender's own line (untranslated Chinese source literal, `PayCommand.java:62`) AND the target's own received line (untranslated Chinese source literal, `PayCommand.java:64`) never localize (UltiKits/UltiEconomy#14) — both parties see the raw Chinese source string under `language: en`, both quoting `100` (with tax disabled, the target's cash genuinely rises by exactly 100, so the figure and the actual credit agree here) | server | |
| ultieconomy.pay.pay.neg-tax-message-mismatch | `language: en` in config.yml; `tax.transaction-tax.enabled: true` (shipped default) with a nonzero `tax.transaction-tax.rate`; sender's cash ≥ the amount; a second online player as the target, not the sender | Run `/pay <target> 100`, then separately read the target's own `/money` cash figure before and after | BOTH chat lines (sender's and target's) quote the GROSS `100`, not the net amount actually credited — `PayCommand#onPay` formats `EconomyServiceImpl#transfer`'s own `amount` parameter (`PayCommand.java:60`), never the `received = amount - tax` value `transfer()` computes internally (`EconomyServiceImpl.java:210`) and actually applies to the target's balance; the target's `/money` cash figure rises by ONLY `100 - tax` (e.g. 95 at the shipped 5% rate), confirming the chat text overstates what was actually credited by the tax amount — UltiKits/UltiEconomy#18 | server | |
| ultieconomy.pay.pay.neg-self | `language: en` in config.yml | Run `/pay <own name> 10` | Chat line is the RED-colored "invalid amount" message (`PayCommand.java:54`) — localizes correctly to `Invalid amount`; `PayCommand#onPay` treats paying oneself as the same rejection path as a bad amount, not a distinct message | server | |
| ultieconomy.pay.pay.neg-insufficient | `language: en` in config.yml; sender's cash < the amount; a second online player as the target | Run `/pay <target> <amount greater than sender's cash>` | Chat line is the RED-colored "insufficient balance" message (`PayCommand.java:66`) — localizes correctly to `Insufficient balance`; the target receives NO message at all (the transfer never reaches `EconomyServiceImpl#transfer`, which refuses before crediting anyone) | server | |
| ultieconomy.pay.pay.neg-offline-target | `language: en` in config.yml | Run `/pay <a name with no player currently online>` | Chat line is the RED-colored "player not found" message (`PayCommand.java:49`) — localizes correctly to `Player not found`; `Bukkit#getPlayer` only resolves ONLINE players for this command, unlike `/eco`'s admin commands which also accept offline targets | server | |
| ultieconomy.pay.pay-currency | `language: en` in config.yml; `tax.transaction-tax.enabled: false` (same isolation as `ultieconomy.pay.pay` above, for the same reason); a second, non-primary currency (e.g. `gems`) configured; sender's `gems` cash ≥ the amount; a second online player as the target | Run `/pay <target> 50 gems` | Both parties' messages are the same untranslated Chinese source literals as `ultieconomy.pay.pay` above (`PayCommand.java:105,107`, UltiKits/UltiEconomy#14), formatted in `gems`; the same gross-vs-net mismatch (UltiKits/UltiEconomy#18) applies to this currency-aware overload too if tax is re-enabled — `EconomyServiceImpl#transfer(from, to, amount, currencyId)` computes the identical `received = amount - tax` internally | server | |

## Money (balance display)

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultieconomy.money.balance | `language: en` in config.yml | Run `/money` (also confirm `/bal` produces byte-identical output) | The header line is `=== Economy System ===` (English — `plugin.i18n("Economy System")`'s source key, at `MoneyCommand.java:63`, DOES have an exact dictionary match; not among the 23 keys UltiKits/UltiEconomy#14 lists); all three lines beneath it (`MoneyCommand.java:64-66`) render as their untranslated Chinese source literals — a working header directly over three broken body lines is itself the evidence the defect is per-key, not per-command | server | |
| ultieconomy.money.balance-currency | `language: en` in config.yml; a second, non-primary currency (e.g. `gems`) configured | Run `/money gems` | Header reads `=== Economy System (gems) ===` (English, working, `MoneyCommand.java:87`); the three body lines (`MoneyCommand.java:88-90`) render as their untranslated Chinese source literals, formatted in `gems` (UltiKits/UltiEconomy#14) | server | |
| ultieconomy.money.balance-currency.neg-unknown | `language: en` in config.yml | Run `/money does-not-exist` | Chat line is the RED-colored "currency not found" message (`MoneyCommand.java:74`) — localizes correctly to `Currency not found` | server | |

## Money Notes

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultieconomy.note.create | `language: en` in config.yml; sender's primary-currency cash ≥ the amount requested; sender has at least one free inventory slot | Run `/note 500` | Chat line reads `Money note created: $500.00` (this key, `NoteCommand.java:73`, DOES localize correctly — not among the 23 keys UltiKits/UltiEconomy#14 lists); a `Material.PAPER` item is added to the sender's inventory whose display name reads `[Money Note] 500.00 coins` and whose lore names the currency, value, and creator; `/money`'s cash figure drops by 500 | server | |
| ultieconomy.note.create.neg-insufficient | `language: en` in config.yml; sender's cash < the amount requested | Run `/note <amount greater than current cash>` | Chat line is the RED-colored "insufficient balance" message (`NoteCommand.java:66`) — localizes correctly; no item is added to the inventory | server | |
| ultieconomy.note.create-currency | `language: en` in config.yml; a second, non-primary currency (e.g. `gems`) configured; sender's `gems` cash ≥ the amount; sender has at least one free inventory slot — `NoteCommand#onCreateCurrencyNote` deducts the cash and calls `Inventory#addItem` without handling a full-inventory's returned leftovers, so a full inventory would deduct the cash with no note actually placed | Run `/note 20 gems` | Chat line reads `Money note created: <formatted>` (localizes correctly, `NoteCommand.java:100`); the created note's lore names `gems`, not the primary currency | server | |
| ultieconomy.note.redeem | `language: en` in config.yml; the sender is holding a money-note item in their main hand (created via `ultieconomy.note.create` above) | Run `/note redeem` | Chat line reads `Money note redeemed: $500.00` (localizes correctly, `NoteCommand.java:129`); the note item is consumed by one (removed entirely if the stack was 1); `/money`'s cash figure increases by the note's face value in its own currency | server | |
| ultieconomy.note.redeem.neg-empty-hand | `language: en` in config.yml; the sender is NOT holding a money-note item in their main hand | Run `/note redeem` | Chat line is the RED-colored message at `NoteCommand.java:108` — localizes correctly to `You are not holding a money note`; no balance change occurs | server | |
| ultieconomy.note.redeem-onuse | The sender is holding a money-note item in their main hand (created via `ultieconomy.note.create`) | Right-click air or a block while holding the note | The note is redeemed exactly as `ultieconomy.note.redeem` above (same chat line, same balance credit, same stack decrement, `NoteRedeemListener.java:75`) WITHOUT running any command; the interaction event that triggered it is cancelled regardless of outcome (confirmed by the block not being interacted with if right-clicking a block with its own interaction, e.g. a door) | os-input | |
| ultieconomy.note.redeem-onuse.neg-empty-hand | The sender is NOT holding a money-note item | Right-click air while holding an ordinary item (or empty hand) | Nothing happens — no chat line, no balance change, the interaction event is NOT cancelled by this listener (`NoteRedeemListener#onInteract`'s `isMoneyNote` check at line 54 returns false before any cancellation logic runs) | os-input | |

## Admin

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultieconomy.admin.check | `language: en` in config.yml; a target player who has joined at least once before (online or offline) | Run `/eco check <target>` from console | Header line `=== <target> ===` (hardcoded literal, `EcoAdminCommand.java:155`, not run through i18n at all, so unaffected by UltiKits/UltiEconomy#14 either way); the two balance lines AND the total-wealth line (`EcoAdminCommand.java:156-161`) all render as their untranslated Chinese source literals — never the English translation | server | |
| ultieconomy.admin.check.neg-unknown-player | `language: en` in config.yml | Run `/eco check does-not-exist-and-never-played` from console | Chat line is the RED-colored "player not found" message (`EcoAdminCommand.java:362`) — localizes correctly to `Player not found` | server | |
| ultieconomy.admin.check-currency | `language: en` in config.yml; a second, non-primary currency (e.g. `gems`) configured; a target player who has joined before | Run `/eco check <target> gems` from console | Header line `=== <target> (gems) ===` (`EcoAdminCommand.java:273`); all three body lines (`EcoAdminCommand.java:274-279`) render as their untranslated Chinese source literals, formatted in `gems` (UltiKits/UltiEconomy#14) | server | |
| ultieconomy.admin.give | `language: en` in config.yml; a target player who has joined before, currently offline | Run `/eco give <target> 1000` from console | Chat line renders as the untranslated Chinese source literal at `EcoAdminCommand.java:92-93` followed by `$1000.00` (UltiKits/UltiEconomy#14); `/eco check <target>` immediately afterward reflects the increased balance, proving the offline-target write actually persisted | server | |
| ultieconomy.admin.give-currency | `language: en` in config.yml; a second, non-primary currency (e.g. `gems`) configured; a target player who has joined AT LEAST ONCE AFTER `gems` was added to `config/currencies.yml` — a player who last joined before `gems` existed has no `CurrencyBalanceEntity` row for it at all (those are created by `PlayerJoinListener` only on join), and `EconomyServiceImpl#addCash(..., "gems")` returns `false` for a missing balance row rather than creating one, which this row would otherwise misread as the command failing | Run `/eco give <target> 500 gems` from console | Same untranslated-literal success pattern (`EcoAdminCommand.java:188-189`, UltiKits/UltiEconomy#14), formatted in `gems` | server | |
| ultieconomy.admin.take | `language: en` in config.yml; a target player whose current primary-currency cash ≥ the amount | Run `/eco take <target> 200` from console | Chat line renders as the untranslated Chinese source literal at `EcoAdminCommand.java:114-115` followed by `$200.00` (UltiKits/UltiEconomy#14) | server | |
| ultieconomy.admin.take.neg-insufficient | `language: en` in config.yml; a target player whose current cash < the amount | Run `/eco take <target> <amount greater than current cash>` from console | Chat line is the RED-colored "insufficient balance" message (`EcoAdminCommand.java:117`) — localizes correctly | server | |
| ultieconomy.admin.take-currency | `language: en` in config.yml; a second, non-primary currency configured; a target player with sufficient `gems` cash | Run `/eco take <target> 50 gems` from console | Same untranslated-literal success pattern (`EcoAdminCommand.java:217-218`, UltiKits/UltiEconomy#14), formatted in `gems` | server | |
| ultieconomy.admin.set | `language: en` in config.yml; a target player who has joined before | Run `/eco set <target> 9999` from console | Chat line renders as the untranslated Chinese source literal at `EcoAdminCommand.java:136-137` followed by `$9999.00` (UltiKits/UltiEconomy#14); `/eco check <target>` afterward confirms the balance is now exactly 9999, not additive | server | |
| ultieconomy.admin.set-currency | `language: en` in config.yml; a second, non-primary currency configured; a target player who has joined before | Run `/eco set <target> 42 gems` from console | Same untranslated-literal success pattern (`EcoAdminCommand.java:246-247`), formatted in `gems` | server | |
| ultieconomy.admin.treasury | `tax.transaction-tax.enabled: true` (shipped default); at least one taxed transfer already collected via `ultieconomy.pay.pay.neg-tax-message-mismatch` above — NOT `ultieconomy.pay.pay`, which this document's own Preconditions deliberately disables tax for | Run `/eco treasury` from console | One line per configured currency reading `<display-name>: <formatted treasury balance>` (`EcoAdminCommand.java:290-293`) — this row's own messages localize correctly, since `EcoAdminCommand#onTreasury` never routes any of its per-currency output through `plugin.i18n(...)` at all | server | |
| ultieconomy.admin.treasury-withdraw | `language: en` in config.yml; the primary-currency treasury balance ≥ the amount requested — collect tax first via `ultieconomy.pay.pay.neg-tax-message-mismatch` above, NOT `ultieconomy.pay.pay`, which this document's own Preconditions deliberately disables tax for and would leave the treasury at zero | Run `/eco treasury withdraw <amount>` from console | Chat line reads `Withdrew $<amount> from treasury` (`EcoAdminCommand.java:312-313`, localizes correctly); `/eco treasury` afterward shows the reduced treasury balance | server | |
| ultieconomy.admin.treasury-withdraw.neg-insufficient | `language: en` in config.yml; the treasury's primary-currency balance < the amount requested | Run `/eco treasury withdraw <amount greater than current treasury balance>` from console | Chat line reads `Insufficient treasury balance` (`EcoAdminCommand.java:315`, localizes correctly) | server | |
| ultieconomy.admin.treasury-withdraw-currency | `language: en` in config.yml; a second, non-primary currency configured with a nonzero treasury balance | Run `/eco treasury withdraw <amount> gems` from console | Chat line reads `Withdrew <formatted> from treasury` (`EcoAdminCommand.java:336-337`, localizes correctly), formatted in `gems` | server | |

## Vault and PlaceholderAPI registration

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultieconomy.vault.register | A Vault-compatible plugin installed alongside this module (e.g. a shop plugin, or Vault's own economy test command if available); Vault itself is a HARD dependency of this module (`plugin.yml: depend: [Vault]`), so this row cannot be exercised without Vault present at all | Query the registered `Economy` service (e.g. via a Vault-aware plugin's own economy check, or `/eco give`/`/money` interacting with a shop plugin that charges via Vault) | The Vault-compatible plugin reads and modifies the SAME cash balance `/money` reports — confirming `VaultEconomyProvider` is registered and correctly delegates to `EconomyServiceImpl` | server | |
| ultieconomy.placeholder.register.neg-absent | PlaceholderAPI NOT installed | Restart the server with this module loaded and PlaceholderAPI absent | The server starts with no error related to `EconomyPlaceholderExpansion` — `UltiEconomy#registerSelf` checks `Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null` before attempting registration, so its absence is a no-op, not a load failure | server | |
| ultieconomy.placeholder.register | PlaceholderAPI installed and enabled | Restart the server with this module loaded and PlaceholderAPI present, then run `/papi parse me %ultieconomy_cash%` (or equivalent) | The placeholder resolves to a raw two-decimal number rather than a literal `%ultieconomy_cash%` echo — confirming the expansion registered | server | |

## Player join

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultieconomy.player.join-init | A player UUID that has never joined this server (with this module installed) before; at least one non-primary currency configured (e.g. `gems`, added before this join) | The player joins for the first time | `/money` immediately shows the shipped `initial-cash` value (1000.0 by default) as the player's primary-currency cash; `/money gems` shows `gems`'s own configured `initial-cash` (independently — a currency-specific starting balance, not the primary currency's) | server | |

## Interest (gate)

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultieconomy.gate.interest | A player with a positive bank balance, online, for the full observation window below; `interest.interval` at its shipped default (1800 seconds = 30 minutes) | Restart the server with `interest.enabled: true` (shipped default), wait at least 35 real minutes (the full configured 1800-second interval PLUS scheduling tolerance — waiting only a few minutes cannot distinguish "never scheduled" from "correctly scheduled at a 30-minute interval that just hasn't fired yet", which is exactly the ambiguity a shorter wait would leave unresolved) with the player online and their bank balance unchanged by any command | NO interest notification chat line (the untranslated Chinese literal at `InterestService.java:136`, or its English-key sibling) is ever received, and the player's bank balance is unchanged from before the wait — confirming UltiKits/UltiEconomy#15: nothing schedules `InterestService#distributeInterest()` regardless of this gate's own state. Toggling `interest.enabled` to `false` and repeating this row's Steps (another 35-minute wait) produces the IDENTICAL observable outcome (still nothing happens) — the gate's own on/off state currently makes no difference a player or admin can observe | server | |

## Leaderboard placeholders

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultieconomy.placeholder.bank | PlaceholderAPI installed; `ultieconomy.placeholder.register` above passed | Run `/papi parse me %ultieconomy_bank%` | Resolves to the player's own bank balance as a raw two-decimal number | server | |
| ultieconomy.placeholder.cash | PlaceholderAPI installed | Run `/papi parse me %ultieconomy_cash%` | Resolves to the player's own cash balance as a raw two-decimal number | server | |
| ultieconomy.placeholder.cash-formatted | PlaceholderAPI installed | Run `/papi parse me %ultieconomy_cash_formatted%` | Resolves to the player's cash balance WITH the configured currency symbol prefixed (e.g. `$1,000.00`), unlike the raw-number rows above | server | |
| ultieconomy.placeholder.currency-scoped | PlaceholderAPI installed; a second, non-primary currency (e.g. `gems`) configured | Run `/papi parse me %ultieconomy_gems_cash%` | Resolves to the player's `gems`-currency cash balance, independent of their primary-currency `%ultieconomy_cash%` value | server | |
| ultieconomy.placeholder.rank | PlaceholderAPI installed; at least two players with distinct, nonzero total wealth online at once | Run `/papi parse me %ultieconomy_rank%` for BOTH players | BOTH players see `-`, regardless of their actual relative wealth — confirming UltiKits/UltiEconomy#15: `LeaderboardService`'s cache is never populated, so `getPlayerRank` never finds either UUID in its permanently-empty snapshot | server | |
| ultieconomy.placeholder.top-balance | PlaceholderAPI installed; at least one player with a substantial nonzero total wealth | Run `/papi parse me %ultieconomy_top_balance_1%` | Resolves to `0.00`, not the actual wealthiest player's real total — confirming UltiKits/UltiEconomy#15 | server | |
| ultieconomy.placeholder.top-name | PlaceholderAPI installed; at least one player online | Run `/papi parse me %ultieconomy_top_name_1%` | Resolves to `-`, not any real player's name — confirming UltiKits/UltiEconomy#15 | server | |
| ultieconomy.placeholder.total | PlaceholderAPI installed | Run `/papi parse me %ultieconomy_total%` | Resolves to the player's cash + bank balance as a raw two-decimal number, matching `/money`'s own total-wealth line's numeric value (ignoring that line's own i18n breakage, UltiKits/UltiEconomy#14 — the underlying number is correct even though its surrounding chat text is not) | server | |

## Data persistence

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultieconomy.persistence.balance-restart | A player's primary-currency cash/bank changed via any command above (e.g. `ultieconomy.deposit.deposit`), and a per-currency balance changed via a `-currency` command above, both noted before the restart | Stop the server completely (not `/ul reload`), start it again, then run `/money` and `/money gems` for the same player | Both the primary-currency and the per-currency balances read back EXACTLY the values noted before the restart, not the pre-change defaults | server | |
| ultieconomy.persistence.treasury-restart | A nonzero treasury balance from a collected transaction tax, noted via `/eco treasury` before the restart | Stop the server completely, start it again, then run `/eco treasury` | The treasury balance reads back exactly the value noted before the restart | server | |

## Configuration

One row per shipped yml file (D-06's config-per-file rule, narrowed for this module to exactly
one row per this plan's own instruction — see the Conventions block above for why
`currencies.yml` is folded into this same row rather than given its own).

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultieconomy.config.config-yml | Fresh `plugins/UltiTools/UltiEconomy/config/config.yml` AND `config/currencies.yml` at their shipped defaults (not hand-edited); server started at least once | Confirm the 12 keys the packaged resource ships (`initial-cash`, `currency-name`, `currency-symbol`, `bank.*`, `interest.*`, `leaderboard.*`) are present in the on-disk `config.yml` at their documented defaults, AND that the 7 `tax.*` keys — absent from the packaged resource — have been migrated onto disk with their Java-declared defaults after this first boot (per `AbstractConfigEntity#init()`); confirm `currencies.yml` carries exactly the one shipped `coins` currency marked `primary: true`; then set `language: "en"` and restart, confirming `/money`'s header switches from the localized (untranslated at `MoneyCommand.java:63`'s Chinese key) block to `Economy System` (the one working header line, proving the config file itself loads and applies); separately, add a second currency `gems` to `currencies.yml` (not marking it `primary`), restart, and confirm `/money gems` now resolves without the "currency not found" refusal it would have produced before the edit — proving `currencies.yml`'s own content genuinely drives `CurrencyManager`'s resolution set, not merely that the file is well-formed; as a THIRD, deliberately-invalid edit within this same row, mark BOTH `coins` and `gems` `primary: true` at once and restart again | All 12 packaged `config.yml` keys present at their documented defaults before either change, plus all 7 `tax.*` keys present at their Java-declared defaults after the first boot; `currencies.yml` starts with exactly the one `coins` entry; after the language change, `/money`'s header reads `Economy System`; after adding `gems`, `/money gems` resolves instead of refusing; after marking two currencies `primary: true` at once, the module FAILS to load — `CurrencyManager`'s constructor throws `IllegalStateException` ("Multiple primary currencies: ...") during `UltiEconomy#getCurrencyManager()`'s first call, which the framework's `PluginManager` catches and reports as a failed module registration, not a graceful in-game refusal. Do NOT attempt to observe an effect from `interest.interval`, `leaderboard.update-interval`, `tax.enabled`, or `tax.wealth-tax.*` — none of the four has one (UltiKits/UltiEconomy#15, #16) | server | |
