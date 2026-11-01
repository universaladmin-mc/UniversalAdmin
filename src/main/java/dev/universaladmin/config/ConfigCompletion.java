package dev.universaladmin.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Brings an existing {@code config.yml} up to date with the options of the
 * running version: every option the bundled file has and the server's file
 * does not is added with its default value and its explanation, so a new
 * option can be found where the others are. Nothing the server owner set is
 * changed or removed. Runs after {@link ConfigMigrationRunner}, which renames
 * and moves options first.
 */
public final class ConfigCompletion {

    private ConfigCompletion() {
    }

    /**
     * Adds what {@code config} lacks from {@code defaults}.
     *
     * @return the paths of the options that were added, in the bundled file's order
     */
    public static List<String> complete(FileConfiguration config, Configuration defaults) {
        Set<String> existingSections = new HashSet<>();
        for (String path : config.getKeys(true)) {
            if (config.isConfigurationSection(path)) {
                existingSections.add(path);
            }
        }
        List<String> added = new ArrayList<>();
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) || config.isSet(path) || "config-version".equals(path)) {
                continue;
            }
            config.set(path, defaults.get(path));
            copyComments(config, defaults, path);
            for (String section = parent(path); section != null; section = parent(section)) {
                if (existingSections.add(section)) {
                    copyComments(config, defaults, section);
                }
            }
            added.add(path);
        }
        return added;
    }

    private static void copyComments(FileConfiguration config, Configuration defaults, String path) {
        if (defaults instanceof FileConfiguration file) {
            config.setComments(path, file.getComments(path));
            config.setInlineComments(path, file.getInlineComments(path));
        }
    }

    private static String parent(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? null : path.substring(0, dot);
    }
}
