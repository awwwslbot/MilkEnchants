# TODO：把 NMS 调用从反射改为直接调用（参考 LibMilkyWay）

> 状态：待办
> 优先级：中（功能已可用，但反射路径缺少编译期保障）
> 影响版本：`0.2-milk.7` 起
> 相关代码：`src/main/java/com/windsor/meowsEnchants/listeners/AutoBowManager.java`

## 1. 背景

`auto_bow` 需要在服务端“替玩家松手”发射弓箭。Bukkit API 没有对应方法：

- `LivingEntity#completeUsingActiveItem()` 走的是**消耗品完成**路径（`finishUsingItem`），对弓只停止使用，不会发射；
- 真正射箭的入口是 NMS `LivingEntity#releaseUsingItem()`，它会走 `BowItem.releaseUsing` → `ProjectileWeaponItem.shoot`，同时触发 `EntityShootBowEvent`、消耗箭矢、应用 `Infinity` / `Power` / `Punch` / `Flame`。

当前通过反射调用：

```java
Method handleGetter = player.getClass().getMethod("getHandle");
Object handle = handleGetter.invoke(player);
Method release = handle.getClass().getMethod("releaseUsingItem");
release.invoke(handle);
```

问题：

- 没有编译期检查，服务端更新后方法改名/改签名只能在运行时发现；
- 首次失败后 `releaseUnavailable = true`，整个 `rapid_fire` 静默失效，只留一条错误日志；
- 热路径上虽然缓存了 `Method`，但仍是反射调用；
- 未来若再加入 NMS 能力，会继续堆积反射代码。

## 2. 范围

**In scope**

- `AutoBowManager.releaseUsingItem(Player)` 的 NMS 调用；
- 建立可复用的 NMS 桥接层（版本检测 + 版本化实现 + 反射兜底）。

**Out of scope**

- `utils/SchedulerCompat`：反射的是 Folia 的 `EntityScheduler` API，不是 NMS；
- `utils/PlaceholderAPIHook`：可选依赖反射；
- 其他 Bukkit/Paper API 调用。

## 3. 目标与非目标

**目标**

- 在受支持的服务端版本上使用**直接 NMS 调用**，编译期可校验；
- 未知/不支持的版本仍能回退到反射，插件不因 NMS 变化而无法启动；
- 保持现有行为完全不变：箭矢消耗、`Infinity`、`Power/Punch/Flame`、`EntityShootBowEvent`、Folia 线程纪律；
- NMS 代码集中在一个包内，便于后续扩展（如其他需要 NMS 的动作）。

**非目标**

- 不引入客户端 mod / ProtocolLib；
- 不改变 `rapid_fire` / `quick_draw` 的玩法语义；
- 不为旧于 1.20.5 的服务端提供支持（README 已要求 1.21.11+）。

## 4. 方案对比

### 方案 A：迁移到 Gradle + `paperweight.userdev`（推荐，与 LibMilkyWay 一致）

参照 `plugins/MilkPlugins/LibMilkyWay/build.gradle.kts`：

```kotlin
plugins {
    java
    alias(libs.plugins.paperweight.userdev)
}

dependencies {
    paperweight.paperDevBundle(libs.versions.minecraft.get())
    compileOnly(libs.bukkit.placeholderapi)
}
```

- 可直接 `import net.minecraft.world.entity.LivingEntity;`、`org.bukkit.craftbukkit.entity.CraftPlayer;`；
- Paper 1.20.5+ 运行时就是 Mojang 映射，`net.minecraft.*` / `org.bukkit.craftbukkit.*` 名字即运行时名字，无需 reobf；
- LibMilkyWay 的 `nms/MilkNMS.kt`、`nms/mapart/v26_1/MapArtNMS26_1.kt`、`nms/mapart/fallback/ReflectionMapArtNMS.kt` 就是现成范式。

