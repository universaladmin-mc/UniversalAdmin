package dev.universaladmin.core;

import java.util.Optional;

/**
 * Static access to the running {@link UniversalAdmin} platform for code that
 * is not a module - most importantly an extension plugin's {@code onEnable}.
 * The same instance is also registered in Bukkit's {@code ServicesManager}
 * under {@code UniversalAdmin.class}.
 *
 * <p>Only available while UniversalAdmin is enabled. A plugin that declares
 * {@code depend: [UniversalAdmin]} is enabled after it, so {@link #get()} is
 * safe from that plugin's {@code onEnable} - unless UniversalAdmin itself
 * failed to start, which {@link #find()} lets a plugin handle gracefully.
 */
public final class UniversalAdminProvider {

    /**
     * The extension API version. Bumped in the minor digit for backwards
     * compatible additions and in the major digit for breaking changes -
     * an extension can check it before relying on newer API.
     */
    public static final String API_VERSION = "1.8";

    private static volatile UniversalAdmin instance;

    private UniversalAdminProvider() {
    }

    /**
     * @throws IllegalStateException if UniversalAdmin is not (or no longer) enabled
     */
    public static UniversalAdmin get() {
        UniversalAdmin current = instance;
        if (current == null) {
            throw new IllegalStateException("UniversalAdmin is not enabled. Make sure it is installed, started without "
                    + "errors, and that your plugin.yml declares depend: [UniversalAdmin].");
        }
        return current;
    }

    public static Optional<UniversalAdmin> find() {
        return Optional.ofNullable(instance);
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    /**
     * Internal: set once by UniversalAdmin's own bootstrap. Not for
     * extensions - a second registration is refused.
     */
    public static synchronized void register(UniversalAdmin platform) {
        if (instance != null && instance != platform) {
            throw new IllegalStateException("UniversalAdmin is already registered");
        }
        instance = platform;
    }

    /** Internal: cleared by UniversalAdmin's own bootstrap on disable. */
    public static synchronized void unregister(UniversalAdmin platform) {
        if (instance == platform) {
            instance = null;
        }
    }
}
