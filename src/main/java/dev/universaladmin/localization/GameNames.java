package dev.universaladmin.localization;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

/**
 * Names Minecraft already knows - game modes, difficulties, effects, entity
 * types - as translatable components: the player's client shows them in its
 * own language ("Überlebensmodus", "Eile"), not as the server's constant
 * ("SURVIVAL", "SPEED"), and no language file needs them.
 */
public final class GameNames {

    private GameNames() {
    }

    public static Component gameMode(GameMode mode) {
        return Component.translatable("gameMode." + mode.name().toLowerCase(Locale.ROOT));
    }

    public static Component difficulty(Difficulty difficulty) {
        return Component.translatable("options.difficulty." + difficulty.name().toLowerCase(Locale.ROOT));
    }

    public static Component effect(PotionEffectType type) {
        return keyed("effect", type.getKey());
    }

    public static Component entity(EntityType type) {
        if (type == EntityType.UNKNOWN) {
            return Component.text(type.name());
        }
        return keyed("entity", type.getKey());
    }

    /**
     * One of these names as a message argument ({@code {0}}): translatable
     * names stay translatable ({@code <lang:...>}), anything else is plain
     * text.
     */
    public static Object arg(Component name) {
        if (name instanceof TranslatableComponent translatable) {
            return MessageService.markup("<lang:" + translatable.key() + ">");
        }
        return PlainTextComponentSerializer.plainText().serialize(name);
    }

    /**
     * One of these names as a value of an audit sentence
     * ({@code ActionDefinition.Builder#auditArgs}): {@code lang:<key>}, shown
     * in the reader's language when the entry is displayed.
     */
    public static String auditArg(Component name) {
        if (name instanceof TranslatableComponent translatable) {
            return "lang:" + translatable.key();
        }
        return PlainTextComponentSerializer.plainText().serialize(name);
    }

    /** The time of day as a clock: tick 0 is sunrise, 06:00. */
    public static String clock(long ticks) {
        long dayTicks = Math.floorMod(ticks, 24000L);
        long hours = (dayTicks / 1000 + 6) % 24;
        long minutes = (dayTicks % 1000) * 60 / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private static Component keyed(String kind, NamespacedKey key) {
        return Component.translatable(kind + "." + key.getNamespace() + "." + key.getKey());
    }
}
