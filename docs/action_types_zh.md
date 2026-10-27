# 动作类型

所有动作类型都在附魔 YAML 的 `actions` 段里使用。每个动作有一个自定的键名（说明占位符用它引用，例如 `{actions.head.amount}`）和一个 `type` 字段，其余字段是该动作的参数。

```yaml
actions:
  my_key:
    type: action_type_name
    chance: 0.5        # 可选，所有动作通用，见下文
    # ... 参数
```

- `actions` 必须写成段（键名 → 动作）；写成列表会被当成没有动作。动作按 YAML 顺序执行。
- 任何一个动作失败（条件不满足、概率没中、`chance` 没中、抛异常）都会**中止后续动作**，这次触发不进冷却、不播附魔的音效和粒子。所以 `cooldown` 实际是「成功触发后的冷却」。
- 缺参数、写法不合法或类型未知的动作在触发时被跳过并写日志，其余动作照常执行；启动和 `/enchants reload` 时的加载期校验会提前报告这些问题。
- 下面的参数表以插件源码里的参数声明为准（`editor/enchant-schema.json` 由同一份声明导出）。「默认值」是不写该参数时插件实际使用的值；milk.13 新增的可选参数默认值都等于 milk.12 写死的值，不写就和以前一样。
- 编辑器开放 23 种动作，不开放 `command`。

## 按等级取值（ScalingFunction）

类型写作 ScalingFunction 的参数按附魔等级取值，接受四种写法：

| 写法 | 含义 |
| --- | --- |
| `amount: 0.5` | 固定值 |
| `amount: {scaling: fixed, value: 0.5}` | 固定值（旧写法，保留） |
| `amount: {base: 0.1, per_level: 0.1}` | `base + per_level × (等级 − 1)` |
| `amount: [8, 16, 32, 64, 128]` | 逐级列表（milk.13 新增）：L 级取第 `min(L, 长度)` 项 |

- 逐级列表比最高等级短时，更高等级取最后一项，插件日志对同一份列表只告警一次；空列表或列表里有非数字是错误（该动作被跳过）。
- 标「整数」的参数取值后按 `(int)` 截断（向零取整），例如 `{base: 10, per_level: 2.5}` 在 2 级是 12.5，实际用 12。
- 例外：跟踪的 `tracking.radius` 写成列表时逐项覆盖默认列表 `[1.5, 2.5, 3.5]`（缺的项沿用默认），见 [enchants/tracking.md](enchants/tracking.md)。

## 通用参数 chance

每个动作都可以写 `chance`（ScalingFunction，0~1，默认 `1.0`）：执行这个动作前先掷骰，没中就当作该动作失败——中止后续动作、不进冷却、不播音效粒子，和 `drop_head` 等概率类动作没中时一样。

```yaml
actions:
  poison:
    type: potion_effect
    effect: poison
    duration: {base: 60, per_level: 40}
    amplifier: 0
    chance: [0.25, 0.5, 0.75]   # 一/二/三级 25% / 50% / 75%
```

- 没写 `chance` 的动作不掷骰，行为和 milk.12 完全一样。
- 概率不大于 0 时永不通过，不小于 1 时必定通过。
- `chance` 写法不合法（例如空列表）时该动作被跳过。

## 动作一览

`potion_effect`、`bonus_damage`、`healthsteal`、`teleport`、`explosion`、`velocity`、`shockwave`、`break_armor`、`drop_head`、`attribute_modifier`、`multi_break`、`vacuum_drops`、`replant`、`multi_arrow`、`draw_accelerate`、`auto_bow`、`repair`、`hook`、`feast`、`deflect`、`till`、`reflect_damage`、`block_break`、`command`（编辑器不开放）。

---

### potion_effect（药水效果）

对目标施加药水效果。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `effect` | string | 是 | -- | 药水效果名，如 `poison`、`strength`（不分大小写） |
| `duration` | ScalingFunction（整数） | 是 | -- | 持续时间，单位 tick（20 tick = 1 秒）；`-1` = 无限 |
| `amplifier` | ScalingFunction（整数） | 是 | -- | 效果等级，0 = I 级，1 = II 级 |
| `particles` | boolean | 否 | `true` | `false` 时效果按环境效果（ambient）施加，粒子变淡变少，**不会**完全隐藏粒子 |
| `icon` | boolean | 否 | `true` | `false` 时既不显示粒子，也不显示 HUD 图标 |

