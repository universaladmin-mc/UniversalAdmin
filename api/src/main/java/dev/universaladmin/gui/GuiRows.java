package dev.universaladmin.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * The hub layout: rows of the content area, each behind a pane in a colour
 * that says what the row is for, named after it - the layout of a player's
 * profile and of the server, worlds and performance pages (see
 * docs/architecture/decisions/0027-menu-design-system.md). Content rows are
 * numbered 0 to 3, columns 0 to 8.
 *
 * <pre>{@code
 * GuiRows.place(view, viewer, 0, 0, GuiGlass.LIGHT_BLUE, text("my.status"), statusTiles);
 * GuiRows.place(view, viewer, 2, 0, GuiGlass.RED, text("my.danger"), dangerousButtons);
 * }</pre>
 *
 * @since 1.8
 */
public final class GuiRows {

    /** Content rows: 1 to 4 of the six. */
    public static final int ROWS = GuiLayout.ROWS - 2;

    private GuiRows() {
    }

    /** The inventory slot of {@code column} in content row {@code row}. */
    public static int slot(int row, int column) {
        if (row < 0 || row >= ROWS || column < 0 || column >= GuiLayout.COLUMNS) {
            throw new IllegalArgumentException("No content slot at row " + row + ", column " + column);
        }
        return GuiLayout.CONTENT_START_SLOT + row * GuiLayout.COLUMNS + column;
    }

    /**
     * Places a row: the label pane at {@code column}, then the entries the
     * viewer may see, as many as fit up to the end of the row. Nothing is
     * placed if the viewer may see none of them.
     *
     * @return the visible entries that did not fit, e.g. to place them in a "more" row
     */
    public static List<GuiButton> place(
            GuiView view, Player viewer, int row, int column, GuiGlass glass, Component label, List<GuiButton> entries) {
        List<GuiButton> visible = new ArrayList<>();
        for (GuiButton entry : entries) {
            if (entry.isVisibleTo(viewer)) {
                visible.add(entry);
            }
        }
        if (visible.isEmpty()) {
            return List.of();
        }
        view.place(slot(row, column), GuiItem.glass(glass, label, List.of()));
        int room = GuiLayout.COLUMNS - column - 1;
        for (int i = 0; i < visible.size() && i < room; i++) {
            view.place(slot(row, column + 1 + i), visible.get(i), viewer);
        }
        return visible.size() > room ? List.copyOf(visible.subList(room, visible.size())) : List.of();
    }

    /** A light grey pane between two sections - at one slot. */
    public static void divider(GuiView view, int row, int column) {
        view.place(slot(row, column), GuiItem.pane(GuiGlass.LIGHT_GRAY));
    }
}
