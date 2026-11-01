package dev.universaladmin.localization;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Looks up user-facing text by {@link MessageKey} instead of hardcoding
 * strings in GUI/command/action code, so messages can be translated and
 * (later) reused by the web app without duplicating copy.
 *
 * <p><b>Arguments are untrusted by default.</b> Every {@link String}
 * argument passed to {@link #get} has its MiniMessage tags escaped before it
 * is substituted into the (trusted) template, so a ban reason, report text or
 * chat message typed by a player can never inject formatting, click or hover
 * events into a message another player sees. Wrap an argument in
 * {@link #markup(String)} only when it is itself trusted markup (e.g. another
 * already-resolved message).
 *
 * <p><b>Layering.</b> Defaults ship inside the plugin jar (and inside
 * extension jars, via {@link #registerDefaults}); server owners override any
 * key in {@code plugins/UniversalAdmin/lang/<locale>.yml}. A key missing from
 * the override file falls back to the bundled default for the same locale,
 * then to {@code en_US}, then to a visible {@code [missing: key]} marker.
 */
public interface MessageService {

    /** Resolves {@code key} in the active locale, substituting {@code args} with {@link java.text.MessageFormat}. */
    String get(MessageKey key, Object... args);

    /**
     * Like {@link #get(MessageKey, Object...)}, but from {@code locale}
     * (e.g. {@code de_DE}) instead of the server's language - for a message
     * meant for one player in their own language. Falls back like the
     * server's language does; the default ignores {@code locale}.
     *
     * @since 1.8
     */
    default String get(String locale, MessageKey key, Object... args) {
        return get(key, args);
    }

    /**
     * Whether {@code key} resolves in the active locale or the fallback -
     * for optional, overridable texts (e.g. a localized module name) where a
     * missing key should fall back to something else instead of showing a
     * {@code [missing: ...]} marker. The default answers {@code true}.
     */
    default boolean has(MessageKey key) {
        return true;
    }

    /**
     * Registers bundled default messages for {@code locale} - the lowest
     * layer, below anything the server owner put into
     * {@code plugins/UniversalAdmin/lang/<locale>.yml}. Intended for
     * extensions shipping their own translations; keys should be prefixed
     * with the extension's own namespace (e.g. {@code myextension.gui.title})
     * so they cannot collide with core keys. A later registration of the same
     * key replaces the earlier one. The default implementation ignores the
     * registration - only for lightweight test doubles; the real service
     * always implements it.
     */
    default void registerDefaults(String locale, Map<String, String> messages) {
    }

    /** Re-reads the server owner's override files from disk. Never throws for a broken file - it is logged and skipped. */
    default void reload() {
    }

    /** Every locale that has at least one bundled or overriding message. */
    default Set<String> availableLocales() {
        return Set.of();
    }

    /**
     * Marks {@code trustedMarkup} as already-safe MiniMessage that must be
     * substituted verbatim instead of escaped - see the class javadoc.
     */
    static Object markup(String trustedMarkup) {
        return new Markup(trustedMarkup);
    }

    /** Trusted-markup argument wrapper, see {@link #markup(String)}. */
    record Markup(String value) {
        public Markup {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
