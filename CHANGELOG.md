# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- Unloading this module now always runs the framework's own command and listener cleanup, right
  after this module's own cleanup work (removing its Vault economy provider from Bukkit's services
  manager, unchanged). Previously `/upm uninstall UltiTools-Economy` skipped both command and
  listener cleanup, and server shutdown skipped command cleanup (UltiKits/UltiEconomy#22). Reload
  is unaffected: this module never replaced the framework's reload method.
- 卸载本模块时，现在总会在本模块自身的清理工作（从 Bukkit 服务管理器移除其 Vault 经济提供者，行为不变）之后，
  执行框架自身的命令与监听器清理。此前 `/upm uninstall UltiTools-Economy` 会跳过命令与监听器清理，
  服务器关闭时会跳过命令清理（UltiKits/UltiEconomy#22）。重载不受影响：本模块从未替换框架的重载方法。
