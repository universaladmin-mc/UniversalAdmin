package dev.universaladmin.gui;

import dev.universaladmin.permission.PermissionNode;
import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * A clickable {@link GuiView} slot: an icon plus a click handler, and
 * optionally a {@link PermissionNode} the viewer must hold.
 *
 * <p><b>Permission is display-only.</b> {@link GuiView#place(int, GuiButton, org.bukkit.entity.Player)}
 * refuses to place a button the viewer lacks permission for - the slot is
 * left empty rather than shown disabled, so there is nothing to click in
 * the first place. {@link #handle} re-checks anyway, purely as defense in
 * depth against a stale click racing a permission change; it is not the
 * real authorization. The {@link dev.universaladmin.action.Action}/service
 * this button ultimately calls into must authorize again on its own - see
 * docs/architecture/decisions/0004-gui-service-separation.md and the
 * "Permissions" section of docs/development/gui-framework.md.
 */
public final class GuiButton {

    private final GuiItem item;
    private final PermissionNode permission;
    private final ClickHandler onClick;

    public GuiButton(GuiItem item, PermissionNode permission, ClickHandler onClick) {
        this.item = item;
        this.permission = permission;
        this.onClick = onClick;
    }

    /** A button anyone who can see the page may click. */
    /**
     * An item that only shows something - a value, a status - placed like a
     * button, e.g. among the entries of a {@link GuiRows} row. A click does
     * nothing.
     *
     * @since 1.8
     */
    public static GuiButton display(GuiItem item) {
        return new GuiButton(item, null, context -> {
        });
    }

    public static GuiButton of(GuiItem item, ClickHandler onClick) {
        return new GuiButton(item, null, onClick);
    }

    public GuiItem item() {
        return item;
    }

    public Optional<PermissionNode> permission() {
        return Optional.ofNullable(permission);
    }

    /** {@code true} if this button has no permission requirement, or {@code viewer} holds it. */
    public boolean isVisibleTo(Player viewer) {
        return permission == null || viewer.hasPermission(permission.value());
    }

    void handle(GuiClickContext context) {
        if (!isVisibleTo(context.viewer())) {
            return;
        }
        onClick.handle(context);
    }

    @FunctionalInterface
    public interface ClickHandler {
        void handle(GuiClickContext context);
    }
}