> `particles` / `icon` 的实际含义来自 Bukkit 五参构造 `PotionEffect(type, duration, amplifier, ambient, particles)`：`particles` 进的是 ambient 位，`icon` 进的是 particles 位（图标随之隐藏）。milk.13 没有改这个行为，只把文档改成真实效果。

```yaml
actions:
  poison:
    type: potion_effect
    effect: poison
    duration:
      base: 40
      per_level: 20
    amplifier:
      scaling: fixed
      value: 0
```

---

### bonus_damage（额外伤害）

在伤害事件上加伤害或按倍率放大伤害。需要伤害事件（`ATTACK_ENTITY` 等）。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `amount` | ScalingFunction | 是 | -- | 加到本次伤害上；`multiplier` 为 true 时改为乘以本次伤害 |
| `multiplier` | boolean | 否 | `false` | 按倍率 |
| `conditions.sneaking` | boolean | 否 | `false` | 需要潜行 |
| `conditions.night_time` | boolean | 否 | `false` | 仅当世界时间在 `night_start`–`night_end` 之间 |
| `conditions.night_start` | int | 否 | `13000` | 夜晚开始（一天内的 tick，0–24000） |
| `conditions.night_end` | int | 否 | `23000` | 夜晚结束 |
| `conditions.low_health` | double | 否 | `0.0` | 大于 0 时，自己生命值不高于此值才生效 |
| `conditions.dimension` | string | 否 | -- | 限定 Bukkit 世界名（不分大小写），如 `world_nether` |
| `conditions.passive_mobs` | boolean | 否 | `false` | 只对动物、环境生物、鱼、鱿鱼、雪傀儡、铁傀儡、海豚生效 |
| `conditions.target_types` | list | 否 | `[]` | 实体类型枚举名（大写，如 `ZOMBIE`）；不写不限 |

```yaml
actions:
  extra_dmg:
    type: bonus_damage
    amount:
      base: 2.0
      per_level: 1.0
    conditions:
      night_time: true
```

---

### healthsteal（生命偷取）

按本次伤害的比例治疗自己，不修改伤害本身。需要伤害事件。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `amount` | ScalingFunction | 是 | -- | 偷取比例（0.10 = 10%），按减伤前的伤害计算 |
| `max_fraction` | double | 否 | `1.0` | 比例上限，`amount` 超过它时按它算 |

```yaml
actions:
  steal:
    type: healthsteal
    amount:
      base: 0.1
      per_level: 0.1
```

---

### teleport（传送）

沿视线方向传送玩家。从最远距离往回找安全落点：落点下方若干格内要有可站立方块，脚部和头部可通行。开启 Skyllia 兼容时不会传出当前岛屿。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `range` | ScalingFunction | 是 | -- | 最远传送距离（格） |
| `step` | double | 否 | `1.0` | 找不到落点时每次往回退的距离 |
| `min_distance` | double | 否 | `0.5` | 回退到比它更近时放弃传送 |
| `ground_search_depth` | int | 否 | `4` | 落点下方多少格内要有可站立的方块 |

```yaml
actions:
  blink:
    type: teleport
    range:
      base: 5.0
      per_level: 3.0
```

---

### explosion（爆炸）

在目标位置制造爆炸。`PROJECTILE_HIT` 触发时用弹射物的命中点（命中方块时为方块中心），**不看** `target` 过滤，而且弹射物必须是从带此动作附魔的弓弩射出的（射出时打标记）；其他触发器用第一个目标的位置。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `power` | ScalingFunction | 是 | -- | 原版爆炸威力（TNT 为 4，闪电苦力怕为 6） |
| `fire` | boolean | 否 | `false` | 引燃 |
| `break_blocks` | boolean | 否 | `false` | 破坏方块 |

