package com.windsor.meowsEnchants.tracking;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * One online session of a player who fired a tracking shot. Projectile threads read only these
 * thread-safe fields, never the player's live position, game mode or inventory.
 */
final class Shooter {
    final UUID id;
    final Player player;
    volatile boolean online = true;
    private final AtomicLong lastChimeTick = new AtomicLong(Long.MIN_VALUE);

    Shooter(Player player) {
        this.id = player.getUniqueId();
        this.player = player;
    }

    /**
     * Claims the lock chime for one region game tick. Projectiles of the same region read the same
     * game time during a tick, so multishot arrows locking together produce a single chime.
     */
    boolean claimChime(long gameTick) {
        long previous = lastChimeTick.get();
        return previous != gameTick && lastChimeTick.compareAndSet(previous, gameTick);
    }

    /** The view used by target rules for one projectile: the shot-time location never changes. */
    record View(UUID id, Player player, Location location) {}

    View view(Location shotLocation) { return new View(id, player, shotLocation); }
}
