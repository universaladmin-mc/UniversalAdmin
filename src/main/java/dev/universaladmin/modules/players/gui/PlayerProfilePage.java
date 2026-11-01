package dev.universaladmin.modules.players.gui;

import dev.universaladmin.action.ActionResult;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiGlass;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.gui.PlayerMenuGroup;
import dev.universaladmin.localization.GameNames;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.modules.players.PlayerSnapshot;
import dev.universaladmin.modules.players.action.GetPlayerIpAddressAction;
import dev.universaladmin.modules.players.action.PlayerTargetInput;
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkVisibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

/**
 * A player's profile, the hub for everything about them: on the left what
 * is known about them (who, where, how long, how they are - an offline
 * target simply omits whatever needs live entity state, see
 * {@link PlayerSnapshot}), behind a light grey divider one row per
 * {@link PlayerMenuGroup} with its buttons, at the bottom the previous and
 * next online player. Ephemeral like {@code AuditLogDetailPage} - built
 * fresh per click with the target baked in via the constructor, never
 * registered in {@code GuiRegistry}.
 */
public final class PlayerProfilePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("players.profile");

    private static final int CONTENT_ROWS = 4;
    private static final int INFO_COLUMN = 0;
    private static final int DIVIDER_COLUMN = 1;
    private static final int LABEL_COLUMN = 2;
    private static final int FIRST_BUTTON_COLUMN = 3;
    private static final int BUTTONS_PER_ROW = GuiLayout.COLUMNS - FIRST_BUTTON_COLUMN;

    private final PlayerGuiContext ctx;
    private final UUID targetId;
    private final String targetName;

    public PlayerProfilePage(PlayerGuiContext ctx, UUID targetId, String targetName) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    @Override
    protected Component title(Player viewer) {
        return Component.text(targetName);
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        renderPlaceholder(context.view(), framework.icons().loading(), text("gui.loading"));
        Player viewer = context.viewer();

        ctx.playerService().snapshot(targetId)
                .thenCompose(snapshot -> fetchIpIfPermitted(viewer, snapshot))
                .whenComplete((result, error) -> ctx.scheduler().runOnMainThread(() -> {
                    if (!stillOpen(context)) {
                        return;
                    }
                    if (error != null) {
                        renderPlaceholder(context.view(), framework.icons().error(), text("gui.error"));
                        return;
                    }
                    if (result.snapshot().isEmpty()) {
                        renderPlaceholder(context.view(), framework.icons().empty(), text("players.gui.not-found"));
                        return;
                    }
                    renderProfile(context, result.snapshot().get(), result.ip());
                }));
    }

    private CompletableFuture<Result> fetchIpIfPermitted(Player viewer, Optional<PlayerSnapshot> snapshot) {
        boolean eligible = snapshot.isPresent() && snapshot.get().online() && viewer.hasPermission(PlayerPermissions.IP.value());
        if (!eligible) {
            return CompletableFuture.completedFuture(new Result(snapshot, null));
        }
        return ctx.actionExecutor()
                .<PlayerTargetInput, String>execute(GetPlayerIpAddressAction.ID, PlayerGuiActions.contextFor(viewer), new PlayerTargetInput(targetId))
                .thenApply(ipResult -> new Result(snapshot, ipResult instanceof ActionResult.Success<String> success ? success.value() : null));
    }

    private void renderProfile(GuiRenderContext context, PlayerSnapshot snapshot, String ip) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        view.clearContentArea();

        List<GuiItem> info = List.of(header(snapshot), location(snapshot, viewer), playtime(snapshot), condition(snapshot, ip));
        GuiItem divider = GuiItem.pane(GuiGlass.LIGHT_GRAY);
        for (int row = 0; row < CONTENT_ROWS; row++) {
            view.place(slot(row, INFO_COLUMN), info.get(row));
            view.place(slot(row, DIVIDER_COLUMN), divider);
        }
        placeGroups(view, viewer, snapshot);
        placeToolbar(view, viewer);
    }

    private static int slot(int row, int column) {
        return GuiLayout.CONTENT_START_SLOT + row * GuiLayout.COLUMNS + column;
    }

    /**
     * One row per {@link PlayerMenuGroup}, behind a pane in its colour: this
     * module's own buttons, then every {@link PlayerMenuEntry} other modules
     * and extensions contributed, in their declared order. A row holds six
     * buttons; the rest move on to the last row.
     */
    private void placeGroups(GuiView view, Player viewer, PlayerSnapshot snapshot) {
        Map<PlayerMenuGroup, List<GuiButton>> groups = new EnumMap<>(PlayerMenuGroup.class);
        for (PlayerMenuGroup group : PlayerMenuGroup.values()) {
            groups.put(group, new ArrayList<>());
        }
        // Inventory/Ender Chest and the quick actions need the live player -
        // hidden (not disabled) while the target is offline, per "hide (don't
        // disable) a GUI button whose action needs an online player".
        if (snapshot.online()) {
            groups.get(PlayerMenuGroup.VIEW).add(new GuiButton(GuiItem.of(Material.CHEST, text("players.gui.profile.inventory")),
                    PlayerPermissions.INVENTORY_VIEW,
                    clickCtx -> clickCtx.open(new PlayerInventoryPage(ctx, targetId, targetName))));
            groups.get(PlayerMenuGroup.VIEW).add(new GuiButton(GuiItem.of(Material.ENDER_CHEST, text("players.gui.profile.enderchest")),
                    PlayerPermissions.ENDERCHEST_VIEW,
                    clickCtx -> clickCtx.open(new PlayerEnderChestPage(ctx, targetId, targetName))));
        }
        groups.get(PlayerMenuGroup.VIEW).add(GuiButton.of(GuiItem.of(Material.BREWING_STAND, text("players.gui.profile.effects")),
                clickCtx -> clickCtx.open(new PlayerEffectsPage(ctx, targetId, targetName))));
        if (snapshot.online()) {
            groups.get(PlayerMenuGroup.HELP).addAll(
                    new PlayerActionsPage(ctx, targetId, targetName).quickButtons(Bukkit.getPlayer(targetId), this));
        }
        groups.get(PlayerMenuGroup.HELP).add(GuiButton.of(GuiItem.of(Material.COMMAND_BLOCK, text("players.gui.profile.actions")),
                clickCtx -> clickCtx.open(new PlayerActionsPage(ctx, targetId, targetName))));

        PlayerRef target = new PlayerRef(targetId, targetName);
        for (PlayerMenuEntry entry : ctx.playerMenu().entries()) {
            if (entry.requiresOnline() && !snapshot.online() || !entry.visible(viewer, target)) {
                continue;
            }
            PlayerMenuGroup group = entry.group() == null ? PlayerMenuGroup.MORE : entry.group();
            groups.get(group).add(new GuiButton(entry.icon(viewer, target), entry.permission(), clickCtx -> entry.onClick(clickCtx, target)));
        }

        List<GuiButton> overflow = new ArrayList<>();
        int row = 0;
        for (PlayerMenuGroup group : PlayerMenuGroup.values()) {
            List<GuiButton> buttons = new ArrayList<>(group == PlayerMenuGroup.MORE ? overflow : List.of());
            groups.get(group).stream().filter(button -> button.isVisibleTo(viewer)).forEach(buttons::add);
            if (group != PlayerMenuGroup.MORE && buttons.size() > BUTTONS_PER_ROW) {
                overflow.addAll(buttons.subList(BUTTONS_PER_ROW, buttons.size()));
                buttons = buttons.subList(0, BUTTONS_PER_ROW);
            }
            if (!buttons.isEmpty()) {
                view.place(slot(row, LABEL_COLUMN), GuiItem.glass(group.glass(),
                        text("players.gui.profile.group." + group.name().toLowerCase(Locale.ROOT)), List.of()));
                for (int i = 0; i < buttons.size() && i < BUTTONS_PER_ROW; i++) {
                    view.place(slot(row, FIRST_BUTTON_COLUMN + i), buttons.get(i), viewer);
                }
            }
            row++;
        }
    }

    /** The previous and next online player (by name) either side, the UUID to copy in the middle. */
    private void placeToolbar(GuiView view, Player viewer) {
        view.place(GuiLayout.PAGE_INDICATOR_SLOT, GuiButton.of(
                GuiItem.of(Material.NAME_TAG, text("players.gui.profile.uuid"),
                        List.of(Component.text(targetId.toString(), NamedTextColor.GRAY), text("players.gui.profile.uuid-hint"))),
                clickCtx -> clickCtx.viewer().sendMessage(text("players.gui.profile.uuid-chat").append(
                        Component.text(targetId.toString(), NamedTextColor.WHITE)
                                .clickEvent(ClickEvent.copyToClipboard(targetId.toString()))
                                .hoverEvent(HoverEvent.showText(text("players.gui.profile.uuid-hint")))))),
                viewer);
        List<Player> online = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(viewer) || viewer.canSee(player)) {
                online.add(player);
            }
        }
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int index = -1;
        for (int i = 0; i < online.size(); i++) {
            if (online.get(i).getUniqueId().equals(targetId)) {
                index = i;
            }
        }
        if (index < 0 || online.size() < 2) {
            return;
        }
        Player previous = online.get((index - 1 + online.size()) % online.size());
        Player next = online.get((index + 1) % online.size());
        view.place(GuiLayout.TOOL_LEFT_1, neighbour(previous, "players.gui.profile.previous"), viewer);
        view.place(GuiLayout.TOOL_RIGHT_3, neighbour(next, "players.gui.profile.next"), viewer);
    }

    /** In place of this profile - "back" still leads to where the first one was opened from. */
    private GuiButton neighbour(Player player, String labelKey) {
        return GuiButton.of(GuiItem.playerHead(player, text(labelKey), List.of(Component.text(player.getName(), NamedTextColor.WHITE))),
                clickCtx -> new PlayerProfilePage(ctx, player.getUniqueId(), player.getName()).open(clickCtx.viewer()));
    }

    /** Where the player is, if they are online on another server of the network - and the viewer may see them there. */
    private Optional<NetworkPlayer> elsewhere(Player viewer) {
        if (!ctx.network().enabled()) {
            return Optional.empty();
        }
        return ctx.network().player(targetId)
                .filter(player -> !player.server().equalsIgnoreCase(ctx.network().serverName()))
                .filter(player -> NetworkVisibility.canSee(viewer, ctx.network(), player));
    }

    private GuiItem header(PlayerSnapshot snapshot) {
        Optional<NetworkPlayer> elsewhere = snapshot.online() ? Optional.empty() : ctx.network().player(targetId)
                .filter(player -> ctx.network().enabled() && !player.server().equalsIgnoreCase(ctx.network().serverName()));
        if (elsewhere.isPresent()) {
            List<Component> lore = List.of(text("players.gui.field.status-elsewhere", elsewhere.get().server()),
                    Component.text(snapshot.id().toString(), NamedTextColor.DARK_GRAY));
            return GuiItem.playerHead(Bukkit.getOfflinePlayer(snapshot.id()), Component.text(targetName, NamedTextColor.GOLD), lore);
        }
        Component statusKey = text(snapshot.online() ? "players.gui.field.status-online" : "players.gui.field.status-offline");
        List<Component> lore = List.of(statusKey, Component.text(snapshot.id().toString(), NamedTextColor.DARK_GRAY));
        if (snapshot.online()) {
            return GuiItem.playerHead(Bukkit.getOfflinePlayer(snapshot.id()), Component.text(targetName, NamedTextColor.GOLD), lore);
        }
        return GuiItem.of(Material.SKELETON_SKULL, Component.text(targetName, NamedTextColor.GOLD), lore);
    }

    private GuiItem location(PlayerSnapshot snapshot, Player viewer) {
        List<Component> lines = new ArrayList<>();
        Optional<NetworkPlayer> elsewhere = elsewhere(viewer);
        if (ctx.network().enabled() && (snapshot.online() || elsewhere.isPresent())) {
            lines.add(line("players.gui.field.server", elsewhere.map(NetworkPlayer::server).orElse(ctx.network().serverName())));
        }
        if (snapshot.world() != null) {
            lines.add(line("players.gui.field.world", snapshot.world()));
            lines.add(line("players.gui.field.coordinates", PlayerGuiFormat.coordinates(snapshot.x(), snapshot.y(), snapshot.z())));
        }
        if (snapshot.respawnLocation() != null) {
            lines.add(line("players.gui.field.respawn-location", snapshot.respawnLocation()));
        }
        return info(Material.COMPASS, "players.gui.profile.info.location", lines);
    }

    private GuiItem playtime(PlayerSnapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        lines.add(line("players.gui.field.first-join", PlayerGuiFormat.instant(snapshot.firstJoin())));
        lines.add(line("players.gui.field.last-join", PlayerGuiFormat.instant(snapshot.lastSeen())));
        if (snapshot.sessionDuration() != null) {
            lines.add(line("players.gui.field.session-duration", PlayerGuiFormat.duration(snapshot.sessionDuration())));
        }
        lines.add(line("players.gui.field.total-playtime", PlayerGuiFormat.duration(snapshot.totalPlaytime())));
        return info(Material.CLOCK, "players.gui.profile.info.playtime", lines);
    }

    private GuiItem condition(PlayerSnapshot snapshot, String ip) {
        List<Component> lines = new ArrayList<>();
        if (snapshot.gamemode() != null) {
            lines.add(line("players.gui.field.gamemode", GameNames.gameMode(snapshot.gamemode())));
        }
        if (snapshot.health() != null) {
            lines.add(line("players.gui.field.health", "%.1f / %.1f".formatted(snapshot.health(), snapshot.maxHealth())));
        }
        if (snapshot.food() != null) {
            String saturation = snapshot.saturation() == null ? "" : " (%.1f)".formatted(snapshot.saturation());
            lines.add(line("players.gui.field.food", snapshot.food() + saturation));
        }
        if (snapshot.level() != null) {
            String progress = snapshot.experienceProgress() == null ? "" : " (%.0f%%)".formatted(snapshot.experienceProgress() * 100);
            lines.add(line("players.gui.field.level", snapshot.level() + progress));
        }
        if (snapshot.ping() != null) {
            lines.add(line("players.gui.field.ping", snapshot.ping() + "ms"));
        }
        if (snapshot.locale() != null) {
            lines.add(line("players.gui.field.locale", snapshot.locale()));
        }
        lines.add(line("players.gui.field.effects", snapshot.activeEffects().isEmpty() ? Component.text("-")
                : Component.join(JoinConfiguration.commas(true), snapshot.activeEffects().stream().map(PlayerProfilePage::effect).toList())));
        if (ip != null) {
            lines.add(line("players.gui.field.ip", ip));
        }
        return info(Material.GOLDEN_APPLE, "players.gui.profile.info.condition", lines);
    }

    private GuiItem info(Material material, String titleKey, List<Component> lines) {
        return GuiItem.of(material, text(titleKey), lines.isEmpty() ? List.of(Component.text("-", NamedTextColor.DARK_GRAY)) : lines);
    }

    /** "Speed 2 (1:30)" with the effect's name in the reader's language. */
    private static Component effect(PotionEffect effect) {
        long seconds = effect.getDuration() / 20L;
        String rest = effect.isInfinite() ? " " + (effect.getAmplifier() + 1)
                : " %d (%d:%02d)".formatted(effect.getAmplifier() + 1, seconds / 60, seconds % 60);
        return Component.join(JoinConfiguration.noSeparators(), GameNames.effect(effect.getType()), Component.text(rest));
    }

    private Component line(String labelKey, String value) {
        return line(labelKey, Component.text(value));
    }

    /** {@code Label: value} - the label from the language file, the value as it is. */
    private Component line(String labelKey, Component value) {
        return Component.join(JoinConfiguration.noSeparators(), text(labelKey).colorIfAbsent(NamedTextColor.GRAY),
                Component.text(": ", NamedTextColor.DARK_GRAY), value.colorIfAbsent(NamedTextColor.WHITE));
    }

    private void renderPlaceholder(GuiView view, Material material, Component label) {
        view.clearContentArea();
        view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(material, label));
    }

    private boolean stillOpen(GuiRenderContext context) {
        Player viewer = context.viewer();
        return context.view().isOpenFor(viewer);
    }

    private record Result(Optional<PlayerSnapshot> snapshot, String ip) {
    }
}
