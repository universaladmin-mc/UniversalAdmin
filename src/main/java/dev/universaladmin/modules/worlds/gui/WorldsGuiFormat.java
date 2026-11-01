package dev.universaladmin.modules.worlds.gui;

import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.worlds.WeatherState;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/** Small formatting helpers shared by every Worlds GUI page - mirrors {@code PlayerGuiFormat}. */
final class WorldsGuiFormat {

    private WorldsGuiFormat() {
    }

    static String weatherLabel(MessageService messages, boolean storm, boolean thundering) {
        WeatherState state = WeatherState.of(storm, thundering);
        return messages.get(MessageKey.of("worlds.gui.weather." + state.name().toLowerCase(Locale.ROOT)));
    }

    /** The block that stands for a world of this environment. */
    static Material material(World.Environment environment) {
        return switch (environment) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }

    static String coordinates(Location location) {
        return "%.1f, %.1f, %.1f".formatted(location.getX(), location.getY(), location.getZ());
    }
}
