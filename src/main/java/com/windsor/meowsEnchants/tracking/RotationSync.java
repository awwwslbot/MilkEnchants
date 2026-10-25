package com.windsor.meowsEnchants.tracking;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Entity;

/**
 * Makes a steered projectile's rotation reach clients on the same tick. Clients do not take
 * rotation from velocity packets: each client tick only lerps the model 20% towards its velocity,
 * and the server's tracker sends position/rotation for arrows every 20 ticks unless the NMS entity
 * is flagged {@code needsSync}. Without the flag a 20-degree-per-tick turn leaves the model lagging
 * tens of degrees behind its flight path. Bukkit has no API for the flag, so it is set reflectively;
 * when the field is missing on another server build, steering still works with the lagging model.
 */
final class RotationSync {
    private static final Map<Class<?>, Method> HANDLES = new ConcurrentHashMap<>();
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static volatile Field needsSync;
    private static volatile boolean unavailable;

    private RotationSync() {}

    static void mark(Entity entity, Logger logger) {
        if (unavailable) return;
        try {
            Method handle = HANDLES.computeIfAbsent(entity.getClass(), type -> {
                try {
                    return type.getMethod("getHandle");
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException(e);
                }
            });
            Object nms = handle.invoke(entity);
            Field field = needsSync;
            if (field == null) {
                field = findField(nms.getClass(), "needsSync");
                needsSync = field;
            }
            field.setBoolean(nms, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            unavailable = true;
            if (WARNED.compareAndSet(false, true)) {
                logger.log(Level.WARNING, "Cannot flag steered projectiles for rotation sync; clients may show them lagging behind turns", e);
            }
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                if (field.getType() == boolean.class) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (NoSuchFieldException ignored) { /* Try the superclass. */ }
        }
        throw new NoSuchFieldException(name);
    }
}
