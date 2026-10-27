package com.windsor.meowsEnchants.tracking;

import static com.windsor.meowsEnchants.tracking.TargetChoice.Priority.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bee;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.metadata.MetadataValue;
import org.junit.jupiter.api.Test;

class TargetRulesTest {
    private final World overworld = world("minecraft:overworld", "world");
    private final World pvpWorld = world("milk:pvp", "milk_pvp");
    private final Player shooterPlayer = player(overworld);
    private final Shooter.View shooter = new Shooter.View(shooterPlayer.getUniqueId(), shooterPlayer, new Location(overworld, 0, 64, 0));
    private final Set<UUID> fighting = new HashSet<>();
    private final Set<World> noPvp = new HashSet<>();
    private final TargetRules.Protection protection = mock(TargetRules.Protection.class);
    private final TargetRules rules = new TargetRules(TrackingSettings.DEFAULTS, fighting::contains, protection, w -> !noPvp.contains(w));

    TargetRulesTest() {
        when(protection.allowsHarm(any(), any())).thenReturn(true);
        when(protection.allowsPvp(any(), any(), any())).thenReturn(true);
        when(shooterPlayer.canSee(any(Player.class))).thenReturn(true);
    }

    @Test void monstersAreHostileIncludingSpidersGhastsAndShulkers() {
        for (Class<? extends Mob> type : List.of(Zombie.class, Skeleton.class, Spider.class, Ghast.class, Shulker.class)) {
            assertEquals(HOSTILE, rules.priority(mob(type), shooter), type.getSimpleName());
        }
    }

    @Test void neutralMonstersCountOnlyWhileAttackingAPlayer() {
        for (Class<? extends Mob> type : List.of(Enderman.class, PigZombie.class, Piglin.class)) {
            Mob calm = mob(type);
            assertNull(rules.priority(calm, shooter), type.getSimpleName() + " without a target");
            Mob fightingMob = mob(type);
            LivingEntity cow = mob(Cow.class);
            when(fightingMob.getTarget()).thenReturn(cow);
            assertNull(rules.priority(fightingMob, shooter), type.getSimpleName() + " attacking a mob");
            Mob angry = mob(type);
            Player victim = player(overworld);
            when(angry.getTarget()).thenReturn(victim);
            assertEquals(HOSTILE, rules.priority(angry, shooter), type.getSimpleName() + " attacking a player");
        }
    }

    @Test void neverTrackedAndNeutralListsComeFromTheConfig() {
        TrackingSettings settings = TrackingSettings.fromYaml(java.util.Map.of(
                "never_tracked", List.of("iron_golem"), "neutral_until_attacking", List.of("minecraft:piglin")));
        TargetRules configured = new TargetRules(settings, fighting::contains, protection, w -> true);
        assertEquals(FRIENDLY, configured.priority(mob(Villager.class), shooter), "villagers removed from never_tracked");
        assertNull(configured.priority(mob(IronGolem.class), shooter));
        assertEquals(HOSTILE, configured.priority(mob(Enderman.class), shooter), "calm endermen no longer neutral");
        assertNull(configured.priority(mob(Piglin.class), shooter), "calm piglins still neutral");
        TrackingSettings zombies = TrackingSettings.fromYaml(java.util.Map.of("neutral_until_attacking", List.of("ZOMBIE")));
        TargetRules exact = new TargetRules(zombies, fighting::contains, protection, w -> true);
        assertNull(exact.priority(mob(Zombie.class), shooter), "calm zombies are now neutral");
        assertEquals(HOSTILE, exact.priority(mob(org.bukkit.entity.Husk.class), shooter), "a listed type is matched exactly, not its subtypes");
        assertEquals(HOSTILE, exact.priority(mob(PigZombie.class), shooter), "zombified piglins left the list");
    }

    @Test void animalsIncludingNeutralOnesAreFriendly() {
        for (Class<? extends Mob> type : List.of(Cow.class, Wolf.class, Bee.class, PolarBear.class)) {
            assertEquals(FRIENDLY, rules.priority(mob(type), shooter), type.getSimpleName());
        }
    }

