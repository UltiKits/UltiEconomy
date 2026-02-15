# UltiEconomy - 经济模块 / Economy Module

[![UltiTools-API](https://img.shields.io/badge/UltiTools--API-6.2.0-blue)](https://github.com/UltiKits/UltiTools-Reborn)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-green)](../../LICENSE)

A full-featured Vault economy provider for UltiTools-API with dual-wallet (cash + bank), interest mechanics, leaderboard rankings, and PlaceholderAPI integration.

UltiTools-API 的完整 Vault 经济提供者模块。支持双钱包（现金 + 银行）、利息机制、财富排行榜和 PlaceholderAPI 集成。

## Features / 功能

- **Vault Economy** - Full Vault API integration as economy provider / 完整 Vault API 经济提供者
- **Dual Wallet** - Separate cash and bank balances / 现金和银行双钱包
- **Bank System** - Deposit, withdraw, min deposit, max balance / 存款、取款、最低存款、最高余额
- **Interest** - Configurable periodic interest on bank balances / 可配置的银行利息
- **Leaderboard** - Cached wealth rankings with periodic refresh / 定期刷新的财富排行榜
- **PlaceholderAPI** - Rich placeholder support / 丰富的占位符支持
- **Admin Commands** - Give, take, set, check player balances / 管理员经济管理命令
- **i18n** - Chinese and English language support / 中英文支持

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
  max-balance: -1               # Max bank balance (-1 = unlimited) / 最高银行余额

interest:
  enabled: true                 # Enable interest / 启用利息
  rate: 0.03                    # Interest rate per interval / 每周期利率
  interval: 1800                # Interval in seconds / 利息发放间隔（秒）
  max-interest: 10000.0         # Max interest per payment / 单次最大利息

leaderboard:
  update-interval: 60           # Refresh interval in seconds / 排行榜刷新间隔
  display-count: 10             # Default top N / 默认显示前 N 名
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
- `@ConditionalOnConfig` for feature toggling (interest)
- `AbstractDataEntity` + `@Table` / `@Column` ORM
- Query DSL (`operator.query().where("x").eq(y).list()`)
- `DataOperator<T>` for account persistence
- `UltiToolsPlugin.i18n()` for translations
- PlaceholderAPI expansion integration