```yaml
actions:
  boom:
    type: explosion
    power:
      base: 2.0
      per_level: 1.0
```

---

### velocity（速度）

给自己或目标一个速度（击退、弹射）。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `direction` | string | 是 | -- | `UP`、`DOWN`（绝对竖直）；`FORWARD`/`LOOK`（视线方向）；`BACKWARD`（视线反方向）；`AWAY`（水平远离目标，需要目标） |
| `power` | ScalingFunction | 是 | -- | 速度大小，竖直分量再乘 `vertical_scale` |
| `apply_to` | string | 否 | `SELF` | `SELF` 自己；其他任何值按 `TARGET`（事件目标）处理 |
| `no_fall_damage` | boolean | 否 | `false` | 作用于玩家时，`no_fall_damage_ticks` 内的下一次摔落伤害被取消 |
| `on_ground` | boolean | 否 | `true` | 需要触发者接近地面：站在地上或离支撑方块不超过 `ground_distance` 格（`SELF` 和 `TARGET` 都检查触发者）。旧写法 `onGround` 照常有效（内置 leap.yml 就这样写），两者都写时以 `onGround` 为准（与 milk.12 一致），加载期校验会告警 |
| `vertical_scale` | double | 否 | `0.25` | 竖直分量倍率（所以 `UP` 方向 `power: 5` 实际向上 1.25） |
| `ground_distance` | int | 否 | `1` | 离地判定的格数 |
| `no_fall_damage_ticks` | int | 否 | `100` | 免摔保护持续的 tick（100 tick = 5 秒） |

```yaml
actions:
  launch:
    type: velocity
    direction: UP
    power:
      base: 0.8
      per_level: 0.3
    apply_to: SELF
    no_fall_damage: true
```

---

### shockwave（冲击波）

把自己周围半径内的所有生物以抛射角推开，**不看** `target`。离得越近冲击越强；目标的 `minecraft:knockback_resistance` 最终值按比例减免冲击。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `radius` | ScalingFunction | 是 | -- | 半径（格）；超过 10 时编辑器会提示（Folia 跨区域未验证） |
| `power` | ScalingFunction | 是 | -- | `falloff_start` 以内的冲击速度 |
| `max_entities` | int | 否 | `64` | 只取离自己最近的这么多个生物 |
| `max_power` | double | 否 | `16.0` | 冲击力度上限 |
| `knockback_resistance_reduction` | double | 否 | `0.5` | 击退抗性为 1 时减免的比例，按抗性线性 |
| `falloff_start` | double | 否 | `1.0` | 衰减起点：更远处力度 = 力度 ÷ (距离 − 起点)^指数 |
| `falloff_exponent` | double | 否 | `2.0` | 衰减指数 |
| `launch_angle_degrees` | double | 否 | `45.0` | 抛射仰角 |

```yaml
actions:
  push:
    type: shockwave
    radius:
      base: 5.0
      per_level: 2.0
    power:
      base: 1.0
      per_level: 0.5
```

---

### break_armor（损坏盔甲）

随机损坏目标玩家的一件盔甲（受耐久附魔影响）。目标必须是玩家。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `damage` | ScalingFunction（整数） | 是 | -- | 耐久损耗 |

```yaml
actions:
  shatter:
    type: break_armor
    damage:
      base: 10
      per_level: 10
```

---

### drop_head（掉落头颅）

按概率在第一个目标处掉落它的头颅。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `amount` | ScalingFunction | 是 | -- | 掉落概率（0~1） |
| `heads` | map | 否 | 见下 | 实体类型（大写枚举名，可带 `minecraft:`）→ 掉落的物品；不在表里的生物不掉落。写了就整表替换默认表 |
| `player_heads` | boolean | 否 | `true` | 击杀玩家时按同样概率掉落该玩家的头 |
| `player_head_success` | boolean | 否 | `false` | 掉了玩家头是否算动作成功。`false`（milk.12 行为）时算失败：不进冷却、不播音效粒子、不执行后续动作 |

