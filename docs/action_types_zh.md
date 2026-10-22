# 动作类型

所有动作类型可在附魔YAML配置文件中使用。每个动作定义在 `actions` 部分下，包含唯一键和 `type` 字段。

```yaml
actions:
  my_key:
    type: action_type_name
    # ... 参数
```

#### 通用 `exclude` 参数

任何动作类型均支持可选的 `exclude` 列表。若玩家的当前状态匹配列表中的任意条目，则该动作在此次触发中完全跳过。

```yaml
actions:
  my_key:
    type: multi_break
    exclude:
      - sneaking
      - sprinting
    # ... 其他参数
```

| 值         | 跳过条件               |
| ---------- | ---------------------- |
| `sneaking` | `player.isSneaking`   |
| `sprinting`| `player.isSprinting`  |
| `swimming` | `player.isSwimming`   |
| `flying`   | `player.isFlying`     |
| `gliding`  | `player.isGliding`    |

---

### potion_effect（药水效果）

对目标施加药水效果。

| 参数        | 类型            | 必须 | 默认值 | 描述                                       |
| ----------- | --------------- | ---- | ------ | ------------------------------------------ |
| `effect`    | string          | 是   | --     | 药水效果类型（小写，如 `poison`, `strength`, `slowness`） |
| `duration`  | ScalingFunction | 是   | --     | 持续时间（刻，20刻 = 1秒）                |
| `amplifier` | ScalingFunction | 是   | --     | 效果等级（0 = I级，1 = II级，依此类推）   |
| `particles` | boolean         | 否   | `true` | 是否显示粒子效果                           |
| `icon`      | boolean         | 否   | `true` | 是否在HUD上显示效果图标                    |

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
    particles: true
    icon: true
```

---

### bonus_damage（额外伤害）

造成额外伤害或对现有伤害进行倍率加成。

| 参数                         | 类型            | 必须 | 默认值  | 描述                                         |
| ---------------------------- | --------------- | ---- | ------- | -------------------------------------------- |
| `amount`                     | ScalingFunction | 是   | --      | 伤害值（若 `multiplier` 为 true，则为倍率） |
| `multiplier`                 | boolean         | 否   | `false` | 若为 true，`amount` 作为现有伤害的倍率       |
| `conditions.sneaking`        | boolean         | 否   | `false` | 仅当潜行时激活                               |
| `conditions.night_time`      | boolean         | 否   | `false` | 仅当夜晚时激活                               |
| `conditions.low_health`      | double          | 否   | `0.0`   | 仅当玩家生命值低于此值时激活                 |
| `conditions.dimension`       | string          | 否   | --      | 仅当在此维度中激活（如 `the_nether`）       |
| `conditions.target_types`    | list            | 否   | --      | 仅对指定实体类型生效                         |
| `conditions.passive_mobs`    | boolean         | 否   | `false` | 仅对被动生物生效                             |

```yaml
actions:
  extra_dmg:
    type: bonus_damage
    amount:
      base: 2.0
      per_level: 1.0
    multiplier: false
    conditions:
      night_time: true
```

---

### healthsteal（生命偷取）

从目标偷取生命值并治疗玩家。

| 参数     | 类型            | 必须 | 默认值 | 描述                         |
| -------- | --------------- | ---- | ------ | ---------------------------- |
| `amount` | ScalingFunction | 是   | --     | 偷取伤害的比例（如 0.10 = 10%） |

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

将玩家向视线方向传送。
插件会从最大距离向玩家方向回退寻找安全落点，落点需要有可站立方块且脚部、头部空间可通行。

| 参数    | 类型            | 必须 | 默认值 | 描述               |
| ------- | --------------- | ---- | ------ | ------------------ |
| `range` | ScalingFunction | 是   | --     | 最大传送距离（格） |

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

在目标位置生成爆炸。

| 参数           | 类型            | 必须 | 默认值  | 描述         |
| -------------- | --------------- | ---- | ------- | ------------ |
| `power`        | ScalingFunction | 是   | --      | 爆炸威力     |
| `fire`         | boolean         | 否   | `false` | 是否引发火焰 |
| `break_blocks` | boolean         | 否   | `false` | 是否破坏方块 |

```yaml
actions:
  boom:
    type: explosion
    power:
      base: 2.0
      per_level: 1.0
    fire: false
    break_blocks: false
