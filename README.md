# UltiEconomy - 经济模块 / Economy Module

[![UltiTools-API](https://img.shields.io/badge/UltiTools--API-6.3.0%2B-blue)](https://github.com/UltiKits/UltiTools-Reborn)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

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
- **Shared database** - Several servers can share one economy database: every balance change is a conditional write that retries when another server changed the row, and replies "busy, please try again" if it keeps losing. Measured on SQLite, the JSON store and MySQL 8+/9; **MariaDB is untested** — older MariaDB versions may return fewer decimal digits for a `DOUBLE`, which would make such writes fail. Module transfers now commit the sender's debit and receiver's credit in one framework storage transaction, including JSON crash recovery (UltiKits/UltiEconomy#44). One exception to the retry: on MySQL, whose default isolation level is REPEATABLE READ, a transfer (`/pay` or an `EconomyService` transfer) re-reads its rows inside its own transaction and sees the same snapshot, so a transfer that loses this race to another server cannot win on retry and replies "busy, please try again" after its three attempts; nothing is written and the player can simply repeat it (UltiKits/UltiEconomy#60) / 共用数据库：多台服务器可共用一个经济数据库，每次余额变动都是条件写入，被其他服务器抢先修改时重试，持续失败则提示「请稍后再试」。已在 SQLite、JSON 存储和 MySQL 8+/9 上验证；**MariaDB 未经测试**——较旧版本可能返回较少的小数位，导致此类写入失败。模块转账的扣款与入账现由框架的一次存储事务共同提交，包括 JSON 崩溃恢复（UltiKits/UltiEconomy#44）。例外：MySQL 默认隔离级别为 REPEATABLE READ，转账（`/pay` 或 `EconomyService` 的转账）在自身事务内重读时看到的是同一快照，因此与其他服务器同时修改同一余额而落败的转账重试也无法成功，在三次尝试后提示「请稍后再试」；不会写入任何数据，玩家重新执行即可（UltiKits/UltiEconomy#60）

### Transfer guarantees and limits / 转账保证与限制

`/pay` and `EconomyService` transfers commit both wallets together on the framework's SQLite, MySQL and JSON backends. An interrupted transaction leaves neither wallet changed; a committed transfer survives restart. The JSON backend's framework warning about a filesystem that cannot force its journal directory still applies to power loss. Transaction tax is deposited into the treasury separately after the wallet transaction, so a treasury failure can still leave that tax uncollected. Separate Vault withdrawal and deposit calls made by another plugin are not one module transfer and are not made atomic by this change.

If the database connection fails during commit, both wallets may already have changed even though the caller sees an error. Check both balances before retrying; the module does not retry or refund an unknown commit outcome.

`/pay` 和 `EconomyService` 转账在框架的 SQLite、MySQL、JSON 后端上将两个钱包共同提交；事务中断时两个钱包都不变，已提交的转账在重启后保留。若文件系统无法强制落盘日志目录，JSON 后端关于断电风险的框架警告仍然适用。转账税在钱包事务之后单独存入国库，国库失败仍可能导致该笔税未入库。其他插件分别发起的 Vault 扣款与入账不是一次模块转账，本改动不保证这两个独立调用的原子性。

数据库连接在提交时失败，可能出现双方余额已改变、调用方却收到错误的情况。重试前请先核对双方余额；模块不会对提交结果未知的事务自动重试或退款。

## Transfer and redemption failure feedback / 转账与兑换失败提示

A failed `/pay` receiver credit rolls back both wallets and reports `Operation failed`, or the existing busy message when the credit exhausts conditional-write retries. An unaffordable debit still reports `Insufficient balance`. There is no compensating refund or refund-failed state. A non-busy failed note redemption reports `Operation failed` and keeps the note (#45).

These replies do not redefine the transaction limits above: tax collection is separate, independent Vault calls are not atomic transfers, and a JDBC commit acknowledgement failure can leave both wallets committed despite an error. Operators should check both balances and database transaction/audit records before retrying an uncertain commit; do not blindly repeat the payment or apply a manual refund.

`/pay` 的接收方入账失败会回滚双方钱包，并提示“操作失败”；条件写入重试耗尽时保留繁忙提示。余额确实不足时仍提示“余额不足”。不再执行补偿退款，也没有退款失败状态。纸币兑换非繁忙失败时提示“操作失败”，并保留纸币（#45）。

这些提示不改变上文的事务限制：国库税款另行入账，独立的 Vault 调用不构成原子转账，JDBC 提交确认失败时双方钱包仍可能已提交。提交结果未知时，管理员应结合数据库事务或审计记录核对双方余额，再决定是否重试；不要盲目重付或人工退款。

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
