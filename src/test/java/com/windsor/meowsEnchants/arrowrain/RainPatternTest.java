package com.windsor.meowsEnchants.arrowrain;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RainPatternTest {
    @Test void exactDiametersCountsAndTotalDurability() {
        assertArrayEquals(new double[]{3, 5, 7}, new double[]{RainPattern.radius(1)*2, RainPattern.radius(2)*2, RainPattern.radius(3)*2});
        assertArrayEquals(new int[]{8, 16, 32}, new int[]{RainPattern.count(1), RainPattern.count(2), RainPattern.count(3)});
        assertArrayEquals(new int[]{4, 8, 16}, new int[]{RainPattern.extraDurability(1)+1, RainPattern.extraDurability(2)+1, RainPattern.extraDurability(3)+1});
    }

    @Test void everyArrowStaysInDiskAndArrivesWithinOneSecondAt20Tps() {
        Random random = new Random(624798);
        for (int level = 1; level <= 3; level++) {
            for (int repetition = 0; repetition < 500; repetition++) {
                var drops = RainPattern.drops(level, random);
                assertEquals(RainPattern.count(level), drops.size());
                assertEquals(1L, drops.getFirst().tick());
                assertEquals(10L, drops.getLast().tick());
                for (var drop : drops) {
                    assertTrue(Math.hypot(drop.x(), drop.z()) <= RainPattern.radius(level));
                    assertTrue(drop.tick() >= 1 && drop.tick() <= 10);
                    // One extra tick for collision reconstruction, three for the six-block fall.
                    assertTrue(1 + drop.tick() + Math.ceil(RainPattern.HEIGHT / RainPattern.SPEED) <= 20);
                }
            }
        }
    }

    @Test void samplesHaveUniformAreaDensity() {
        Random random = new Random(14141);
        double squareRadiusSum = 0;
        int count = 0;
        for (int i = 0; i < 2000; i++) for (var drop : RainPattern.drops(3, random)) {
            squareRadiusSum += (drop.x()*drop.x() + drop.z()*drop.z()) / (3.5*3.5);
            count++;
        }
        assertEquals(0.5, squareRadiusSum/count, 0.01);
    }

    @Test void unsafeOverlevelsCannotExceedRequestedCaps() {
        assertEquals(32, RainPattern.count(Integer.MAX_VALUE));
        assertEquals(3.5, RainPattern.radius(4));
        assertEquals(15, RainPattern.extraDurability(100));
    }
}
