package com.windsor.meowsEnchants.tracking;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** The per-scan pick: keep a still-valid target unless a strictly higher priority one appears. */
public final class TargetChoice {
    private TargetChoice() {}

    /** Lower ordinal wins: hostile mobs, then players in PvP, then friendly mobs. */
    public enum Priority { HOSTILE, PVP_PLAYER, FRIENDLY }

    /** distance is from the projectile to the nearest point of the target's bounding box. */
    public record Candidate(UUID id, Priority priority, double distance) {}

    private static final Comparator<Candidate> ORDER =
            Comparator.comparing(Candidate::priority).thenComparingDouble(Candidate::distance);

    /** Returns null when nothing is in range, which releases the current target. */
    public static Candidate choose(UUID current, List<Candidate> candidates) {
        Candidate best = null;
        Candidate kept = null;
        for (Candidate candidate : candidates) {
            if (best == null || ORDER.compare(candidate, best) < 0) best = candidate;
            if (candidate.id().equals(current)) kept = candidate;
        }
        if (kept != null && kept.priority().compareTo(best.priority()) <= 0) return kept;
        return best;
    }
}