**优点**：官方支持、编译期校验、后续扩展 NMS 成本低。
**缺点**：需要把构建从 Maven 迁到 Gradle（或至少增加 Gradle 构建），CI/本地脚本要同步调整。

### 方案 B：保留 Maven，编译期引入服务端 jar

两种做法：

1. 把 `run/survival/versions/26.1.2/folia-26.1.2.jar` 安装到本地仓库：

   ```bash
   mvn install:install-file \
     -Dfile=run/survival/versions/26.1.2/folia-26.1.2.jar \
     -DgroupId=dev.folia -DartifactId=folia-server \
     -Dversion=26.1.2 -Dpackaging=jar
   ```

   然后在 `pom.xml` 以 `provided` 引入。

2. 使用非官方 Maven 插件 `paper-nms-maven-plugin`（Alvinn8）生成/安装映射后的依赖。

**优点**：改动最小，仍用现有 Maven 流程。
**缺点**：服务端 jar 路径/版本与机器绑定；CI 需要额外准备 jar；非官方插件长期维护风险；升级服务端要手动重装 jar。

### 方案 C：独立 NMS 适配模块（Gradle/paperweight），主插件依赖接口

- 新建一个小模块（Gradle + paperweight）产出 `meows-nms`；
- 暴露 `BowNms` 接口与版本化实现；
- MeowsEnchants 通过 `compileOnly` + `ServiceLoader` 或直接类引用调用，反射兜底放在适配模块或主插件。

**优点**：主插件构建不动；NMS 隔离。
**缺点**：多一个 artifact/发布步骤；类加载与打包（shade 与否）需要设计。

### 方案 D：扩展 LibMilkyWay 的 `MilkNMS` 门面

- 在 LibMilkyWay 增加 `bow` 能力域：`BowNms#releaseUsingItem(Player)` + `v26_1` 实现；
- MeowsEnchants 声明对 LibMilkyWay 的依赖，直接调用 `MilkNMS.bow()`。

**优点**：复用已有的 paperweight 构建；服务端只多一个已有插件依赖。
**缺点**：MeowsEnchants 从“独立插件”变成强依赖 LibMilkyWay；跨仓库耦合；对外部 fork 的使用者不友好。

## 5. 推荐路线

1. **首选方案 A**：与 LibMilkyWay 保持一致，一次性把 NMS 基础设施搭好；
2. 若短期不能迁移构建，选 **方案 C**（独立适配模块）；
3. 方案 B 只作为临时过渡；
4. 无论选哪个，**保留反射兜底**，并在启动日志打印当前使用的 NMS 实现。

## 6. 实施步骤

- [ ] 新建包 `com.windsor.meowsEnchants.nms`
- [ ] 移植版本探测：`MinecraftVersion`（参照 LibMilkyWay `nms/MinecraftVersion.kt`）
  - 解析 `Bukkit.getMinecraftVersion()`，支持 `26.1.2` / `1.21.11` 两种形态
- [ ] 定义能力接口：

  ```java
  public interface BowNms {
      boolean releaseUsingItem(Player player);
  }
  ```

- [ ] 实现 `v26_1/BowNms26_1`：

  ```java
  public final class BowNms26_1 implements BowNms {
      @Override
      public boolean releaseUsingItem(Player player) {
          if (!(player instanceof CraftPlayer craftPlayer)) return false;
          craftPlayer.getHandle().releaseUsingItem();
          return true;
      }
  }
  ```

  - `CraftPlayer#getHandle()` 在 1.20.5+ 返回 `ServerPlayer`；
  - `releaseUsingItem()` 是 `LivingEntity` 的 public 方法，26.1.2 已核对。

- [ ] 把现有反射代码移动到 `ReflectionBowNms`（保留 `getMethod` 缓存与失败日志）
- [ ] 实现门面 `NmsBridge`：
  - `init(Plugin)` 在 `onEnable` 早解析；
  - `version.within(26, 1)` → `BowNms26_1`，否则 `ReflectionBowNms`；
  - 启动日志：`NMS bridge: BowNms26_1 selected for Minecraft 26.1.2`。
