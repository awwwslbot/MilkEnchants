# TODO: 表达式引擎 —— action / effect 分层与概率包装

> 状态: 设计草案 (未实现)
> 分支: `feature/expression-engine`
> 说明: 本分支仅为占位与设计记录，暂不改动运行时代码。

## 1. 背景 / 现状

- 配置里 `actions:` 是一组**固定类型**的动作节点，节点里的 `type` 直接映射到 22 个 Java 实现。
- `ActionRegistry` 中硬编码 22 个工厂；接口为 `Action#execute(ActionContext): boolean`。
- **没有表达式、没有组合、没有条件/概率 DSL**；唯一的"计算"是 `ScalingFunction`（`fixed` / `linear`）。
- `chance`（概率）**散落在少数 effect 内部**（`drop_head` / `repair` / `feast` / `deflect` / `reflect_damage`），
  其它 effect（尤其 `potion_effect`）**没有概率能力** —— 这正是"淬毒附魔无法做概率版"的根因。

## 2. 目标

1. 引入**表达式引擎**：未来的 `action` 本身是一个**表达式**，可组合、可嵌套。
2. 把现在这 22 个动作的语义**改名为 `effect`**：`effect` = 叶子节点，表示一个具体效果。
3. 把 `chance` 从各 effect 内部**抽离**，统一为一个**包装节点** `PossibilityAction`：
   按概率决定是否执行其内部表达式/effect。

## 3. 术语与命名

| 旧 | 新 | 含义 |
|---|---|---|
| `Action`（22 个实现） | `Effect`（22 个实现，改名 `XxxEffect`） | 具体效果叶子节点 |
| `ActionContext` | （保留/更名 `EffectContext`?） | 运行期上下文 |
| `ActionRegistry` | `EffectRegistry`（+ 新的表达式节点注册表） | 类型 → 工厂 |
| —— | `Action`（新） | **表达式节点基类/接口**，可组合 |
| —— | `PossibilityAction` | 概率包装节点（用户原拼写 `PosibilityAction`） |

配置层：
- 现状：`actions: { <key>: { type: <effect>, ... } }`
- 目标：`action:`（表达式树），其中 `effect` 叶子写 `effect: <name>`；
  旧的 `type:` 视为 effect 的兼容写法。

## 4. 表达式模型（草案）

节点种类（示意，语法待定）：

- `Effect`（叶子）：`{ effect: potion_effect, ... }`
- `PossibilityAction`（概率门）：`{ possibility: { chance: ScalingFunction, action: <Action> } }`
- `Sequence` / `All`：顺序/全部
- `Any` / `Or`：任一
- `Condition`：`{ if: <predicate>, then: <Action>, else?: <Action> }`
- 变量 / 占位符：如 `{level}`、`{damage}` 等（待定义）

示意 YAML（**非最终语法**）：

```yaml
# 淬毒(概率版) 期望形态
action:
  quench:
    possibility:
      chance: { base: 0.25, per_level: 0.05 }
      action:
        effect: potion_effect
        effect_type: poison
        duration: { base: 60, per_level: 40 }
        amplifier: { base: 0, per_level: 0 }
```

## 5. 里程碑 (Checklist)

- [ ] 拆分接口：`Action`(表达式) 与 `Effect`(叶子)
- [ ] 现有 22 个 `Action` 实现重命名为 `XxxEffect`，改实现 `Effect`
- [ ] `ActionRegistry` → `EffectRegistry`；新增表达式节点注册表
- [ ] 从 `drop_head` / `repair` / `feast` / `deflect` / `reflect_damage` 中**移除内部 chance**
- [ ] 实现 `PossibilityAction`（统一概率门，作用于任意子表达式）
- [ ] 实现表达式 AST + 解析器（从 YAML → 节点树）
- [ ] `EnchantConfig` / `EnchantConfigLoader` / `EnchantTriggerHelper` 适配新 schema
- [ ] YAML schema 版本号 + 迁移工具（旧 `actions/type` → 新 `action/effect`）
- [ ] 更新 `docs/action_types_{zh,en}.md`（区分 action / effect）
- [ ] 迁移内置 35 个 `enchants/*.yml` 示例

## 6. 影响面

- 代码：`actions/Action.java`、`ActionContext.java`、`ActionRegistry.java`、`ActionLoader.java`、
  `actions/impl/*`（22 个）、`config/EnchantConfig*.java`、`listeners/EnchantTriggerHelper.java`、
  `AttributeModifierHelper.java`、`gui/EnchantBrowser.java`
- 配置：全部 `enchants/*.yml`（35 个）+ 用户自定义附魔（如 `quench_poison.yml`）
- 文档：`README.md` / `README_EN.md` / `docs/*`
- **破坏性变更**：是（需迁移；建议支持旧 schema 一段时间）

## 7. 待决问题

1. 表达式 YAML 语法：嵌套 map vs 内联 DSL 字符串 vs 两者兼有？
2. 向后兼容：是否保留 `actions:` + `type:` 作为 `effect` 的兼容写法？版本号怎么标？
3. `PosibilityAction` 拼写：正式名用 `PossibilityAction`（正确拼写），是否需要别名兼容？
4. 概率门的短路语义：未命中时返回 `true`（继续后续）还是 `false`（中断）？—— 现有 `boolean` 返回值的"false=中断"语义需一并重审。
5. 性能：目前**每次触发都重建 Action**（无缓存），引入表达式树后是否要做解析缓存？
6. 控件：`condition` / 变量系统是否需要，还是先把 `possibility` + `sequence` 做出来即可？
