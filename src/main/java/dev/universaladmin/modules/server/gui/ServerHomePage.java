package dev.universaladmin.modules.server.gui;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiForm;
import dev.universaladmin.gui.GuiGlass;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiRows;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.server.MaintenanceScope;
import dev.universaladmin.modules.server.MaintenanceState;
import dev.universaladmin.modules.server.ServerDashboardSnapshot;
import dev.universaladmin.modules.server.ServerLifecycleService;
import dev.universaladmin.modules.server.ServerPermissions;
import dev.universaladmin.modules.server.action.BroadcastTitleInput;
import dev.universaladmin.modules.server.action.ServerActionIds;
import dev.universaladmin.settings.CoreSettings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code server.home} - the Server module's dashboard: live environment info
 * plus navigation into broadcast/maintenance/shutdown/restart, registered by
 * {@code ServerModule} in place of the {@code PlaceholderGuiPage} {@code
 * UniversalAdminPlugin} used to register for this id (same precedent as
 * {@code players.home}/{@code moderation.home}).
 */
public final class ServerHomePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("server.home");

    private final ServerGuiContext ctx;

    public ServerHomePage(ServerGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("server.gui.home.title");
    }

    /** Uptime, players, memory and CPU change while you look. */
    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        GuiRows.place(view, viewer, 0, 0, GuiGlass.LIGHT_BLUE, text("server.gui.home.row.status"), statusTiles());
        GuiRows.place(view, viewer, 1, 0, GuiGlass.LIME, text("server.gui.home.row.broadcast"), List.of(
                new GuiButton(GuiItem.of(Material.PAPER, text("server.gui.home.broadcast-message")),
                        ServerPermissions.BROADCAST, clickCtx -> promptBroadcastMessage(clickCtx.viewer())),
                new GuiButton(GuiItem.of(Material.OAK_SIGN, text("server.gui.home.broadcast-title")),
                        ServerPermissions.BROADCAST, clickCtx -> promptBroadcastTitle(clickCtx.viewer())),
                new GuiButton(GuiItem.of(Material.NAME_TAG, text("server.gui.home.broadcast-actionbar")),
                        ServerPermissions.BROADCAST, clickCtx -> promptBroadcastActionbar(clickCtx.viewer()))));
        GuiRows.place(view, viewer, 2, 0, GuiGlass.ORANGE, text("server.gui.home.row.maintenance"), List.of(maintenanceTile(viewer)));
        GuiRows.place(view, viewer, 3, 0, GuiGlass.RED, text("server.gui.home.row.lifecycle"), lifecycleButtons(viewer));
    }

    /** What the server is and how it is doing - the value in the name, readable without hovering. */
    private List<GuiButton> statusTiles() {
        ServerDashboardSnapshot snapshot = ctx.dashboardService().snapshot();
        String cpu = snapshot.cpuLoadPercent().isPresent()
                ? String.format(Locale.ROOT, "%.0f%%", snapshot.cpuLoadPercent().getAsDouble())
                : plain("server.gui.home.cpu-unavailable");
        return List.of(
                tile(Material.BOOK, "server.gui.home.version", "v" + snapshot.pluginVersion(), List.of(
                        text("server.gui.home.version-lore", snapshot.pluginVersion(), snapshot.paperVersion(),
                                snapshot.minecraftVersion(), snapshot.javaVersion()))),
                tile(Material.CLOCK, "server.gui.home.uptime", formatUptime(snapshot.uptime()), List.of()),
                tile(Material.PLAYER_HEAD, "server.gui.home.players", snapshot.onlinePlayers() + " / " + snapshot.maxPlayers(), List.of()),
                tile(Material.GRASS_BLOCK, "server.gui.home.worlds", String.valueOf(snapshot.worldCount()), List.of()),
                tile(Material.REDSTONE, "server.gui.home.memory",
                        formatMegabytes(snapshot.usedMemoryBytes()) + " / " + formatMegabytes(snapshot.maxMemoryBytes()) + " MB", List.of()),
                tile(Material.COMPARATOR, "server.gui.home.cpu", cpu,
                        List.of(text("server.gui.home.cores", snapshot.availableProcessors()))),
                tile(Material.ENDER_CHEST, "server.gui.home.database", snapshot.databaseStatus().name(), List.of()),
                tile(Material.BOOKSHELF, "server.gui.home.modules", String.valueOf(snapshot.enabledModules().size()),
                        List.of(Component.text(String.join(", ", snapshot.enabledModules()), NamedTextColor.GRAY))));
    }

    private GuiButton tile(Material material, String labelKey, String value, List<Component> lore) {
        Component name = Component.join(JoinConfiguration.noSeparators(), text(labelKey),
                Component.text(": ", NamedTextColor.DARK_GRAY), Component.text(value, NamedTextColor.WHITE));
        return GuiButton.display(GuiItem.of(material, name, lore));
    }

    /** On or off, why, and a click to manage it. */
    private GuiButton maintenanceTile(Player viewer) {
        MaintenanceState state = ctx.maintenanceService().current();
        MaintenanceState network = ctx.maintenanceService().current(MaintenanceScope.NETWORK);
        boolean active = state.enabled() || network.enabled();
        List<Component> lore = new ArrayList<>();
        if (network.enabled()) {
            lore.add(text("server.gui.home.maintenance-network"));
        }
        MaintenanceState shown = state.enabled() ? state : network;
        if (active && shown.reason() != null && !shown.reason().isBlank()) {
            lore.add(text("server.gui.home.maintenance-reason", shown.reason()));
        }
        Material material = active ? Material.RED_WOOL : Material.LIME_WOOL;
        Component name = text(active ? "server.gui.home.maintenance-on" : "server.gui.home.maintenance-off");
        if (!viewer.hasPermission(ServerPermissions.MAINTENANCE.value())) {
            return GuiButton.display(GuiItem.of(material, name, lore));
        }
        lore.add(text("gui.hint.open"));
        return GuiButton.of(GuiItem.of(material, name, lore), clickCtx -> clickCtx.open(new ServerMaintenancePage(ctx)));
    }

    /** Restart and shutdown - and, while one is counting down, the button to cancel it. */
    private List<GuiButton> lifecycleButtons(Player viewer) {
        List<GuiButton> buttons = new ArrayList<>();
        buttons.add(new GuiButton(GuiItem.of(Material.RESPAWN_ANCHOR, text("server.gui.home.restart")),
                ServerPermissions.RESTART, clickCtx -> promptLifecycle(clickCtx.viewer(), false)));
        buttons.add(new GuiButton(GuiItem.of(Material.MAGMA_BLOCK, text("server.gui.home.shutdown")),
                ServerPermissions.SHUTDOWN, clickCtx -> promptLifecycle(clickCtx.viewer(), true)));
        ServerLifecycleService.PendingSnapshot pending = ctx.lifecycleService().pending();
        if (pending.action() != ServerLifecycleService.PendingAction.NONE) {
            boolean isShutdown = pending.action() == ServerLifecycleService.PendingAction.SHUTDOWN;
            ActionId cancelId = isShutdown ? ServerActionIds.CANCEL_SHUTDOWN : ServerActionIds.CANCEL_RESTART;
            buttons.add(new GuiButton(GuiItem.of(Material.BARRIER,
                    text(isShutdown ? "server.gui.home.pending-shutdown" : "server.gui.home.pending-restart", pending.remainingSeconds())),
                    isShutdown ? ServerPermissions.SHUTDOWN : ServerPermissions.RESTART,
                    clickCtx -> ServerGuiActions.<Void>runAction(ctx, clickCtx.viewer(), cancelId, null, () -> this.open(clickCtx.viewer()))));
        }
        return buttons;
    }

    private String plain(String key) {
        return PlainTextComponentSerializer.plainText().serialize(text(key));
    }

    /** A multi-line message - the dialog keeps line breaks, so a broadcast can span several chat lines. */
    private void promptBroadcastMessage(Player viewer) {
        GuiForm.builder(text("server.gui.home.broadcast-message"))
                .multilineText("message", text("server.gui.prompt.message"), "", 255, 4)
                .open(viewer, text("gui.confirm"), text("gui.cancel"),
                        response -> submitOrReopen(viewer, response.text("message"),
                                message -> ServerGuiActions.runAction(ctx, viewer, ServerActionIds.BROADCAST_MESSAGE,
                                        message, () -> this.open(viewer))),
                        () -> this.open(viewer));
    }

    /** Title and subtitle in one form (ADR 0023). */
    private void promptBroadcastTitle(Player viewer) {
        GuiForm.builder(text("server.gui.home.broadcast-title"))
                .text("title", text("server.gui.prompt.title"), "", 128)
                .text("subtitle", text("server.gui.prompt.subtitle"), "", 128)
                .open(viewer, text("gui.confirm"), text("gui.cancel"),
                        response -> submitOrReopen(viewer, response.text("title"),
                                title -> ServerGuiActions.runAction(ctx, viewer, ServerActionIds.BROADCAST_TITLE,
                                        new BroadcastTitleInput(title, response.textOrNull("subtitle")), () -> this.open(viewer))),
                        () -> this.open(viewer));
    }

    private void promptBroadcastActionbar(Player viewer) {
        GuiTextInput.request(viewer, text("server.gui.home.broadcast-actionbar"), text("server.gui.prompt.message"), "",
                text("gui.confirm"), text("gui.cancel"),
                submitted -> submitOrReopen(viewer, submitted,
                        message -> ServerGuiActions.runAction(ctx, viewer, ServerActionIds.BROADCAST_ACTIONBAR,
                                message, () -> this.open(viewer))),
                () -> this.open(viewer));
    }

    private void promptLifecycle(Player viewer, boolean shutdown) {
        GuiTextInput.request(viewer, text(shutdown ? "server.gui.home.shutdown" : "server.gui.home.restart"),
                text("server.gui.prompt.reason-optional"), "", text("gui.confirm"), text("gui.cancel"),
                reason -> confirmLifecycle(viewer, shutdown, reason == null || reason.isBlank() ? null : reason.trim()),
                () -> this.open(viewer));
    }

    private void confirmLifecycle(Player viewer, boolean shutdown, String reason) {
        ActionId actionId = shutdown ? ServerActionIds.SHUTDOWN : ServerActionIds.RESTART;
        Runnable apply = () -> ServerGuiActions.runAction(ctx, viewer, actionId, reason, () -> this.open(viewer));
        if (!ctx.settings().get(CoreSettings.GUI_CONFIRMATIONS)) {
            apply.run();
            return;
        }
        ConfirmationDialog.open(viewer, framework, messages, text(shutdown ? "server.gui.home.shutdown" : "server.gui.home.restart"),
                List.of(text(shutdown ? "server.gui.confirm.shutdown" : "server.gui.confirm.restart")),
                ConfirmationDialog.DangerLevel.DANGEROUS, confirmCtx -> apply.run(), confirmCtx -> confirmCtx.back());
    }

    private void submitOrReopen(Player viewer, String submitted, java.util.function.Consumer<String> onNonBlank) {
        if (submitted == null || submitted.isBlank()) {
            this.open(viewer);
            return;
        }
        onNonBlank.accept(submitted.trim());
    }

    private String formatUptime(Duration uptime) {
        long seconds = uptime.toSeconds();
        return "%dh %dm %ds".formatted(seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private String formatMegabytes(long bytes) {
        return String.valueOf(bytes / (1024 * 1024));
    }
}