```

---

### velocity（速度）

对玩家或目标施加速度（击退/发射）。
`onGround` 默认启用。启用时，触发者必须站在地面或距离支撑方块不超过约一格；
关闭后允许在空中执行。`SELF` 和 `TARGET` 都会检查触发者的地面状态。

| 参数             | 类型            | 必须 | 默认值  | 描述                                                         |
| ---------------- | --------------- | ---- | ------- | ------------------------------------------------------------ |
| `direction`      | string          | 是   | --      | 方向：`UP`, `DOWN`, `FORWARD`, `BACKWARD`, `LOOK`, `AWAY`    |
| `power`          | ScalingFunction | 是   | --      | 速度大小                                                     |
| `apply_to`       | string          | 否   | `SELF`  | 作用于谁：`SELF` 或 `TARGET`                                |
| `onGround`       | boolean         | 否   | `true`  | 是否要求触发者接近地面                                     |
| `no_fall_damage` | boolean         | 否   | `false` | 取消本次发射导致的摔落伤害                                   |

**方向说明：**

* `UP` / `DOWN` — 绝对垂直方向
* `FORWARD` / `BACKWARD` — 沿玩家视线水平方向
* `LOOK` — 同 `FORWARD`
* `AWAY` — 远离目标（需要已解析的目标）

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

将附近实体推离玩家。目标的 `minecraft:knockback_resistance` 最终属性值会降低冲击动量：
属性值从 0 到 1 线性对应 0% 到 50% 的减免。

| 参数     | 类型            | 必须 | 默认值 | 描述           |
| -------- | --------------- | ---- | ------ | -------------- |
| `radius` | ScalingFunction | 是   | --     | 影响半径（格） |
| `power`  | ScalingFunction | 是   | --     | 击退力量       |

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

随机损坏目标玩家的一件盔甲。

| 参数     | 类型            | 必须 | 默认值 | 描述               |
| -------- | --------------- | ---- | ------ | ------------------ |
| `damage` | ScalingFunction | 是   | --     | 耐久度伤害值       |

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

掉落被击杀实体的头颅。

| 参数     | 类型            | 必须 | 默认值 | 描述           |
| -------- | --------------- | ---- | ------ | -------------- |
| `amount` | ScalingFunction | 是   | --     | 掉落概率（0~1）|

```yaml
actions:
  head:
    type: drop_head
    amount:
      base: 0.01
      per_level: 0.01
```

---

### attribute_modifier（属性修饰符）

在装备物品时持续应用属性修饰符。通常与 `PASSIVE` 触发器配合使用。

| 参数         | 类型            | 必须 | 默认值 | 描述                                                                                             |
| ------------ | --------------- | ---- | ------ | ------------------------------------------------------------------------------------------------ |
| `attribute`  | string          | 是   | --     | 属性名称：`MAX_HEALTH`, `MOVEMENT_SPEED`, `ATTACK_DAMAGE`, `JUMP_STRENGTH`, `KNOCKBACK_RESISTANCE`, `ARMOR_TOUGHNESS` |
| `amount`     | ScalingFunction | 是   | --     | 修饰数值                                                                                         |
| `operation`  | string          | 是   | --     | 运算方式：`ADD_NUMBER`, `ADD_SCALAR`, 或 `MULTIPLY_SCALAR_1`                                      |
| `key`        | string          | 是   | --     | 唯一修饰键                                                                                       |

```yaml
actions:
  speed_boost:
    type: attribute_modifier
    keep_old_attribute: true
    attribute: "MOVEMENT_SPEED"
    amount:
      base: 0.02
      per_level: 0.01
    operation: "ADD_NUMBER"
    key: "speed"
