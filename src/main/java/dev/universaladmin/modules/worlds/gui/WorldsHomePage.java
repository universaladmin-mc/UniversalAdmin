package dev.universaladmin.modules.worlds.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.localization.GameNames;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.worlds.WorldSummary;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * {@code worlds.home} - the World Browser: one tile per currently loaded
 * world ({@link Bukkit#getWorlds()} - every loaded world, never a
 * file-system scan; deliberately no world file manipulation in the core).
 * Always a small, already-in-memory list, so plain synchronous
 * rendering like {@code ServerHomePage}'s dashboard tiles - no async load,
 * no pagination, unlike list pages backed by a database query.
 */
public final class WorldsHomePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("worlds.home");

    private final WorldsGuiContext ctx;

    public WorldsHomePage(WorldsGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("worlds.gui.home.title");
    }

    /** Players, chunks and the time change while you look. */
    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        List<World> worlds = Bukkit.getWorlds();

        if (worlds.isEmpty()) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(framework.icons().empty(), text("gui.empty")));
            return;
        }

        for (int i = 0; i < worlds.size() && i < GuiLayout.contentSize(); i++) {
            World world = worlds.get(i);
            view.place(GuiLayout.contentSlot(i),
                    GuiButton.of(worldTile(world), clickCtx -> clickCtx.open(new WorldProfilePage(ctx, world.getName()))),
                    viewer);
        }
    }

    private GuiItem worldTile(World world) {
        WorldSummary summary = ctx.infoService().summary(world);
        List<Component> lore = List.of(
                text("worlds.gui.home.environment", MessageService.markup(messages.get(
                        MessageKey.of("worlds.gui.environment." + summary.environment().name().toLowerCase(Locale.ROOT))))),
                text("worlds.gui.home.players", summary.players()),
                text("worlds.gui.home.chunks", summary.loadedChunks()),
                text("worlds.gui.home.entities", summary.entities()),
                text("worlds.gui.home.difficulty", GameNames.arg(GameNames.difficulty(summary.difficulty()))),
                text("worlds.gui.home.time", GameNames.clock(summary.time())),
                text("worlds.gui.home.weather", WorldsGuiFormat.weatherLabel(messages, summary.storm(), summary.thundering())),
                text("gui.hint.open"));
        return GuiItem.of(WorldsGuiFormat.material(world.getEnvironment()), Component.text(summary.name(), NamedTextColor.GOLD), lore);
    }
}
