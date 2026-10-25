package com.windsor.meowsEnchants;

import com.windsor.meowsEnchants.actions.impl.TillAction;
import com.windsor.meowsEnchants.arrowrain.ArrowRainConfig;
import com.windsor.meowsEnchants.arrowrain.ArrowRainListener;
import com.windsor.meowsEnchants.commands.EnchantsCommand;
import com.windsor.meowsEnchants.config.EnchantConfig;
import com.windsor.meowsEnchants.config.EnchantConfigLoader;
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
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class MeowsEnchants extends JavaPlugin {

    private static MeowsEnchants instance;
    private static Map<Key, EnchantConfig> enchantConfigs = new HashMap<>();
    private static boolean skylliaCompatibilityEnabled;
    private static boolean enchantingTableUsePlayerLevel;
    private static double enchantingTablePlayerLevelFactor = 500.0;
    private static boolean enchantingTableBalanceBooks;

    private EnchantBrowser enchantBrowser;
    private ArrowRainListener arrowRain;
    private TrackingListener tracking;

    public static MeowsEnchants getInstance() {
        return instance;
    }

    public static void setEnchantConfigs(Map<Key, EnchantConfig> configs) {
        enchantConfigs = configs;
    }

    public static Map<Key, EnchantConfig> getEnchantConfigs() {
        return enchantConfigs;
    }

    public static boolean isSkylliaCompatibilityEnabled() {
        return skylliaCompatibilityEnabled;
    }

    public static boolean isEnchantingTableUsePlayerLevel() {
        return enchantingTableUsePlayerLevel;
    }

    public static double getEnchantingTablePlayerLevelFactor() {
        return enchantingTablePlayerLevelFactor;
    }

    public static boolean isEnchantingTableBalanceBooks() {
        return enchantingTableBalanceBooks;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadRuntimeConfig();
        reloadEnchantConfigs();

        getServer().getPluginManager().registerEvents(new CombatListener(), this);
        getServer().getPluginManager().registerEvents(new BlockInteractionListener(), this);
        getServer().getPluginManager().registerEvents(new ProjectileListener(), this);
        getServer().getPluginManager().registerEvents(new FishingListener(), this);
        PlayerStateListener playerStateListener = new PlayerStateListener(this);
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
        arrowRain = ArrowRainConfig.createListener(this);
        if (arrowRain != null) {
            getServer().getPluginManager().registerEvents(arrowRain, this);
        }
        tracking = TrackingConfig.createListener(this);
        if (tracking != null) {
            getServer().getPluginManager().registerEvents(tracking, this);
            getServer().getPluginManager().registerEvents(tracking.combat(), this);
            if (arrowRain != null) arrowRain.setRainObserver(tracking);
        }

        enchantBrowser = new EnchantBrowser(this);
        getServer().getPluginManager().registerEvents(enchantBrowser, this);
        enchantBrowser.cleanupStaleViews();
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("enchants", "打开附魔查询界面", new EnchantsCommand(enchantBrowser)));

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
        // 清空静态配置表，避免热重载/卸载后旧的 EnchantConfig 仍被类加载器外的引用持有
        enchantConfigs = new HashMap<>();
        instance = null;
    }

    /**
     * 重新读取磁盘上的附魔配置。
     * <p>
     * bootstrap 只在服务端启动时执行一次，PlugmanX 热重载后不会再次执行；若不在
     * onEnable 重新读取，配置表将为空，所有自定义附魔的触发器都会失效。
     */
    private void reloadEnchantConfigs() {
        Map<Key, EnchantConfig> loaded = EnchantConfigLoader.loadAll(
                EnchantConfigLoader.enchantDir(), getSLF4JLogger());
        if (loaded.isEmpty()) {
            getSLF4JLogger().warn("No enchantment configs loaded; custom enchant triggers will not work.");
            return;
        }
        loaded.values().forEach(config -> {
            config.resolveSound();
            config.resolveParticle();
        });
        setEnchantConfigs(loaded);
        TillAction.validateOverlap(loaded, getSLF4JLogger());
    }

    private void reloadRuntimeConfig() {
        reloadConfig();
        skylliaCompatibilityEnabled = getConfig().getBoolean("skyllia_compatibility", false);
        enchantingTableUsePlayerLevel = getConfig().getBoolean("enchanting_table.use_player_level", false);
        enchantingTablePlayerLevelFactor = getConfig().getDouble("enchanting_table.player_level_factor", 500.0);
        if (enchantingTablePlayerLevelFactor <= 0) {
            enchantingTablePlayerLevelFactor = 500.0;
        }
        enchantingTableBalanceBooks = getConfig().getBoolean("enchanting_table.balance_books", false);
    }
}