```

**注意：** 卸载物品时修饰符会自动移除。

---

### multi_break（连锁挖掘）

围绕挖掘的方块，在方形半径内连锁破坏方块（3x3、5x5 图案）。

| 参数        | 类型            | 必须 | 默认值 | 描述                                                       |
| ----------- | --------------- | ---- | ------ | ---------------------------------------------------------- |
| `radius`    | ScalingFunction | 是   | --     | 破坏半径                                                   |
| `exclude`   | list            | 否   | `[]`   | 跳过动作的玩家状态（参见通用 `exclude` 参数）              |
| `conditions.sneaking` | boolean | 否   | `false` | 仅当潜行时激活                               |

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

### vacuum_drops（真空收集）

取消自然方块掉落，并将掉落物直接送入玩家背包。放不下的物品掉落在方块位置。仅适用于 `BLOCK_BREAK` 触发器。

无参数。

```yaml
actions:
  collect:
    type: vacuum_drops
```

---

### replant（自动补种）

收获后自动补种作物。适用于小麦、胡萝卜、马铃薯、甜菜根和地狱疣。

| 参数         | 类型    | 必须 | 默认值  | 描述                                             |
| ------------ | ------- | ---- | ------- | ------------------------------------------------ |
| `fertilizer` | boolean | 否   | `false` | 若为 true，背包有至少5个骨粉时消耗骨粉并补种为成熟作物 |

```yaml
actions:
  plant:
    type: replant
    fertilizer: true
```

---

### multi_arrow（多重箭矢）

在主箭旁额外射出多支箭矢。
需要配合 `BOW_SHOOT` 触发器使用，弓与弩均可生效；额外箭矢会继承原箭矢的暴击、穿透、
着火、药水效果以及发射所用的武器，从而保留力量、火矢等附魔带来的加成。

| 参数           | 类型            | 必须 | 默认值 | 描述               |
| -------------- | --------------- | ---- | ------ | ------------------ |
| `extra_arrows` | ScalingFunction | 是   | --     | 额外箭矢数量       |
| `spread`       | ScalingFunction | 是   | --     | 散布角度（度）     |

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

拉弓时每 tick 额外推进蓄力进度，让弓更早达到原版 power 满值。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `extra_ticks` | ScalingFunction | 否 | `1.0` | 每 tick 额外推进的蓄力 tick 数 |

```yaml
actions:
  accelerate:
    type: draw_accelerate
    extra_ticks:
      base: 1
      per_level: 1
```

**说明：**
- 仅对弓生效（不含弩），需要配合 `RIGHT_CLICK` 触发器。
- 不改变客户端拉弓动画，只修改服务端的剩余使用时间；实际满蓄力时间约为 `20 / (1 + extra_ticks)` tick。
- 单独使用时，玩家提前松手也会按加速后的进度计算箭速。
- 与 `auto_bow` 同时存在时，`auto_bow` 会更早看到 `power = 1.0` 并自动发射。

---

### auto_bow（自动连射）

拉弓后每 tick 读取实际蓄力进度并计算弓的 power，**只有拉满（原版 20 tick）后才自动发射**；可选自动开始下一轮蓄力。该动作不改变蓄力速度。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `refire` | boolean | 否 | `true` | 发射后是否自动开始下一轮 |
| `refire_delay` | int | 否 | `0` | 两轮之间等待的 tick |
| `max_shots` | int | 否 | `0` | 最大射击次数，0 = 不限 |

```yaml
actions:
  auto:
    type: auto_bow
    refire: true
    refire_delay: 0
    max_shots: 0
```

**说明：**
- 仅对弓生效（不含弩），需要配合 `RIGHT_CLICK` 触发器。
- power 使用原版公式：`power = ((t² + 2t) / 3)`，`t = 已用 tick / 20`；只有 `power = 1.0` 才发射。
- 不会加速拉弓、不会修改剩余使用时间，纯粹是“拉满后自动松手”。
- 发射调用 `releaseUsingItem()`（NMS），保留原版箭矢消耗、Infinity、Power/Punch/Flame，并触发 `EntityShootBowEvent`（因此 `multi_arrow` 等 `BOW_SHOOT` 动作仍然生效）。
- 玩家松手、切物品/切副手、死亡、退出时任务自动停止。`refire_delay > 0` 时若玩家在间隔内松手，服务端可能无法感知，建议保持 0。

---

### repair（修复）

恢复物品的耐久度。

| 参数     | 类型            | 必须 | 默认值 | 描述                       |
| -------- | --------------- | ---- | ------ | -------------------------- |
| `amount` | ScalingFunction | 是   | --     | 每次触发修复1点耐久的概率（0~1） |

```yaml
actions:
  mending:
    type: repair
    amount:
      base: 0.4
      per_level: 0.2
    exp_mode: true
