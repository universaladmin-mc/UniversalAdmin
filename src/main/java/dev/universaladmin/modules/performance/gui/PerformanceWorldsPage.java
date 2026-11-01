package dev.universaladmin.modules.performance.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.performance.WorldPerformanceSnapshot;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code performance.worlds} - one read-only tile per loaded world from the
 * cached {@link dev.universaladmin.modules.performance.PerformanceSamplingService#worldSnapshots()}
 * (players/loaded chunks/entities) - same "small, already in-memory list,
 * no async/pagination needed" shape as {@code WorldsHomePage}.
 */
public final class PerformanceWorldsPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("performance.worlds");

    private final PerformanceGuiContext ctx;

    public PerformanceWorldsPage(PerformanceGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("performance.gui.worlds.title");
    }

    /** Players, chunks and entities change while you look. */
    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        List<WorldPerformanceSnapshot> worlds = ctx.samplingService().worldSnapshots();

        if (worlds.isEmpty()) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(framework.icons().empty(), text("gui.empty")));
            return;
        }

        for (int i = 0; i < worlds.size() && i < GuiLayout.contentSize(); i++) {
            WorldPerformanceSnapshot world = worlds.get(i);
            List<Component> lore = List.of(
                    text("performance.gui.worlds.players", world.players()),
                    text("performance.gui.worlds.chunks", world.loadedChunks()),
                    text("performance.gui.worlds.entities", world.entities()));
            view.place(GuiLayout.contentSlot(i), GuiItem.of(Material.GRASS_BLOCK, Component.text(world.worldName(), NamedTextColor.GOLD), lore));
        }
    }
}
