# 跟踪（tracking）

附魔 ID 为 `meowsenchants:tracking`，显示名「跟踪」，适用于弓和弩，最高三级。玩家用带跟踪的弓或弩射出的箭（普通箭、药水箭、光灵箭，多重射击的每一支）和弩射出的烟花火箭会自动拐向飞行路线附近的目标；同一把弓上还有箭雨时，雨箭也会跟踪。骷髅、发射器射出的不跟踪。

| 等级 | 判定半径（飞行路线两侧） |
| --- | --- |
| I | 1.5 格 |
| II | 2.5 格 |
| III | 3.5 格 |

## 判定和转向

- 每支弹射物每 5 tick 判定一次：按原版物理预测它接下来 10 tick 的飞行路线（满弓约 29 格；弩的烟花无重力、无阻力，按直线约 16 格），找路线两侧半径以内的目标。距离量到目标碰撞箱最近的点（线段到碰撞箱的精确距离）。弹射物和目标身体中心之间有会碰撞的方块挡着的不算；草、花这类可穿过的方块和液体不挡。目标在弹射物身后（夹角超过 90°）的也不算。
- 选目标：敌对生物 > PvP 状态的玩家 > 友好生物，同一级里选离弹射物最近的（同样量到碰撞箱最近的点）。每次判定都重新挑：当前目标仍在范围内、仍然合法、也没有更高优先级的目标时继续跟；出现更高优先级的就换；当前目标离开范围、被挡住或失效时换成范围内最好的，没有就松开。
- 锁定后每 tick 把速度方向转向目标碰撞箱中心，每 tick 最多转 20°；离目标中心 6 格以内直接对准。只改方向、速率不变，所以伤害（速率 × 基础伤害）、暴击、火矢、药水效果都保持原样。
- 停止：目标跑到身后（夹角超过 90°）就放弃这个目标，下次判定再重新找。插进方块、被移除、射手下线时停止；每支最多跟踪 200 tick（10 秒）。
- 穿透箭打中目标后记住它，之后的判定不再选它，继续跟踪下一个；不带穿透的箭、雨箭和烟花打中实体或方块就停止。

## 目标分类

- 敌对生物：所有实现 `Enemy` 的生物（怪物、恶魂、史莱姆、幻翼、潜影贝、疣猪兽、末影龙等）。末影人、僵尸猪灵、猪灵只有正在攻击玩家（`Mob#getTarget` 是玩家；猪灵取自 AI 记忆里的攻击目标）时才算，平时不跟踪；这份名单是 `tracking.neutral_until_attacking`。
- PvP 状态的玩家：在 `always_pvp_worlds`（默认 `milk:pvp`）里一直算；在其他世界，要最近 15 秒内和别的玩家互相造成过伤害（`EntityDamageByEntityEvent` 最终伤害大于 0，攻击方是玩家本人或箭、烟花、三叉戟等弹射物的射手，双方都记）。另外射手和目标所在的位置都要允许 PvP：两个世界的原版 `minecraft:pvp` 规则，以及领地的 pvp 设置。射手自己、创造/旁观模式、射手看不见的（`Player#canSee` 为 false，CMI vanish 这样逐个对玩家隐藏）、带 `vanished` 元数据的、以及带 `NPC` 元数据的玩家永远不算。
- 友好生物：其余所有生物（Bukkit `Mob`），包括狼、蜜蜂、北极熊这类动物。以下不跟踪：已驯服的宠物、村民和流浪商人、铁傀儡、雪傀儡和铜傀儡、悦灵、拴着绳或正被玩家骑着的生物。按类型排除的是 `tracking.never_tracked`，加一种生物就是加一项。
- 盔甲架、假人（Mannequin）等不是 `Mob` 的实体永远不跟踪。
- 命名生物：有自定义名字（`customName` 非空）的 `Mob`，永远不跟踪。盔甲架、假人和玩家不归这一条管：它们本来就不会因为生物规则被跟踪（玩家按 PvP 规则），被跟踪弓弩射出的箭直接射中也照原版受伤，有名字也一样。跟踪烟花的爆炸例外：盔甲架、假人不是可以被跟踪的目标，爆炸伤害会被取消（见下文「烟花爆炸」）。

