package com.windsor.meowsEnchants.actions.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.windsor.meowsEnchants.actions.ActionContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 挪进配置的常量在缺省时，动作的实际效果和 milk.12 一样（数值见 MovedConstantsTest）。 */
class ActionDefaultsBehaviourTest {
    private static final Key KEY = Key.key("meowsenchants", "probe");

    private static ActionContext context(Player player, int level, List<Entity> targets, Object event) {
        return new ActionContext(null, player, KEY, level, "TEST", targets,
                (org.bukkit.event.Event) event, null, "SELF");
    }

    @Test void velocityKeepsTheQuarterVerticalScale() {          // jetpack: power 5+1/级 → 向上 1.25 / 1.5 / 1.75
        for (int level = 1; level <= 3; level++) {
            Player player = mock(Player.class);
            when(player.isOnGround()).thenReturn(true);
            new VelocityAction(Map.of("direction", "UP", "power", Map.of("base", 5.0, "per_level", 1.0)))
                    .execute(context(player, level, List.of(player), null));
            ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
            verify(player).setVelocity(velocity.capture());
            assertEquals(new Vector(0, 1.25 + 0.25 * (level - 1), 0), velocity.getValue());
        }
    }

    @Test void velocityStillReadsTheCamelCaseOnGroundKey() {
        Player player = mock(Player.class);
        when(player.isOnGround()).thenReturn(false);
        when(player.getLocation()).thenReturn(new Location(null, 0, 100, 0));
        // onGround: false（leap.yml 的写法）允许在空中执行
        new VelocityAction(Map.of("direction", "UP", "power", 4.0, "onGround", false))
                .execute(context(player, 1, List.of(player), null));
        verify(player).setVelocity(new Vector(0, 1.0, 0));
    }

    @Test void feastRestoresOneFoodAndOneSaturation() {
        Player player = mock(Player.class);
        when(player.getFoodLevel()).thenReturn(10);
        when(player.getSaturation()).thenReturn(2.0f);
        assertTrue(new FeastAction(Map.of("amount", 1.0)).execute(context(player, 1, List.of(player), null)));
        verify(player).setFoodLevel(11);
        verify(player).setSaturation(3.0f);
    }

    @Test void healthstealIsCappedAtTheFullHit() {
        Player player = mock(Player.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(mock(LivingEntity.class));
        when(event.getDamage()).thenReturn(10.0);
        assertTrue(new HealthstealAction(Map.of("amount", 2.5)).execute(context(player, 1, List.of(), event)));
        verify(player).heal(10.0);
    }

    @Test void reflectionReturnsTheFullHit() {
        Player victim = mock(Player.class);
        LivingEntity attacker = mock(LivingEntity.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamage()).thenReturn(7.0);
        assertTrue(new ReflectDamageAction(Map.of("percentage", 1.0)).execute(context(victim, 1, List.of(), event)));
        verify(event).setCancelled(true);
        verify(attacker).damage(7.0, victim);
    }

    @Test void dropHeadMapsTheSameSixMobs() {
        DropHeadAction action = new DropHeadAction(Map.of("amount", 1.0));
        assertEquals(Material.CREEPER_HEAD, action.headMaterial(EntityType.CREEPER));
        assertEquals(Material.ZOMBIE_HEAD, action.headMaterial(EntityType.ZOMBIE));
        assertEquals(Material.SKELETON_SKULL, action.headMaterial(EntityType.SKELETON));
        assertEquals(Material.WITHER_SKELETON_SKULL, action.headMaterial(EntityType.WITHER_SKELETON));
        assertEquals(Material.PIGLIN_HEAD, action.headMaterial(EntityType.PIGLIN));
        assertEquals(Material.DRAGON_HEAD, action.headMaterial(EntityType.ENDER_DRAGON));
        for (EntityType other : new EntityType[]{EntityType.HUSK, EntityType.COW, EntityType.PLAYER, EntityType.STRAY}) {
            assertNull(action.headMaterial(other), other.name());
        }
        DropHeadAction custom = new DropHeadAction(Map.of("amount", 1.0, "heads", Map.of("minecraft:husk", "zombie_head")));
        assertEquals(Material.ZOMBIE_HEAD, custom.headMaterial(EntityType.HUSK));
        assertNull(custom.headMaterial(EntityType.CREEPER), "a configured table replaces the default one");
    }

    @Test void dropHeadForAPlayerStillCountsAsFailureByDefault() {
        Player killer = mock(Player.class);
        Player victim = mock(Player.class);
        World world = mock(World.class);
        when(victim.getWorld()).thenReturn(world);
        when(victim.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        // milk.12：掉了玩家头也返回 false（不进冷却、不播效果）；ItemStack 需要服务端，这里只验证返回值
        DropHeadAction action = new DropHeadAction(Map.of("amount", 1.0, "player_heads", false));
        assertFalse(action.execute(context(killer, 1, List.of(victim), null)));
        verifyNoInteractions(world);
    }

    @Test void replantKnowsTheSameCropsAndSoils() {
        var crops = ReplantAction.parseCrops(ReplantAction.CROPS.defaultValue());
        assertEquals(Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART),
                crops.keySet());
        assertEquals(Material.WHEAT_SEEDS, crops.get(Material.WHEAT).seedMaterial());
        assertEquals(Material.CARROT, crops.get(Material.CARROTS).seedMaterial());
        assertEquals(Material.POTATO, crops.get(Material.POTATOES).seedMaterial());
        assertEquals(Material.BEETROOT_SEEDS, crops.get(Material.BEETROOTS).seedMaterial());
        assertEquals(Material.NETHER_WART, crops.get(Material.NETHER_WART).seedMaterial());
        for (Material crop : List.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS)) {
            assertEquals(Set.of(Material.FARMLAND), crops.get(crop).soil());
        }
        assertEquals(Set.of(Material.SOUL_SAND, Material.SOUL_SOIL), crops.get(Material.NETHER_WART).soil());
    }

