package com.windsor.meowsEnchants.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * 加速蓄力会话的清理。
 * <p>
 * 不监听 {@code PlayerStopUsingItemEvent}：{@code auto_bow} 自动发射时会
 * 触发该事件，如果在这里停止会话，下一轮就无法继续加速。
 * 玩家真正松手后，任务会在下一 tick 因为 activeItem 为空而自行停止。
 */
public class DrawAccelerateListener implements Listener {

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        DrawAccelerateManager.stop(event.getPlayer());
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        DrawAccelerateManager.stop(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        DrawAccelerateManager.stop(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        DrawAccelerateManager.stop(event.getPlayer());
    }
}