- [ ] 替换 `AutoBowManager`：

  ```java
  session.firing = true;
  try {
      NmsBridge.bow().releaseUsingItem(player);
  } finally {
      session.firing = false;
  }
  ```

- [ ] 删除 `AutoBowManager` 中的 `Method` 字段、`getHandleMethod`、`releaseUsingItemMethod`、`releaseUnavailable`
- [ ] 按方案 A/B/C 调整构建
- [ ] 更新 `docs/action_types_zh.md` / `docs/action_types_en.md` 中关于 `releaseUsingItem` 的说明（从“反射”改为“直接 NMS / 兜底反射”）
- [ ] 在 README 的构建说明里补充 paperweight/服务端 jar 的前置条件

## 7. 验收标准

- [ ] `AutoBowManager` 不再包含 NMS 反射代码；
- [ ] 26.1.x 上走 `BowNms26_1` 直接调用，启动日志可见所选实现；
- [ ] 未知版本自动回退 `ReflectionBowNms`，插件仍可启动；
- [ ] `rapid_fire` 能正常射箭，`EntityShootBowEvent` 正常触发；
- [ ] `rapid_fire + quick_draw` 组合仍能提前满蓄力；
- [ ] 无箭 / `Infinity` / 创造模式 / 其他插件取消事件的行为与 `0.2-milk.7` 一致；
- [ ] Folia 下任务仍在玩家所属 region 线程执行；
- [ ] 干净环境可复现构建。

## 8. 测试矩阵

| 场景 | 预期 |
| --- | --- |
| 普通弓 + rapid_fire | 20 tick 满蓄力自动射箭，循环 |
| rapid_fire + quick_draw | 约 5~10 tick 满蓄力自动射箭 |
| 背包无箭 | 不生成箭，不报错 |
| Infinity + 1 支箭 | 连续射击不消耗箭 |
| 创造模式 | 不消耗箭 |
| 其他插件取消 `EntityShootBowEvent` | 不生成箭，任务继续但不崩 |
| 玩家松手 / 切物品 / 切副手 / 死亡 / 退出 | 任务停止，无残留 |
| Folia 跨 region 战斗 | 无跨线程异常 |
| 未知服务端版本 | 回退反射并打印日志 |

## 9. 风险与回滚

- NMS 方法在新版本改名 → 版本化实现 + 反射兜底；
- 直接 import 导致编译绑定服务端版本 → 升级服务端时必须重新构建（README 已要求固定版本）；
- Gradle 迁移影响 CI/发布脚本 → 可先选方案 C 隔离；
- 回滚方式：保留 `ReflectionBowNms`，门面强制选择反射实现即可恢复旧行为。

## 10. 参考资料

- LibMilkyWay（同仓库）：
  - `plugins/MilkPlugins/LibMilkyWay/build.gradle.kts`
  - `plugins/MilkPlugins/LibMilkyWay/src/main/kotlin/nms/MilkNMS.kt`
  - `plugins/MilkPlugins/LibMilkyWay/src/main/kotlin/nms/MinecraftVersion.kt`
  - `plugins/MilkPlugins/LibMilkyWay/src/main/kotlin/nms/mapart/v26_1/MapArtNMS26_1.kt`
  - `plugins/MilkPlugins/LibMilkyWay/src/main/kotlin/nms/mapart/fallback/ReflectionMapArtNMS.kt`
- Paper 官方 paperweight-userdev 文档：<https://docs.papermc.io/paper/dev/userdev>
- 非官方 Maven 方案（备选）：<https://github.com/Alvinn8/paper-nms-maven-plugin>
- 当前反射实现：`src/main/java/com/windsor/meowsEnchants/listeners/AutoBowManager.java` 的 `releaseUsingItem` 方法