`heads` 默认：`CREEPER: CREEPER_HEAD`、`ZOMBIE: ZOMBIE_HEAD`、`SKELETON: SKELETON_SKULL`、`WITHER_SKELETON: WITHER_SKELETON_SKULL`、`PIGLIN: PIGLIN_HEAD`、`ENDER_DRAGON: DRAGON_HEAD`。

```yaml
actions:
  head:
    type: drop_head
    amount:
      base: 0.1
      per_level: 0.1
```

---

### attribute_modifier（属性修饰）

给带此附魔的物品写入属性修饰符。通常配合 `PASSIVE` 触发器；换装备、切手持、进服和每个 PASSIVE 周期都会重算，`/enchants reload` 后也会对在线玩家重算。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `attribute` | string | 是 | -- | 原版属性注册名（minecraft 命名空间，不分大小写），如 `movement_speed`、`max_health`、`scale` |
| `amount` | ScalingFunction | 是 | -- | 修饰值；为 0 时不添加 |
| `operation` | string | 是 | -- | `ADD_NUMBER` 直接加；`ADD_SCALAR` 按基础值比例加；`MULTIPLY_SCALAR_1` 按总值乘 (1 + 数值) |
| `key` | string | 是 | -- | 修饰符键，写进物品时为 `attr_<附魔>_<key>`，同一附魔内唯一 |
| `conditions.equipped` | boolean | 否 | `false` | `true` 时修饰符只在盔甲槽（ARMOR）生效；`false` 时为任意槽位（ANY），拿在手上也生效 |

> **不要改已上线附魔的 `key`**：移除旧修饰符只按当前键，改名后旧修饰符会留在已有物品上和新修饰符叠加。编辑器只在新建动作时允许填写。

```yaml
actions:
  speed_boost:
    type: attribute_modifier
    attribute: movement_speed
    amount:
      base: 0.02
      per_level: 0.01
    operation: ADD_NUMBER
    key: speed
    conditions:
      equipped: true
```

---

### multi_break（范围破坏）

以被挖方块为中心破坏立方体（含上下）范围内的方块，每个方块先发一次 `BlockBreakEvent` 供保护插件拦截，容器里的物品一并掉落。需要 `BLOCK_BREAK` 触发器。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `radius` | ScalingFunction（整数） | 是 | -- | 半径；0 或负数不生效；超过 2 时编辑器会提示（Folia 跨区域未验证） |
| `match_tool` | boolean | 否 | `false` | 只破坏手中工具适合挖掘的方块（镐/锹/斧/锄的可挖掘标签） |
| `exclude` | list | 否 | `[]` | 玩家处于其中任一状态时整次跳过，见下表 |
| `durability_per_block` | int | 否 | `2` | 每多破坏一个方块扣工具的耐久（受耐久附魔影响） |
| `conditions.sneaking` | boolean | 否 | `false` | 需要潜行 |

`exclude` 只有 `multi_break` 读取（不是通用参数），取值不分大小写：

| 值 | 跳过条件 |
| --- | --- |
| `sneaking` | 正在潜行 |
| `sprinting` | 正在疾跑 |
| `swimming` | 正在游泳 |
| `flying` | 正在飞行 |
| `gliding` | 正在滑翔 |

```yaml
actions:
  excavate:
    type: multi_break
    exclude:
      - sneaking
    radius:
      base: 1
      per_level: 1
```

---

### vacuum_drops（掉落物直入背包）

取消方块的自然掉落，直接放进玩家背包，放不下的掉在原地。实际收集由监听器完成：只要主手、副手或四件盔甲里有任何带此动作的附魔就生效，不看触发器和冷却。

无参数。

```yaml
actions:
  collect:
    type: vacuum_drops
```

---

### replant（自动补种）

收割作物后自动补种（从背包扣 1 个种子），可选消耗骨粉直接催熟。需要 `BLOCK_BREAK` 触发器。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `fertilizer` | boolean | 否 | `false` | 骨粉催熟：背包里骨粉不少于 `bone_meal` 个时消耗并直接长成熟 |
| `bone_meal` | int | 否 | `5` | 催熟消耗的骨粉数（优先扣副手） |
| `delay_ticks` | int | 否 | `3` | 收割后等多少 tick 再补种 |
| `crops` | map | 否 | 见下 | 作物方块 → `{seed: 种子物品, soil: [可补种的土壤方块]}`；只处理表里、带生长阶段的作物。写了就整表替换默认表 |

