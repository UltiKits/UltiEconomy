# UltiEconomy - 经济模块 / Economy Module

[![UltiTools-API](https://img.shields.io/badge/UltiTools--API-6.3.0%2B-blue)](https://github.com/UltiKits/UltiTools-Reborn)
[![License](https://img.shields.io/badge/License-MIT-green)](../../LICENSE)

A full-featured Vault economy provider for UltiTools-API with dual-wallet (cash + bank), interest mechanics, leaderboard rankings, and PlaceholderAPI integration.

UltiTools-API 的完整 Vault 经济提供者模块。支持双钱包（现金 + 银行）、利息机制、财富排行榜和 PlaceholderAPI 集成。

## Features / 功能

- **Vault Economy** - Full Vault API integration as economy provider / 完整 Vault API 经济提供者
- **Dual Wallet** - Separate cash and bank balances / 现金和银行双钱包
- **Bank System** - Deposit, withdraw, min deposit, max balance / 存款、取款、最低存款、最高余额
- **Interest** - Bank interest on a configurable interval (30 minutes by default), off by default / 按可配置间隔（默认 30 分钟）发放的银行利息，默认关闭
- **Leaderboard** - Cached wealth rankings on a configurable refresh interval (60 seconds by default) / 按可配置间隔（默认 60 秒）刷新的财富排行榜
- **PlaceholderAPI** - Rich placeholder support / 丰富的占位符支持
- **Admin Commands** - Give, take, set, check player balances / 管理员经济管理命令
- **Money notes** - `/note` turns cash into a tradeable item. A note whose currency has been removed from `config/currencies.yml` cannot be redeemed, by design: the player keeps the note and is told the currency no longer exists, and the console names the currency; adding the currency back makes such notes redeemable again / 纸币：`/note` 把现金换成可交易的物品。货币已从 `config/currencies.yml` 删除的纸币按设计无法兑换：玩家保留纸币并收到该货币已不存在的提示，控制台记录货币名；把该货币加回后即可再次兑换
- **i18n** - Chinese and English language support / 中英文支持
- **Shared database** - Several servers can share one economy database: every balance change is a conditional write that retries when another server changed the row, and replies "busy, please try again" if it keeps losing. Measured on SQLite, the JSON store and MySQL 8+/9; **MariaDB is untested** — older MariaDB versions may return fewer decimal digits for a `DOUBLE`, which would make such writes fail. Module transfers now commit the sender's debit and receiver's credit in one framework storage transaction, including JSON crash recovery (UltiKits/UltiEconomy#44) / 共用数据库：多台服务器可共用一个经济数据库，每次余额变动都是条件写入，被其他服务器抢先修改时重试，持续失败则提示「请稍后再试」。已在 SQLite、JSON 存储和 MySQL 8+/9 上验证；**MariaDB 未经测试**——较旧版本可能返回较少的小数位，导致此类写入失败。模块转账的扣款与入账现由框架的一次存储事务共同提交，包括 JSON 崩溃恢复（UltiKits/UltiEconomy#44）

### Transfer guarantees and limits / 转账保证与限制

`/pay` and `EconomyService` transfers commit both wallets together on the framework's SQLite, MySQL and JSON backends. An interrupted transaction leaves neither wallet changed; a committed transfer survives restart. The JSON backend's framework warning about a filesystem that cannot force its journal directory still applies to power loss. Transaction tax is deposited into the treasury separately after the wallet transaction, so a treasury failure can still leave that tax uncollected. Separate Vault withdrawal and deposit calls made by another plugin are not one module transfer and are not made atomic by this change.

If the database connection fails during commit, both wallets may already have changed even though the caller sees an error. Check both balances before retrying; the module does not retry or refund an unknown commit outcome.

`/pay` 和 `EconomyService` 转账在框架的 SQLite、MySQL、JSON 后端上将两个钱包共同提交；事务中断时两个钱包都不变，已提交的转账在重启后保留。若文件系统无法强制落盘日志目录，JSON 后端关于断电风险的框架警告仍然适用。转账税在钱包事务之后单独存入国库，国库失败仍可能导致该笔税未入库。其他插件分别发起的 Vault 扣款与入账不是一次模块转账，本改动不保证这两个独立调用的原子性。

数据库连接在提交时失败，可能出现双方余额已改变、调用方却收到错误的情况。重试前请先核对双方余额；模块不会对提交结果未知的事务自动重试或退款。

## Transfer failure limitations / 转账失败限制

A failed `/pay` receiver credit with an acknowledged refund reports `Operation failed`, or the existing busy message when another server keeps changing the receiver's balance. If the refund also fails, the sender instead sees `Transfer not completed. Please contact an administrator to check your balance.` (`转账未完成。请联系管理员核查你的余额。` under `language: zh`), even if the credit or refund failed through contention. This warning does not claim a refund or that an administrator was notified. The server still logs both players (names and UUIDs), the amount, the currency and the failed refund for an operator to reconcile. A non-busy failed note redemption reports `Operation failed` and keeps the note. Failure replies do not prove the storage outcome: **check both balances and the server log before paying again** (#45).

Transfers are separate conditional debit and credit writes, not a cross-row transaction (#44). Besides a process stopping between them, a database connection can fail **after a credit or refund committed but before its acknowledgement arrived**. The module cannot tell that apart from a write that failed before committing. Its attempted compensation can then duplicate money, or a refund's outcome can be uncertain. After such a storage error, operators should compare the sender's and receiver's balances with the database's transaction/audit records and reconcile once before allowing a retry; do not blindly repeat the payment or apply another refund (#45).

接收方入账失败、退款收到写入成功确认时，`/pay` 提示“操作失败”；若入账失败是其他服务器持续修改收款方余额所致，则仍提示稍后再试。退款也失败时，付款人改为收到“转账未完成。请联系管理员核查你的余额。”，即使入账或退款因争用失败也优先显示此警告，不承诺已退款或已通知管理员。控制台继续记录双方姓名与 UUID、金额、货币及退款失败步骤，供管理员人工核对。纸币兑换非繁忙失败时仍提示“操作失败”，并保留纸币。失败提示不代表存储结果确定：**再次付款前先核对双方余额及服务器日志**。转账扣款与入账不是同一个事务；若数据库已提交入账或退款、但确认响应丢失，程序无法区分是否提交，补偿可能造成重复金额或退款结果不确定。遇到这类存储错误，管理员应结合数据库事务或审计记录核对并只补偿一次，不要盲目重付或再次退款。

## Commands / 命令

| Command | Description | Permission |
|---------|-------------|------------|
| `/money` or `/bal` | Show your balance / 查看余额 | `ultieconomy.money` |
| `/bank` | Show bank balance / 查看银行余额 | `ultieconomy.bank` |
| `/pay <player> <amount>` | Transfer cash to a player / 转账给玩家 | `ultieconomy.pay` |
| `/deposit <amount>` or `/ck` | Deposit cash to bank / 存款到银行 | `ultieconomy.bank` |
| `/withdraw <amount>` or `/qk` | Withdraw from bank / 从银行取款 | `ultieconomy.bank` |
| `/eco give <player> <amount>` | Give cash to player / 给予玩家现金 | `ultieconomy.admin` |
| `/eco take <player> <amount>` | Take cash from player / 扣除玩家现金 | `ultieconomy.admin` |
| `/eco set <player> <amount>` | Set player balance / 设置玩家余额 | `ultieconomy.admin` |
| `/eco check <player>` | Check player balance / 查看玩家余额 | `ultieconomy.admin` |

## Configuration / 配置

```yaml
# config/config.yml
initial-cash: 1000.0           # Starting cash for new players / 新玩家初始现金
currency-name: Coins            # Currency display name / 货币名称
currency-symbol: "$"            # Currency symbol / 货币符号

bank:
  enabled: true                 # Enable bank feature / 启用银行功能
  min-deposit: 100.0            # Minimum deposit amount / 最低存款金额
  max-balance: -1               # Max bank balance: -1 = unlimited, otherwise above 0 (0 or another negative is refused with a warning and -1 used) / 最高银行余额：-1 为无限，否则须大于 0（0 或其他负数会被拒绝并警告，改用 -1）

interest:
  enabled: false                # Pay bank interest (creates money) / 发放银行利息（凭空产生货币）
  rate: 0.03                    # Interest rate per payment / 每次发放的利率
  interval: 1800                # Seconds between payments; /ul reload applies / 发放间隔（秒），重载生效
  max-interest: 10000.0         # Max interest per payment / 单次最大利息

leaderboard:
  update-interval: 60           # Seconds between refreshes; /ul reload applies / 刷新间隔（秒），重载生效

tax:
  enabled: true                 # Master switch: false collects no tax at all / 总开关：false 时不征收任何税
```

## PlaceholderAPI Placeholders / 占位符

| Placeholder | Description |
|-------------|-------------|
| `%ultieconomy_cash%` | Cash balance / 现金余额 |
| `%ultieconomy_bank%` | Bank balance / 银行余额 |
| `%ultieconomy_total%` | Total wealth / 总资产 |
| `%ultieconomy_cash_formatted%` | Cash with currency symbol / 带符号的现金 |
| `%ultieconomy_rank%` | Wealth rank / 财富排名 |
| `%ultieconomy_top_name_N%` | Nth richest player name / 第 N 名玩家名 |
| `%ultieconomy_top_balance_N%` | Nth richest player balance / 第 N 名玩家余额 |

## Dependencies / 依赖

- **Vault** (required) - Economy API framework
- **PlaceholderAPI** (optional) - Placeholder support

## UltiTools-API Features Used

- `@Service` + constructor injection (IoC)
- `@UltiToolsModule` plugin registration
- `@CmdExecutor` / `@CmdMapping` command system
- `@ConfigEntity` / `@ConfigEntry` config management
- Config-bound `@Scheduled` (UltiTools 6.3.0) for the interest payment and the leaderboard refresh
- `BaseDataEntity<String>` + `@Table` / `@Column` ORM
- Query DSL (`operator.query().where("x").eq(y).list()`)
- `DataOperator<T>` for account persistence
- `UltiToolsPlugin.i18n()` for translations
- PlaceholderAPI expansion integration