### 领地（Residence）

服务器装了 Residence 时，射手在领地里无权伤害的目标不跟踪，语义照 Residence 6.0.3.3 自己的伤害拦截：

- 玩家：射手位置和目标位置都看。两人在同一块领地时，领地 pvp 为 false 就不行，双方 friendlyfire 都为 false 也不行；不在同一块时，任一端所在领地 pvp 为 false 就不行；两端都在野外时看世界 flag 的 pvp。
- 怪物（`Enemy`）看目标位置的 `mobkilling`，动物（Residence 的 `Utils.isAnimal`：动物、水生生物、村民、蝙蝠、雪傀儡、铁傀儡、悦灵、铜傀儡）看 `animalkilling`。Residence 用两个独立的监听器分别检查，所以两类都算的生物（26.1.2 里只有疣猪兽，它同时是 `Enemy` 和 `Animals`）两个 flag 都要允许，任一不允许就不跟踪，跟踪烟花的爆炸也不伤它。按射手的玩家/组权限算，Residence 管理员放行；某个 flag 全局关闭或 Residence 对该世界关闭监听时，只跳过这个 flag 的检查。
- 射手位置用射击那一刻的快照：Folia 下箭和射手可能在不同区域，弹射物线程不读射手的实时位置。雨箭用产生这场箭雨的那次射击的快照（箭雨在原箭的射击事件里记下射手位置，随雨箭一起交给跟踪模块），射手之后走动或再射别的箭都不影响。
- 没装、未启用或版本不认识时整条跳过，不报错（不认识时只记一条警告）。Residence 没有这个版本的公开 Maven 构件，所以用方法句柄按精确签名绑定（不能用 `Class#getMethod`：Residence 主类有以 WorldEdit 类型声明的方法，没装 WorldEdit 时解析全部签名会抛 `NoClassDefFoundError`）。`/resreload` 后每次调用重新取管理器。没有照搬 Residence 的 raid（领地战）队伍规则。

## 命名生物保护和烟花爆炸

- 跟踪弓（弩）射出的所有弹射物都带 BYTE PDC 标记 `meowsenchants:tracking_level`（值为等级），雨箭在生成后由跟踪模块补上。带标记的弹射物碰到命名生物时，在 LOWEST 取消 `ProjectileHitEvent`：箭和雨箭直接穿过去继续飞，烟花不在它身上爆炸。`EntityDamageByEntityEvent` 里再兜底取消一次带标记弹射物对命名生物的伤害。
- 带标记烟花的爆炸伤害只给「可以被跟踪」的目标：按上面的规则判（不看路线和视线），不能跟踪的都取消，包括命名生物、按类型排除的友好生物（已驯服的宠物、村民和流浪商人、铁傀儡、雪傀儡、铜傀儡、悦灵、拴着绳或正被玩家骑着的生物）、盔甲架和假人、领地里无权伤害的目标、不在 PvP 状态的玩家、射手自己、没在攻击玩家的末影人/僵尸猪灵/猪灵。
- 判断用的是这支烟花自己的射击记录（射手会话 + 射击时的位置快照），从发射一直保留到烟花被移除，和它还在不在跟踪无关：烟花撞上目标时跟踪已经停了，爆炸伤害在那之后才结算。不会用射手当前位置或射手之后另一次射击的快照。
- 射手下线后这支烟花再炸：全部取消，谁都不伤。射手下线时跟踪就停止了，此后没有「射手可以跟踪的目标」可言；宁可少伤，也不误伤命名生物、宠物和其他玩家。找不到射击记录的带标记烟花（插件热重载前射出、或飞行中所在区块被卸载过）同样全部取消。

## 效果

