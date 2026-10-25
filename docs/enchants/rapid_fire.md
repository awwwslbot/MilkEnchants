# 连射（rapid_fire）

附魔 ID 为 `meowsenchants:rapid_fire`，只适用于弓，只有一级。玩家开始拉弓后，弓每次拉满（原版 20 tick）都由服务端自动松手发射，并立即开始下一轮蓄力；玩家松手、切换物品或副手、死亡、退出时停止。连射不改变蓄力速度；与急速（`quick_draw`）同时附在弓上时会更早拉满，射速随之提高。

## 规则

- 配置驱动：`trigger: RIGHT_CLICK` 加动作 `auto_bow`，参数说明见 [action_types_zh.md「auto_bow」](../action_types_zh.md)。默认配置为 `refire: true`、`refire_delay: 0`、`max_shots: 0`（不限次数）。
- 右键时主手或副手是带本附魔的弓才会启动。每个玩家同时最多一个连射会话，再次右键会重新开始。
- 每 tick 按原版公式 `power = (t² + 2t) / 3`（`t = 已蓄力 tick / 20`）计算 power，只有 power 达到 1.0 才发射。
- 发射走原版松手路径：照常消耗箭矢，应用无限、力量、冲击、火矢，并触发 `EntityShootBowEvent`。因此每一发都会触发 `BOW_SHOOT` 类动作，以及箭雨这类依赖射击事件的附魔。
- `max_level: 1`，描述注明超等级无收益。

## 实现与维护

- 代码：`actions/impl/AutoBowAction.java`（启动条件、power 公式）、`listeners/AutoBowManager.java`（逐 tick 会话，跑在玩家的实体调度器上）、`listeners/AutoBowListener.java`（停止条件）。
- 发射靠反射调用 NMS `LivingEntity#releaseUsingItem()`；Bukkit 的 `completeUsingActiveItem()` 走消耗品路径，对弓不会发射。服务端更新后如果这个方法改名或改签名，首次失败后连射整体静默失效，日志里只有一条「无法调用 releaseUsingItem」错误，升级服务端后先查这条日志。改为直接 NMS 调用的计划见 [TODO-nms-bridge.md](../TODO-nms-bridge.md)。
- 自动发射也会触发 `PlayerStopUsingItemEvent`。`AutoBowManager` 用 `firing` 标记忽略自己触发的那一次，只有玩家亲自松手才结束会话。
- `refire_delay` 保持 0：大于 0 时，玩家在两轮间隔内松手，服务端可能感知不到。

## 配置和获得方式

附魔台成本 30、概率 1%，村民交易关闭，铁砧基础开销 4，无冷却。

## 历史与测试

- 0.2-milk.2 引入（`de26150`）。milk.3–milk.5 多次调整蓄力判定，最终定为只在原版满蓄力时发射、不加速（`afd693e`）；milk.7 改用 `releaseUsingItem` 发射（`c0c65ea`）。
- 目前没有单元测试。改动后在本地测试服手动验证：持续连射、松手停止、切物品和切副手停止、普通箭与无限的消耗，以及与急速、箭雨同时附魔时的表现。
