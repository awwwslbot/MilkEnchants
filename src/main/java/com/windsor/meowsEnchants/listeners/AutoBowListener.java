package com.windsor.meowsEnchants.listeners;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * 自动连射的会话清理：玩家松手、切物品、切手、死亡、退出时停止任务。
 */
public class AutoBowListener implements Listener {

    @EventHandler
    public void onStopUsing(PlayerStopUsingItemEvent event) {
        AutoBowManager.onStopUsing(event.getPlayer());
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        AutoBowManager.stop(event.getPlayer());
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        AutoBowManager.stop(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        AutoBowManager.stop(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        AutoBowManager.stop(event.getPlayer());
    }
}
