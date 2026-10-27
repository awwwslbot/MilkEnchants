package com.windsor.meowsEnchants.listeners;

import com.windsor.meowsEnchants.config.Trigger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

public class FishingListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onFishing(PlayerFishEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH ||
                event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            EnchantTriggerHelper.checkAndTrigger(player, Trigger.FISHING.name(),
                    EnchantTriggerHelper.getSlotsForTrigger(player),
                    event, null);
        } else if (event.getState() == PlayerFishEvent.State.BITE) {
            EnchantTriggerHelper.checkAndTrigger(player, Trigger.FISH_BITE.name(),
                    EnchantTriggerHelper.getSlotsForTrigger(player),
                    event, null);
        }
    }
}