`crops` 默认：

```yaml
crops:
  WHEAT: {seed: WHEAT_SEEDS, soil: [FARMLAND]}
  CARROTS: {seed: CARROT, soil: [FARMLAND]}
  POTATOES: {seed: POTATO, soil: [FARMLAND]}
  BEETROOTS: {seed: BEETROOT_SEEDS, soil: [FARMLAND]}
  NETHER_WART: {seed: NETHER_WART, soil: [SOUL_SAND, SOUL_SOIL]}
```

```yaml
actions:
  plant:
    type: replant
    fertilizer: true
```

---

### multi_arrow（多重箭矢）

在主箭之后连射额外箭矢。配合 `BOW_SHOOT` 触发器，弓与弩均可；额外箭矢继承原箭的方向、初速度、暴击、穿透、着火、药水效果和发射武器（保留力量、火矢等加成），不可拾取。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `extra_arrows` | ScalingFunction（整数） | 是 | -- | 额外箭数 |
| `spread` | ScalingFunction | 是 | -- | 额外箭与主箭方向的最大夹角（度），不超过 `max_spread_degrees` |
| `max_spread_degrees` | double | 否 | `60.0` | 散射角上限 |
| `default_speed` | double | 否 | `3.0` | 取不到原箭速度时（非射击事件触发）的箭速 |
| `interval_ticks` | int | 否 | `1` | 第 i 支额外箭在 i × 间隔 tick 后射出 |

```yaml
actions:
  volley:
    type: multi_arrow
    extra_arrows:
      base: 1
      per_level: 1
    spread:
      base: 5
      per_level: 2
```

---

### draw_accelerate（加速蓄力）

拉弓时每 tick 额外推进蓄力进度，让弓更早达到原版满弓。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `extra_ticks` | ScalingFunction | 否 | `1.0` | 每 tick 额外推进的蓄力 tick，向下取整，小于 1 不生效 |

```yaml
actions:
  accelerate:
    type: draw_accelerate
    extra_ticks:
      base: 1
      per_level: 1
```

**说明：**
- 只认弓（不含弩），配合 `RIGHT_CLICK` 触发器。
- 不改变客户端拉弓动画，只修改服务端的剩余使用时间；实际满弓时间约为 `20 / (1 + extra_ticks)` tick（20 tick 是原版满弓，不可配置）。
- 单独使用时，玩家提前松手也按加速后的进度计算箭速。
- 和 `auto_bow` 同时存在时，`auto_bow` 会更早判定满弓并自动发射。

---

### auto_bow（自动连射）

拉弓后每 tick 读取实际蓄力并计算 power，**拉满后才自动发射**，可选自动开始下一轮蓄力。不改变蓄力速度。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `refire` | boolean | 否 | `true` | 发射后自动重新开始拉弓 |
| `refire_delay` | int | 否 | `0` | 两次发射之间等待的 tick |
| `max_shots` | int | 否 | `0` | 最多连射次数，0 = 不限（直到松开右键） |
| `full_draw_ticks` | int | 否 | `20` | 实际蓄力达到这么多 tick 才发射；原版弓 20 tick 满弓，改小会提前发射，箭速仍按实际蓄力计算 |

```yaml
actions:
  auto:
    type: auto_bow
    refire: true
    refire_delay: 0
    max_shots: 0
```

**说明：**
- 只认弓（不含弩），配合 `RIGHT_CLICK` 触发器。
- power 用原版公式：`power = (t² + 2t) / 3`，`t = 已用 tick / full_draw_ticks`；只有 `power = 1.0` 才发射。
- 发射调用 NMS 的 `releaseUsingItem()`，保留原版箭矢消耗、无限、力量/冲击/火矢，并触发 `EntityShootBowEvent`（所以 `multi_arrow` 等 `BOW_SHOOT` 动作仍然生效）。
- 玩家松手、切物品/切副手、死亡、退出时任务自动停止。`refire_delay > 0` 时玩家在间隔内松手服务端可能感知不到，建议保持 0。

