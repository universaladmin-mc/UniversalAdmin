package dev.universaladmin.gui;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Draws every open {@link GuiPage#live() live} page again, once a second -
 * in place, into the window the viewer has open, so only the slots whose
 * item changed reach the client. Pages that are not live are never touched.
 */
public final class LiveRefresh implements Runnable {

    private final Server server;
    private final Logger logger;

    public LiveRefresh(Server server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Override
    public void run() {
        for (Player player : server.getOnlinePlayers()) {
            Optional<GuiView> open = GuiView.of(player.getOpenInventory().getTopInventory());
            if (open.isEmpty() || !open.get().isOpenFor(player) || !open.get().page().live()) {
                continue;
            }
            try {
                open.get().page().open(player);
            } catch (RuntimeException failure) {
                // One page failing must not stop the others, nor fill the log every second.
                logger.log(Level.FINE, "Live refresh of " + open.get().pageId() + " failed", failure);
            }
        }
    }
}
