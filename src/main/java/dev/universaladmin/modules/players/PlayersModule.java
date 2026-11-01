package dev.universaladmin.modules.players;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.AuditDetails;
import dev.universaladmin.core.PlayerDirectory;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.GuiGlass;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.MenuGroup;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.modules.players.action.ClearPlayerInventoryAction;
import dev.universaladmin.modules.players.action.GetPlayerIpAddressAction;
import dev.universaladmin.modules.players.action.GetPlayerProfileAction;
import dev.universaladmin.modules.players.action.InventoryChangeSummary;
import dev.universaladmin.modules.players.action.InventoryEditRecord;
import dev.universaladmin.modules.players.action.PlayerActionIds;
import dev.universaladmin.modules.players.action.PlayerActionRegistrar;
import dev.universaladmin.modules.players.action.RecordInventoryEditAction;
import dev.universaladmin.modules.players.action.TeleportInput;
import dev.universaladmin.modules.players.action.TeleportPlayerAction;
import dev.universaladmin.modules.players.command.PlayerCommands;
import dev.universaladmin.modules.players.gui.OnlinePlayersListPage;
import dev.universaladmin.modules.players.gui.PlayerBrowserHomePage;
import dev.universaladmin.modules.players.gui.PlayersGui;
import dev.universaladmin.modules.players.inventory.LiveInventoryService;
import dev.universaladmin.modules.players.jdbc.JdbcPlayerProfileRepository;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Player Browser/Profile/Actions module. See docs/user/modules/players.md
 * for the full feature list; this class is only wiring - migrations,
 * repository/service construction, permission/action/settings registration,
 * the join/quit listener, and the {@code players.home} GUI page.
 */
public final class PlayersModule implements Module {

    public static final ModuleId ID = ModuleId.core("players");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Players")
            .description("Player browser (online/offline/recently seen/search), profile, and admin actions.")
            .icon(new GuiIcon("player_head", "Players"))
            .menu(OnlinePlayersListPage.ID, PlayerPermissions.VIEW)
            .menuOrder(10)
            .menuGroup(MenuGroup.PLAYERS)
            .color(GuiGlass.LIME)
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onLoad(ModuleContext context) {
        // Registered in onLoad, not onEnable: ModuleManager runs loadAll()
        // (every module's onLoad) to completion before enableAll() starts,
        // and UniversalAdminPlugin runs storage.migrations().runPending()
        // in between - so by the time onEnable below constructs the
        // repository, player_profiles is guaranteed to already exist. See
        // docs/architecture/threading.md.
        context.platform().storage().migrations().register(new PlayerProfileMigration());
        context.platform().storage().migrations().register(new PlayerProfileIndexMigration());
    }

    @Override
    public void onEnable(ModuleContext context) {
        TaskScheduler scheduler = context.platform().scheduler();
        PlayerProfileRepository repository =
                new JdbcPlayerProfileRepository(context.platform().storage().dataSource(), scheduler);
        PlayerSessionTracker sessionTracker = new PlayerSessionTracker();
        PlayerService playerService = new PlayerService(repository, scheduler, sessionTracker);
        context.platform().services().register(PlayerService.class, playerService);
        // Name lookups for commands across the whole database - in network
        // mode every player of every server (see PlayerDirectory).
        context.registerService(PlayerDirectory.class, new PlayerDirectory() {
            @Override
            public CompletableFuture<Optional<PlayerRef>> findByName(String name) {
                return playerService.search(new PlayerSearchQuery(name, PlayerSort.NAME_ASC, 25))
                        .thenApply(profiles -> profiles.stream()
                                .filter(profile -> profile.lastKnownName().equalsIgnoreCase(name))
                                .findFirst()
                                .map(profile -> new PlayerRef(profile.id(), profile.lastKnownName())));
            }

            @Override
            public CompletableFuture<Optional<PlayerRef>> findById(UUID playerId) {
                return playerService.findProfile(playerId)
                        .thenApply(profile -> profile.map(found -> new PlayerRef(found.id(), found.lastKnownName())));
            }

            @Override
            public CompletableFuture<Integer> addIfAbsent(Map<PlayerRef, Instant> lastSeen) {
                return playerService.addIfAbsent(lastSeen);
            }
        });

        context.resources().listener(new PlayerActivityListener(playerService, sessionTracker));

        PlayersSettings.registerAll(context.platform().settingRegistry());
        PlayerPermissions.registerAll(context.platform().permissions());

        registerActions(context, playerService, scheduler);
        LiveInventoryService liveInventory = startLiveInventory(context);
        PlayerBrowserHomePage homePage = registerGui(context, playerService, liveInventory);
        // Other modules (e.g. the Staff Mode inspectors) open the players'
        // inventory/ender chest editors through this.
        context.registerService(PlayersGui.class, homePage.gui());
        PlayerCommands.all(playerService, homePage::gui, context.platform().messages(), context.platform().network())
                .forEach(context::registerCommand);
    }

