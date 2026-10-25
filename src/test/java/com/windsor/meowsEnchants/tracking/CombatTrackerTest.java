package com.windsor.meowsEnchants.tracking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

class CombatTrackerTest {
    private long now = 1_000L;
    private final CombatTracker combat = new CombatTracker(TimeUnit.SECONDS.toNanos(15), () -> now);
    private final Player alice = player(), bob = player();

    @Test void meleeMarksBothPlayersForFifteenSeconds() {
        combat.onDamage(damage(alice, bob, 2.0));
        assertTrue(combat.inCombat(alice.getUniqueId()));
        assertTrue(combat.inCombat(bob.getUniqueId()));
        now += TimeUnit.SECONDS.toNanos(15);
        assertTrue(combat.inCombat(bob.getUniqueId()));
        now += 1;
        assertFalse(combat.inCombat(bob.getUniqueId()));
        assertFalse(combat.inCombat(alice.getUniqueId()));
    }

    @Test void arrowsFireworksAndTridentsCountForTheirShooter() {
        for (Class<? extends org.bukkit.entity.Projectile> type : java.util.List.of(Arrow.class, Firework.class, Trident.class)) {
            CombatTracker fresh = new CombatTracker(TimeUnit.SECONDS.toNanos(15), () -> now);
            org.bukkit.entity.Projectile projectile = mock(type);
            when(projectile.getShooter()).thenReturn(alice);
            fresh.onDamage(damage(projectile, bob, 1.0));
            assertTrue(fresh.inCombat(alice.getUniqueId()), type.getSimpleName());
            assertTrue(fresh.inCombat(bob.getUniqueId()), type.getSimpleName());
        }
    }

    @Test void blockedSelfInflictedMobAndNpcDamageDoNotCount() {
        combat.onDamage(damage(alice, bob, 0.0));
        combat.onDamage(damage(alice, alice, 3.0));
        Skeleton skeleton = mock(Skeleton.class);
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(skeleton);
        combat.onDamage(damage(arrow, bob, 3.0));
        combat.onDamage(damage(alice, mock(Zombie.class), 3.0));
        Player npc = player();
        when(npc.hasMetadata("NPC")).thenReturn(true);
        combat.onDamage(damage(npc, bob, 3.0));
        assertFalse(combat.inCombat(alice.getUniqueId()));
        assertFalse(combat.inCombat(bob.getUniqueId()));
    }

    @Test void quitForgetsThePlayer() {
        combat.onDamage(damage(alice, bob, 2.0));
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(alice);
        combat.onQuit(quit);
        assertFalse(combat.inCombat(alice.getUniqueId()));
        assertTrue(combat.inCombat(bob.getUniqueId()));
    }

    private static EntityDamageByEntityEvent damage(Entity damager, Entity victim, double finalDamage) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(damager);
        when(event.getEntity()).thenReturn(victim);
        when(event.getFinalDamage()).thenReturn(finalDamage);
        return event;
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
