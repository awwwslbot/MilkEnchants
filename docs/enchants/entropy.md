# 熵变（entropy）

附魔 ID 为 `meowsenchants:entropy`，只适用于锄头，只有一级。用主手的附魔锄头右键冰块，冰块变成水；右键黑曜石，黑曜石变成熔岩，同时在该方块处播放“刺啦”的蒸发声。

## 规则

- 配置驱动：`trigger: RIGHT_CLICK` 加动作 `till` 的自定义转换表，参数说明见 [action_types_zh.md「till」](../action_types_zh.md)。
  - `radius: 0`：只处理点击的那一个方块。
  - `inherit_defaults: false`：不叠加原版耕地表，泥土、草方块等不受影响。
  - `transforms`：`ICE: WATER`、`OBSIDIAN: LAVA`。
- 只认主手物品，且材质名需包含 `HOE`（未配置 `require_item_*`，沿用旧判定）。
- 每转换一个方块消耗 1 点耐久，走原生物品损伤 API，受耐久附魔和游戏模式规则影响。
- 转换前为该方块模拟一次 `PlayerInteractEvent`，供保护插件拦截（`protection_check`）。点击的方块在转换表里时取消原版右键，即使转换被保护插件拦下，原版也不会接着处理。
- 转换直接把目标方块写成水源或熔岩源，代码不检查维度：下界里冰也会变成水源，而原版在下界不会留下水。
- 音效 `block.lava.extinguish`（音量 1.0，音调 1.2），声源在被转换的方块上（`location: BLOCK`），只发给触发者；只有成功转换时才播放。

## 实现与维护

- 代码：`actions/impl/TillAction.java`（转换、保护检查、耐久）；音效位置由 `listeners/EnchantTriggerHelper.java` 的 `playEffects` 处理。
- Folia：只修改当前区域拥有的方块。
- 多个附魔的 `till` 转换表要互不重叠：重叠时运行结果取决于附魔遍历顺序，插件启动时会打印「till 转换表重叠」警告。新增使用 `till` 的附魔时避开 `ICE` 和 `OBSIDIAN`。

## 配置和获得方式

附魔台成本 30、概率 1%，村民交易关闭，铁砧基础开销 4，无冷却。

## 历史与测试

- 随 0.2-milk.1 引入：`till` 支持按附魔配置转换表时加入（`3c1494c`），随后加入蒸发音效（`9e3a3a3`）和音效位置（`522d193`）。
- 目前没有单元测试。改动后在本地测试服手动验证：冰和黑曜石的转换、保护区内被拦截、耐久消耗、音效只有自己听到。
