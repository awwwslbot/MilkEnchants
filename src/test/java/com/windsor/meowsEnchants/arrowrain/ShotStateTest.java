package com.windsor.meowsEnchants.arrowrain;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ShotStateTest {
    @Test void onlyFirstImpactCanStartRainEvenWhenEventsRace() throws Exception {
        ShotState state = new ShotState();
        AtomicInteger winners = new AtomicInteger();
        try (var workers = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 256; i++) workers.submit(() -> { if (state.impact()) winners.incrementAndGet(); });
        }
        assertEquals(1, winners.get());
        assertFalse(state.flying());
        assertTrue(state.active());
    }

    @Test void removedOrOfflineSourceCannotImpactAfterStop() {
        ShotState state = new ShotState();
        assertTrue(state.flying());
        assertTrue(state.stop());
        assertFalse(state.stop());
        assertFalse(state.impact());
        assertFalse(state.active());
    }

    @Test void impactedSourceRemovalDoesNotCountAsFlying() {
        ShotState state = new ShotState();
        assertTrue(state.impact());
        assertFalse(state.flying());
        assertTrue(state.active());
        assertTrue(state.stop());
        assertFalse(state.active());
    }
}
