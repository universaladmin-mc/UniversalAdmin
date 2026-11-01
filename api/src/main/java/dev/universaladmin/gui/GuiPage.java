package dev.universaladmin.gui;

import org.bukkit.entity.Player;

/**
 * One inventory-GUI screen - the whole navigation model (open, back,
 * refresh) is expressed in terms of this one method, see
 * {@link GuiClickContext}. A click handler must call into a
 * {@link dev.universaladmin.action.Action} or an application service rather
 * than implement logic inline. See docs/architecture/gui.md and
 * docs/development/gui-framework.md for the framework built on top of this
 * interface - {@link AbstractGuiPage} (which every real page extends
 * instead of implementing {@code GuiPage} directly) and {@link GuiView} are
 * the only places that still touch Bukkit inventory APIs and click events
 * directly.
 *
 * <p>A page receives whatever services or actions it needs through its
 * constructor, not by reaching into a shared context at open-time - the
 * same dependency-injection shape services and actions use. This keeps a
 * page testable and keeps the frontend/service boundary honest.
 */
public interface GuiPage {

    GuiPageId id();

    void open(Player viewer);

    /**
     * Whether the page shows values that change on their own - TPS, memory,
     * players. Such a page is drawn again once a second for as long as it is
     * open, in place (see {@link AbstractGuiPage#open}); it should therefore
     * draw its content right away, not after loading it.
     *
     * @since 1.8
     */
    default boolean live() {
        return false;
    }
}
