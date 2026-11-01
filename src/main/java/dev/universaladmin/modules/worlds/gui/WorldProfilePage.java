package dev.universaladmin.modules.worlds.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiGlass;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiRows;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.SelectionDialog;
import dev.universaladmin.localization.GameNames;
import dev.universaladmin.modules.worlds.WeatherState;
import dev.universaladmin.modules.worlds.WorldProfileSnapshot;
import dev.universaladmin.modules.worlds.WorldsPermissions;
import dev.universaladmin.modules.worlds.action.SetWorldDifficultyInput;
import dev.universaladmin.modules.worlds.action.SetWorldSpawnInput;
import dev.universaladmin.modules.worlds.action.SetWorldTimeInput;
import dev.universaladmin.modules.worlds.action.SetWorldWeatherInput;
import dev.universaladmin.modules.worlds.action.WorldActionIds;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Ephemeral World Profile page - built fresh per click with the target
 * world's name baked in (like {@code PlayerProfilePage}/{@code
 * ModeratePlayerPage}), not registered in {@code GuiRegistry}. Re-resolves
 * the live {@link World} by name on every render rather than holding a
 * {@link World} reference, since a world can be unloaded/reloaded between
 * clicks.
 */
public final class WorldProfilePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("worlds.profile");

    /** How many trailing content slots the action-button row reserves - fields never spill into it. */
    private final WorldsGuiContext ctx;
    private final String worldName;

    public WorldProfilePage(WorldsGuiContext ctx, String worldName) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.worldName = worldName;
    }

    @Override
    protected Component title(Player viewer) {
        return Component.text(worldName);
    }

    /** Players, chunks, entities and the time change while you look. */
    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(framework.icons().empty(), text("worlds.gui.not-found")));
            return;
        }

        boolean includeSeed = viewer.hasPermission(WorldsPermissions.VIEW_SEED.value());
        WorldProfileSnapshot snapshot = ctx.infoService().profile(world, includeSeed);
        GuiRows.place(view, viewer, 0, 0, GuiGlass.LIGHT_BLUE, text("worlds.gui.row.world"), infoTiles(snapshot));
        GuiRows.place(view, viewer, 1, 0, GuiGlass.LIME, text("worlds.gui.row.settings"), settingButtons(snapshot));
        GuiRows.place(view, viewer, 2, 0, GuiGlass.BLUE, text("worlds.gui.row.go"), List.of(
                new GuiButton(GuiItem.of(Material.ENDER_PEARL, text("worlds.gui.buttons.teleport")),
                        WorldsPermissions.TELEPORT, clickCtx -> teleport(clickCtx.viewer()))));
    }

    private List<GuiButton> infoTiles(WorldProfileSnapshot snapshot) {
        List<Component> header = new ArrayList<>();
        header.add(value("worlds.gui.field.environment", text("worlds.gui.environment." + snapshot.environment().name().toLowerCase(Locale.ROOT))));
        if (snapshot.seed() != null) {
            header.add(value("worlds.gui.field.seed", Component.text(snapshot.seed())));
        }
        return List.of(
                GuiButton.display(GuiItem.of(WorldsGuiFormat.material(snapshot.environment()),
                        Component.text(snapshot.name(), NamedTextColor.GOLD), header)),
                GuiButton.display(GuiItem.of(Material.PLAYER_HEAD, value("worlds.gui.field.players", Component.text(snapshot.players())))),
                GuiButton.display(GuiItem.of(Material.FILLED_MAP, value("worlds.gui.field.chunks", Component.text(snapshot.loadedChunks())))),
                GuiButton.display(GuiItem.of(Material.ARMOR_STAND, value("worlds.gui.field.entities", Component.text(snapshot.entities())))));
    }

    /** Each one shows what it is now and changes it on a click. */
    private List<GuiButton> settingButtons(WorldProfileSnapshot snapshot) {
        Component change = text("worlds.gui.hint.change");
        return List.of(
                new GuiButton(GuiItem.of(Material.CLOCK, value("worlds.gui.field.time", Component.text(GameNames.clock(snapshot.time()))),
                        List.of(text("worlds.gui.field.ticks", snapshot.time()), change)),
                        WorldsPermissions.TIME, clickCtx -> promptTime(clickCtx.viewer())),
                new GuiButton(GuiItem.of(snapshot.storm() ? Material.WATER_BUCKET : Material.SUNFLOWER, value("worlds.gui.field.weather",
                        Component.text(WorldsGuiFormat.weatherLabel(messages, snapshot.storm(), snapshot.thundering()))), List.of(change)),
                        WorldsPermissions.WEATHER, clickCtx -> promptWeather(clickCtx.viewer())),
                new GuiButton(GuiItem.of(Material.IRON_SWORD, value("worlds.gui.field.difficulty", GameNames.difficulty(snapshot.difficulty())),
                        List.of(change)),
                        WorldsPermissions.DIFFICULTY, clickCtx -> promptDifficulty(clickCtx.viewer())),
                new GuiButton(GuiItem.of(Material.RED_BED, value("worlds.gui.field.spawn", Component.text(WorldsGuiFormat.coordinates(snapshot.spawn()))),
                        List.of(text("worlds.gui.buttons.set-spawn"))),
                        WorldsPermissions.SPAWN, clickCtx -> setSpawn(clickCtx.viewer())),
                new GuiButton(GuiItem.of(Material.BARRIER, value("worlds.gui.field.border-size", Component.text("%.0f".formatted(snapshot.border().size()))),
                        List.of(text("gui.hint.open"))),
                        WorldsPermissions.BORDER, clickCtx -> clickCtx.open(new WorldBorderPage(ctx, worldName))),
                new GuiButton(GuiItem.of(Material.COMMAND_BLOCK, text("worlds.gui.buttons.gamerules"), List.of(text("gui.hint.open"))),
                        WorldsPermissions.GAMERULE, clickCtx -> clickCtx.open(new WorldGameRulesListPage(ctx, worldName))));
    }

    /** {@code Label: value} as an item name - readable without hovering. */
    private Component value(String labelKey, Component value) {
        return Component.join(JoinConfiguration.noSeparators(), text(labelKey).colorIfAbsent(NamedTextColor.GRAY),
                Component.text(": ", NamedTextColor.DARK_GRAY), value.colorIfAbsent(NamedTextColor.WHITE));
    }

    private void teleport(Player viewer) {
        WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.TELEPORT_TO_SPAWN, worldName, () -> this.open(viewer));
    }

    private void setSpawn(Player viewer) {
        WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_SPAWN, SetWorldSpawnInput.atActorLocation(worldName), () -> this.open(viewer));
    }

    private void promptTime(Player viewer) {
        GuiTextInput.request(viewer, text("worlds.gui.buttons.set-time"), text("worlds.gui.prompt.time"), "",
                text("gui.confirm"), text("gui.cancel"),
                submitted -> {
                    Long ticks = parseLong(submitted);
                    if (ticks == null) {
                        WorldsGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                        return;
                    }
                    WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_TIME, new SetWorldTimeInput(worldName, ticks), () -> this.open(viewer));
                },
                () -> this.open(viewer));
    }

    private void promptWeather(Player viewer) {
        SelectionDialog.open(viewer, framework, messages, ctx.scheduler(), text("worlds.gui.buttons.set-weather"),
                List.of(WeatherState.values()),
                state -> GuiItem.of(Material.WATER_BUCKET, text("worlds.gui.weather." + state.name().toLowerCase(Locale.ROOT))),
                (selectCtx, state) -> WorldsGuiActions.runAction(
                        ctx, viewer, WorldActionIds.SET_WEATHER, new SetWorldWeatherInput(worldName, state), () -> this.open(viewer)));
    }

    private void promptDifficulty(Player viewer) {
        SelectionDialog.open(viewer, framework, messages, ctx.scheduler(), text("worlds.gui.buttons.set-difficulty"),
                List.of(Difficulty.values()),
                difficulty -> GuiItem.of(Material.IRON_SWORD, GameNames.difficulty(difficulty)),
                (selectCtx, difficulty) -> WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_DIFFICULTY,
                        new SetWorldDifficultyInput(worldName, difficulty), () -> this.open(viewer)));
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
