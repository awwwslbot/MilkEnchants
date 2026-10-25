package com.windsor.meowsEnchants.tracking;

import static com.windsor.meowsEnchants.tracking.TargetChoice.Priority.*;
import static org.junit.jupiter.api.Assertions.*;
import com.windsor.meowsEnchants.tracking.TargetChoice.Candidate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TargetChoiceTest {
    private final UUID sheep = UUID.randomUUID(), cow = UUID.randomUUID(), zombie = UUID.randomUUID(),
            skeleton = UUID.randomUUID(), player = UUID.randomUUID();

    @Test void hostileBeatsPvpPlayerBeatsFriendlyRegardlessOfDistance() {
        assertEquals(zombie, TargetChoice.choose(null, List.of(
                new Candidate(sheep, FRIENDLY, 0.1), new Candidate(player, PVP_PLAYER, 0.5), new Candidate(zombie, HOSTILE, 9))).id());
        assertEquals(player, TargetChoice.choose(null, List.of(
                new Candidate(sheep, FRIENDLY, 0.1), new Candidate(player, PVP_PLAYER, 5))).id());
    }

    @Test void nearestWinsWithinAPriority() {
        assertEquals(skeleton, TargetChoice.choose(null, List.of(
                new Candidate(zombie, HOSTILE, 3), new Candidate(skeleton, HOSTILE, 1))).id());
    }

    @Test void aValidCurrentTargetIsKeptAgainstNearerTargetsOfTheSamePriority() {
        assertEquals(zombie, TargetChoice.choose(zombie, List.of(
                new Candidate(zombie, HOSTILE, 3), new Candidate(skeleton, HOSTILE, 1))).id());
        assertEquals(sheep, TargetChoice.choose(sheep, List.of(
                new Candidate(cow, FRIENDLY, 0.2), new Candidate(sheep, FRIENDLY, 2))).id());
    }

    @Test void aHigherPriorityTargetReplacesTheCurrentOne() {
        assertEquals(zombie, TargetChoice.choose(sheep, List.of(
                new Candidate(sheep, FRIENDLY, 0.5), new Candidate(zombie, HOSTILE, 4))).id());
    }

    @Test void aCurrentTargetThatLeftRangeIsReplacedOrReleased() {
        assertEquals(cow, TargetChoice.choose(sheep, List.of(new Candidate(cow, FRIENDLY, 2))).id());
        assertNull(TargetChoice.choose(sheep, List.of()));
    }
}