```

`exp_mode`（布尔，可选，默认 `false`）：若为 `true`，每次成功修复消耗1点经验值。

---

### hook（钩爪）

将目标实体拉向玩家（抓钩）。

| 参数    | 类型            | 必须 | 默认值 | 描述       |
| ------- | --------------- | ---- | ------ | ---------- |
| `power` | ScalingFunction | 是   | --     | 拉力强度   |

```yaml
actions:
  pull:
    type: hook
    power:
      base: 1.0
      per_level: 0.5
```

---

### feast（饱食）

恢复玩家的饥饿度和饱和度。

| 参数     | 类型            | 必须 | 默认值 | 描述                           |
| -------- | --------------- | ---- | ------ | ------------------------------ |
| `amount` | ScalingFunction | 是   | --     | 每次触发恢复1点饥饿值的概率（0~1） |

```yaml
actions:
  feed:
    type: feast
    amount:
      base: 0.2
      per_level: 0.2
```

---

### deflect（偏转）

偏转来袭的弹射物（箭矢等）。无参数。配合 `TAKE_DAMAGE` 触发器使用。

```yaml
actions:
  block:
    type: deflect
    amount:
      base: 0.2
      per_level: 0.2
```

`amount`（ScalingFunction，必须）：偏转概率（0~1）。

---

### gravity（引力）

将附近实体拉向玩家。

| 参数     | 类型            | 必须 | 默认值 | 描述       |
| -------- | --------------- | ---- | ------ | ---------- |
| `radius` | ScalingFunction | 是   | --     | 作用半径   |
| `power`  | ScalingFunction | 是   | --     | 拉力强度   |

```yaml
actions:
  pull:
    type: gravity
    radius:
      base: 5
      per_level: 2
    power:
      base: 0.5
      per_level: 0.2
```

---

### till（方块转换）

右键方块时，在半径内把指定方块转换为另一种方块。默认行为是“用锄头耕地”，
但每个附魔都可以通过 `transforms` 声明自己独立的转换表。

| 参数 | 类型 | 必须 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `radius` | ScalingFunction | 是 | -- | 作用半径，0 = 只处理点击的方块 |
| `transforms` | map | 否 | -- | 源方块 → 目标方块；键支持 `#minecraft:dirt` 形式的方块标签 |
| `inherit_defaults` | boolean | 否 | `true` | 是否在原版耕地表（泥土/草方块/灰化土/菌丝→耕地，砂土/缠根泥土→泥土）基础上叠加 |
| `require_item_types` | list | 否 | -- | 允许触发的物品材质，如 `[IRON_HOE, DIAMOND_HOE]` |
| `require_item_tags` | list | 否 | -- | 允许触发的物品标签，如 `[minecraft:hoes]` |
| `consume_durability` | boolean | 否 | `true` | 每转换一个方块是否消耗 1 点耐久 |
| `protection_check` | boolean | 否 | `true` | 是否为每个方块模拟 `PlayerInteractEvent` 供保护插件拦截 |
| `cancel_vanilla` | boolean | 否 | `true` | 至少转换一个方块后是否取消原版右键 |
| `drop_roots` | boolean | 否 | `true` | 缠根泥土 → 泥土时是否掉落垂根 |

> 不写 `require_item_types` / `require_item_tags` 时沿用旧行为：主手物品名必须包含 `HOE`。
>
> 多个附魔可以各自维护一张 `transforms` 表。**建议每个附魔的表互不重叠**：运行时按附魔逐个执行，
> 重叠时“先改到的生效”，而附魔遍历顺序不保证稳定；插件会在 `onEnable` 时对重叠项打印警告。

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

---

### reflect_damage（伤害反弹）

完全反弹近战伤害给攻击者。

| 参数         | 类型            | 必须 | 默认值 | 描述                   |
| ------------ | --------------- | ---- | ------ | ---------------------- |
| `percentage` | ScalingFunction | 是   | --     | 反弹伤害的概率（0~1）  |

```yaml
actions:
  reflect:
    type: reflect_damage
    percentage:
      base: 0.10
      per_level: 0.05
```