    @Test void autoBowFiresAtTheVanillaFullDraw() {
        for (int ticks = 0; ticks <= 30; ticks++) {
            double t = ticks / 20.0;
            double vanilla = Math.min(1.0, Math.max(0.0, (t * t + 2.0 * t) / 3.0));   // AutoBowAction.java:63-67
            assertEquals(vanilla, AutoBowAction.powerForTicks(ticks, AutoBowAction.FULL_DRAW_TICKS.defaultValue()), 0.0);
        }
        assertEquals(20, new AutoBowAction(Map.of()).getFullDrawTicks());
    }

    @Test void hookPullsWithTheOldFalloffAndAngle() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        LivingEntity target = mock(LivingEntity.class);
        when(player.getLocation()).thenAnswer(i -> new Location(world, 0, 64, 0));
        when(target.getLocation()).thenAnswer(i -> new Location(world, 3, 64, 0));
        assertTrue(new HookAction(Map.of("power", 2.0)).execute(context(player, 1, List.of(target), null)));
        ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
        verify(target).setVelocity(velocity.capture());
        double magnitude = 2.0 / Math.pow(5.0 - 3.0, 2);                 // HookAction.java:46-49
        double c = Math.cos(Math.toRadians(45));
        assertEquals(-c * magnitude, velocity.getValue().getX(), 1e-12);  // towards the player
        assertEquals(c * magnitude, velocity.getValue().getY(), 1e-12);
        assertEquals(0.0, velocity.getValue().getZ(), 1e-12);
    }

    @Test void deflectWithoutAmountAlwaysDeflects() {
        for (int i = 0; i < 200; i++) {
            Fixture f = new Fixture();
            assertTrue(new DeflectAction(Map.of()).execute(f.context()));
            verify(f.event).setCancelled(true);
            Vector out = f.deflected();
            assertEquals(1.0, out.length(), 1e-9, "speed kept");
            // 竖直方向在反方向基础上随机旋转 ±30°（水平任意）
            double pitch = Math.toDegrees(Math.asin(out.getY() / out.length()));
            assertTrue(Math.abs(pitch) <= 30.0 + 1e-9, "pitch " + pitch);
        }
    }

    @Test void deflectAmountIsAProbability() {
        Fixture never = new Fixture();
        assertFalse(new DeflectAction(Map.of("amount", 0.0)).execute(never.context()));
        verify(never.event, never()).setCancelled(true);
        verify(never.projectile, never()).setVelocity(any());
        int deflected = 0;
        for (int i = 0; i < 4000; i++) {
            Fixture f = new Fixture();
            if (new DeflectAction(Map.of("amount", Map.of("base", 0.2, "per_level", 0.2))).execute(f.context(2))) deflected++;
        }
        assertEquals(0.4, deflected / 4000.0, 0.04, "level II of the bundled deflection.yml deflects 40%");
    }

    @Test void bonusDamageNightWindowIsUnchanged() {
        for (long time : new long[]{12999, 13000, 18000, 23000, 23001}) {
            Player player = mock(Player.class);
            World world = mock(World.class);
            when(player.getWorld()).thenReturn(world);
            when(world.getTime()).thenReturn(time);
            EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
            when(event.getEntity()).thenReturn(mock(LivingEntity.class));
            when(event.getDamage()).thenReturn(4.0);
            boolean applied = new BonusDamageAction(Map.of("amount", 2.0, "conditions", Map.of("night_time", true)))
                    .execute(context(player, 1, List.of(), event));
            assertEquals(time >= 13000 && time <= 23000, applied, "time " + time);
        }
    }

    private static final class Fixture {
        final Player victim = mock(Player.class);
        final Projectile projectile = mock(Projectile.class);
        final EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);

        Fixture() {
            when(event.getDamager()).thenReturn(projectile);
            when(event.getEntity()).thenReturn(victim);
            when(projectile.getVelocity()).thenReturn(new Vector(1, 0, 0));
        }

        ActionContext context() { return context(1); }
        ActionContext context(int level) { return ActionDefaultsBehaviourTest.context(victim, level, List.of(victim), event); }

        Vector deflected() {
            ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
            verify(projectile).setVelocity(velocity.capture());
            return velocity.getValue();
        }
    }
}