    /** The live inventory editors sync once per tick; every open editor is closed (and recorded) on disable. */
    private LiveInventoryService startLiveInventory(ModuleContext context) {
        LiveInventoryService liveInventory = new LiveInventoryService(
                context.platform().actionExecutor(), context.platform().scheduler(), context.platform().messages());
        Plugin plugin = context.platform().plugin();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, liveInventory::tick, 1L, 1L);
        context.resources().task(task);
        context.resources().closeable(liveInventory::closeAll);
        return liveInventory;
    }

    private void registerActions(ModuleContext context, PlayerService playerService, TaskScheduler scheduler) {
        ActionRegistry actions = context.platform().actions();
        String module = ID.key().name();

        actions.register(ActionDefinition.builder(new GetPlayerProfileAction(playerService))
                .permission(PlayerPermissions.VIEW)
                .module(module)
                .notAudited()
                .build());
        actions.register(ActionDefinition.builder(new GetPlayerIpAddressAction(scheduler))
                .permission(PlayerPermissions.IP)
                .module(module)
                .target(input -> Optional.of(ActionTarget.player(input.targetId(), PlayerNames.of(input.targetId()))))
                // Audited (unlike GetPlayerProfileAction) - who looked up
                // whose IP address is exactly the kind of sensitive read an
                // audit trail exists for. The IP itself never goes into the
                // summary/metadata: an audit-log viewer without
                // PlayerPermissions.IP must not learn it that way.
                .auditArgs(input -> List.of(PlayerNames.of(input.targetId())))
                .auditSummary(input -> "Viewed IP address of " + PlayerNames.of(input.targetId()))
                .build());
        actions.register(ActionDefinition.builder(new TeleportPlayerAction(scheduler))
                .permission(PlayerPermissions.TELEPORT)
                .module(module)
                .target(PlayersModule::teleportTarget)
                .auditArgs(input -> List.of("text:players.audit.teleport." + input.kind().name().toLowerCase(Locale.ROOT).replace('_', '-'),
                        PlayerNames.of(input.targetId())))
                .auditSummary(input -> "Teleport (" + input.kind() + ") " + PlayerNames.of(input.targetId()))
                .build());
        actions.register(ActionDefinition.builder(new RecordInventoryEditAction(
                        PlayerActionIds.INVENTORY_EDIT, MessageKey.of("players.inventory.edited")))
                .permission(PlayerPermissions.INVENTORY_EDIT)
                .module(module)
                .target(PlayersModule::editTarget)
                .auditArgs(input -> List.of(input.targetName(), input.summary().changedSlots()))
                .auditSummary(input -> "Edited inventory of " + input.targetName() + " live ("
                        + input.summary().changedSlots() + " slot(s) changed)")
                .auditDetails((input, result) -> inventoryDetails(result))
                .build());
        actions.register(ActionDefinition.builder(new RecordInventoryEditAction(
                        PlayerActionIds.ENDERCHEST_EDIT, MessageKey.of("players.enderchest.edited")))
                .permission(PlayerPermissions.ENDERCHEST_EDIT)
                .module(module)
                .target(PlayersModule::editTarget)
                .auditArgs(input -> List.of(input.targetName(), input.summary().changedSlots()))
                .auditSummary(input -> "Edited ender chest of " + input.targetName() + " live ("
                        + input.summary().changedSlots() + " slot(s) changed)")
                .auditDetails((input, result) -> inventoryDetails(result))
                .build());
        actions.register(ActionDefinition.builder(new ClearPlayerInventoryAction(scheduler))
                .permission(PlayerPermissions.INVENTORY_EDIT)
                .module(module)
                .target(input -> Optional.of(ActionTarget.player(input.targetId(), PlayerNames.of(input.targetId()))))
                .auditArgs(input -> List.of(PlayerNames.of(input.targetId())))
                .auditSummary(input -> "Cleared inventory of " + PlayerNames.of(input.targetId()))
                .auditDetails((input, result) -> inventoryDetails(result))
                .build());

        PlayerActionRegistrar.registerAll(actions, scheduler);
    }

    private static Optional<ActionTarget> teleportTarget(TeleportInput input) {
        return Optional.of(ActionTarget.player(input.targetId(), PlayerNames.of(input.targetId())));
    }

    private static Optional<ActionTarget> editTarget(InventoryEditRecord input) {
        return Optional.of(ActionTarget.player(input.targetId(), input.targetName()));
    }

    /** Coarse before/after slot-count summary, never the actual items - see {@link InventoryChangeSummary}. */
    private static <R> AuditDetails inventoryDetails(ActionResult<R> result) {
        if (!(result instanceof ActionResult.Success<R> success) || !(success.value() instanceof InventoryChangeSummary summary)) {
            return AuditDetails.EMPTY;
        }
        return AuditDetails.builder().oldValue(summary.describeBefore()).newValue(summary.describeAfter())
                .metadata(Map.of("changedSlots", summary.changedSlots())).build();
    }

    private PlayerBrowserHomePage registerGui(ModuleContext context, PlayerService playerService, LiveInventoryService liveInventory) {
        PlayerBrowserHomePage homePage = new PlayerBrowserHomePage(
                context.platform().guiFramework(), context.platform().messages(), context.platform().scheduler(),
                playerService, context.platform().actionExecutor(), context.platform().settings(), context.platform().playerMenu(),
                liveInventory, context.platform().network());
        context.registerPage(homePage);
        context.registerPage(homePage.onlineList());
        return homePage;
    }
}
