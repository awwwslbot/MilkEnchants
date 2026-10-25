# 急速（quick_draw）

附魔 ID 为 `meowsenchants:quick_draw`，只适用于弓，最高三级。拉弓时服务端每 tick 额外推进蓄力进度，让弓更早达到满蓄力；客户端的拉弓动画不变。

## 规则

| 等级 | 每 tick 额外推进 | 满蓄力约需 |
| --- | --- | --- |
| I | 1 tick | 10 tick（0.5 秒） |
| II | 2 tick | 约 7 tick（0.35 秒） |
| III | 3 tick | 5 tick（0.25 秒） |

原版满蓄力为 20 tick（1 秒），秒数按 20 TPS 换算。额外推进量为 `extra_ticks.base + per_level × (等级 − 1)`，满蓄力约需 `20 / (1 + 额外推进)` tick。

- 配置驱动：`trigger: RIGHT_CLICK` 加动作 `draw_accelerate`，参数说明见 [action_types_zh.md「draw_accelerate」](../action_types_zh.md)。
- 右键时主手或副手是带本附魔的弓才会启动；只对弓生效，不含弩。
- 推进不会越过满蓄力点。玩家提前松手时，按加速后的进度计算箭速。
- 与连射（`rapid_fire`）同时附魔时，连射会更早看到满蓄力并自动发射；急速的会话跨越连射的每一轮持续生效。

## 实现与维护

- 代码：`actions/impl/DrawAccelerateAction.java`（启动条件）、`listeners/DrawAccelerateManager.java`（逐 tick 用 `setActiveItemRemainingTime` 调小服务端剩余使用时间，跑在玩家的实体调度器上）、`listeners/DrawAccelerateListener.java`（停止条件）。
- 停止条件刻意不监听 `PlayerStopUsingItemEvent`：连射自动发射时会触发该事件，若在那里停止，下一轮就不再加速。玩家真正松手后，任务在下一 tick 因手上没有正在使用的物品而自行结束。
- 每 tick 的推进量向下取整，`extra_ticks` 小于 1 的配置不生效。

## 配置和获得方式

附魔台成本 30、概率 1%，村民交易关闭，铁砧基础开销 3，无冷却。

## 历史与测试

- 0.2-milk.6 引入（`c821af4`）。
- 目前没有单元测试。改动后在本地测试服手动验证：各等级的满蓄力时间、提前松手的箭速、与连射叠加时每一轮都加速。
