package com.windsor.meowsEnchants.arrowrain;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RainPatternTest {
    @Test void exactDiametersCountsAndTotalDurability() {
        assertArrayEquals(new double[]{3, 5, 7, 9, 11}, new double[]{BundledRain.RAIN.radius(1)*2, BundledRain.RAIN.radius(2)*2, BundledRain.RAIN.radius(3)*2, BundledRain.RAIN.radius(4)*2, BundledRain.RAIN.radius(5)*2});
        assertArrayEquals(new int[]{8, 16, 32, 64, 128}, new int[]{BundledRain.RAIN.count(1), BundledRain.RAIN.count(2), BundledRain.RAIN.count(3), BundledRain.RAIN.count(4), BundledRain.RAIN.count(5)});
        assertArrayEquals(new int[]{2, 4, 8, 16, 32}, new int[]{BundledRain.RAIN.extraDurability(1)+1, BundledRain.RAIN.extraDurability(2)+1, BundledRain.RAIN.extraDurability(3)+1, BundledRain.RAIN.extraDurability(4)+1, BundledRain.RAIN.extraDurability(5)+1});
    }

    @Test void releaseIsAtMostTwentyBlocksAboveTheImpactPointAndNeedsFourBlocksOfHeadroom() {
        assertEquals(20.0, BundledRain.RAIN.height());
        assertEquals(4.0, BundledRain.RAIN.minHeight());
    }

    @Test void everyArrowStaysInDiskAndIsReleasedEvenlyWithinItsLevelWindow() {
        Random random = new Random(624798);
        for (int level = 1; level <= BundledRain.MAX_LEVEL; level++) {
            for (int repetition = 0; repetition < 500; repetition++) {
                var drops = BundledRain.RAIN.drops(level, random);
                assertEquals(BundledRain.RAIN.count(level), drops.size());
                assertEquals(1L, drops.getFirst().tick());
                int lastTick = 20 * level;
                assertEquals(lastTick, drops.getLast().tick());
                double idealGap = (double) (lastTick - 1) / (drops.size() - 1);
                long previousTick = -1;
                for (var drop : drops) {
                    assertTrue(Math.hypot(drop.x(), drop.z()) <= BundledRain.RAIN.radius(level));
                    assertTrue(drop.tick() >= 1 && drop.tick() <= lastTick);
                    if (previousTick > 0) {
                        long gap = drop.tick() - previousTick;
                        assertTrue(gap >= Math.floor(idealGap) && gap <= Math.ceil(idealGap));
                    }
                    previousTick = drop.tick();
                }
            }
        }
    }

    @Test void samplesHaveUniformAreaDensity() {
        Random random = new Random(14141);
        double squareRadiusSum = 0;
        int count = 0;
        for (int i = 0; i < 2000; i++) for (var drop : BundledRain.RAIN.drops(3, random)) {
            squareRadiusSum += (drop.x()*drop.x() + drop.z()*drop.z()) / (3.5*3.5);
            count++;
        }
        assertEquals(0.5, squareRadiusSum/count, 0.01);
    }

    @Test void limitedAmmoHandlesZeroOneAndPartialRainWithoutExceedingItsBudget() {
        Random random = new Random(70319);
        for (int level = 1; level <= BundledRain.MAX_LEVEL; level++) for (int budget = 0; budget <= BundledRain.RAIN.count(level); budget++) {
            var drops = BundledRain.RAIN.drops(level, budget, random);
            int expected = Math.min(budget, BundledRain.RAIN.count(level));
            assertEquals(expected, drops.size());
            if (expected == 0) continue;
            assertEquals(1, drops.getFirst().tick());
            assertEquals(expected == 1 ? 1 : level * 20, drops.getLast().tick());
            for (var drop : drops) assertTrue(Math.hypot(drop.x(), drop.z()) <= BundledRain.RAIN.radius(level));
        }
    }

    @Test void unsafeOverlevelsCannotExceedRequestedCaps() {
        assertEquals(128, BundledRain.RAIN.count(Integer.MAX_VALUE));
        assertEquals(4.5, BundledRain.RAIN.radius(4));
        assertEquals(5.5, BundledRain.RAIN.radius(Integer.MAX_VALUE));
        assertEquals(31, BundledRain.RAIN.extraDurability(100));
        assertEquals(100, BundledRain.RAIN.lastSpawnTick(Integer.MAX_VALUE));
    }
}
