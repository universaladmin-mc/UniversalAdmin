package dev.universaladmin.localization;

import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * {@link MessageService} backed by YAML files, in two layers per locale:
 *
 * <ol>
 *   <li><b>Overrides</b> - every {@code lang/*.yml} in the plugin's data
 *       folder, i.e. what the server owner edited. Re-read by {@link #reload()}.
 *   <li><b>Bundled defaults</b> - the {@code lang/<locale>.yml} files shipped
 *       inside the plugin jar, plus anything an extension added via
 *       {@link #registerDefaults}.
 * </ol>
 *
 * The bundled layer is what makes upgrades safe: the data-folder copy of
 * {@code en_US.yml} is written once, on first start, and never overwritten -
 * a key added in a later version is still found in the jar's own copy
 * instead of rendering as {@code [missing: ...]}.
 *
 * <p>Resolution order: override(active) → bundled(active) →
 * override({@code en_US}) → bundled({@code en_US}) → a visible
 * {@code [missing: key]} marker (logged once per key). The active locale is
 * read fresh from {@link CoreSettings#LANGUAGE} on every call, so changing
 * {@code general.language} takes effect on {@code /admin reload}.
 *
 * <p>String arguments are MiniMessage-escaped before substitution - see
 * {@link MessageService}'s javadoc.
 */
public final class YamlLocaleMessageService implements MessageService {

    private static final String FALLBACK_LOCALE = "en_US";

    private final Path langDirectory;
    private final SettingsService settingsService;
    private final Logger logger;
    private final Map<String, Map<String, String>> bundled = new ConcurrentHashMap<>();
    private volatile Map<String, Map<String, String>> overrides;
    private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();

    /**
     * @param langDirectory   the data-folder {@code lang/} directory holding the server owner's overrides
     * @param bundledResource opens a bundled resource such as {@code "lang/en_US.yml"}, or returns {@code null}
     *                        if there is none - typically {@code plugin::getResource}
     * @param bundledLocales  which bundled locale files to load through {@code bundledResource}
     */
    public YamlLocaleMessageService(
            Path langDirectory,
            Function<String, InputStream> bundledResource,
            List<String> bundledLocales,
            SettingsService settingsService,
            Logger logger) {
        this.langDirectory = langDirectory;
        this.settingsService = settingsService;
        this.logger = logger;
        for (String locale : bundledLocales) {
            loadBundled(bundledResource, locale);
        }
        this.overrides = loadOverrides();
    }

    /** Overrides only, no bundled layer - kept for tests and tools that only have a directory. */
    public YamlLocaleMessageService(Path langDirectory, SettingsService settingsService, Logger logger) {
        this(langDirectory, resource -> null, List.of(), settingsService, logger);
    }

    @Override
    public String get(MessageKey key, Object... args) {
        return format(key, resolveTemplate(key), args);
    }

    @Override
    public String get(String locale, MessageKey key, Object... args) {
        String template = lookup(locale, key.value());
        return template == null ? get(key, args) : format(key, template, args);
    }

    private String format(MessageKey key, String template, Object... args) {
        if (args.length == 0) {
            return template;
        }
        Object[] safeArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            safeArgs[i] = sanitize(args[i]);
        }
        try {
            return MessageFormat.format(template, safeArgs);
        } catch (IllegalArgumentException e) {
            // A broken pattern in a server owner's override (an unbalanced
            // brace or quote) must not turn every use of the key into an
            // exception - show the raw template instead and say why once.
            if (warnedMissingKeys.add("format:" + key.value())) {
                logger.warning(() -> "Message '" + key.value() + "' is not a valid MessageFormat pattern: " + e.getMessage());
            }
            return template;
        }
    }

    @Override
    public boolean has(MessageKey key) {
        String activeLocale = settingsService.get(CoreSettings.LANGUAGE);
        return lookup(activeLocale, key.value()) != null || lookup(FALLBACK_LOCALE, key.value()) != null;
    }

    @Override
    public void registerDefaults(String locale, Map<String, String> messages) {
        bundled.computeIfAbsent(locale, ignored -> new ConcurrentHashMap<>()).putAll(messages);
    }

    @Override
    public void reload() {
        overrides = loadOverrides();
        warnedMissingKeys.clear();
    }

    @Override
    public Set<String> availableLocales() {
        Set<String> locales = new TreeSet<>(bundled.keySet());
        locales.addAll(overrides.keySet());
        return locales;
    }

    private static Object sanitize(Object arg) {
        if (arg instanceof Markup markup) {
            return markup.value();
        }
        if (arg instanceof String text) {
            return MiniMessage.miniMessage().escapeTags(text);
        }
        return arg;
    }

    private String resolveTemplate(MessageKey key) {
        String activeLocale = settingsService.get(CoreSettings.LANGUAGE);
        String template = lookup(activeLocale, key.value());
        if (template == null && !FALLBACK_LOCALE.equals(activeLocale)) {
            template = lookup(FALLBACK_LOCALE, key.value());
        }
        if (template != null) {
            return template;
        }
        if (warnedMissingKeys.add(key.value())) {
            logger.warning(() -> "Missing localization key '" + key.value() + "' in locale '" + activeLocale
                    + "' and fallback '" + FALLBACK_LOCALE + "'.");
        }
        return "[missing: " + key.value() + "]";
    }

    private String lookup(String locale, String key) {
        String value = overrides.getOrDefault(locale, Map.of()).get(key);
        return value != null ? value : bundled.getOrDefault(locale, Map.of()).get(key);
    }

    private void loadBundled(Function<String, InputStream> bundledResource, String locale) {
        String resource = "lang/" + locale + ".yml";
        try (InputStream in = bundledResource.apply(resource)) {
            if (in == null) {
                logger.warning(() -> "Bundled locale file " + resource + " not found - skipping it.");
                return;
            }
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            registerDefaults(locale, flatten(config));
        } catch (IOException | InvalidConfigurationException e) {
            // A broken *bundled* file is a packaging bug, but it still must
            // not take the whole plugin down - the other layers keep working.
            logger.log(Level.SEVERE, "Failed to load bundled locale file " + resource, e);
        }
    }

    private Map<String, Map<String, String>> loadOverrides() {
        File[] files = langDirectory.toFile().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return Map.of();
        }
        Map<String, Map<String, String>> loaded = new HashMap<>();
        for (File file : files) {
            String locale = file.getName().substring(0, file.getName().length() - ".yml".length());
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.load(file);
                loaded.put(locale, flatten(config));
            } catch (IOException | InvalidConfigurationException e) {
                logger.log(Level.SEVERE, "Could not read " + file + " - its overrides are ignored until it is fixed "
                        + "(bundled texts are used instead). Cause: " + e.getMessage());
            }
        }
        return Map.copyOf(loaded);
    }

    private static Map<String, String> flatten(YamlConfiguration config) {
        Map<String, String> flat = new LinkedHashMap<>();
        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                flat.put(key, config.getString(key));
            }
        }
        return flat;
    }
}
