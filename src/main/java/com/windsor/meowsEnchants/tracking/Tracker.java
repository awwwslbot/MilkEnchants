package com.windsor.meowsEnchants.tracking;

import com.windsor.meowsEnchants.tracking.TargetChoice.Candidate;
import com.windsor.meowsEnchants.tracking.TargetChoice.Priority;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * One tracked projectile. Its task runs every tick on the projectile's own entity scheduler: an
 * entity search every scan interval, then (while locked) one steering step and one particle burst.
 * Fields below the volatile ones are touched only on that region thread, including from the
 * projectile's own hit event.
 */
final class Tracker {
    enum Kind { ARROW, FIREWORK, RAIN }

    final UUID id;
    final Projectile projectile;
    final Kind kind;
    final int level;
    final Shooter shooter;
    final Shooter.View view;
    final long createdNanos = System.nanoTime();
    private final TrackingListener owner;
    /** The listener's settings snapshot when this projectile was fired. */
    final TrackingListener.Runtime runtime;
    private final TrackingSettings settings;
    private final TargetRules rules;
    private final Runnable stopTrail;
    volatile boolean stopped;
    volatile ScheduledTask task;

    private final Set<UUID> hit = new HashSet<>();
    private LivingEntity target;
    private int age;
    private boolean trailReplaced;

    Tracker(TrackingListener owner, TrackingListener.Runtime runtime, Projectile projectile, Kind kind, int level,
            Shooter shooter, Location shotLocation, Runnable stopTrail) {
        this.owner = owner;
        this.runtime = runtime;
        this.settings = runtime.settings();
        this.rules = runtime.rules();
        this.id = projectile.getUniqueId();
        this.projectile = projectile;
        this.kind = kind;
        this.level = level;
        this.shooter = shooter;
        this.view = shooter.view(shotLocation);
        this.stopTrail = stopTrail;
    }

    void tick(ScheduledTask scheduled) {
        if (stopped || !owner.active() || !shooter.online || !projectile.isValid()
                || projectile instanceof AbstractArrow arrow && arrow.isInBlock()
                || age >= settings.maxTrackingTicks()) {
            stop();
            return;
        }
        try {
            if (age++ % settings.scanIntervalTicks() == 0) scan();
            if (target != null) steer();
        } catch (RuntimeException | LinkageError e) {
            // A failure in another plugin's hook must not leave a dead task rescheduled every tick.
            stop();
            owner.failed(e);
        }
    }

    private void scan() {
        Location location = projectile.getLocation();
        World world = location.getWorld();
        Vector position = location.toVector();
        Vector velocity = projectile.getVelocity();
        if (velocity.lengthSquared() < 1.0e-6) {
            target = null;
            return;
        }
        List<Vector> path = kind == Kind.FIREWORK
                ? PathGeometry.predictLinear(position, velocity, settings.lookaheadTicks())
                : PathGeometry.predictArrow(position, velocity, settings.lookaheadTicks(), projectile.hasGravity(), projectile.isInWater());
        double radius = settings.radius(level);
        double giveUp = settings.giveUpAngleRadians();
        List<Candidate> candidates = new ArrayList<>();
        Map<UUID, LivingEntity> found = new HashMap<>();
        for (Entity entity : RegionView.nearbyOwned(world, PathGeometry.bounds(path, radius))) {
            if (!(entity instanceof LivingEntity living) || hit.contains(entity.getUniqueId())) continue;
            BoundingBox box = entity.getBoundingBox();
            if (PathGeometry.pathBoxDistance(path, box) > radius) continue;
            Vector centre = box.getCenter();
            // A target already behind the projectile would be dropped by the next steering step.
            if (PathGeometry.angle(velocity, centre.clone().subtract(position)) > giveUp) continue;
            Priority priority = rules.priority(living, view);
            if (priority == null || !RegionView.visible(world, position, centre) || !rules.permitted(living, priority, view)) continue;
            candidates.add(new Candidate(entity.getUniqueId(), priority, PathGeometry.pointBoxDistance(position, box)));
            found.put(entity.getUniqueId(), living);
        }
        Candidate chosen = TargetChoice.choose(target == null ? null : target.getUniqueId(), candidates);
        LivingEntity previous = target;
        target = chosen == null ? null : found.get(chosen.id());
        if (target != null && (previous == null || !previous.getUniqueId().equals(chosen.id()))) locked(world);
    }

    /** A new lock or a switch to another target. */
    private void locked(World world) {
        if (kind == Kind.RAIN) {
            if (!trailReplaced && stopTrail != null) stopTrail.run(); // Rain locks are silent.
            trailReplaced = true;
        } else {
            owner.chime(runtime, shooter, world.getGameTime());
        }
    }

    private void steer() {
        LivingEntity current = target;
        if (!Bukkit.isOwnedByCurrentRegion(current) || !current.isValid() || current.isDead()
                || current.getWorld() != projectile.getWorld()) {
            target = null;
            return;
        }
        Location location = projectile.getLocation();
        Vector toTarget = current.getBoundingBox().getCenter().subtract(location.toVector());
        Vector next = nextVelocity(projectile.getVelocity(), toTarget, settings);
        if (next == null) {
            target = null; // Behind the projectile: the next scan looks for another target.
            return;
        }
        projectile.setVelocity(next);
        // setVelocity leaves the rotation alone; keep the model along the new flight direction.
        projectile.setRotation(PathGeometry.yaw(next), PathGeometry.pitch(next));
        RotationSync.mark(projectile, owner.logger());
        owner.emit(runtime, location.subtract(next.clone().normalize().multiply(settings.tailOffset())));
    }

    /**
     * The next velocity towards the target's body centre: at most the configured turn per tick,
     * aimed straight within the direct-aim distance, same speed. Null gives the target up because it
     * is behind the projectile (or the projectile has stopped).
     */
    static Vector nextVelocity(Vector velocity, Vector toTarget, TrackingSettings settings) {
        if (velocity.lengthSquared() < 1.0e-8) return null;
        if (toTarget.lengthSquared() < 1.0e-8) return velocity.clone();
        if (PathGeometry.angle(velocity, toTarget) > settings.giveUpAngleRadians()) return null;
        boolean direct = toTarget.length() <= settings.directAimDistance();
        return PathGeometry.steer(velocity, toTarget, settings.maxTurnRadians(), direct);
    }

    /**
     * The projectile hit something. A piercing arrow remembers the entity and keeps tracking the next
     * one it has not hit; anything else stops tracking.
     */
    void hit(Entity entity) {
        if (entity != null && kind != Kind.FIREWORK && projectile instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
            hit.add(entity.getUniqueId());
            if (target != null && target.getUniqueId().equals(entity.getUniqueId())) target = null;
            return;
        }
        stop();
    }

    /** Safe from any thread: the task stops itself and Folia task cancellation is thread-safe. */
    void stop() {
        stopped = true;
        owner.stopped(this);
        ScheduledTask current = task;
        if (current != null) current.cancel();
    }

    LivingEntity target() { return target; }
}
