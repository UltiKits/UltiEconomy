# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- Unloading this module (for example `/upm uninstall UltiTools-Economy`, or disabling the module)
  now actually runs the framework's own unload steps (command and listener cleanup), right after
  this module's own cleanup work (removing its Vault economy provider from Bukkit's services
  manager, unchanged) — previously these framework steps were silently skipped
  (UltiKits/UltiEconomy#22).
  Reload is unaffected: this module never replaced the framework's reload method.
- 卸载本模块（例如 `/upm uninstall UltiTools-Economy`，或禁用本模块）现在会在本模块自身的清理工作
  （从 Bukkit 服务管理器移除其 Vault 经济提供者，行为不变）之后，真正执行框架自身的卸载步骤（命令与监听器清理）
  ——此前这些框架步骤会被静默跳过（UltiKits/UltiEconomy#22）。重载不受影响：本模块从未替换框架的重载方法。
