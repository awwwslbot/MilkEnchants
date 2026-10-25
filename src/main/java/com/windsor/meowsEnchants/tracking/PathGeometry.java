package com.windsor.meowsEnchants.tracking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Pure flight-path geometry, shared by the tracker and deterministic tests. */
public final class PathGeometry {
    public static final double ARROW_GRAVITY = 0.05;
    public static final double AIR_INERTIA = 0.99;
    public static final double WATER_INERTIA = 0.6;
    private PathGeometry() {}

    /**
     * Positions after 0..ticks ticks of an unsteered arrow. Folia 26.1.2 AbstractArrow.tick applies
     * water drag before the move, then air drag and gravity after it.
     */
    public static List<Vector> predictArrow(Vector position, Vector velocity, int ticks, boolean gravity, boolean inWater) {
        List<Vector> points = new ArrayList<>(ticks + 1);
        Vector p = position.clone();
        Vector v = velocity.clone();
        points.add(p.clone());
        for (int i = 0; i < ticks; i++) {
            if (inWater) v.multiply(WATER_INERTIA);
            p.add(v);
            points.add(p.clone());
            if (!inWater) v.multiply(AIR_INERTIA);
            if (gravity) v.setY(v.getY() - ARROW_GRAVITY);
        }
        return points;
    }

    /** Crossbow fireworks are shot at an angle: no gravity, drag or acceleration. */
    public static List<Vector> predictLinear(Vector position, Vector velocity, int ticks) {
        List<Vector> points = new ArrayList<>(ticks + 1);
        for (int i = 0; i <= ticks; i++) points.add(position.clone().add(velocity.clone().multiply(i)));
        return points;
    }

    public static BoundingBox bounds(List<Vector> path, double margin) {
        BoundingBox box = BoundingBox.of(path.getFirst(), path.getFirst());
        for (Vector point : path) box.union(point);
        return box.expand(margin);
    }

    public static double pathBoxDistance(List<Vector> path, BoundingBox box) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < path.size(); i++) {
            best = Math.min(best, segmentBoxDistanceSquared(path.get(i), path.get(i + 1), box));
            if (best == 0.0) break;
        }
        if (path.size() == 1) best = segmentBoxDistanceSquared(path.getFirst(), path.getFirst(), box);
        return Math.sqrt(best);
    }

    public static double pointBoxDistance(Vector point, BoundingBox box) {
        return Math.sqrt(segmentBoxDistanceSquared(point, point, box));
    }

    /**
     * Exact squared distance between a segment and a box. Each coordinate's clamp side only changes
     * where it crosses a box face, so between those breakpoints the distance is one quadratic in t.
     */
    static double segmentBoxDistanceSquared(Vector a, Vector b, BoundingBox box) {
        double[] p = {a.getX(), a.getY(), a.getZ()};
        double[] d = {b.getX() - p[0], b.getY() - p[1], b.getZ() - p[2]};
        double[] lo = {box.getMinX(), box.getMinY(), box.getMinZ()};
        double[] hi = {box.getMaxX(), box.getMaxY(), box.getMaxZ()};
        double[] breaks = new double[8];
        int count = 0;
        breaks[count++] = 0.0;
        breaks[count++] = 1.0;
        for (int k = 0; k < 3; k++) {
            if (d[k] == 0.0) continue;
            for (double face : new double[]{lo[k], hi[k]}) {
                double t = (face - p[k]) / d[k];
                if (t > 0.0 && t < 1.0) breaks[count++] = t;
            }
        }
        Arrays.sort(breaks, 0, count);
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < count; i++) {
            double from = breaks[i], to = breaks[i + 1], mid = (from + to) / 2.0;
            double qa = 0.0, qb = 0.0, qc = 0.0;
            for (int k = 0; k < 3; k++) {
                double x = p[k] + d[k] * mid;
                if (x >= lo[k] && x <= hi[k]) continue;
                double offset = p[k] - (x < lo[k] ? lo[k] : hi[k]);
                qa += d[k] * d[k];
                qb += 2.0 * offset * d[k];
                qc += offset * offset;
            }
            double t = qa > 0.0 ? Math.clamp(-qb / (2.0 * qa), from, to) : from;
            best = Math.min(best, Math.max(0.0, (qa * t + qb) * t + qc));
        }
        return best;
    }

    /** Angle between two directions in radians; zero vectors count as aligned. */
    public static double angle(Vector a, Vector b) {
        double lengths = a.length() * b.length();
        if (lengths == 0.0) return 0.0;
        return Math.acos(Math.clamp(a.dot(b) / lengths, -1.0, 1.0));
    }

    /**
     * Rotates velocity towards toTarget by at most maxTurnRadians (all the way when direct), keeping
     * its speed, so arrow damage (speed x base damage) is unchanged.
     */
    public static Vector steer(Vector velocity, Vector toTarget, double maxTurnRadians, boolean direct) {
        double speed = velocity.length();
        Vector u = velocity.clone().multiply(1.0 / speed);
        Vector w = toTarget.clone().normalize();
        double angle = Math.acos(Math.clamp(u.dot(w), -1.0, 1.0));
        Vector next;
        if (direct || angle <= maxTurnRadians || angle < 1.0e-9) {
            next = w;
        } else {
            double t = maxTurnRadians / angle, sin = Math.sin(angle);
            next = u.multiply(Math.sin((1.0 - t) * angle) / sin).add(w.multiply(Math.sin(t * angle) / sin));
        }
        return next.normalize().multiply(speed);
    }

    /** Projectile yaw as vanilla Projectile.updateRotation computes it. */
    public static float yaw(Vector velocity) {
        return (float) Math.toDegrees(Math.atan2(velocity.getX(), velocity.getZ()));
    }

    /** Projectile pitch: atan2(dy, horizontalSpeed), so straight down is -90 degrees. */
    public static float pitch(Vector velocity) {
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        return (float) Math.toDegrees(Math.atan2(velocity.getY(), horizontal));
    }
}
