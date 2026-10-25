package com.windsor.meowsEnchants.arrowrain;

import java.util.concurrent.atomic.AtomicInteger;

/** Source removal after a hit must not cancel the rain; an unhit removed arrow must be forgotten. */
final class ShotState {
    private final AtomicInteger phase = new AtomicInteger(); // flying, impacted, stopped
    boolean impact() { return phase.compareAndSet(0, 1); }
    boolean flying() { return phase.get() == 0; }
    boolean active() { return phase.get() != 2; }
    boolean stop() { return phase.getAndSet(2) != 2; }
}