- 锁定后每 tick 在弹射物身后 0.3 格生成 1–2 个 `ENCHANT`（附魔符文）粒子，按普通粒子范围发给附近所有玩家。
- 锁定那一刻（包括换到新目标）只对射手播放 `block.note_block.chime`，音量 1.0（客户端把单个声音的增益限制在 1.0，再大只是传得更远，所以这已是最大响度），类别 PLAYERS，跟随射手位置。同一射手同一区域 tick 里只响一次（按弹射物所在区域的游戏时间去重，多重射击同时锁定只响一次）。声音调度到射手自己的实体调度器上播放。
- 雨箭锁定后停止箭雨原来的 CRIT 轨迹任务，改成上面的附魔符文粒子；雨箭锁定不响。
- 原版客户端不会按速度包更新箭的朝向：每个客户端 tick 只把模型往速度方向插值 20%，服务端对箭的位置/朝向同步默认每 20 tick 一次。转向时除了 `setRotation`，还通过反射给 NMS 实体置 `needsSync`，让当 tick 发出带朝向的位置包，客户端模型跟着转（实测每个转向 tick 都收到 `entity_move_look`）。反射找不到字段时只记一条警告，转向照常，只是模型会滞后。

## 配置和获得方式

`plugins/MeowsEnchants/enchants/tracking.yml`，启动时只在文件不存在时补上，不覆盖已有配置。专用机制标记 `mechanic: tracking_v1`，`actions: {}` 留空；没有这个标记时跟踪不启用，启动日志会提示。附魔台、村民交易都关（`enabled: false`），注册时不加原版战利品/交易标签，只能用命令获得（`/minecraft:enchant @s meowsenchants:tracking 3`）。铁砧基础成本 2，不和任何附魔冲突，不额外消耗耐久和箭。

可调参数在 `tracking:` 段（旧文件缺键时逐项按默认值；不要放到顶层 `sound`/`particle`，那是通用触发器在每次射击时都会播放的效果）：

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `radius` | `[1.5, 2.5, 3.5]` | 一/二/三级半径；更高等级沿用最后一项。写成列表时逐项覆盖默认列表，缺的或不大于 0 的项沿用默认的同位项（和 milk.12 一样）；milk.13 起也可以写一个数字或 `{base, per_level}`，按通用的按等级取值规则。超过 8 时编辑器提示 |
| `scan_interval_ticks` | 5 | 搜索间隔 |
| `lookahead_ticks` | 10 | 每次搜索预测的路线长度 |
| `max_turn_degrees` | 20 | 锁定后每 tick 最大转角 |
| `direct_aim_distance` | 6 | 离目标中心这么近时直接对准 |
| `give_up_angle_degrees` | 90 | 目标夹角超过它就放弃 |
| `max_tracking_ticks` | 200 | 每支最长跟踪时间 |
| `pvp_combat_seconds` | 15 | 战斗状态持续时间 |
| `always_pvp_worlds` | `["milk:pvp"]` | 世界 key 或 Bukkit 世界名，忽略大小写 |
| `particle.*` | `ENCHANT`，1–2 个，散布 0.05，速度 0，箭尾 0.3 格 | 需要额外数据的粒子会退回 `ENCHANT` |
| `sound.*` | `block.note_block.chime`，`PLAYERS`，音量 1.0，音调 1.0 | 音量大于 1.0 不会更响，只会让更远的地方也听得到 |
| `never_tracked` | `[VILLAGER, WANDERING_TRADER, IRON_GOLEM, SNOW_GOLEM, COPPER_GOLEM, ALLAY]` | 永不跟踪的非敌对生物（实体类型大写枚举名，可带 `minecraft:`），跟踪烟花的爆炸也不伤它们；写敌对生物无效。已驯服、拴绳、被玩家骑着、有名字的生物另有规则，不受这份名单影响 |
| `neutral_until_attacking` | `[ENDERMAN, ZOMBIFIED_PIGLIN, PIGLIN]` | 只在正在攻击玩家时才算目标的敌对生物 |

这两项是 milk.13 新增的，默认值就是 milk.12 写死的名单。名单按实体类型精确匹配：写 `ZOMBIE` 只算僵尸本身，不含尸壳、溺尸、僵尸村民和僵尸猪灵。不认识的名字跳过，加载期校验报警告。

> **危险**：从 `never_tracked` 删掉一项后，跟踪弓弩的箭会自动射向这种生物，烟花爆炸也会伤到它们（例如村民、铁傀儡）。从 `neutral_until_attacking` 删掉一项后，跟踪的箭会主动射向没在攻击任何人的这种生物，可能把整群中立怪（如僵尸猪灵）引来。

