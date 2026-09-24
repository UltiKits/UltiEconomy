# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Changed

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

### Fixed

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