**注意：** 仅对近战伤害有效（需要 `EntityDamageByEntityEvent`）。

---

### command（命令）

以玩家或控制台身份运行服务器命令。

| 参数      | 类型   | 必须 | 默认值   | 描述                                       |
| --------- | ------ | ---- | -------- | ------------------------------------------ |
| `command` | string | 是   | --       | 要运行的命令（不含前导 `/`）。支持占位符。 |
| `run_as`  | string | 否   | `PLAYER` | 运行者：`PLAYER` 或 `CONSOLE`              |

**占位符：**

| 占位符      | 替换为                                   |
| ----------- | ---------------------------------------- |
| `{player}`  | 玩家名称                                 |
| `{target}`  | 目标实体名称（若无则为空字符串）         |
| `{level}`   | 附魔等级                                 |

```yaml
actions:
  announce:
    type: command
    run_as: CONSOLE
    command: "say {player} triggered the enchant at level {level}!"
```

```yaml
actions:
  reward:
    type: command
    run_as: CONSOLE
    command: "give {player} diamond 1"
```

**注意：** `PLAYER` 使用玩家自己的权限运行命令。如需管理员权限请使用 `CONSOLE`。

---

### send_message（发送消息）

向玩家或附近玩家发送 MiniMessage 格式的消息。

| 参数      | 类型   | 必须 | 默认值 | 描述                                                               |
| --------- | ------ | ---- | ------ | ------------------------------------------------------------------ |
| `message` | string | 是   | --     | MiniMessage 文本。支持 `{player}`, `{target}`, `{level}` 占位符。  |
| `radius`  | double | 否   | `0.0`  | 若 > 0，则广播给半径内所有玩家。                                   |

```yaml
actions:
  notify:
    type: send_message
    message: "<gold>{player}</gold> <gray>击中了</gray> <red>{target}</red> <gray>，附魔等级为 {level}！"
    radius: 0.0
```

**说明：**
- `{player}` 替换为附魔使用者的名称。
- `{target}` 替换为目标实体名称，若无则为空字符串。
- `{level}` 替换为附魔等级。
- 当 `radius > 0` 时，半径内所有在线玩家都会收到消息。

---

### durability（耐久损耗）

按配置量损坏指定装备槽的物品。尊重原版 Unbreaking 附魔的耐久损耗计算。

| 参数     | 类型            | 必须 | 默认值   | 描述                                                             |
| -------- | --------------- | ---- | -------- | ---------------------------------------------------------------- |
| `amount` | ScalingFunction | 是   | --       | 耐久损耗量（最小为1）                                            |
| `slot`   | string          | 否   | `HAND`   | 装备槽：`HAND`, `MAIN_HAND`, `OFF_HAND`, `HEAD`, `CHEST`, `LEGS`, `FEET` |
| `exclude`| list            | 否   | `[]`     | 跳过动作的玩家状态（参见通用 `exclude` 参数）                    |

```yaml
actions:
  durability_cost:
    type: durability
    exclude:
      - sneaking
    amount:
      base: 1
      per_level: 1
    slot: HAND
```

**注意：**
- `HAND` 和 `MAIN_HAND` 均指主手。
- 耐久损耗受 Unbreaking 附魔影响，实际消耗可能因物品的 Unbreaking 等级而减少。
- 若槽位为空或物品无法损坏，则不生效。

---

## 附魔级音效与粒子

`sound` / `particle` 与 `trigger`、`target` 同级，不属于 `actions`：

```yaml
sound:
  type: block.lava.extinguish   # 音效注册表 key
  volume: 1.0
  pitch: 1.2
  location: BLOCK               # SELF（默认）/ TARGET / BLOCK

particle:
  type: cloud
  count: 12
  spread: 0.4
  speed: 0.05
  location: BLOCK
```

- `location` 语义：`SELF` = 玩家位置，`TARGET` = 目标实体位置，`BLOCK` = 本次交互/破坏的方块中心。
- 音效使用 `Player#playSound(Location, ...)`：**只向触发者发包**，但声源是 `location` 指定的世界坐标，因此是带方位感的本地音效。
- 音效与粒子只在本次动作全部成功执行后播放。
