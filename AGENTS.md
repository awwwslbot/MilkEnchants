# AGENTS.md

本仓库是 Project Milk 服务器使用的 MeowsEnchants 分支（上游 [WindsorMacmillan/MeowsEnchants](https://github.com/WindsorMacmillan/MeowsEnchants)），版本号为 `0.2-milk.N`，生产与测试运行在 Folia 26.1.2。上游提供通用的配置驱动附魔框架，Milk 在其上持续增加定制附魔。

## 附魔文档

- 每个 Milk 定制附魔在 `docs/enchants/<id>.md` 有一份文档，`<id>` 与 `src/main/resources/enchants/<id>.yml` 同名。它是该附魔规则、配置迁移、维护要点和测试方法的唯一权威说明：改某个附魔前先读它的文档，新增附魔时同时新建一份。
- `docs/` 根目录放上游的通用文档：配置驱动附魔可用的动作类型见 `docs/action_types_zh.md` 和 `docs/action_types_en.md`。
- 新增或修改附魔时，在同一提交里同步：实现、默认配置 yml（含 `description` 文案和 `max_level`）、`docs/enchants/<id>.md`、`README.md` 与 `README_EN.md` 的相关说明、单元测试。新增动作类型时同步更新两份 `action_types` 文档。新增或修改可配置项（参数声明）时重新导出 `editor/enchant-schema.json`：`JAVA_HOME=/usr/lib/jvm/zulu-25 mvn -B test -Dtest=EnchantSchemaExportTest -Dmeowsenchants.updateSchema=true`。改占位符或按等级取值的规则时，同步更新 `editor/placeholder-vectors.json` 和 `editor/scaling-vectors.json`，插件和编辑器都跑这两份向量。发版时递增 `pom.xml` 的 `milk.N`。

## 两种实现方式

- **配置驱动**：只写 `enchants/<id>.yml`，组合现有动作类型。需要新行为时在 `actions/impl/` 加一个动作类型，先例是 `auto_bow` 和 `draw_accelerate`。
- **专用机制**：动作系统表达不了的附魔，像箭雨一样单独建包（`arrowrain/`）。yml 里写 `mechanic: <名称>_v<N>` 作为启用标记，`actions: {}` 留空，避免和同名旧配置的动作叠加触发。写对标记的附魔不走通用触发器（`EnchantTriggerHelper.handles` 只认 `EnchantSchema.isMechanicEnchant` 列出的「文件 + 标记」，新机制要加进去；别的附魔误写 `mechanic:` 照常走通用触发器，校验器报警告），`cooldown` 由机制自己经 `listeners/MechanicCooldown` 读：射击时查，机制真正触发时开始计时，计时和通用附魔是同一套。新机制照此接入。

## 配置分发

- 插件只在 `plugins/MeowsEnchants/enchants/` 不存在时释放全部内置 yml。已有服务器既拿不到新附魔的 yml，也不会更新旧 yml。新增附魔时，要么像箭雨那样在 `MeowsEnchantsBootstrap` 里只补缺失的文件（`ArrowRainConfig.installMissing`），要么部署时手动放入 yml；修改已有附魔的配置时，把线上迁移步骤写进该附魔的文档。
- 附魔在 bootstrap 阶段注册：换 jar 或改动 `max_level` 等注册相关配置（schema 里标 `apply: restart` 的字段）后，完整重启服务器。milk.13 起其余配置用 `/enchants reload` 热应用。插件也不支持热禁用后再启用（PlugManX reload 同样不行）：`onEnable` 里注册 `/enchants` 命令会被 Paper 拒绝，插件随即又被禁用，只能完整重启。

## Folia

- 按区域线程模型写代码：玩家和背包在玩家所属区域内访问；按位置生成实体用该位置的区域调度器；实体的后续处理用实体自己的调度器（`utils/SchedulerCompat.java`）；调度失败时放弃，保持在区域线程内。
- 编译依赖是 Paper `1.21.11-R0.1-SNAPSHOT` API，实际运行在 Folia 26.1.2。原生事件顺序、物品消耗、随机分布只能在真实测试服上证明，单元测试的 mock 证明不了。

## 构建

```sh
JAVA_HOME=/usr/lib/jvm/zulu-25 mvn -B clean verify
```

部署用 `target/meowsenchants-<版本>.jar`；`original-*.jar` 是 Shade 前的产物。测试报告在 `target/surefire-reports/`。

## 本地测试服

本地 Folia 测试服在仓库外：`/home/swim/code/ProjectMilkRepo/_arrowrain/runtime/`（下称 `$RT`），为箭雨建立，所有附魔都在这里测。世界 `arrowrain-test`，离线模式，仅监听 `127.0.0.1:25576`，在 tmux 会话 `milk-arrowrain` 中运行 `run-local.sh`（Java 25）。插件目录里的 `ArrowRainQA.jar`、`TrackingQA.jar` 分别是箭雨和跟踪的测试观察插件，只用于本地。为跟踪另装了 Residence 6.0.3.3 + CMILib 1.5.9.9（不含生产领地数据）和 Worlds 4.4.0（`milk:pvp` 世界），并开了只监听本机的 RCON（端口 25617），详见 `docs/enchants/tracking.md`。

- 用户会亲自在这台服里体验验收：停服、重启、换 jar 之前先告诉用户。
- 换 jar 流程：
  1. `tmux send-keys -t milk-arrowrain stop Enter`，等到 `tmux has-session -t milk-arrowrain` 找不到会话。
  2. 备份旧 jar 和 `$RT/plugins/MeowsEnchants/enchants/` 下要改的配置，`plugins/` 里只保留一个 `meowsenchants-*.jar`。
  3. `tmux new-session -d -s milk-arrowrain -c $RT ./run-local.sh`
  4. `$RT/logs/latest.log` 出现 `Enabling MeowsEnchants v<版本>` 和 `Done`；`ss -ltn '( sport = :25576 )'` 有监听；`sha256sum` 与 `target/` 中的 jar 一致。
- 手动验证：`/minecraft:enchant @s meowsenchants:<id> <等级>`。
- 自动化：协议测试客户端可参考 `_arrowrain/test-client/`（npm `minecraft-protocol`，版本标识 `26.1`）。某个附魔专用的套件和证据写在它自己的文档里。
- 客户端画面测试只用 `_arrowrain/render-client*/` 下的隔离客户端，启动时把工作目录设为该目录，客户端模组才会读到相对路径的测试配置。用户自己的 `~/.minecraft` 只读。

## 发布到生产

- 生产服 jar 以二进制形式提交在配置仓库 MilkServerConfig（本地 `../MilkServerConfig`；GitHub 仓库名 ProjectMilkConfigs，生产裸库名 MilkServerConfig）的 `survival/plugins/` 中（先例：`cc992ef8 chore(meowsenchants): fix auto_bow release (0.2-milk.7)`），部署靠手动。替换前先核对线上正在运行的 jar 和配置备份，线上 jar 可能被热补丁过。
- 线上 yml 按各附魔文档里的迁移步骤处理。
- 本地测试服的插件组合和区域都比生产少：涉及其他插件交互或跨区域的行为，生产前还要在完整插件组合和多个 Folia 区域并发下验证。
