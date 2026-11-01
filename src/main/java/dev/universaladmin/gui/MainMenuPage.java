package dev.universaladmin.gui;

import dev.universaladmin.core.ServiceRegistry;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.module.MenuGroup;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.module.ModuleRegistry;
import dev.universaladmin.module.ModuleState;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.status.MaintenanceStatus;
import dev.universaladmin.status.Nicks;
import dev.universaladmin.status.PlayerStatus;
import dev.universaladmin.status.ReportOverview;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code /admin}'s home screen - a dashboard (ADR 0027):
 *
 * <ul>
 *   <li>Row 1: how things stand - players online, open reports, TPS,
 *       maintenance, the network - and the viewer themselves. A tile opens
 *       the module it is about.</li>
 *   <li>Rows 2-4: one row per {@link MenuGroup}, headed by a coloured pane
 *       with its name, holding a button per {@link ModuleState#ENABLED}
 *       module with a main-menu entry - built-in modules and extensions
 *       alike, nothing here knows a module by name. A button is hidden when
 *       the viewer lacks the module's {@link ModuleDescriptor#menuPermission()}.</li>
 *   <li>Row 5: "My status" - every {@link StatusToggle} the viewer may use,
 *       green while on, red while off.</li>
 * </ul>
 *
 * The status tiles read the status services ({@link ReportOverview},
 * {@link MaintenanceStatus}, ...) when they are there and leave a tile out
 * when its module is not running.
 */
public final class MainMenuPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("main-menu");

    private static final int GROUP_COLUMNS = GuiLayout.COLUMNS - 1;

    private final ModuleRegistry moduleRegistry;
    private final GuiRegistry guiPages;
    private final StatusToggleRegistry toggles;
    private final ServiceRegistry services;
    private final NetworkService network;

    public MainMenuPage(GuiFramework framework, MessageService messages, ModuleRegistry moduleRegistry, GuiRegistry guiPages,
            StatusToggleRegistry toggles, ServiceRegistry services, NetworkService network) {
        super(ID, framework, messages);
        this.moduleRegistry = moduleRegistry;
        this.guiPages = guiPages;
        this.toggles = toggles;
        this.services = services;
        this.network = network;
    }

    /** Players online, TPS, open reports and your own switches change while you look. */
    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected Component title(Player viewer) {
        return text("gui.main-menu.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        renderStatus(view, viewer);
        int placed = renderGroups(view, viewer);
        renderToggles(view, viewer);
        if (placed == 0) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2),
                    GuiItem.of(framework.icons().empty(), text("gui.empty")));
        }
    }

    // --- Row 1: status tiles -------------------------------------------------

    private void renderStatus(GuiView view, Player viewer) {
        int online = Bukkit.getOnlinePlayers().size();
        int vanished = services.get(PlayerStatus.class).map(PlayerStatus::vanishedOnline).orElse(0);
        tile(view, viewer, 10, GuiItem.of(Material.PLAYER_HEAD, text("gui.main-menu.status.players", online - vanished),
                vanished > 0 ? List.of(text("gui.main-menu.status.players-vanished", vanished)) : List.of()), "players");
        services.get(ReportOverview.class).ifPresent(reports -> {
            int open = reports.openReports();
            tile(view, viewer, 11, GuiItem.of(Material.BELL, text(open == 0 ? "gui.main-menu.status.reports-none"
                    : "gui.main-menu.status.reports", open)), "reports");
        });
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        GuiGlass health = tps >= 18.0 ? GuiGlass.LIME : tps >= 15.0 ? GuiGlass.YELLOW : GuiGlass.RED;
        tile(view, viewer, 12, GuiItem.glass(health, text("gui.main-menu.status.tps", String.format(Locale.ROOT, "%.1f", tps)),
                List.of(text("gui.main-menu.status.tps-hint"))), "performance");
        services.get(MaintenanceStatus.class).ifPresent(maintenance -> tile(view, viewer, 13, maintenance.active()
                ? GuiItem.glass(GuiGlass.RED, text("gui.main-menu.status.maintenance-on"), List.of())
                : GuiItem.glass(GuiGlass.LIME, text("gui.main-menu.status.maintenance-off"), List.of()), "server"));
        if (network.enabled()) {
            tile(view, viewer, 14, GuiItem.of(Material.ENDER_EYE, text("gui.main-menu.status.network",
                    network.servers().size(), network.players().size())), "network");
        }
        view.place(16, GuiItem.playerHead(viewer, text("gui.main-menu.status.me", viewer.getName()), me(viewer)));
    }

    /** A status tile that opens the module it is about, if that module has a page the viewer may open. */
    private void tile(GuiView view, Player viewer, int slot, GuiItem item, String module) {
        Optional<ModuleDescriptor> target = moduleRegistry.get(ModuleId.core(module))
                .filter(found -> moduleRegistry.state(found.descriptor().id()) == ModuleState.ENABLED)
                .map(Module::descriptor)
                .filter(descriptor -> descriptor.homePage() != null && guiPages.get(descriptor.homePage()).isPresent());
        if (target.isEmpty()) {
            view.place(slot, item);
            return;
        }
        GuiButton button = new GuiButton(item, target.get().menuPermission(),
                ctx -> guiPages.get(target.get().homePage()).ifPresent(ctx::open));
        if (button.isVisibleTo(viewer)) {
            view.place(slot, button, viewer);
        } else {
            view.place(slot, item);
        }
    }

    private List<Component> me(Player viewer) {
        List<Component> lines = new ArrayList<>();
        Optional<PlayerStatus> status = services.get(PlayerStatus.class);
        status.filter(players -> players.vanished(viewer.getUniqueId())).ifPresent(on -> lines.add(text("gui.main-menu.status.me-vanished")));
        status.filter(players -> players.staffMode(viewer.getUniqueId())).ifPresent(on -> lines.add(text("gui.main-menu.status.me-staff-mode")));
        status.filter(players -> players.godmode(viewer.getUniqueId())).ifPresent(on -> lines.add(text("gui.main-menu.status.me-godmode")));
        services.get(Nicks.class).flatMap(nicks -> nicks.nick(viewer.getUniqueId()))
                .ifPresent(nick -> lines.add(text("gui.main-menu.status.me-nick", nick.name())));
        if (lines.isEmpty()) {
            lines.add(text("gui.main-menu.status.me-plain"));
        }
        return lines;
    }

    // --- Rows 2-4: module groups ----------------------------------------------

    private int renderGroups(GuiView view, Player viewer) {
        List<ModuleDescriptor> entries = moduleRegistry.withState(ModuleState.ENABLED).stream()
                .map(Module::descriptor)
                .filter(descriptor -> descriptor.homePage() != null && guiPages.get(descriptor.homePage()).isPresent())
                .sorted(Comparator.comparingInt(ModuleDescriptor::menuOrder).thenComparing(ModuleDescriptor::displayName))
                .toList();
        int row = 2;
        int placed = 0;
        for (MenuGroup group : MenuGroup.values()) {
            List<GuiButton> buttons = entries.stream()
                    .filter(descriptor -> descriptor.menuGroup() == group)
                    .map(descriptor -> new GuiButton(item(descriptor), descriptor.menuPermission(),
                            ctx -> guiPages.get(descriptor.homePage()).ifPresent(ctx::open)))
                    .filter(button -> button.isVisibleTo(viewer))
                    .toList();
            if (buttons.isEmpty()) {
                continue;
            }
            // A group with more buttons than a row continues in the next one, under the same label.
            for (int start = 0; start < buttons.size() && row <= 4; start += GROUP_COLUMNS, row++) {
                view.place(row * GuiLayout.COLUMNS, GuiItem.glass(groupGlass(group),
                        text("gui.main-menu.group." + group.name().toLowerCase(Locale.ROOT)), List.of()));
                for (int i = start; i < Math.min(buttons.size(), start + GROUP_COLUMNS); i++) {
                    view.place(row * GuiLayout.COLUMNS + 1 + (i - start), buttons.get(i), viewer);
                    placed++;
                }
            }
        }
        return placed;
    }

    private static GuiGlass groupGlass(MenuGroup group) {
        return switch (group) {
            case PLAYERS -> GuiGlass.RED;
            case SERVER -> GuiGlass.CYAN;
            case SYSTEM -> GuiGlass.LIGHT_GRAY;
        };
    }

    // --- Row 5: my status -----------------------------------------------------

    private void renderToggles(GuiView view, Player viewer) {
        List<StatusToggle> visible = toggles.toggles().stream()
                .filter(toggle -> toggle.permission() == null || viewer.hasPermission(toggle.permission().value()))
                .toList();
        if (visible.isEmpty()) {
            return;
        }
        view.place(GuiLayout.TOOL_LEFT_1, GuiItem.glass(GuiGlass.BLACK, text("gui.main-menu.my-status"), List.of()));
        int slot = GuiLayout.TOOL_LEFT_2;
        for (StatusToggle toggle : visible) {
            if (slot > GuiLayout.TOOL_RIGHT_3) {
                break;
            }
            boolean on = toggle.active(viewer);
            Component label = toggle.label(viewer);
            GuiItem item = GuiItem.glass(on ? GuiGlass.LIME : GuiGlass.RED,
                    text(on ? "gui.main-menu.toggle-on" : "gui.main-menu.toggle-off", PlainTextComponentSerializer.plainText().serialize(label)),
                    List.of(text(on ? "gui.main-menu.toggle-on-hint" : "gui.main-menu.toggle-off-hint")));
            view.place(slot++, GuiButton.of(item, ctx -> toggle.toggle(ctx, () -> {
                if (ctx.viewer().isOnline()) {
                    this.open(ctx.viewer());
                }
            })), viewer);
        }
    }

    /**
     * Name and description come from {@code menu.<namespace>.<module>.name}/
     * {@code .description} when the active locale (or an extension's bundled
     * defaults) has them, otherwise from the descriptor's own English text.
     */
    private GuiItem item(ModuleDescriptor descriptor) {
        Material material = descriptor.icon() != null ? framework.icons().resolve(descriptor.icon()) : Material.PAPER;
        String base = "menu." + descriptor.id().key().namespace() + "." + descriptor.id().key().name();
        Component name = localizedOr(base + ".name", descriptor.displayName()).colorIfAbsent(NamedTextColor.GOLD);
        Component description = localizedOr(base + ".description", descriptor.description()).colorIfAbsent(NamedTextColor.GRAY);
        return GuiItem.of(material, name, List.of(description, text("gui.hint.open")));
    }

    private Component localizedOr(String key, String fallback) {
        MessageKey messageKey = MessageKey.of(key);
        return messages.has(messageKey) ? ComponentMessages.render(messages.get(messageKey)) : Component.text(fallback);
    }
}
