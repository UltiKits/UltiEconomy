# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Changed

- Language keys were renamed from Chinese sentences to ASCII keys (for example `economy.money.header`).
  An operator who edited this module's `lang/en.json` or `lang/zh.json` must re-apply those edits to
  the new keys; until then the renamed messages show the new built-in text. A server whose language
  files were never edited needs no action.
- 语言键已从中文句子改为 ASCII 键（例如 `economy.money.header`）。改过本模块 `lang/en.json` 或
  `lang/zh.json` 的运维需要把改动重新套到新键上；在此之前，这些消息显示新的内置文本。从未改过语言文件的服务器无需任何操作。

- **Requires UltiTools 6.3.0.** `plugin.yml` now declares `api-version: 630`. The interest payment
  and the leaderboard refresh read their intervals from `interest.interval` and
  `leaderboard.update-interval` through a framework feature added in UltiTools 6.3.0
  (UltiKits/UltiTools-Reborn#531). UltiTools 6.2.x refuses to load this module because of that
  `api-version`.
- **需要 UltiTools 6.3.0。** `plugin.yml` 现声明 `api-version: 630`。利息发放与排行榜刷新通过 UltiTools 6.3.0 新增的框架功能
  （UltiKits/UltiTools-Reborn#531）从 `interest.interval` 与 `leaderboard.update-interval` 读取间隔。UltiTools 6.2.x
  会因该 `api-version` 拒绝加载本模块。
- **Upgrade consequence — interest may start being paid.** `interest.enabled` in
  `config/config.yml` now takes effect (UltiKits/UltiEconomy#15). Previously nothing ever scheduled
  the payment, so no interest was paid on any server whatever the key said. The shipped file said
  `interest.enabled: true` in both 1.0.0 and 2.0.0, so every server that has run either holds `true`
  unless its operator changed it — **on such a server, upgrading starts paying interest, which creates money**:
  every `interest.interval` seconds (the first payment one interval after the module loads), a player's primary-currency bank balance — the one `/bank`, `/money`, `/eco check <player>`
  and Vault show, paid once per player and not also on the separate per-currency row that
  `/bank <primary currency>` shows (UltiKits/UltiEconomy#25) — and their bank balance in every other
  currency with `bank-enabled: true` in `config/currencies.yml` each earn `interest.rate` of itself (`0.03`, 3%, by
  default), capped at `interest.max-interest` per payment (`10000.0` by default; `-1` means no cap)
  and never taking a balance above its bank maximum (`bank.max-balance`, or the currency's own
  `max-bank-balance`, when set above 0 — a balance already at the maximum earns nothing), and an
  online player is told in chat once the credit has been written. **If several servers share one
  database, each server with interest on pays the full rate on every balance in it: turn interest
  on for exactly one of them.** The value in the file is the one that applies. While it is `true`
  the module logs one WARNING at every boot naming the rate, the interval, the cap and the
  shared-database rule. `/ul reload UltiTools-Economy` applies a changed `interest.interval` keeping
  the payment's place in its cycle: the next payment is the last one (or, before the first, the
  module load) plus the new interval, or at once if that moment has passed — a reload never pays
  early and never postpones a payment. **The interval that applies is the one in your file.** 1.0.0
  and 2.0.0 shipped `interest.interval: 1800` (30 minutes) but nothing read it; a value your file
  holds — for example a changed `60`, which pays every minute — applies from this release, and
  `1800` is only what a file without the key gets. A value below 1 or above 107374182 refuses the
  **whole economy module** at load, naming the key — the Vault economy provider included, so every
  plugin that uses Vault loses it and `/money`, `/pay` and the other commands are unavailable, even
  while `interest.enabled` is `false` — until the value is fixed and the server restarted; at
  `/ul reload` it is not applied, the running interval is kept and a WARNING names the key. To not
  pay interest, set `interest.enabled: false` and run `/ul reload UltiTools-Economy`; the switch is
  read at every payment, so the next one is skipped. The declared default and the shipped file now
  say `false`, which reaches only a file that does not hold the key yet.
- **升级后果——可能开始发放利息。** `config/config.yml` 中的 `interest.enabled` 现在生效
  （UltiKits/UltiEconomy#15）。此前从未有任何代码调度利息发放，因此无论该键如何设置，任何服务器上都从未发放过利息。
  1.0.0 与 2.0.0 的出厂文件都写的是 `interest.enabled: true`，所以所有运行过其中任一版本的服务器，除非运维改过，文件里都是 `true`——
  **这样的服务器升级后开始发放利息，这会凭空产生货币**：每 `interest.interval` 秒（模块加载后满一个间隔首次发放），
  玩家的主货币银行余额——即 `/bank`、`/money`、`/eco check <玩家>` 与 Vault 显示的那一个，每名玩家只计一次，
  不会再对 `/bank <主货币>` 显示的那一行单独货币余额重复计息（UltiKits/UltiEconomy#25）——以及其在 `config/currencies.yml`
  中其他所有 `bank-enabled: true` 货币的银行余额，各自获得其自身
  `interest.rate`（默认 `0.03`，即 3%）的利息，单次上限为 `interest.max-interest`（默认 `10000.0`；`-1` 表示无上限），
  且不会使余额超过其银行上限（`bank.max-balance`，或该货币自己的 `max-bank-balance`，大于 0 时生效——已达上限的余额不再获得利息），
  利息写入成功后在线玩家才会收到聊天提示。**若多台服务器共用同一个数据库，每台开启利息的服务器都会对其中每个余额按全额利率发放：
  请只在其中一台上开启利息。** 以文件中的值为准。该值为 `true` 时，本模块每次启动都会记录一条 WARNING，点名利率、间隔、上限与共用数据库的规则。
  `/ul reload UltiTools-Economy` 会应用修改后的 `interest.interval` 并保持发放节拍：下一次发放 = 上一次发放（首次发放前则为模块加载时刻）
  加新间隔，若该时刻已过则立即发放——重载既不会提前发放，也不会推迟发放。**生效的是你文件里的间隔。** 1.0.0 与 2.0.0 出厂写的是
  `interest.interval: 1800`（30 分钟），但从未被读取；你文件中的值——例如改过的 `60`，即每分钟发放一次——自本版本起生效，
  `1800` 只是缺少该键的文件得到的值。小于 1 或大于 107374182 的值在加载时使**整个经济模块**拒载并点名该键——包括 Vault 经济提供者，
  所有使用 Vault 的插件都会失去它，`/money`、`/pay` 等命令也不可用，即使 `interest.enabled` 为 `false` 也是如此——直到修正该值并重启服务器；
  在 `/ul reload` 时则不生效，保留正在使用的间隔并记录一条点名该键的 WARNING。
  若不想发放利息，请设置 `interest.enabled: false` 并执行 `/ul reload UltiTools-Economy`；该开关在每次发放时读取，
  下一次发放即被跳过。声明默认值与出厂文件现均为 `false`，只影响尚未包含该键的文件。
- **Upgrade consequence — the transfer tax may stop.** `tax.enabled` in `config/config.yml` now
  takes effect, as the master switch over all taxation: while it is `false`, `/pay` takes no
  transaction tax and nothing is paid into the treasury, whatever `tax.transaction-tax.*` says
  (UltiKits/UltiEconomy#16). Previously nothing read it, and a transfer was taxed at
  `tax.transaction-tax.rate` (5% by default) whenever `tax.transaction-tax.enabled` was true. The
  key is not in the shipped file, and the framework writes a missing key into the operator's file
  with its declared default on first boot; that default was `false` in 2.0.0, the release that
  added the tax settings, so every server that has run 2.0.0 holds `tax.enabled: false` unless its
  operator changed it — **on such a server, upgrading stops the transfer tax.** The value in the file is the one that applies. While
  it is `false` the module logs one WARNING at every boot saying so. To keep taxing transfers, set
  `tax.enabled: true` and run `/ul reload UltiTools-Economy`; the switch is read at every transfer.
  The declared default is now `true`, which reaches only a file that does not hold the key yet.
- **升级后果——转账税可能停止征收。** `config/config.yml` 中的 `tax.enabled` 现在生效，作为全部税收的总开关：
  其为 `false` 时，`/pay` 不收取交易税，也不向国库存入任何金额，无论 `tax.transaction-tax.*` 如何设置
  （UltiKits/UltiEconomy#16）。此前没有任何代码读取该键，只要 `tax.transaction-tax.enabled` 为 true，
  转账就按 `tax.transaction-tax.rate`（默认 5%）征税。该键不在出厂文件中，而框架会在首次启动时把缺失的键按
  其声明默认值写入运维的文件；在加入税收设置的 2.0.0 版本中，该默认值为 `false`，因此所有运行过 2.0.0 的服务器，
  除非运维改过，文件里都是 `tax.enabled: false`——**这样的服务器升级后，转账税停止征收。** 以文件中的值为准。该值为 `false` 时，
  本模块每次启动都会记录一条 WARNING 说明这一点。若要继续对转账征税，请设置 `tax.enabled: true` 并执行
  `/ul reload UltiTools-Economy`；该开关在每次转账时读取。声明默认值现为 `true`，只影响尚未包含该键的文件。

- **The primary currency now has one wallet** (UltiKits/UltiEconomy#25). Since 2.0.0 every player
  also had a second primary-currency wallet, credited with its own starting amount when they first
  joined. The commands and placeholders that name the currency — `/money coins`, `/bank coins`,
  `/pay <player> <amount> coins`, `/deposit <amount> coins`, `/withdraw <amount> coins`,
  `/eco give|take|set|check <player> … coins`, `/note <amount> coins`, `%ultieconomy_coins_*%` and the
  `coins` leaderboard — used that second wallet, while Vault, `/money`, `/pay` and `/bank` used the
  account wallet. All of them now use the account wallet, and a player joining for the first time is
  given `initial-cash` once. The primary currency's starting cash, bank switch, minimum deposit and
  maximum bank balance are read from `config/config.yml`; the primary entry's `initial-cash`,
  `bank-enabled`, `min-deposit` and `max-bank-balance` in `config/currencies.yml` are no longer read
  (its `display-name` and `symbol` still are). Every other currency keeps its own wallet.
- **主货币现在只有一个钱包**（UltiKits/UltiEconomy#25）。自 2.0.0 起，每位玩家还有第二个主货币钱包，首次进服时按其自身的
  初始金额入账。带货币名的命令与占位符——`/money coins`、`/bank coins`、`/pay <玩家> <金额> coins`、`/deposit <金额> coins`、
  `/withdraw <金额> coins`、`/eco give|take|set|check <玩家> … coins`、`/note <金额> coins`、`%ultieconomy_coins_*%` 以及
  `coins` 排行榜——使用的是这第二个钱包，而 Vault、`/money`、`/pay`、`/bank` 使用账户钱包。现在它们全部使用账户钱包，
  新玩家首次进服只获得一次 `initial-cash`。主货币的初始金额、银行开关、最低存款与银行上限读取 `config/config.yml`；
  `config/currencies.yml` 中主货币条目的 `initial-cash`、`bank-enabled`、`min-deposit`、`max-bank-balance` 不再被读取
  （其 `display-name` 与 `symbol` 仍被读取）。其他货币仍各自拥有独立的钱包。

- **Upgrade consequence — the second wallet is merged once** (UltiKits/UltiEconomy#25). On the first
  start after upgrading, before anything can read or move a balance, each player's second
  primary-currency wallet is added to their account wallet — its cash to their cash, its bank balance
  to their bank balance — and then removed. The server log gets one line per merged player with the
  amounts and the new balances, and one total line. A later start finds nothing to merge. Nobody's
  balance goes down: an amount below zero in a second wallet (which no command of this module can
  produce) is not taken from the account, and is logged. A merged bank balance may end above
  `bank.max-balance`; deposits and interest then stop at the cap as before. **Known trade-off:** the
  starting amount 2.0.0 credited a second time stays in circulation — on a 103-player test server,
  103,588.95 was merged, 101,000 of it untouched duplicated starting amounts. If storage fails during
  the merge, the module does not start (so no balance changes); fix storage and restart, and the merge
  resumes without adding anything twice. It is safe to stop the server at any point during the merge,
  on SQLite, MySQL and JSON storage alike.
- **升级后果——第二钱包一次性并入**（UltiKits/UltiEconomy#25）。升级后首次启动时，在任何余额可以被读取或变动之前，每位玩家的
  第二个主货币钱包会并入其账户钱包——现金并入现金，存款并入存款——随后删除。服务器日志为每位被合并的玩家记录一行（含金额与合并后余额），
  并记录一行总计。之后的启动不会再合并任何内容。没有人的余额会减少：第二钱包中低于零的金额（本模块的任何命令都无法产生）不会从账户扣除，
  并会记入日志。合并后的存款可能高于 `bank.max-balance`；此后存款与利息照旧在上限处停止。**已知取舍：** 2.0.0 重复发放的初始金额会继续流通——
  在一台 103 名玩家的测试服务器上，共并入 103,588.95，其中 101,000 是从未动用的重复初始金额。若合并过程中存储出错，模块不会启动
  （因此任何余额都不会变化）；修复存储后重启，合并会从中断处继续，不会重复并入。无论使用 SQLite、MySQL 还是 JSON 存储，
  在合并过程中的任何时刻停止服务器都是安全的。

- When `config/currencies.yml` gives the primary currency an `initial-cash`, `bank-enabled`,
  `min-deposit` or `max-bank-balance` different from `config/config.yml`'s `initial-cash`,
  `bank.enabled`, `bank.min-deposit` or `bank.max-balance`, a WARNING at startup names both files,
  both keys and both values, and says that the `config.yml` value applies (UltiKits/UltiEconomy#25).
- 当 `config/currencies.yml` 中主货币的 `initial-cash`、`bank-enabled`、`min-deposit` 或 `max-bank-balance` 与
  `config/config.yml` 的 `initial-cash`、`bank.enabled`、`bank.min-deposit` 或 `bank.max-balance` 不一致时，启动时会记录一条
  WARNING，写明两个文件、两个键与两个值，并说明以 `config.yml` 的值为准（UltiKits/UltiEconomy#25）。

### Removed

- Twenty-two language entries that no code displayed were removed from both language files: thirteen
  near-duplicates of the command messages above that carried one `%s` too many (the reason those
  messages never matched), and nine that no version of this module ever referenced (a leaderboard
  line and title, a transaction-tax, a wealth-tax and a wealth-tax-exemption message, a current
  currency line, a bank-not-supported message, a players-only message and an `/eco` usage line).
  Nothing an operator or player sees changes.
- 从两份语言文件中删除了二十二条从未被显示的条目：十三条是上述命令消息多带一个 `%s` 的近似副本（正是这些消息从未匹配的原因），
  另外九条从未被本模块任何版本引用（排行榜行与标题、交易税、财富税与财富税豁免消息、当前货币行、货币不支持银行功能、仅限玩家执行的提示、
  `/eco` 用法行）。运维和玩家看到的内容没有任何变化。

### Fixed

- `language: en` now applies to the command messages that showed their Chinese source text in every
  language because their keys were missing from both language files: the `/money` and `/bank`
  balance lines, the `/pay`, `/deposit` and `/withdraw` success lines, the `/eco give`, `take`, `set`
  and `check` lines, the primary-currency interest notification, and the `/bank`, `/deposit`,
  `/withdraw`, `/pay` and `/money` help and command descriptions, and the `/eco` and `/note` command
  descriptions (UltiKits/UltiEconomy#14).
  `language: zh` now also applies to text that was fixed English: the six help headers
  (`=== UltiEconomy Bank ===` and the rest), a money note's name and lore (`[Money Note] …`,
  `Currency:`, `Value:`, `Created by:`), the two startup warnings about `interest.enabled` and
  `tax.enabled`, three console errors (a failed account, balance or interest write), and the messages
  the Vault bridge returns when a withdrawal or deposit fails, which shop plugins show to the player.
  English wording is unchanged, except that `/pay` to yourself now answers `You cannot pay yourself`
  instead of `Invalid amount`. A money note made before this change keeps the name it was made with; notes
  are recognised by their stored data, not their name, so old and new notes both redeem.
- `language: en` 现在对以下命令消息生效（它们的键在两份语言文件中都缺失，因此在任何语言下都显示中文源文本）：`/money` 与
  `/bank` 的余额行，`/pay`、`/deposit`、`/withdraw` 的成功提示，`/eco give`、`take`、`set`、`check` 的提示，主货币利息到账通知，
  以及 `/bank`、`/deposit`、`/withdraw`、`/pay`、`/money` 的帮助与命令描述和 `/eco`、`/note` 的命令描述（UltiKits/UltiEconomy#14）。`language: zh` 现在也对
  原先写死为英文的文本生效：六个帮助标题（`=== UltiEconomy Bank ===` 等）、纸币的名称与说明、`interest.enabled` 与
  `tax.enabled` 的两条启动警告、三条控制台错误（账户、余额或利息写入失败），以及 Vault 接口在扣款或存款失败时返回的消息
  （商店插件会把它显示给玩家）。英文措辞不变，唯一例外是向自己 `/pay` 时现在提示“不能向自己转账”，而不是“无效的金额”。改动前制作的纸币保留原名称；
  纸币按存储的数据识别而非名称，新旧纸币都能兑换。

- The wealth leaderboard is now refreshed: once as soon as the module loads, then every
  `leaderboard.update-interval` seconds — the value in your file; 1.0.0 and 2.0.0 shipped `60` but
  nothing read it, and `60` is only what a file without the key gets. `/ul reload` applies a change.
  A value below 1 or above 107374182 refuses the whole economy module at load, the Vault provider
  included, until it is fixed; at reload it is ignored with a WARNING.
  Previously nothing refreshed it, so `%ultieconomy_rank%` and `%ultieconomy_<currency>_rank%` always
  returned `-`, `%ultieconomy_top_name_<N>%` always `-` and `%ultieconomy_top_balance_<N>%` always
  `0.00`, whatever the players' balances (UltiKits/UltiEconomy#15).
- 财富排行榜现在会刷新：模块加载后立即刷新一次，之后每 `leaderboard.update-interval` 秒一次（默认 60；`/ul reload` 生效，
  小于 1 或大于 107374182 的值在加载时使整个经济模块（包括 Vault 提供者）拒载直到修正，在重载时不生效并记录 WARNING；
  生效的是你文件里的值，1.0.0 与 2.0.0 出厂写的 `60` 从未被读取，`60` 只是缺少该键的文件得到的值）。此前从未刷新，因此无论玩家余额如何，
  `%ultieconomy_rank%` 与 `%ultieconomy_<currency>_rank%` 始终返回 `-`，`%ultieconomy_top_name_<N>%` 始终为 `-`，
  `%ultieconomy_top_balance_<N>%` 始终为 `0.00`（UltiKits/UltiEconomy#15）。
- `/upm uninstall UltiTools-Economy` now runs the framework's own command and listener cleanup,
  right after this module's own cleanup work (removing its Vault economy provider from Bukkit's
  services manager, unchanged): after it, the module's commands are really removed and its listeners
  stop firing; previously both stayed active until the server restarted (UltiKits/UltiEconomy#22).
  Reload is unaffected: this module never replaced the framework's reload method.
- `/upm uninstall UltiTools-Economy` 现在会在本模块自身的清理工作（从 Bukkit 服务管理器移除其 Vault 经济提供者，
  行为不变）之后，执行框架自身的命令与监听器清理：执行后，本模块的命令会被真正移除，其监听器也不再触发；此前两者都会
  保持生效，直到服务器重启（UltiKits/UltiEconomy#22）。重载不受影响：本模块从未替换框架的重载方法。
