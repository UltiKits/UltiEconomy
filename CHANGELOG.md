# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Changed

- **Upgrade consequence — interest may start being paid.** `interest.enabled` in
  `config/config.yml` now takes effect (UltiKits/UltiEconomy#15). Previously nothing ever scheduled
  the payment, so no interest was paid on any server whatever the key said. The shipped file said
  `interest.enabled: true` in both 1.0.0 and 2.0.0, so every server that has run either holds `true`
  unless its operator changed it — **on such a server, upgrading starts paying interest, which creates money**:
  every 1800 seconds (30 minutes, a fixed period; the first payment 30 minutes after the module
  loads), every positive bank balance — in the primary currency and in every currency with
  `bank-enabled: true` in `config/currencies.yml` — earns `interest.rate` of itself (`0.03`, 3%, by
  default), capped at `interest.max-interest` per payment (`10000.0` by default; `-1` means no cap)
  and never taking a balance above its bank maximum (`bank.max-balance`, or the currency's own
  `max-bank-balance`, when set above 0 — a balance already at the maximum earns nothing), and an
  online player is told in chat once the credit has been written. **If several servers share one
  database, each server with interest on pays the full rate on every balance in it: turn interest
  on for exactly one of them.** The value in the file is the one that applies. While it is `true`
  the module logs one WARNING at every boot naming the rate, the interval, the cap and the
  shared-database rule. To not
  pay interest, set `interest.enabled: false` and run `/ul reload UltiTools-Economy`; the switch is
  read at every payment, so the next one is skipped. The declared default and the shipped file now
  say `false`, which reaches only a file that does not hold the key yet.
- **升级后果——可能开始发放利息。** `config/config.yml` 中的 `interest.enabled` 现在生效
  （UltiKits/UltiEconomy#15）。此前从未有任何代码调度利息发放，因此无论该键如何设置，任何服务器上都从未发放过利息。
  1.0.0 与 2.0.0 的出厂文件都写的是 `interest.enabled: true`，所以所有运行过其中任一版本的服务器，除非运维改过，文件里都是 `true`——
  **这样的服务器升级后开始发放利息，这会凭空产生货币**：每 1800 秒（30 分钟，固定周期；模块加载 30 分钟后首次发放），
  每个为正的银行余额——主货币以及 `config/currencies.yml` 中所有 `bank-enabled: true` 的货币——获得其自身
  `interest.rate`（默认 `0.03`，即 3%）的利息，单次上限为 `interest.max-interest`（默认 `10000.0`；`-1` 表示无上限），
  且不会使余额超过其银行上限（`bank.max-balance`，或该货币自己的 `max-bank-balance`，大于 0 时生效——已达上限的余额不再获得利息），
  利息写入成功后在线玩家才会收到聊天提示。**若多台服务器共用同一个数据库，每台开启利息的服务器都会对其中每个余额按全额利率发放：
  请只在其中一台上开启利息。** 以文件中的值为准。该值为 `true` 时，本模块每次启动都会记录一条 WARNING，点名利率、间隔、上限与共用数据库的规则。
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

### Removed

- `interest.interval` is removed from `config/config.yml`: nothing ever read it. Interest is paid
  every 1800 seconds, a fixed period in the code. Making the period configurable is requested of the
  framework as UltiKits/UltiTools-Reborn#531 — removing the key is not a rejection of that. If your
  file still holds the key, the module names it in a WARNING at every boot; it can be deleted
  (UltiKits/UltiEconomy#15).
- `interest.interval` 已从 `config/config.yml` 中移除：从未有代码读取它。利息每 1800 秒发放一次，周期在代码中固定。
  「周期可配置」已作为框架功能请求 UltiKits/UltiTools-Reborn#531 提出——移除该键并不意味着否决这一功能。
  若你的文件中仍有该键，本模块每次启动都会以 WARNING 点名；可以删除（UltiKits/UltiEconomy#15）。
- `leaderboard.update-interval` is removed from `config/config.yml`: nothing ever read it. The
  leaderboard is refreshed every 60 seconds, a fixed period in the code. Making the period
  configurable is requested of the framework as UltiKits/UltiTools-Reborn#531 — removing the key is
  not a rejection of that. If your file still holds the key, the module names it in a WARNING at
  every boot; it can be deleted (UltiKits/UltiEconomy#15).
- `leaderboard.update-interval` 已从 `config/config.yml` 中移除：从未有代码读取它。排行榜每 60 秒刷新一次，周期在代码中固定。
  「周期可配置」已作为框架功能请求 UltiKits/UltiTools-Reborn#531 提出——移除该键并不意味着否决这一功能。
  若你的文件中仍有该键，本模块每次启动都会以 WARNING 点名；可以删除（UltiKits/UltiEconomy#15）。

### Fixed

- The wealth leaderboard is now refreshed: once as soon as the module loads, then every 60 seconds.
  Previously nothing refreshed it, so `%ultieconomy_rank%` and `%ultieconomy_<currency>_rank%` always
  returned `-`, `%ultieconomy_top_name_<N>%` always `-` and `%ultieconomy_top_balance_<N>%` always
  `0.00`, whatever the players' balances (UltiKits/UltiEconomy#15).
- 财富排行榜现在会刷新：模块加载后立即刷新一次，之后每 60 秒一次。此前从未刷新，因此无论玩家余额如何，
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