---

### repair（自动修复）

对主手、副手和四件盔甲里带此附魔、有损耗的物品逐件掷骰，掷中的修复耐久。通常配合 `PASSIVE` 触发器。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `amount` | ScalingFunction | 是 | -- | 每件物品每次修复的概率（0~1） |
| `exp_mode` | boolean | 否 | `false` | 每次修复扣经验，经验不足时停止 |
| `repair_amount` | int | 否 | `1` | 掷中后修复的耐久点数（不超过已损耗的量） |
| `exp_cost` | int | 否 | `1` | `exp_mode` 开启时每次修复扣的经验点 |

```yaml
actions:
  mending:
    type: repair
    amount:
      base: 0.4
      per_level: 0.2
    exp_mode: true
```

---

### hook（拉拽）

把第一个目标以抛射角拉向自己。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `power` | ScalingFunction | 是 | -- | 拉拽力度 |
| `min_distance` | double | 否 | `1.0` | 目标离自己不超过它时不拉 |
| `full_power_distance` | double | 否 | `5.0` | 不近于它时用满力度；更近时力度 = power ÷ (满力度距离 − 距离)² |
| `launch_angle_degrees` | double | 否 | `45.0` | 拉拽仰角 |

```yaml
actions:
  pull:
    type: hook
    power:
      base: 1.0
      per_level: 0.5
```

---

### feast（恢复饥饿）

饥饿值未满时按概率恢复饥饿值和饱和度。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `amount` | ScalingFunction | 是 | -- | 触发概率（0~1） |
| `food` | int | 否 | `1` | 恢复的饥饿值（上限 20） |
| `saturation` | double | 否 | `1.0` | 恢复的饱和度，不超过恢复后的饥饿值 |

```yaml
actions:
  feed:
    type: feast
    amount:
      base: 0.2
      per_level: 0.2
```

---

### deflect（偏转弹射物）

被弹射物击中时按概率取消这次伤害，并把弹射物以原速率随机弹开（在反方向基础上水平、竖直随机旋转）。需要 `TAKE_DAMAGE` 触发器，只对玩家受到的弹射物伤害生效。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `amount` | ScalingFunction | 否 | `1.0` | 偏转概率（0~1） |
| `yaw_spread_degrees` | double | 否 | `360.0` | 水平随机旋转 0 到此角度 |
| `pitch_spread_degrees` | double | 否 | `30.0` | 竖直随机旋转 ±此角度 |

> milk.12 及以前插件不读 `amount`，偏转必定发生。milk.13 起 `amount` 生效：没写时仍是 1.0（必定偏转），内置 `deflection.yml` 写的是 `0.2 + 0.2/级`，即 20% / 40% / 60%。这是平衡改动；偏转附魔没有上线，不影响生产。

```yaml
actions:
  block:
    type: deflect
    amount:
      base: 0.2
      per_level: 0.2
```

---

### till（范围方块转换）

右键方块时，在半径内把指定方块转换为另一种方块，只处理点击方块所在的那一层。默认行为是“用锄头耕地”，每个附魔都可以用 `transforms` 声明自己的转换表。Folia 下只修改当前区域拥有的方块。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `radius` | ScalingFunction（整数） | 是 | -- | 正方形半径，0 = 只处理点击的方块；超过 4 时编辑器会提示 |
| `transforms` | map | 否 | -- | 源方块规格 → 目标方块规格，见下方 DSL |
| `inherit_defaults` | boolean | 否 | `true` | 是否并入原版耕地表（泥土/草方块/灰化土/菌丝 → 耕地，砂土/缠根泥土 → 泥土） |
| `require_item_types` | list | 否 | -- | 允许触发的主手物品材质，如 `[IRON_HOE, DIAMOND_HOE]`；也可写单个值 |
| `require_item_tags` | list | 否 | -- | 允许触发的物品标签，如 `[minecraft:hoes]`；也可写单个值 |
| `consume_durability` | boolean | 否 | `true` | 每转换一个方块是否扣耐久 |
| `durability_per_block` | int | 否 | `1` | 每转换一个方块扣的耐久（受耐久附魔影响） |
| `protection_check` | boolean | 否 | `true` | 每个方块先发一次 `PlayerInteractEvent`，被保护插件取消的跳过 |
| `cancel_vanilla` | boolean | 否 | `true` | 点击的方块在表里或发生了转换时取消原版右键，避免重复处理 |
| `drop_roots` | boolean | 否 | `true` | 缠根泥土被转换时掉落垂根（与原版一致） |
| `apply_physics` | boolean | 否 | `true` | 写入方块时是否触发方块/红石更新 |

