package com.windsor.meowsEnchants;

import com.windsor.meowsEnchants.actions.ListScaling;
import com.windsor.meowsEnchants.actions.impl.AttributeModifierHelper;
import com.windsor.meowsEnchants.actions.impl.TillAction;
import com.windsor.meowsEnchants.arrowrain.ArrowRainListener;
import com.windsor.meowsEnchants.commands.EnchantsCommand;
import com.windsor.meowsEnchants.config.BukkitRegistries;
import com.windsor.meowsEnchants.config.ConfigReload;
import com.windsor.meowsEnchants.config.EnchantConfig;
import com.windsor.meowsEnchants.config.EnchantConfigLoader;
import com.windsor.meowsEnchants.config.GlobalSettings;
import com.windsor.meowsEnchants.config.RegisteredEnchant;
import com.windsor.meowsEnchants.config.ReloadReport;
import com.windsor.meowsEnchants.config.RuntimeConfig;
import com.windsor.meowsEnchants.gui.EnchantBrowser;
import com.windsor.meowsEnchants.listeners.AutoBowListener;
import com.windsor.meowsEnchants.listeners.AutoBowManager;
import com.windsor.meowsEnchants.listeners.BlockDropItemListener;
import com.windsor.meowsEnchants.listeners.DrawAccelerateListener;
import com.windsor.meowsEnchants.listeners.DrawAccelerateManager;
import com.windsor.meowsEnchants.listeners.BlockInteractionListener;
import com.windsor.meowsEnchants.listeners.CombatListener;
import com.windsor.meowsEnchants.listeners.ConnectionListener;
import com.windsor.meowsEnchants.listeners.EnchantTableListener;
import com.windsor.meowsEnchants.listeners.EquipmentChangeListener;
import com.windsor.meowsEnchants.listeners.FallDamageListener;
import com.windsor.meowsEnchants.listeners.FishingListener;
import com.windsor.meowsEnchants.listeners.ItemListener;
import com.windsor.meowsEnchants.listeners.LifecycleListener;
import com.windsor.meowsEnchants.listeners.PassiveTaskManager;
import com.windsor.meowsEnchants.listeners.PlayerStateListener;
import com.windsor.meowsEnchants.listeners.ProjectileListener;
import com.windsor.meowsEnchants.listeners.VillagerTradeListener;
import com.windsor.meowsEnchants.tracking.TrackingConfig;
import com.windsor.meowsEnchants.tracking.TrackingListener;
import com.windsor.meowsEnchants.utils.SchedulerCompat;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class MeowsEnchants extends JavaPlugin {

    private static MeowsEnchants instance;
    /** 运行时配置的唯一入口；reload 时整体替换（见 {@link RuntimeConfig}）。 */
    private static volatile RuntimeConfig runtime = RuntimeConfig.EMPTY;

    private EnchantBrowser enchantBrowser;
    private ArrowRainListener arrowRain;
    private TrackingListener tracking;
    private PlayerStateListener playerStateListener;
    /** 启动加载时确定的注册值，reload 拿它判断哪些改动要重启。 */
    private Map<String, RegisteredEnchant> baseline = Map.of();

    public static MeowsEnchants getInstance() {
        return instance;
    }

    public static RuntimeConfig getRuntime() {
        return runtime;
    }

    public static void setEnchantConfigs(Map<Key, EnchantConfig> configs) {
        runtime = runtime.withEnchants(configs);
    }

    public static Map<Key, EnchantConfig> getEnchantConfigs() {
        return runtime.enchants();
    }

    public static GlobalSettings getGlobalSettings() {
        return runtime.global();
    }

    public static boolean isSkylliaCompatibilityEnabled() {
        return runtime.global().skylliaCompatibility();
    }

    public static boolean isEnchantingTableUsePlayerLevel() {
        return runtime.global().tableUsePlayerLevel();
    }

    public static double getEnchantingTablePlayerLevelFactor() {
        return runtime.global().tablePlayerLevelFactor();
    }

    public static boolean isEnchantingTableBalanceBooks() {
        return runtime.global().tableBalanceBooks();
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ListScaling.setWarner(message -> getSLF4JLogger().warn(message));

        // 两个专用机制的监听器总是注册，是否启用由当前运行时配置决定（reload 可开关）
        arrowRain = new ArrowRainListener(this, () -> runtime.arrowRain());
        tracking = TrackingConfig.createListener(this);
        ReloadReport report = loadConfiguration(true);
        report.lines(getPluginMeta().getVersion(), true).forEach(line -> getSLF4JLogger().info(line));

        getServer().getPluginManager().registerEvents(new CombatListener(), this);
        getServer().getPluginManager().registerEvents(new BlockInteractionListener(), this);
        getServer().getPluginManager().registerEvents(new ProjectileListener(), this);
        getServer().getPluginManager().registerEvents(new FishingListener(), this);
        playerStateListener = new PlayerStateListener(this);
        getServer().getPluginManager().registerEvents(playerStateListener, this);
        getServer().getPluginManager().registerEvents(new LifecycleListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemListener(this), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new FallDamageListener(), this);
        getServer().getPluginManager().registerEvents(new EquipmentChangeListener(this), this);
        getServer().getPluginManager().registerEvents(new VillagerTradeListener(), this);
        getServer().getPluginManager().registerEvents(new EnchantTableListener(), this);
        getServer().getPluginManager().registerEvents(new BlockDropItemListener(), this);
        getServer().getPluginManager().registerEvents(new AutoBowListener(), this);
        getServer().getPluginManager().registerEvents(new DrawAccelerateListener(), this);
        getServer().getPluginManager().registerEvents(arrowRain, this);
        getServer().getPluginManager().registerEvents(tracking, this);
        getServer().getPluginManager().registerEvents(tracking.combat(), this);
        arrowRain.setRainObserver(tracking);

        enchantBrowser = new EnchantBrowser(this);
        getServer().getPluginManager().registerEvents(enchantBrowser, this);
        enchantBrowser.cleanupStaleViews();
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("enchants", "打开附魔查询界面；/enchants reload 重载配置",
                        new EnchantsCommand(enchantBrowser, this)));

        Bukkit.getOnlinePlayers().forEach(player -> PassiveTaskManager.startTaskForPlayer(this, player));
    }

    @Override
    public void onDisable() {
        if (tracking != null) {
            if (arrowRain != null) arrowRain.setRainObserver(null);
            tracking.close();
            tracking = null;
        }
        if (arrowRain != null) {
            arrowRain.close();
            arrowRain = null;
        }
        PassiveTaskManager.stopAll();
        AutoBowManager.stopAll();
        DrawAccelerateManager.stopAll();
        if (enchantBrowser != null) {
            enchantBrowser.shutdown();
            enchantBrowser = null;
        }
        // 清空静态配置，避免热重载/卸载后旧的 EnchantConfig 仍被类加载器外的引用持有
        runtime = RuntimeConfig.EMPTY;
        ListScaling.setWarner(null);
        instance = null;
    }

    /**
     * {@code /enchants reload}：重读 config.yml 和全部附魔 yml，整体替换运行时配置，
     * 再在各在线玩家自己的区域线程上重排 PASSIVE / 持续潜行疾跑任务、刷新属性修饰。
     */
    public synchronized ReloadReport reloadConfiguration() {
        ReloadReport report = loadConfiguration(false);
        for (Player player : Bukkit.getOnlinePlayers()) {
            SchedulerCompat.run(player, this, task -> {
                if (!player.isOnline()) return;
                PassiveTaskManager.startTaskForPlayer(this, player);
                playerStateListener.restartHoldTasks(player);
                AttributeModifierHelper.updateAllForPlayer(player, this);
            });
        }
        return report;
    }

    /**
     * 构建并换上新的运行时配置。启动时（initial）以 bootstrap 注册的值为基准，并记下供以后 reload 比较。
     */
    private synchronized ReloadReport loadConfiguration(boolean initial) {
        ConfigReload.Outcome outcome = ConfigReload.load(EnchantConfigLoader.enchantDir(),
                getDataFolder().toPath().resolve("config.yml"), runtime,
                initial ? MeowsEnchantsBootstrap.registered() : baseline, initial,
                BukkitRegistries.INSTANCE, getSLF4JLogger());
        RuntimeConfig next = outcome.runtime();
        next.enchants().values().forEach(config -> {
            config.resolveSound();
            config.resolveParticle();
        });
        if (initial) baseline = outcome.baseline();
        runtime = next;
        tracking.apply(next.tracking());
        if (next.enchants().isEmpty()) {
            getSLF4JLogger().warn("No enchantment configs loaded; custom enchant triggers will not work.");
        }
        TillAction.validateOverlap(next.enchants(), getSLF4JLogger());
        return outcome.report();
    }
}
