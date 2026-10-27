# MeowsEnchants

[English Version](README_EN.md)

MeowsEnchants 是一个面向 Paper/Folia 的自定义附魔插件，声明式创建自定义附魔并将其注册进原版附魔注册表。

## 1. 功能简述

MeowsEnchants 让自定义附魔像原版附魔一样注册到服务端中，而非传统更多附魔那样通过 NBT、PersistentDataContainer 数据或物品 Lore 伪装成“附魔”。 

与常见基于 NBT 或 Lore 的更多附魔插件相比，本插件的主要区别是：

- 自定义附魔注册进原版注册表，附魔与真正的原版附魔完全无异，可以被Bukkit API直接获取，此插件无需提供任何API。
- 附魔注册后可以在创造模式物品栏、原版`/minecraft:enchant`或其他插件的`/enchant`命令中直接获取到，并能正确获取所有属性。
- 插件注册的附魔兼容原版附魔冲突、适用物品、铁砧成本等注册表属性，天然兼容任何其他基于附魔的插件。
- 附魔效果由“触发器 + 目标 + 动作”声明式组合，而不是为每个附魔写死一套 Java 逻辑，可以自由组合轻松拓展。
- 插件监听器按配置单独控制每一个附魔在村民交易和附魔台随机出现的逻辑和概率。

## 2. 服务端兼容性

- 目标服务端：Paper/Folia `1.21.11 - 26.2+`
- Java：`25`
- 插件类型：Paper plugin，使用 `paper-plugin.yml` 的 `bootstrapper`
- Folia：`全功能支持`
- 可选依赖：Residence（跟踪附魔跳过射手在领地内无权伤害的目标；未安装时跳过这项判断）


| 服务端 / 版本                      | 兼容性  | 结论                                                             |
|-------------------------------|------|----------------------------------------------------------------|
| Folia `1.21.11 - 26.2+`       | ✅支持  | 当前主要目标环境，已按 Folia 调度模型适配                                       |
| Paper `1.21.11 - 26.2+`       | ✅支持  | 支持普通 Paper 调度器回退，但仍要求 Paper plugin bootstrapper 与 Registry API |
| Paper/Folia `1.21.10` 及更低     | ❌不支持 | 缺少插件使用的实验性API `RegistryComposeEvent` `ItemTypeTagKeys`         |
| Spigot / CraftBukkit          | ❌不支持 | 不提供 Paper plugin bootstrapper 与 Paper Registry Mutation API    |
| Purpur Leaf Leaves 等 Paper 下游 | ✅支持  | Fork如果完整保留对应实验性 Paper API，则支持此插件                               |

Paper 的 Registry Mutation API 标记为实验性，未来 Paper/Folia 更新可能改变 API 形态，如遇到问题请反馈。

## 3. 运行原理

### 附魔注册

插件在 bootstrap 阶段读取 `plugins/MeowsEnchants/enchants/*.yml`。
每个附魔都对应一个 YAML 文件并声明以下内容：

- 附魔 ID、显示名称、最大等级、铁砧成本、适用物品标签和冲突附魔
- 触发器，例如攻击、挖掘、右键、被动、潜行、重生等
- 目标，例如自身、玩家、生物或命中的实体
- 动作，例如额外伤害、药水效果、爆炸、连锁挖掘、自动补种、速度、命令等
- 附魔台与图书管理员交易中的独立出现规则

每个有效的 YAML 文件会被解析为一个 `EnchantConfig`，随后通过 Paper 的 `RegistryEvents.ENCHANTMENT.compose()` 注册进原版附魔注册表。注册时会设置：

- 附魔描述文本（物品上显示的附魔名，取自 `name`）
- 附魔适用物品标签
- 附魔生效的装备槽（`registry.active_slots`，默认 `ANY`）
- 附魔最大等级
- 附魔铁砧成本
- 原版权重和原版附魔台最低/最高费用（`registry` 段，默认权重 1、费用都是 30）
- 附魔冲突集合

注册相关的字段可以在附魔 YAML 的 `registry` 段里改（milk.13 起；不写时和以前写死的值一样）：

```yaml
registry:
  weight: 1               # 原版附魔权重，1–1024；插件自己的附魔台逻辑不看它
  min_cost: 30            # 原版附魔台最低费用 = min_cost + min_cost_per_level × (等级 − 1)
  min_cost_per_level: 0
  max_cost: 30            # 原版附魔台最高费用 = max_cost + max_cost_per_level × (等级 − 1)
  max_cost_per_level: 0
  active_slots: ANY       # 原版生效槽位组；插件自己的触发器始终检查主手、副手和四件盔甲
```

注册表在启动后冻结，所以 `name`、`max_level`、`anvil_cost`、`applicable_items`、`conflicts` 和 `registry` 段改了要完整重启才生效；其余配置可以用 `/enchants reload` 热应用，见下文「重载配置」。

### 触发器

插件通过 Bukkit/Paper 事件监听玩家行为，并按附魔配置中的 `触发器` 执行动作。当前常用触发时机包括：