> 不写 `require_item_types` / `require_item_tags` 时沿用旧行为：主手物品名必须包含 `HOE`。

#### `transforms` DSL

键 = **源规格**，值 = **目标规格**，两端都支持可选的方块状态段：

```text
源规格   := (材质 | "#标签") [ "[" 状态列表 "]" ]
目标规格 := 材质 [ "[" 状态列表 "]" ]
状态列表 := 条目 ("," 条目)*
条目     := 属性=值           # 两端都可用
          | same             # 仅目标侧，same 生成器
```

**源侧：子集匹配**

| 写法 | 含义 |
| --- | --- |
| `ICE` | 匹配该材质任意状态（旧行为） |
| `WATER[level=0]` | 只匹配 `level=0`，其余属性不参与比较 |
| `OAK_STAIRS[facing=east, half=top]` | 多属性 AND |
| `#minecraft:wooden_stairs[facing=east]` | 标签展开成多个材质，每个都按该状态约束匹配 |

未写出的属性不参与匹配；属性名/属性值由服务端解析器校验，写错在配置加载时直接报错。

**目标侧：默认状态 / 显式状态 / `same` 生成器**

| 写法 | 目标 BlockData |
| --- | --- |
| `EXPOSED_COPPER` | 目标默认状态（等价旧版本的 `setType`） |
| `OAK_SLAB[type=top]` | 显式状态，未写出的属性取目标默认值 |
| `EXPOSED_CUT_COPPER_STAIRS[same]` | 拷贝源方块中「目标也拥有」的同名属性 |
| `COPPER_BULB[same, lit=false]` | 先 `same` 拷贝，再被字面条目覆盖（字面永远优先） |

`same` 生成器 = 纯 Bukkit 版的 `Block#withPropertiesOf`：

- 按**属性名**求交集拷贝（源有 `facing/half/shape/waterlogged`，目标也有 → 全部搬过去）；
- 交集外的源属性静默丢弃，源的缺省属性使用目标默认值；
- 生成的 BlockData 若被服务端拒绝，先降级为「只应用字面条目」，仍失败才回退目标默认状态，并对该 `(源→目标)` 组合只告警一次。

`same` 只能出现在**目标侧**；写在源侧会在加载期报错。

**优先级（同一材质内）**

1. 状态约束更多的规则优先；
2. 约束数量相同时，`transforms` 里显式写的规则优先于 `inherit_defaults` 默认表；
3. 仍然相同则按 YAML 声明顺序，先声明者优先。

命中第一条即停止，不做链式跳转。因此下面这段可以共存：`level=0` 的水结冰，其余状态的水不变。

```yaml
transforms:
  "WATER[level=0]": ICE
  WATER: WATER
```

> 多个附魔可以各自维护一张 `transforms` 表。**建议每个附魔的表互不重叠**：运行时按附魔逐个执行，
> 重叠时“先改到的生效”，而附魔遍历顺序不保证稳定；插件会在启动和 `/enchants reload` 时对状态约束相容的重叠项打印警告。

```yaml
# 默认耕地行为
actions:
  farm:
    type: till
    radius:
      base: 1
      per_level: 1
```

```yaml
# 自定义转换：右键把冰块变成水、黑曜石变成熔岩
actions:
  entropy:
    type: till
    radius: 0
    inherit_defaults: false
    transforms:
      ICE: WATER
      OBSIDIAN: LAVA
    consume_durability: true
```

