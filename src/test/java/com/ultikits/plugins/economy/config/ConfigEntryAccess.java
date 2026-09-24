package com.ultikits.plugins.economy.config;

import com.ultikits.ultitools.annotations.ConfigEntry;

import java.lang.reflect.Field;

/**
 * Reads and writes an {@link EconomyConfig} field by its {@code @ConfigEntry} path, the way the
 * framework does, rather than through a generated accessor.
 *
 * <p>Tests of a key that a change adds or restores use this so that, with the change reverted, they
 * fail at run time by naming the missing key -- a behaviour-level red -- instead of failing to compile.
 */
public final class ConfigEntryAccess {

    private ConfigEntryAccess() {
    }

    /** Sets the field bound to {@code path}; fails the test if no field declares that path. */
    public static void set(EconomyConfig config, String path, Object value) {
        try {
            Field field = field(path);
            field.set(config, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    /** Reads the field bound to {@code path}; fails the test if no field declares that path. */
    public static Object get(EconomyConfig config, String path) {
        try {
            return field(path).get(config);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Field field(String path) {
        for (Field field : EconomyConfig.class.getDeclaredFields()) {
            ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
            if (entry != null && path.equals(entry.path())) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new AssertionError("EconomyConfig declares no @ConfigEntry(path = \"" + path + "\")");
    }
}
