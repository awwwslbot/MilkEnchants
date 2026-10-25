package com.windsor.meowsEnchants.tracking;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Random;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class PathGeometryTest {
    private static final BoundingBox ZOMBIE = new BoundingBox(-0.3, 0, -0.3, 0.3, 1.95, 0.3);

    @Test void segmentDistanceIsExactForKnownShapes() {
        // Straight through the box.
        assertEquals(0.0, dist(new Vector(-5, 1, 0), new Vector(5, 1, 0), ZOMBIE), 1e-12);
        // Parallel to a face, 1.2 blocks out along z.
        assertEquals(1.2, dist(new Vector(-5, 1, 1.5), new Vector(5, 1, 1.5), ZOMBIE), 1e-12);
        // Ends before the box: the endpoint is closest.
        assertEquals(2.0, dist(new Vector(-5, 1, 0), new Vector(-2.3, 1, 0), ZOMBIE), 1e-12);
        // Passes a vertical edge diagonally above the head: corner distance in y and z.
        assertEquals(Math.hypot(1.05, 0.7), dist(new Vector(-5, 3.0, 1.0), new Vector(5, 3.0, 1.0), ZOMBIE), 1e-12);
        // A degenerate segment is a point.
        assertEquals(Math.hypot(0.7, 0.7), dist(new Vector(1, 1, 1), new Vector(1, 1, 1), ZOMBIE), 1e-12);
    }

    @Test void segmentDistanceMatchesDenseSamplingOnRandomCases() {
        Random random = new Random(42);
        for (int i = 0; i < 2000; i++) {
            Vector a = new Vector(random.nextGaussian() * 4, random.nextGaussian() * 4, random.nextGaussian() * 4);
            Vector b = new Vector(random.nextGaussian() * 4, random.nextGaussian() * 4, random.nextGaussian() * 4);
            double exact = dist(a, b, ZOMBIE);
            double sampled = Double.POSITIVE_INFINITY;
            for (int s = 0; s <= 4000; s++) {
                Vector q = a.clone().add(b.clone().subtract(a).multiply(s / 4000.0));
                sampled = Math.min(sampled, PathGeometry.pointBoxDistance(q, ZOMBIE));
            }
            assertTrue(exact <= sampled + 1e-9, "exact must never exceed a sampled point");
            assertEquals(sampled, exact, 0.01);
        }
    }

    @Test void arrowPredictionFollowsVanillaMoveThenDragThenGravity() {
        Vector v = new Vector(3, 0, 0);
        List<Vector> path = PathGeometry.predictArrow(new Vector(0, 64, 0), v, 2, true, false);
        assertEquals(3, path.size());
        assertEquals(new Vector(3, 64, 0), path.get(1));
        Vector second = path.get(2);
        assertEquals(3 + 3 * 0.99, second.getX(), 1e-12);
        assertEquals(64 - 0.05, second.getY(), 1e-12);
        // Full draw (3 blocks/tick) covers roughly 30 blocks in the 10-tick look-ahead.
        List<Vector> lookahead = PathGeometry.predictArrow(new Vector(), v, 10, true, false);
        assertEquals(28.7, lookahead.getLast().getX(), 0.1);
    }

    @Test void waterDragAppliesBeforeTheMoveAndNoGravityKeepsHeight() {
        List<Vector> path = PathGeometry.predictArrow(new Vector(), new Vector(1, 0, 0), 1, false, true);
        assertEquals(0.6, path.get(1).getX(), 1e-12);
        assertEquals(0.0, path.get(1).getY(), 1e-12);
    }

    @Test void crossbowFireworksFlyStraightAtConstantSpeed() {
        List<Vector> path = PathGeometry.predictLinear(new Vector(0, 70, 0), new Vector(1.6, 0, 0), 10);
        assertEquals(11, path.size());
        assertEquals(new Vector(16, 70, 0), path.getLast());
    }

    @Test void steeringTurnsAtMostTheLimitAndKeepsSpeed() {
        Vector velocity = new Vector(3, 0, 0);
        Vector side = new Vector(0, 0, 10);
        Vector next = PathGeometry.steer(velocity, side, Math.toRadians(20), false);
        assertEquals(20.0, Math.toDegrees(PathGeometry.angle(velocity, next)), 1e-9);
        assertEquals(3.0, next.length(), 1e-12);
        Vector direct = PathGeometry.steer(velocity, side, Math.toRadians(20), true);
        assertEquals(0.0, PathGeometry.angle(direct, side), 1e-9);
        assertEquals(3.0, direct.length(), 1e-12);
        Vector small = PathGeometry.steer(velocity, new Vector(10, 0, 1), Math.toRadians(20), false);
        assertEquals(0.0, PathGeometry.angle(small, new Vector(10, 0, 1)), 1e-9);
    }

    @Test void projectileRotationUsesVanillaConventions() {
        assertEquals(-90f, PathGeometry.pitch(new Vector(0, -2.5, 0)), 1e-4);
        assertEquals(0f, PathGeometry.yaw(new Vector(0, 0, 1)), 1e-4);
        assertEquals(90f, PathGeometry.yaw(new Vector(1, 0, 0)), 1e-4);
        assertEquals(45f, PathGeometry.pitch(new Vector(1, 1, 0)), 1e-4);
    }

    @Test void boundsCoverThePathPlusRadius() {
        BoundingBox box = PathGeometry.bounds(List.of(new Vector(0, 0, 0), new Vector(10, -2, 3)), 1.5);
        assertEquals(new BoundingBox(-1.5, -3.5, -1.5, 11.5, 1.5, 4.5), box);
    }

    private static double dist(Vector a, Vector b, BoundingBox box) {
        return Math.sqrt(PathGeometry.segmentBoxDistanceSquared(a, b, box));
    }
}
