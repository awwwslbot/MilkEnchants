package com.windsor.meowsEnchants.tracking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.windsor.meowsEnchants.arrowrain.ArrowRainListener;
import com.windsor.meowsEnchants.listeners.MechanicCooldown;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class TrackingListenerTest {
    static final MechanicCooldown NO_COOLDOWN = new MechanicCooldown(key -> 0, System::currentTimeMillis);
    private final Plugin plugin = mock(Plugin.class);
    private final World world = TargetRulesTest.world("minecraft:overworld", "world");
    private final CombatTracker combat = new CombatTracker(TimeUnit.SECONDS.toNanos(15));
    /** A no-PvP claim covers x >= 400: shots fired from there may not track players. */
    private final TargetRules.Protection claims = new TargetRules.Protection() {
        @Override public boolean allowsPvp(Player shooter, Location shooterLocation, Player target) { return shooterLocation.getX() < 400; }
        @Override public boolean allowsHarm(Player shooter, org.bukkit.entity.Mob target) { return true; }
    };
    private final TargetRules rules = new TargetRules(TrackingSettings.DEFAULTS, combat, claims, w -> true);
    private final Sound chime = Sound.sound(Key.key("block.note_block.chime"), Sound.Source.PLAYER, 1f, 1f);
    private final TrackingListener listener = new TrackingListener(plugin, TrackingSettings.DEFAULTS, combat, rules, Particle.ENCHANT, chime, NO_COOLDOWN);
    private final Player player = TargetRulesTest.player(world);
    private final EntityScheduler playerScheduler = mock(EntityScheduler.class);
    private final Location shotLocation = new Location(world, 0, 64, 0);
    private final List<Entity> nearby = new ArrayList<>();

    TrackingListenerTest() {
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.isEnabled()).thenReturn(true);
        when(player.getLocation()).thenAnswer(invocation -> shotLocation.clone());
        when(player.getScheduler()).thenReturn(playerScheduler);
        when(player.canSee(any(Player.class))).thenReturn(true);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getGameTime()).thenReturn(100L);
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(nearby);
    }

    @Test void enchantLevelReadsBowsAndCrossbowsOnly() {
        assertEquals(2, TrackingListener.enchantLevel(weapon(Material.BOW, 2)));
        assertEquals(3, TrackingListener.enchantLevel(weapon(Material.CROSSBOW, 3)));
        assertEquals(0, TrackingListener.enchantLevel(weapon(Material.TRIDENT, 3)));
        assertEquals(0, TrackingListener.enchantLevel(weapon(Material.BOW, 0)));
        assertEquals(0, TrackingListener.enchantLevel(null));
    }

    @Test void aShotIsMarkedAtTheShootEventAndTrackedOnlyAfterASuccessfulLaunch() {
        Shot shot = shoot(Arrow.class, Material.BOW, 2);
        verify(shot.pdc).set(TrackingListener.MARK, PersistentDataType.BYTE, (byte) 2);
        verify(shot.scheduler, never()).runAtFixedRate(any(), any(), any(), anyLong(), anyLong());
        launch(shot.projectile, false);
        verify(shot.scheduler).runAtFixedRate(eq(plugin), any(), any(), eq(1L), eq(1L));
        Tracker tracker = listener.tracker(shot.projectile.getUniqueId());
        assertEquals(Tracker.Kind.ARROW, tracker.kind);
        assertEquals(2, tracker.level);
        assertEquals(shotLocation, tracker.view.location());

        Shot cancelled = shoot(Arrow.class, Material.BOW, 1);
        launch(cancelled.projectile, true);
        verify(cancelled.scheduler, never()).runAtFixedRate(any(), any(), any(), anyLong(), anyLong());
        assertNull(listener.tracker(cancelled.projectile.getUniqueId()));
    }

    @Test void crossbowFireworksAndEachMultishotArrowAreTrackedSeparately() {
        Shot firework = shoot(Firework.class, Material.CROSSBOW, 3);
        launch(firework.projectile, false);
        assertEquals(Tracker.Kind.FIREWORK, listener.tracker(firework.projectile.getUniqueId()).kind);
        List<Tracker> volley = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Shot arrow = shoot(Arrow.class, Material.CROSSBOW, 1);
            launch(arrow.projectile, false);
            volley.add(listener.tracker(arrow.projectile.getUniqueId()));
        }
        assertEquals(3, volley.stream().map(t -> t.id).distinct().count());
        assertTrue(volley.stream().allMatch(t -> t.shooter == volley.getFirst().shooter));
    }

    @Test void skeletonsTridentsRainArrowsAndPlainBowsAreNotMarked() {
        EntityShootBowEvent skeletonShot = shootEvent(mock(Skeleton.class), projectile(Arrow.class).projectile, weapon(Material.BOW, 3));
        listener.onShoot(skeletonShot);
        Shot trident = projectile(Trident.class);
        listener.onShoot(shootEvent(player, trident.projectile, weapon(Material.BOW, 3)));
        Shot rain = projectile(Arrow.class);
        when(rain.pdc.has(ArrowRainListener.GENERATED, PersistentDataType.BYTE)).thenReturn(true);
        listener.onShoot(shootEvent(player, rain.projectile, weapon(Material.BOW, 3)));
        Shot plain = projectile(Arrow.class);
        listener.onShoot(shootEvent(player, plain.projectile, weapon(Material.BOW, 0)));
        for (Shot shot : List.of(trident, rain, plain)) verify(shot.pdc, never()).set(any(), any(), any());
        verify(skeletonShot, never()).getBow();
    }

    @Test void rainArrowsUseTheLevelAndShooterPositionOfTheShotThatStartedTheRain() {
        Shot source = shoot(Arrow.class, Material.BOW, 3);
        launch(source.projectile, false);
        Location rainShot = shotLocation.clone();
        // The shooter walks into a claim and fires again before this rain arrow spawns.
        shotLocation.setX(500);
        Shot later = shoot(Arrow.class, Material.BOW, 3);
        launch(later.projectile, false);
        Shot rain = projectile(Arrow.class);
        Runnable stopTrail = mock(Runnable.class);
        listener.rainSpawned((Arrow) rain.projectile, player, weapon(Material.BOW, 2), rainShot, stopTrail);
        verify(rain.pdc).set(TrackingListener.MARK, PersistentDataType.BYTE, (byte) 2);
        Tracker tracker = listener.tracker(rain.projectile.getUniqueId());
        assertEquals(Tracker.Kind.RAIN, tracker.kind);
        assertEquals(2, tracker.level);
        assertEquals(rainShot, tracker.view.location());
        assertEquals(500, listener.tracker(later.projectile.getUniqueId()).view.location().getX());

        Shot untracked = projectile(Arrow.class);
        listener.rainSpawned((Arrow) untracked.projectile, player, weapon(Material.BOW, 0), rainShot, stopTrail);
        verify(untracked.pdc, never()).set(any(), any(), any());
        Player stranger = TargetRulesTest.player(world);
        Shot orphan = projectile(Arrow.class);
        listener.rainSpawned((Arrow) orphan.projectile, stranger, weapon(Material.BOW, 1), rainShot, stopTrail);
        verify(orphan.pdc).set(TrackingListener.MARK, PersistentDataType.BYTE, (byte) 1);
        assertNull(listener.tracker(orphan.projectile.getUniqueId()), "never fired a tracking shot this session");
    }

    @Test void markedProjectilesPassThroughNamedMobsOnly() {
        Shot marked = projectile(Arrow.class);
        when(marked.pdc.has(TrackingListener.MARK, PersistentDataType.BYTE)).thenReturn(true);
        Zombie named = TargetRulesTest.mob(Zombie.class);
        when(named.customName()).thenReturn(Component.text("Bob"));
        ProjectileHitEvent namedHit = hit(marked.projectile, named);
        listener.onHitNamed(namedHit);
        verify(namedHit).setCancelled(true);
        ProjectileHitEvent plainHit = hit(marked.projectile, TargetRulesTest.mob(Zombie.class));
        listener.onHitNamed(plainHit);
        verify(plainHit, never()).setCancelled(anyBoolean());
        ProjectileHitEvent unmarkedHit = hit(projectile(Arrow.class).projectile, named);
        listener.onHitNamed(unmarkedHit);
        verify(unmarkedHit, never()).setCancelled(anyBoolean());
        ProjectileHitEvent blockHit = hit(marked.projectile, null);
        listener.onHitNamed(blockHit);
        verify(blockHit, never()).setCancelled(anyBoolean());
    }

    @Test void trackingFireworkBlastsOnlyHurtTrackableTargets() {
        Shot firework = shoot(Firework.class, Material.CROSSBOW, 1);
        launch(firework.projectile, false);
        when(firework.pdc.has(TrackingListener.MARK, PersistentDataType.BYTE)).thenReturn(true);
        Zombie named = TargetRulesTest.mob(Zombie.class);
        when(named.customName()).thenReturn(Component.text("Bob"));
        Player bystander = TargetRulesTest.player(world);
        for (Entity spared : List.<Entity>of(named, TargetRulesTest.mob(Villager.class), TargetRulesTest.mob(CopperGolem.class), player, bystander)) {
            EntityDamageByEntityEvent event = damage(firework.projectile, spared);
            listener.onDamage(event);
            verify(event).setCancelled(true);
        }
        EntityDamageByEntityEvent zombie = damage(firework.projectile, TargetRulesTest.mob(Zombie.class));
        listener.onDamage(zombie);
        verify(zombie, never()).setCancelled(anyBoolean());
        combat.onDamage(damage(bystander, TargetRulesTest.player(world)));
        EntityDamageByEntityEvent fighter = damage(firework.projectile, bystander);
        listener.onDamage(fighter);
        verify(fighter, never()).setCancelled(anyBoolean());
        EntityDamageByEntityEvent arrowOnVillager = damage(marked(Arrow.class), TargetRulesTest.mob(Villager.class));
        listener.onDamage(arrowOnVillager);
        verify(arrowOnVillager, never()).setCancelled(anyBoolean());
        EntityDamageByEntityEvent arrowOnNamed = damage(marked(Arrow.class), named);
        listener.onDamage(arrowOnNamed);
        verify(arrowOnNamed).setCancelled(true);
    }

    @Test void aFireworkBlastUsesItsOwnShotRecordAfterItStoppedTracking() {
        Player fighter = TargetRulesTest.player(world);
        combat.onDamage(damage(fighter, TargetRulesTest.player(world)));
        // Rocket 1 from the wilderness; then the shooter walks into the claim and fires again.
        Shot wild = fireworkThatHit(0);
        launch(shoot(Arrow.class, Material.BOW, 1).projectile, false); // shot from x = 400
        assertNull(listener.tracker(wild.projectile.getUniqueId()), "tracking stopped at the hit");
        EntityDamageByEntityEvent pvp = damage(wild.projectile, fighter);
        listener.onDamage(pvp);
        verify(pvp, never()).setCancelled(anyBoolean());
        EntityDamageByEntityEvent zombie = damage(wild.projectile, TargetRulesTest.mob(Zombie.class));
        listener.onDamage(zombie);
        verify(zombie, never()).setCancelled(anyBoolean());
        EntityDamageByEntityEvent villager = damage(wild.projectile, TargetRulesTest.mob(Villager.class));
        listener.onDamage(villager);
        verify(villager).setCancelled(true);

        // Rocket 2 from inside the claim; then the shooter steps out and fires again.
        Shot claimed = fireworkThatHit(400);
        shotLocation.setX(0);
        launch(shoot(Arrow.class, Material.BOW, 1).projectile, false);
        EntityDamageByEntityEvent fromClaim = damage(claimed.projectile, fighter);
        listener.onDamage(fromClaim);
        verify(fromClaim).setCancelled(true);
    }

    /** Fires a tracking rocket from x, lets it hit (which stops tracking) and moves the shooter to x = 400. */
    private Shot fireworkThatHit(double x) {
        shotLocation.setX(x);
        Shot firework = shoot(Firework.class, Material.CROSSBOW, 1);
        launch(firework.projectile, false);
        when(firework.pdc.has(TrackingListener.MARK, PersistentDataType.BYTE)).thenReturn(true);
        listener.onHit(hit(firework.projectile, TargetRulesTest.mob(Zombie.class)));
        shotLocation.setX(400);
        return firework;
    }

    @Test void shotRecordsLastUntilTheProjectileIsRemovedOrItsSchedulerRetires() {
        Shot removed = fireworkThatHit(0);
        UUID removedId = removed.projectile.getUniqueId();
        assertNull(listener.tracker(removedId));
        assertTrue(listener.hasShotRecord(removedId), "kept after tracking stopped");
        EntityRemoveEvent remove = mock(EntityRemoveEvent.class);
        when(remove.getEntity()).thenReturn(removed.projectile);
        listener.onRemove(remove);
        assertFalse(listener.hasShotRecord(removedId));
        EntityDamageByEntityEvent late = damage(removed.projectile, TargetRulesTest.mob(Zombie.class));
        listener.onDamage(late);
        verify(late).setCancelled(true);

        Shot retired = shoot(Arrow.class, Material.BOW, 1);
        launch(retired.projectile, false);
        UUID retiredId = retired.projectile.getUniqueId();
        assertTrue(listener.hasShotRecord(retiredId));
        retired.retire(); // Folia retires the entity scheduler when the entity is removed.
        assertFalse(listener.hasShotRecord(retiredId));
        assertNull(listener.tracker(retiredId));
    }

    @Test void aFireworkBlastHurtsNobodyAfterItsShooterLeftOrWithoutAShotRecord() {
        Shot firework = shoot(Firework.class, Material.CROSSBOW, 1);
        launch(firework.projectile, false);
        when(firework.pdc.has(TrackingListener.MARK, PersistentDataType.BYTE)).thenReturn(true);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        listener.onQuit(quit);
        EntityDamageByEntityEvent afterQuit = damage(firework.projectile, TargetRulesTest.mob(Zombie.class));
        listener.onDamage(afterQuit);
        verify(afterQuit).setCancelled(true);
        // A marked rocket this listener never saw launch (fired before a reload).
        Entity unknown = marked(Firework.class);
        EntityDamageByEntityEvent noRecord = damage(unknown, TargetRulesTest.mob(Zombie.class));
        listener.onDamage(noRecord);
        verify(noRecord).setCancelled(true);
    }

    @Test void scanLocksTheHostileTargetSteersAndChimesOncePerTickForMultishot() {
        Zombie zombie = TargetRulesTest.mob(Zombie.class);
        standAt(zombie, 9, 63.5, 1.5);
        nearby.add(zombie);
        List<Shot> volley = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Shot shot = shoot(Arrow.class, Material.CROSSBOW, 1);
            launch(shot.projectile, false);
            volley.add(shot);
        }
        try (MockedStatic<Bukkit> bukkit = ownedRegion()) {
            for (Shot shot : volley) shot.tick();
        }
        for (Shot shot : volley) {
            assertSame(zombie, listener.tracker(shot.projectile.getUniqueId()).target());
            verify(shot.projectile).setVelocity(argThat(v -> Math.abs(v.length() - 3.0) < 1e-9
                    && Math.toDegrees(PathGeometry.angle(new Vector(3, 0, 0), v)) > 1));
            verify(shot.projectile).setRotation(anyFloat(), anyFloat());
        }
        verify(world, times(3)).spawnParticle(eq(Particle.ENCHANT), any(Location.class), intThat(c -> c >= 1 && c <= 2),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(playerScheduler, times(1)).run(eq(plugin), any(), isNull());
    }

    @Test void rainLocksReplaceTheCritTrailSilently() {
        Shot source = shoot(Arrow.class, Material.BOW, 3);
        launch(source.projectile, false);
        Zombie zombie = TargetRulesTest.mob(Zombie.class);
        standAt(zombie, 1.5, 55, 0);
        nearby.add(zombie);
        Shot rain = projectile(Arrow.class);
        when(rain.projectile.getVelocity()).thenAnswer(invocation -> new Vector(0, -2.5, 0));
        Runnable stopTrail = mock(Runnable.class);
        listener.rainSpawned((Arrow) rain.projectile, player, weapon(Material.BOW, 3), shotLocation.clone(), stopTrail);
        try (MockedStatic<Bukkit> bukkit = ownedRegion()) {
            rain.tick();
            rain.tick();
        }
        assertSame(zombie, listener.tracker(rain.projectile.getUniqueId()).target());
        verify(stopTrail, times(1)).run();
        verify(playerScheduler, never()).run(any(), any(), any());
        verify(world, atLeastOnce()).spawnParticle(eq(Particle.ENCHANT), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test void targetsBehindNamedOrBlockedAreNotLocked() {
        Zombie behind = TargetRulesTest.mob(Zombie.class);
        standAt(behind, -2, 63.5, 0.5);
        Zombie named = TargetRulesTest.mob(Zombie.class);
        standAt(named, 6, 64, 0);
        when(named.customName()).thenReturn(Component.text("Bob"));
        nearby.addAll(List.of(behind, named));
        Shot shot = shoot(Arrow.class, Material.BOW, 3);
        launch(shot.projectile, false);
        try (MockedStatic<Bukkit> bukkit = ownedRegion()) { shot.tick(); }
        assertNull(listener.tracker(shot.projectile.getUniqueId()).target());

        Zombie walled = TargetRulesTest.mob(Zombie.class);
        standAt(walled, 8, 64, 1);
        nearby.add(walled);
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(), anyBoolean()))
                .thenReturn(mock(RayTraceResult.class));
        Shot blocked = shoot(Arrow.class, Material.BOW, 3);
        launch(blocked.projectile, false);
        try (MockedStatic<Bukkit> bukkit = ownedRegion()) { blocked.tick(); }
        assertNull(listener.tracker(blocked.projectile.getUniqueId()).target());
        verify(shot.projectile, never()).setVelocity(any());
        verify(blocked.projectile, never()).setVelocity(any());
        verify(playerScheduler, never()).run(any(), any(), any());
    }

    @Test void piercingArrowsSkipHitTargetsWhileOthersStopOnTheFirstHit() {
        Zombie first = TargetRulesTest.mob(Zombie.class);
        standAt(first, 6, 63.5, 0);
        nearby.add(first);
        Shot piercing = shoot(Arrow.class, Material.CROSSBOW, 1);
        when(((Arrow) piercing.projectile).getPierceLevel()).thenReturn(2);
        launch(piercing.projectile, false);
        try (MockedStatic<Bukkit> bukkit = ownedRegion()) { piercing.tick(); }
        Tracker tracker = listener.tracker(piercing.projectile.getUniqueId());
        assertSame(first, tracker.target());
        listener.onHit(hit(piercing.projectile, first));
        assertNull(tracker.target());
        assertFalse(tracker.stopped);
        try (MockedStatic<Bukkit> bukkit = ownedRegion()) {
            for (int i = 0; i < 5; i++) piercing.tick();
        }
        assertNull(tracker.target(), "an entity already hit is never chosen again");

        Shot plain = shoot(Arrow.class, Material.BOW, 1);
        launch(plain.projectile, false);
        Tracker plainTracker = listener.tracker(plain.projectile.getUniqueId());
        listener.onHit(hit(plain.projectile, first));
        assertTrue(plainTracker.stopped);
        assertNull(listener.tracker(plain.projectile.getUniqueId()), "a stopped tracker leaves the active map");
        verify(plain.task).cancel();
    }

    @Test void trackingStopsAfterTenSecondsOnQuitAndOnDisable() {
        Shot timed = shoot(Arrow.class, Material.BOW, 1);
        launch(timed.projectile, false);
        try (MockedStatic<Bukkit> bukkit = ownedRegion()) {
            for (int i = 0; i < 200; i++) timed.tick();
            verify(timed.task, never()).cancel();
            timed.tick();
        }
        verify(timed.task).cancel();

        Shot quitter = shoot(Arrow.class, Material.BOW, 1);
        launch(quitter.projectile, false);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        listener.onQuit(quit);
        quitter.tick();
        verify(quitter.task).cancel();

        Shot open = shoot(Arrow.class, Material.BOW, 1);
        launch(open.projectile, false);
        listener.close();
        verify(open.task).cancel();
        assertNull(listener.tracker(open.projectile.getUniqueId()));
    }

    @Test void aFailingHookStopsOnlyThatProjectileAndLogsOnce() {
        TargetRules.Protection broken = mock(TargetRules.Protection.class);
        when(broken.allowsHarm(any(), any())).thenThrow(new NoClassDefFoundError("com/sk89q/worldedit/bukkit/WorldEditPlugin"));
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        TrackingListener fragile = new TrackingListener(plugin, TrackingSettings.DEFAULTS, combat,
                new TargetRules(TrackingSettings.DEFAULTS, combat, broken, w -> true), Particle.ENCHANT, chime, NO_COOLDOWN);
        Zombie zombie = TargetRulesTest.mob(Zombie.class);
        standAt(zombie, 9, 63.5, 1.5);
        nearby.add(zombie);
        List<Shot> shots = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Shot shot = projectile(Arrow.class);
            fragile.onShoot(shootEvent(player, shot.projectile, weapon(Material.BOW, 1)));
            ProjectileLaunchEvent launch = mock(ProjectileLaunchEvent.class);
            when(launch.getEntity()).thenReturn(shot.projectile);
            fragile.onLaunch(launch);
            shots.add(shot);
        }
        try (MockedStatic<Bukkit> bukkit = ownedRegion()) {
            for (Shot shot : shots) shot.tick();
        }
        for (Shot shot : shots) verify(shot.task).cancel();
        verify(logger, times(1)).log(eq(java.util.logging.Level.WARNING), anyString(), any(Throwable.class));
    }

    @Test void embeddedOrRemovedProjectilesStopTracking() {
        Shot embedded = shoot(Arrow.class, Material.BOW, 1);
        launch(embedded.projectile, false);
        when(((Arrow) embedded.projectile).isInBlock()).thenReturn(true);
        embedded.tick();
        verify(embedded.task).cancel();
        Shot removed = shoot(Arrow.class, Material.BOW, 1);
        launch(removed.projectile, false);
        when(removed.projectile.isValid()).thenReturn(false);
        removed.tick();
        verify(removed.task).cancel();
    }

    private MockedStatic<Bukkit> ownedRegion() {
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(any(World.class), anyInt(), anyInt())).thenReturn(true);
        bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
        return bukkit;
    }

    private void standAt(LivingEntity entity, double x, double y, double z) {
        when(entity.getBoundingBox()).thenAnswer(invocation -> new BoundingBox(x - 0.3, y, z - 0.3, x + 0.3, y + 1.95, z + 0.3));
        when(entity.isValid()).thenReturn(true);
        when(entity.getWorld()).thenReturn(world);
    }

    private Shot shoot(Class<? extends Projectile> type, Material weapon, int level) {
        Shot shot = projectile(type);
        listener.onShoot(shootEvent(player, shot.projectile, weapon(weapon, level)));
        return shot;
    }

    private void launch(Projectile projectile, boolean cancelled) {
        ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
        when(event.getEntity()).thenReturn(projectile);
        when(event.isCancelled()).thenReturn(cancelled);
        listener.onLaunch(event);
    }

    private Entity marked(Class<? extends Projectile> type) {
        Shot shot = projectile(type);
        when(shot.pdc.has(TrackingListener.MARK, PersistentDataType.BYTE)).thenReturn(true);
        return shot.projectile;
    }

    private Shot projectile(Class<? extends Projectile> type) {
        Projectile projectile = mock(type);
        when(projectile.getUniqueId()).thenReturn(UUID.randomUUID());
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(projectile.getPersistentDataContainer()).thenReturn(pdc);
        when(projectile.isValid()).thenReturn(true);
        when(projectile.getWorld()).thenReturn(world);
        when(projectile.hasGravity()).thenReturn(true);
        when(projectile.getLocation()).thenAnswer(invocation -> new Location(world, 0, 65, 0));
        when(projectile.getVelocity()).thenAnswer(invocation -> new Vector(3, 0, 0));
        Shot shot = new Shot(projectile);
        when(projectile.getScheduler()).thenReturn(shot.scheduler);
        return shot;
    }

    private static EntityShootBowEvent shootEvent(LivingEntity shooter, Entity projectile, ItemStack bow) {
        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        when(event.getEntity()).thenReturn(shooter);
        when(event.getProjectile()).thenReturn(projectile);
        when(event.getBow()).thenReturn(bow);
        return event;
    }

    private static ItemStack weapon(Material type, int level) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        Keyed tracking = mock(Keyed.class);
        when(tracking.getKey()).thenReturn(TrackingListener.ENCHANT);
        Keyed power = mock(Keyed.class);
        when(power.getKey()).thenReturn(NamespacedKey.minecraft("power"));
        doReturn(level == 0 ? Map.of(power, 5) : Map.of(power, 5, tracking, level)).when(item).getEnchantments();
        return item;
    }

    private static ProjectileHitEvent hit(Entity projectile, Entity target) {
        ProjectileHitEvent event = mock(ProjectileHitEvent.class);
        when(event.getEntity()).thenReturn((Projectile) projectile);
        when(event.getHitEntity()).thenReturn(target);
        return event;
    }

    private static EntityDamageByEntityEvent damage(Entity damager, Entity victim) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(damager);
        when(event.getEntity()).thenReturn(victim);
        when(event.getFinalDamage()).thenReturn(4.0);
        return event;
    }

    /** A mocked projectile with its entity scheduler; tick() runs the captured tracking task once. */
    private static final class Shot {
        final Projectile projectile;
        final PersistentDataContainer pdc;
        final EntityScheduler scheduler = mock(EntityScheduler.class);
        final ScheduledTask task = mock(ScheduledTask.class);
        Consumer<ScheduledTask> body;
        Runnable retired;

        @SuppressWarnings("unchecked")
        Shot(Projectile projectile) {
            this.projectile = projectile;
            this.pdc = projectile.getPersistentDataContainer();
            when(scheduler.runAtFixedRate(any(), any(), any(), anyLong(), anyLong())).thenAnswer(invocation -> {
                body = invocation.getArgument(1);
                retired = invocation.getArgument(2);
                return task;
            });
        }

        void tick() { body.accept(task); }
        void retire() { retired.run(); }
    }
}
