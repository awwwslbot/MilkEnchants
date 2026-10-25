package com.windsor.meowsEnchants.tracking;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class SteeringTest {
    private static final TrackingSettings S = TrackingSettings.DEFAULTS;
    private static final double HALF_WIDTH = 0.3, HEIGHT = 1.95;

    @Test void farTargetsTurnTwentyDegreesPerTickAtTheSameSpeed() {
        Vector velocity = new Vector(3, 0, 0);
        Vector next = Tracker.nextVelocity(velocity, new Vector(10, 0, 8), S);
        assertNotNull(next);
        assertEquals(20.0, Math.toDegrees(PathGeometry.angle(velocity, next)), 1e-9);
        assertEquals(3.0, next.length(), 1e-12);
    }

    @Test void withinSixBlocksTheArrowAimsStraightAtTheBodyCentre() {
        Vector velocity = new Vector(3, 0, 0);
        Vector toTarget = new Vector(4, 0, 4); // 5.66 blocks away, 45 degrees off.
        Vector next = Tracker.nextVelocity(velocity, toTarget, S);
        assertEquals(0.0, PathGeometry.angle(next, toTarget), 1e-9);
        assertEquals(3.0, next.length(), 1e-12);
    }

    @Test void aTargetBehindTheArrowIsGivenUp() {
        Vector velocity = new Vector(3, 0, 0);
        assertNull(Tracker.nextVelocity(velocity, new Vector(-1, 0, 0.5), S));
        assertNull(Tracker.nextVelocity(velocity, new Vector(-0.01, 0, 5), S), "just past 90 degrees");
        assertNotNull(Tracker.nextVelocity(velocity, new Vector(0.01, 0, 5), S), "just inside 90 degrees");
        assertNull(Tracker.nextVelocity(new Vector(), new Vector(1, 0, 0), S), "a stopped projectile has no direction");
    }

    /**
     * Re-runs the decision board's simulation (_tracking/sim/steer_hybrid.py) on the production
     * geometry and steering: a zombie beside a full-draw arrow's path, 5-tick scans over a 10-tick
     * look-ahead, 20 degrees per tick and direct aim within 6 blocks. Every case must hit without
     * the arrow ever turning more than 90 degrees away from its target.
     */
    @Test void defaultSteeringHitsStaticTargetsCleanlyNearAndFar() {
        for (int level = 1; level <= 3; level++) {
            double radius = S.radius(level);
            int maxTicks = 0;
            for (double ahead = 1.0; ahead <= 45.0; ahead += 0.5) {
                for (int step = 0; step <= 5; step++) {
                    int ticks = flight(ahead, step * radius / 5.0, radius);
                    assertTrue(ticks >= 0, "level " + level + " ahead " + ahead + " offset " + step + " missed or orbited");
                    maxTicks = Math.max(maxTicks, ticks);
                }
            }
            assertTrue(maxTicks <= 12, "slowest clean hit after lock took " + maxTicks + " ticks");
        }
    }

    /** Ticks from lock to hit, or -1 for a miss or an orbit (angle to the target above 90 degrees). */
    private static int flight(double ahead, double offset, double radius) {
        Vector p = new Vector(0, 1, 0), v = new Vector(3, 0, 0);
        double pathY = PathGeometry.predictArrow(p, v, 40, true, false).stream()
                .filter(point -> point.getX() >= ahead).findFirst().orElseThrow().getY();
        Vector feet = new Vector(ahead, pathY - HEIGHT / 2, offset + HALF_WIDTH);
        BoundingBox box = new BoundingBox(feet.getX() - HALF_WIDTH, feet.getY(), feet.getZ() - HALF_WIDTH,
                feet.getX() + HALF_WIDTH, feet.getY() + HEIGHT, feet.getZ() + HALF_WIDTH);
        int lockedAt = -1;
        for (int tick = 0; tick < 100; tick++) {
            if (tick % S.scanIntervalTicks() == 0 && lockedAt < 0) {
                List<Vector> path = PathGeometry.predictArrow(p, v, S.lookaheadTicks(), true, false);
                if (PathGeometry.pathBoxDistance(path, box) <= radius) lockedAt = tick;
            }
            if (lockedAt >= 0) {
                Vector next = Tracker.nextVelocity(v, box.getCenter().subtract(p), S);
                if (next == null) return -1;
                v = next;
            }
            Vector moved = p.clone().add(v);
            if (Math.sqrt(PathGeometry.segmentBoxDistanceSquared(p, moved, box)) == 0.0) {
                return lockedAt < 0 ? 0 : tick - lockedAt + 1;
            }
            p = moved;
            v.multiply(PathGeometry.AIR_INERTIA).setY(v.getY() - PathGeometry.ARROW_GRAVITY);
            if (lockedAt < 0 && (p.getX() > ahead + 20 || p.getY() < feet.getY() - 10)) return -1;
        }
        return -1;
    }
}