```yaml
# 状态感知：只处理静止水；换楼梯木种时保留朝向/半格/水面
actions:
  work:
    type: till
    radius: 0
    inherit_defaults: false
    transforms:
      "WATER[level=0]": ICE
      "#minecraft:wooden_stairs": "OAK_STAIRS[same]"
      CUT_COPPER_STAIRS: "EXPOSED_CUT_COPPER_STAIRS[same]"
      COPPER_BULB: "EXPOSED_COPPER_BULB[same, lit=false]"
    require_item_tags: [minecraft:axes]
```

---

### reflect_damage（伤害反弹）

受到近战伤害时按概率取消这次伤害，并对攻击者造成伤害。只认近战（攻击者是生物，弹射物不算）。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `percentage` | ScalingFunction | 是 | -- | 反弹概率（0~1） |
| `damage_ratio` | double | 否 | `1.0` | 对攻击者造成的伤害 = 本次伤害 × 比例 |

```yaml
actions:
  reflect:
    type: reflect_damage
    percentage:
      base: 0.10
      per_level: 0.05
```

---

### block_break（瞬间破坏方块）

左键方块时直接破坏它（配合 `BLOCK_DIG` 触发器），先发一次 `BlockBreakEvent` 供保护插件拦截；掉落物考虑时运和精准采集，身上有 `vacuum_drops` 时直接进背包。硬度小于 0 的方块不处理。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `target_types` | list | 否 | `[]` | 可破坏的方块：材质名（如 `GLASS`）、方块标签（含冒号，如 `minecraft:impermeable`）或 `includes:片段`（材质名包含该片段）；不写不限 |
| `durability` | int | 否 | `1` | 每破坏一个方块扣工具的耐久（受耐久附魔影响） |
| `conditions.sneaking` | boolean | 否 | `false` | 需要潜行 |

```yaml
actions:
  shatter:
    type: block_break
    target_types:
      - "includes:glass"
```

---

### command（命令）

以玩家或控制台身份执行命令。**编辑器不开放这个动作**（`run_as: CONSOLE` 等于控制台权限），插件仍照常执行已有配置里的 `command`。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `command` | string | 是 | -- | 要执行的命令（不含前导 `/`），支持下表占位符和 PlaceholderAPI |
| `run_as` | string | 否 | `PLAYER` | 执行者：`PLAYER` 或 `CONSOLE` |

| 占位符 | 替换为 |
| --- | --- |
| `{player}` | 玩家名称 |
| `{target}` | 目标实体名称（玩家为名字，其它为小写实体类型；没有目标时为空） |
| `{level}` | 附魔等级 |

```yaml
actions:
  reward:
    type: command
    run_as: CONSOLE
    command: "give {player} diamond 1"
```

`PLAYER` 用玩家自己的权限执行；命令返回失败时该动作也算失败。

---

## 附魔级音效与粒子

`sound` / `particle` 与 `trigger`、`target` 同级，不属于 `actions`：

```yaml
sound:
  type: block.lava.extinguish   # 原版音效注册名
  volume: 1.0                   # 默认 1.0
  pitch: 1.2                    # 默认 1.0
  location: BLOCK               # SELF（默认）/ TARGET / BLOCK

particle:
  type: cloud                   # 原版粒子注册名或 Bukkit 枚举名
  count: 12                     # 默认 10
  spread: 0.4                   # 默认 0.5
  speed: 0.05                   # 默认 0.1
  location: BLOCK               # 默认 SELF
  data:                         # 需要额外数据的粒子：material / color / to_color / size（默认 1.0）/ value
    material: "minecraft:nether_wart_block"
```

- `location` 语义：`SELF` = 玩家位置，`TARGET` = 第一个目标的位置，`BLOCK` = 本次交互/破坏的方块中心；取不到时退回玩家位置。粒子再上移 1 格生成。
- 音效使用 `Player#playSound(Location, ...)`：**只向触发者发包**，但声源是 `location` 指定的世界坐标。
- 音效与粒子只在本次动作全部成功执行后播放。不认识的音效或粒子名在加载时告警，不播放。