新附魔要在 bootstrap 阶段注册：上线时换 jar 后完整重启。milk.13 起 `tracking:` 段和 `mechanic` 开关可以用 `/enchants reload` 生效：之后射出的弹射物用新参数，已经在跟踪的保留开始跟踪时的参数快照（`pvp_combat_seconds` 例外，战斗表是共用的，改了立即对所有判定生效）；去掉 `mechanic` 后正在跟踪的弹射物下一 tick 停止。`max_level` 等注册字段仍要完整重启。`paper-plugin.yml` 把 Residence 声明为可选的服务端依赖（`required: false`、`load: BEFORE`、`join-classpath: true`）。

## 生命周期与 Folia

- 在 `EntityShootBowEvent`（MONITOR，未取消）里读这次射击用的弓/弩上的等级、打 PDC 标记、记下射手位置快照；`ProjectileLaunchEvent` 未取消后才在弹射物自己的实体调度器上启动每 tick 任务。弩的多重射击每支各有一次射击事件和发射事件（真服实测 3 支 = 3 次射击事件 + 3 次发射事件）。之后不再读手持物品。
- 雨箭由箭雨用 `world.spawn` 生成，没有射击事件：`ArrowRainListener` 生成雨箭后通过 `RainObserver` 交给跟踪模块，等级取箭雨在射击时克隆的弓。
- 每 5 tick 一次实体范围查询，锁定后每 tick 只做向量计算、一次 `setVelocity`/`setRotation` 和一次粒子。查询框里不属于当前区域的区块直接跳过（全部属于当前区域时一次查询，否则只查属于当前区域的区块切片）；视线射线经过的区块未加载或属于别的区域时算被挡住。锁定目标离开当前区域时松开。
- 弹射物线程不读写射手的实时状态，只读射击时快照和线程安全的数据。两处跨区域只读：`Player#canSee`（隐身判断）和 Residence 的权限查询（Residence 自己的伤害监听也在各区域线程里这样做）。PvP 战斗表是并发表，玩家下线清理。
- 每支跟踪弹射物有两份状态：跟踪任务（停止时立即从表里移除）和射击记录（射手会话 + 射击时快照，保留到实体移除，供烟花爆炸过滤使用）。
- 射手下线：射手句柄标为离线，它的弹射物下一 tick 停止，之后它的烟花爆炸不伤任何目标。插件关闭/热禁用：停止所有任务、清空状态；Folia 在插件禁用后不允许新调度，任务只会自行退出。MeowsEnchants 不支持热禁用后再启用（PlugManX reload 也一样）：`onEnable` 注册 `/enchants` 命令时 Paper 拒绝在启动后注册生命周期处理器，插件随即又被禁用，这是原有问题，和跟踪无关。要重新加载插件本身只能完整重启；只改配置时用 `/enchants reload`（见「配置和获得方式」）。跟踪任务里抛出的异常只停掉这一支并记一条警告，不会留下每 tick 重复报错的任务。

## 已知限制

- 26.1.2 原版烟花的实体命中检测只认起点在目标（按年龄外扩 0–0.3 格的）碰撞箱之外的情况：烟花某个 tick 恰好停在这条边带里，下一 tick 会穿过目标。之后它会继续飞到寿命结束或撞方块再爆炸，爆炸半径 5 格仍可能伤到目标。这是原版行为，跟踪没有改它。
- 锁定后瞄准的是身体中心，从眼睛高度射向站在地上的目标时箭是斜向下的；穿透箭打穿第一个目标后通常几格内就落地，能否接着跟下一个取决于下一次判定前还在不在空中。
- CMI 没装在本地测试服：隐身用 `hidePlayer`（CMI vanish 的实现方式）和 `vanished` 元数据模拟，没在真 CMI 上验证。
- 射手在另一个区域的情况由 QA 在真服验证过（证据 `/home/swim/code/ProjectMilkRepo/_tracking/qa/evidence/runtime/qa_multiRegion*.json`）：射手被传送到 5000 多格外的另一个区域后，烟花仍锁定并命中僵尸，射手收到提示音、旁观者收不到；烟花锁定 PvP 状态的玩家，爆炸伤害没有被取消（跨区域读 `canSee` 和 Residence 的 pvp 判定）；射手离开后才生成的雨箭全部锁定，不响。服务端日志没有异常，但这只说明能用，证明不了跨区域只读没有竞态。
- 查询框和视线跨进别的区域的裁剪分支只按代码推理：本地 `grid-exponent: 4`，每个 section 是 16×16 区块（256 格），相邻的已加载 section 会并入同一个区域，而弹射物的查询框最多约 37 格，只可能跨进相邻 section，所以本地构造不出「查询框的一部分属于别的区域」。生产用同样的设置时也基本碰不到。生产前仍要在完整插件组合和多区域并发下再看。