- 战斗：攻击实体、受到伤害、盾牌格挡、击杀实体
- 方块：破坏方块、左键挖掘方块、放置方块、右键交互
- 投射物：投射物命中实体或方块
- 射击：射箭或射弩（BOW_SHOOT）
- 状态：开始潜行、持续潜行、开始疾跑、持续疾跑、周期性被动触发
- 玩家生命周期和物品：死亡、重生、切换主副手、消耗物品
- 钓鱼：钓上鱼或实体、鱼咬钩

### 目标

动作执行前会根据配置中的 `目标` 解析附魔作用的目标。当前支持的目标语义包括：

- `触发者自身`
- `目标玩家`
- `目标怪物`
- `所有生物`
- `不指定目标`

### 动作

附魔触发时执行的动作由 `actions` 段声明（键名 → 动作）并按顺序执行。当前内置动作包括：

`potion_effect`, `bonus_damage`, `healthsteal`, `teleport`, `explosion`, `velocity`, `shockwave`, `break_armor`, `drop_head`, `attribute_modifier`, `multi_break`, `vacuum_drops`, `replant`, `multi_arrow`, `auto_bow`, `draw_accelerate`, `repair`, `hook`, `feast`, `deflect`, `till`, `reflect_damage`, `command`, `block_break`

数值参数可以按等级取值：固定值、`{base, per_level}`，或 milk.13 起的逐级列表（如 `[8, 16, 32]`，第 L 级取第 L 项，超出列表长度取最后一项）。milk.13 起每个动作都可以加通用参数 `chance`（0–1，也可按等级取值），没中时视为动作失败。

详细动作参数不写在 README 中，请查看：

- [English: docs/action_types_en.md](docs/action_types_en.md)
- [中文版：docs/action_types_zh.md](docs/action_types_zh.md)

## 4. 插件使用方法

1. 下载插件 jar。
2. 将插件jar放入服务端 `plugins/` 目录。
3. 启动一次服务端，让插件生成 `plugins/MeowsEnchants/config.yml`。
4. 在 `plugins/MeowsEnchants/enchants/` 下创建每个自定义附魔的 YAML 文件。
5. 重启服务端。自定义附魔只能在 bootstrap 阶段注册，插件无法通过 reload 动态加入服务器注册表：新增或删除附魔文件、改注册相关字段后必须完整重启；其余配置改动可以用 `/enchants reload` 热应用。
6. 所有成功注册的自定义附魔可以直接在创造模式物品栏，JEI/REI的附魔列表、配方查询中直接查看。

全局配置位于： `plugins/MeowsEnchants/config.yml`。 当前全局配置用于控制：

- Skyllia 兼容逻辑开关（用于使附魔遵循空岛保护机制），以及岛屿边长 `skyllia_region_size`（默认 512）和主城区域内禁用的动作 `skyllia_protected_actions`
- 附魔台概率是否考虑玩家等级（玩家等级将对附魔获取概率产生影响）
- 附魔书概率平衡（附魔书比武器装备更难获得自定义附魔）
- 附魔台逐级概率底数 `enchanting_table.level_chance_divisor`（默认 2）和附魔书概率除数 `enchanting_table.book_divisor`（默认 4）
- 村民交易 `villager_trades`：附魔书价格公式、宝藏附魔倍数、价格上下限、默认交易次数和污染交易提示 `polluted_message`
- 周期触发器 `triggers`：PASSIVE 和持续潜行/疾跑的最短触发周期（`passive_min_period_ticks` / `hold_min_period_ticks`，默认 20 tick）

后四项是 milk.13 新增的，默认值都等于以前写死的值。插件不会往已有的 `config.yml` 里补新键，缺的键按默认值；要改时照 jar 里内置的 `config.yml` 加上。

附魔配置文件位于：

```text
plugins/MeowsEnchants/enchants/<enchant_id>.yml
```

### 重载配置

`/enchants reload` 重读 `config.yml` 和全部附魔 YAML，整体换上新配置，再为在线玩家重排 PASSIVE 和持续潜行/疾跑任务、重算属性修饰。玩家、控制台和 RCON 都能执行，需要权限 `meowsenchants.reload`（默认只有 OP 有）。

- 立即生效：`config.yml` 全部内容；附魔的触发器、目标、冷却、动作、说明、音效粒子、附魔台和村民交易出现规则；箭雨 `arrow_rain` 段和跟踪 `tracking` 段（包括 `mechanic` 开关）。已经射出的箭雨和跟踪箭沿用射出时的参数，之后射出的才用新参数。
- 需要重启：上文的注册相关字段。reload 时这些字段保持注册时的值，报告里列为「需重启」；只改 `name` 时查询界面的名字会先换，物品上的附魔名重启后才变。新加的附魔文件重启后才注册，删掉的文件在重启前保留旧配置。
- 某个附魔文件写坏（YAML 语法错、空文件、缺必填字段）时，这个附魔保留旧配置，报告里记一条错误；`config.yml` 写坏时保留原全局配置。

命令先输出中文摘要（生效几项、需重启几项、警告和错误），最后一行固定是一行 JSON，编辑器经 RCON 解析它：

