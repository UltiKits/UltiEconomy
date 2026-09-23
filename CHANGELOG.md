# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Changed

- **Upgrade consequence — the transfer tax may stop.** `tax.enabled` in `config/config.yml` now
  takes effect, as the master switch over all taxation: while it is `false`, `/pay` takes no
  transaction tax and nothing is paid into the treasury, whatever `tax.transaction-tax.*` says
  (UltiKits/UltiEconomy#16). Previously nothing read it, and a transfer was taxed at
  `tax.transaction-tax.rate` (5% by default) whenever `tax.transaction-tax.enabled` was true. The
  key is not in the shipped file, and the framework writes a missing key into the operator's file
  with its declared default on first boot; that default was `false`, so every server that has
  already run this module holds `tax.enabled: false` unless its operator changed it — **on such a
  server, upgrading stops the transfer tax.** The value in the file is the one that applies. While
  it is `false` the module logs one WARNING at every boot saying so. To keep taxing transfers, set
  `tax.enabled: true` and run `/ul reload UltiTools-Economy`; the switch is read at every transfer.
  The declared default is now `true`, which reaches only a file that does not hold the key yet.
- **升级后果——转账税可能停止征收。** `config/config.yml` 中的 `tax.enabled` 现在生效，作为全部税收的总开关：
  其为 `false` 时，`/pay` 不收取交易税，也不向国库存入任何金额，无论 `tax.transaction-tax.*` 如何设置
  （UltiKits/UltiEconomy#16）。此前没有任何代码读取该键，只要 `tax.transaction-tax.enabled` 为 true，
  转账就按 `tax.transaction-tax.rate`（默认 5%）征税。该键不在出厂文件中，而框架会在首次启动时把缺失的键按
  其声明默认值写入运维的文件；该默认值此前为 `false`，因此所有运行过本模块的服务器，除非运维改过，文件里都是
  `tax.enabled: false`——**这样的服务器升级后，转账税停止征收。** 以文件中的值为准。该值为 `false` 时，
  本模块每次启动都会记录一条 WARNING 说明这一点。若要继续对转账征税，请设置 `tax.enabled: true` 并执行
  `/ul reload UltiTools-Economy`；该开关在每次转账时读取。声明默认值现为 `true`，只影响尚未包含该键的文件。

### Fixed

- `/upm uninstall UltiTools-Economy` now runs the framework's own command and listener cleanup,
  right after this module's own cleanup work (removing its Vault economy provider from Bukkit's
  services manager, unchanged): after it, the module's commands are really removed and its listeners
  stop firing; previously both stayed active until the server restarted (UltiKits/UltiEconomy#22).
  Reload is unaffected: this module never replaced the framework's reload method.
- `/upm uninstall UltiTools-Economy` 现在会在本模块自身的清理工作（从 Bukkit 服务管理器移除其 Vault 经济提供者，
  行为不变）之后，执行框架自身的命令与监听器清理：执行后，本模块的命令会被真正移除，其监听器也不再触发；此前两者都会
  保持生效，直到服务器重启（UltiKits/UltiEconomy#22）。重载不受影响：本模块从未替换框架的重载方法。