    @Test void excludedFriendlyMobsAreNeverTracked() {
        Wolf pet = mob(Wolf.class);
        when(pet.isTamed()).thenReturn(true);
        assertNull(rules.priority(pet, shooter));
        for (Class<? extends Mob> type : List.of(Villager.class, WanderingTrader.class, IronGolem.class, Snowman.class, CopperGolem.class, Allay.class)) {
            assertNull(rules.priority(mob(type), shooter), type.getSimpleName());
        }
        Cow leashed = mob(Cow.class);
        when(leashed.isLeashed()).thenReturn(true);
        assertNull(rules.priority(leashed, shooter));
        Cow ridden = mob(Cow.class);
        Player rider = player(overworld);
        doReturn(List.<Entity>of(rider)).when(ridden).getPassengers();
        assertNull(rules.priority(ridden, shooter));
        Cow carryingMob = mob(Cow.class);
        Zombie jockey = mob(Zombie.class);
        doReturn(List.<Entity>of(jockey)).when(carryingMob).getPassengers();
        assertEquals(FRIENDLY, rules.priority(carryingMob, shooter));
    }

    @Test void namedMobsAndNonMobsAreNeverTracked() {
        Zombie named = mob(Zombie.class);
        when(named.customName()).thenReturn(Component.text("Bob"));
        assertNull(rules.priority(named, shooter));
        assertTrue(TargetRules.isNamed(named));
        ArmorStand stand = living(ArmorStand.class);
        when(stand.customName()).thenReturn(Component.text("Sign"));
        assertNull(rules.priority(stand, shooter));
        assertFalse(TargetRules.isNamed(stand), "armour stands are not creatures");
        assertFalse(TargetRules.isNamed(null));
    }

    @Test void playersNeedRecentPvpOutsideThePvpWorld() {
        Player calm = player(overworld);
        assertNull(rules.priority(calm, shooter));
        fighting.add(calm.getUniqueId());
        assertEquals(PVP_PLAYER, rules.priority(calm, shooter));
        Player arena = player(pvpWorld);
        assertEquals(PVP_PLAYER, rules.priority(arena, shooter), "milk:pvp counts without combat");
    }

    @Test void shooterCreativeSpectatorVanishedAndNpcPlayersAreNeverTracked() {
        assertNull(rules.priority(shooterPlayer, shooter));
        for (GameMode mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
            Player p = player(pvpWorld);
            when(p.getGameMode()).thenReturn(mode);
            assertNull(rules.priority(p, shooter), mode.name());
        }
        Player hidden = player(pvpWorld);
        when(shooterPlayer.canSee(hidden)).thenReturn(false);
        assertNull(rules.priority(hidden, shooter));
        Player vanished = player(pvpWorld);
        MetadataValue flag = mock(MetadataValue.class);
        when(flag.asBoolean()).thenReturn(true);
        when(vanished.getMetadata("vanished")).thenReturn(List.of(flag));
        assertNull(rules.priority(vanished, shooter));
        Player npc = player(pvpWorld);
        when(npc.hasMetadata("NPC")).thenReturn(true);
        assertNull(rules.priority(npc, shooter));
    }

    @Test void pvpNeedsTheVanillaRuleAtBothEndsAndTheClaimPlugin() {
        Player target = player(overworld);
        fighting.add(target.getUniqueId());
        assertTrue(rules.trackable(target, shooter));
        noPvp.add(overworld);
        assertFalse(rules.trackable(target, shooter), "vanilla pvp off");
        noPvp.clear();
        Player elsewhere = player(pvpWorld);
        noPvp.add(pvpWorld);
        assertFalse(rules.trackable(elsewhere, shooter), "target world forbids pvp");
        noPvp.clear();
        when(protection.allowsPvp(shooterPlayer, shooter.location(), target)).thenReturn(false);
        assertFalse(rules.trackable(target, shooter), "claim forbids pvp");
        Shooter.View unknown = new Shooter.View(shooter.id(), shooterPlayer, null);
        assertFalse(rules.trackable(elsewhere, unknown), "no shot-time location");
    }

    @Test void claimsCanForbidHarmingMobs() {
        Cow cow = mob(Cow.class);
        Zombie zombie = mob(Zombie.class);
        when(protection.allowsHarm(shooterPlayer, cow)).thenReturn(false);
        assertFalse(rules.trackable(cow, shooter));
        assertTrue(rules.trackable(zombie, shooter));
        assertFalse(rules.trackable(mob(Villager.class), shooter));
    }

    static World world(String key, String name) {
        World world = mock(World.class);
        when(world.getKey()).thenReturn(NamespacedKey.fromString(key));
        when(world.getName()).thenReturn(name);
        return world;
    }

    static Player player(World world) {
        Player player = living(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        return player;
    }

    static <T extends Mob> T mob(Class<T> type) { return living(type); }

    static <T extends LivingEntity> T living(Class<T> type) {
        T entity = mock(type);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        // Like the server: the entity type whose class is exactly this interface.
        for (EntityType entityType : EntityType.values()) {
            if (entityType.getEntityClass() == type) when(entity.getType()).thenReturn(entityType);
        }
        return entity;
    }
}