```text
MEOWSENCHANTS_RELOAD {"ok":true,"pluginVersion":"0.2-milk.13","applied":["night_vision"],"restartRequired":[{"enchant":"night_vision","fields":["max_level"],"reason":"changed"}],"warnings":[],"errors":[]}
```

`applied` 只列至少有一项可热生效的值真的变了的附魔；只改了需重启字段（包括只改 `name`）的附魔只出现在 `restartRequired`。上例中 night_vision 同时改了冷却和 `max_level`，所以两边都有它。`ok` 为 `false` 表示有错误；`reason` 是 `changed`（注册字段改了）、`added`（新文件）或 `removed`（文件被删）；警告和错误都带 `enchant`、`path`、`message`。玩家或 RCON 执行时，摘要也会写进服务端日志。

加载期校验在启动和每次 reload 时运行，只报告，不拦截：类型不符、缺必填参数、逐级列表为空是错误（对应的动作触发时被跳过）；未知键、未知取值、注册表里不存在的名字、超出范围、无法解析的说明占位符是警告。启动时的报告打在服务端日志里。

### 附魔查询界面

在游戏内输入 `/enchants` 可打开附魔查询界面（6 行箱子 GUI）：

- 第一行中间的槽位用于放入要查询的物品，界面会列出该物品可附魔的自定义附魔；放入附魔书或留空时列出全部附魔。
- 第 2~5 行按「可用类型标签、附魔名称」排序展示附魔书，名称与 lore 取自附魔配置文件。
- 查询物品已拥有的附魔会显示为知识之书；边框颜色表示物品类别：工具黄色、武器红色、防具蓝色、其它黑色。
- 附魔超过 36 个时最后一行显示上一页/下一页按钮，箭的数量为目标页码。

每个附魔配置支持 `description` 字段（字符串列表），用于查询界面的 lore 显示，同时支持 Bukkit 颜色代码与 MiniMessage 标签：

```yaml
description:
  - "&f装备后获得额外击退抗性"
  - "&b冷却：5秒"
  - "&7可用于：&e护腿"
```

milk.13 起 `description` 里可以写说明占位符，显示时换成配置里的实际数值，改了参数不用再手改说明：

```yaml
description:
  - "&b冷却：{cooldown|s}秒"
  - "&7掉头概率：{actions.head.amount|%}，满级 {actions.head.amount@max|%}"
```

- 引用：`cooldown`、`max_level`、`anvil_cost`、`enchanting_table.chance`、`villager_trades.chance`、`actions.<键名>.<参数>`（包括 `chance`，子段用点连接）；箭雨和跟踪还能引用 `arrow_rain.<参数>` / `tracking.<参数>`。没写的参数按默认值。
- `@等级`：`@2` 取 2 级的值，`@max` 取最高等级的值，`@step` 取每级增量（`{base, per_level}` 的 `per_level`，固定值为 0，逐级列表不支持）；不写时取 1 级。整数参数按插件实际用法截断。
- `|格式`，可以叠加：`%` 乘 100 加百分号，`s` 把毫秒或 tick 换成秒，`0`–`3` 固定小数位；不指定小数位时整数不带小数，其余最多两位。
- `{{` / `}}` 输出花括号。无法解析的占位符原样显示，加载期校验报一条警告。
- 插件和编辑器预览跑同一组测试向量 [editor/placeholder-vectors.json](editor/placeholder-vectors.json)，结果必须一致。

## 5. 项目构建方法

Milk 定制附魔的参数、配置迁移和验证说明按附魔放在 [docs/enchants/](docs/enchants/)，例如箭雨见 [docs/enchants/arrow_rain.md](docs/enchants/arrow_rain.md)，跟踪（弓弩射出的箭和烟花自动拐向目标，可选配合 Residence）见 [docs/enchants/tracking.md](docs/enchants/tracking.md)。

项目使用 Maven 构建，依赖从 PaperMC Maven 仓库和 PlaceholderAPI 仓库解析。  
本项目 Java 版本为 `25`。构建前请确认本机 JDK 与 Maven 使用的 Java 版本匹配。  

在已安装 Maven 的环境中：

```bash
mvn clean package
```

构建产物位于：

```text
target/meowsenchants-0.2-milk.13.jar
```

### 编辑器契约文件

`editor/` 下的三个文件给配置编辑器用：

- `enchant-schema.json`：全部可配置项（附魔字段、动作参数、箭雨和跟踪段、`config.yml`）的类型、默认值、范围和生效方式（`apply: reload` 或 `apply: restart`），由源码里的参数声明导出。
- `scaling-vectors.json`：按等级取值的测试向量。
- `placeholder-vectors.json`：说明占位符的测试向量。

改了参数声明后重新导出 schema：

```bash
JAVA_HOME=/usr/lib/jvm/zulu-25 mvn -B test -Dtest=EnchantSchemaExportTest -Dmeowsenchants.updateSchema=true
```

平时跑测试时 `EnchantSchemaExportTest` 只做比较，提交的 schema 和代码声明不一致时测试失败。
