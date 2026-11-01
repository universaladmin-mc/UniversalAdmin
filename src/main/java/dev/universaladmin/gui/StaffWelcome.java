package dev.universaladmin.gui;

import dev.universaladmin.command.builtin.CorePermissions;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Once per staff member and server, a few seconds after their first join:
 * where to find everything ({@code /admin}). If their game runs in a language
 * UniversalAdmin ships that the server does not use, staff who may change
 * settings also get a click that switches the server to it - in that
 * language. Remembered in the player's data ({@code universaladmin:welcomed}).
 */
public final class StaffWelcome implements Listener {

    private static final NamespacedKey WELCOMED = NamespacedKey.fromString("universaladmin:welcomed");
    private static final PermissionNode SETTINGS = PermissionNode.core("settings.manage");
    private static final long DELAY_TICKS = 60L;

    private final Plugin plugin;
    private final MessageService messages;
    private final SettingsService settings;

    public StaffWelcome(Plugin plugin, MessageService messages, SettingsService settings) {
        this.plugin = plugin;
        this.messages = messages;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission(CorePermissions.MENU.value())
                || player.getPersistentDataContainer().has(WELCOMED, PersistentDataType.BYTE)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> welcome(player), DELAY_TICKS);
    }

    private void welcome(Player player) {
        if (!player.isOnline()) {
            return;
        }
        player.getPersistentDataContainer().set(WELCOMED, PersistentDataType.BYTE, (byte) 1);
        player.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("gui.welcome.menu"))));
        if (player.hasPermission(SETTINGS.value())) {
            ownLanguage(player).ifPresent(locale -> player.sendMessage(ComponentMessages.render(
                    messages.get(locale, MessageKey.of("gui.welcome.language"), locale))));
        }
    }

    /** A language UniversalAdmin ships for the player's game language, if the server uses another one. */
    private Optional<String> ownLanguage(Player player) {
        String language = player.locale().getLanguage().toLowerCase(Locale.ROOT);
        String server = settings.get(CoreSettings.LANGUAGE);
        if (server.toLowerCase(Locale.ROOT).startsWith(language + "_")) {
            return Optional.empty();
        }
        return messages.availableLocales().stream()
                .filter(locale -> locale.toLowerCase(Locale.ROOT).startsWith(language + "_"))
                .findFirst();
    }
}