## 构建与验证

```sh
JAVA_HOME=/usr/lib/jvm/zulu-25 mvn -B clean verify
```

单元测试（`src/test/java/com/windsor/meowsEnchants/tracking/`，另有 `arrowrain/RainObserverTest`）：线段/路线到碰撞箱的精确距离（含 2000 个随机样例对照密集采样）、原版箭/烟花轨迹预测、限角转向与保持速率、直接对准、放弃、复现决策台模拟（各级 1–45 格静止目标全部利落命中、锁定后最多 12 tick）、优先级与换目标、目标分类和各排除项、PvP 战斗表、Residence 语义（假实现，含疣猪兽两个 flag 都要允许、按 flag 跳过检查）及不认识 Residence 时只警告一次、配置默认值和缺键、`never_tracked` / `neutral_until_attacking` 来自配置、监听器的射击/发射/雨箭等级与位置来源/命名穿过/烟花爆炸过滤（用这支烟花自己的射击记录，射手之后在别处再射不影响 PvP 判定）/射击记录在实体移除和调度器退休时释放/多重射击只响一次/穿透/超时/下线/关闭。

真服实测在仓库外 `/home/swim/code/ProjectMilkRepo/_tracking/`（以下相对于该目录）：

- 测试服是 `_arrowrain/runtime`（见仓库根目录 `AGENTS.md`），另装了 Residence 6.0.3.3 + CMILib 1.5.9.9（从生产只读复制的 jar，sha256 已核对，不含生产领地数据和配置）、Worlds 4.4.0（建了 `milk:pvp` 世界）和观察插件 `TrackingQA.jar`（源码 `test-harness/src/TrackingQA.java`）；`server.properties` 开了只监听本机的 RCON（端口 25617，密码在 `test-client/.rcon-password`）。测试领地由 `test-client/res-setup.cjs` 让 op 机器人 `TrackC` 建：`farm`（默认 flag：pvp、animalkilling 为 false）、`nomob`（mobkilling false）、`arena`（pvp true），位置写在 `test-client/suite.cjs` 里。
- 运行：`NODE_PATH=/home/swim/code/ProjectMilkRepo/_arrowrain/test-client/node_modules node test-client/suite.cjs [用例,…]`。三个协议机器人 `TrackA`（射手）、`TrackB`（PvP 目标）、`TrackC`（旁观/领地主人）同时在线，场景用 RCON 驱动观察插件布置。`shooterQuitStopsTracking`、`fireworkBlastAfterShooterQuit` 会让射手机器人下线再重连。`pluginDisableStopsTracking` 会热禁用 MeowsEnchants，直到下次重启前所有附魔都不生效：不传用例名时跳过它，要放在最后单独点名运行，跑完立刻用 `test-client/restart.sh` 重启。用户（`TheSw1m`）会在这台服里验收，并同意在线时照常重启、跑套件；除机器人和用户以外有其他玩家在线时，这个用例会跳过，`restart.sh` 也拒绝停服。套件清理实体只清测试场地（由 op 机器人 `TrackC` 执行按距离限制的 `kill`；Folia 下控制台带范围的选择器会因线程检查失败），不碰用户在出生点附近的东西。
- 结果：`evidence/runtime/suite.json`（汇总），每个用例一份 `<用例>.json`（每 tick 的位置/速度/朝向/PDC 标记、命中、伤害事件）和 `<用例>-packets.json`（机器人收到的声音、粒子、实体移动包）。箭雨回归（`_arrowrain/test-client/milk10-suite.cjs` 全部 40 个场景）的结果在 `evidence/arrowrain-regression/`。
