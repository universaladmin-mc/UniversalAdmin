package dev.universaladmin.modules.whitelist;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.ActionValidator;
import dev.universaladmin.action.AuditDetails;
import dev.universaladmin.action.ValidationError;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.gui.GuiGlass;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.MenuGroup;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.modules.whitelist.action.AddWhitelistEntryAction;
import dev.universaladmin.modules.whitelist.action.AddWhitelistEntryInput;
import dev.universaladmin.modules.whitelist.action.DisableWhitelistAction;
import dev.universaladmin.modules.whitelist.action.EnableWhitelistAction;
import dev.universaladmin.modules.whitelist.action.RemoveWhitelistEntryAction;
import dev.universaladmin.modules.whitelist.action.SetNetworkWhitelistAction;
import dev.universaladmin.modules.whitelist.command.WhitelistCommands;
import dev.universaladmin.modules.whitelist.gui.WhitelistGuiContext;
import dev.universaladmin.modules.whitelist.gui.WhitelistHomePage;
import dev.universaladmin.modules.whitelist.jdbc.JdbcWhitelistEntryRepository;
import dev.universaladmin.modules.whitelist.jdbc.JdbcWhitelistStateRepository;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.network.ScopeKeys;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Wraps Paper's native whitelist (enable/disable/list/add/remove) with
 * UniversalAdmin's own metadata (added-by, added-at, reason, notes,
 * expiration) and optional temporary access - see {@link WhitelistService}'s
 * javadoc for how the native list and this module's own table stay in sync,
 * and {@link WhitelistSource}'s javadoc for the ownership rule that keeps
 * automatic expiry from ever touching an entry this module didn't create.
 */
public final class WhitelistModule implements Module {

    public static final ModuleId ID = ModuleId.core("whitelist");

    /** Mirrors {@code ModerationModule}'s hourly expiry sweep - catches entries whose owner never logs back in. */
    private static final long SWEEP_PERIOD_TICKS = 20L * 60 * 60;
    /** Network mode: how often the network whitelist is re-read, in case a change message was missed. */
    private static final long NETWORK_REFRESH_TICKS = 20L * 10;

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Whitelist")
            .description("Native whitelist plus UniversalAdmin metadata: added-by/at, reason, notes, and optional expiry.")
            .icon(new GuiIcon("name_tag", "Whitelist"))
            .menu(WhitelistHomePage.ID, WhitelistPermissions.VIEW)
            .menuOrder(50)
            .menuGroup(MenuGroup.SERVER)
            .color(GuiGlass.WHITE)
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onLoad(ModuleContext context) {
        // See PlayersModule#onLoad's javadoc for why registration happens
        // here rather than in onEnable.
        context.platform().storage().migrations().register(new WhitelistMigration());
        context.platform().storage().migrations().register(new WhitelistScopeMigration());
    }

    @Override
    public void onEnable(ModuleContext context) {
        TaskScheduler scheduler = context.platform().scheduler();

        WhitelistSettings.registerAll(context.platform().settingRegistry());
        WhitelistPermissions.registerAll(context.platform().permissions());

        NetworkService network = context.platform().network();
        DataSource dataSource = context.platform().storage().dataSource();
        // This server's metadata lives under its own scope key: shared tables
        // must not let one server's entries (and expiry) act on another.
        WhitelistEntryRepository repository = new JdbcWhitelistEntryRepository(dataSource, scheduler, () -> ScopeKeys.server(network));
        WhitelistService whitelistService = new DefaultWhitelistService(repository, scheduler);
        context.platform().services().register(WhitelistService.class, whitelistService);
        NetworkWhitelistService networkWhitelist = new NetworkWhitelistService(
                new JdbcWhitelistEntryRepository(dataSource, scheduler, () -> ScopeKeys.NETWORK),
                new JdbcWhitelistStateRepository(dataSource, scheduler), network, context.logger());
        registerActions(context.platform().actions(), scheduler, whitelistService, networkWhitelist, network);
        context.resources().listener(new WhitelistJoinListener(repository, context.platform().actionExecutor(), context.platform().messages()));
        if (network.enabled()) {
            adoptSharedEntries(context, repository);
            context.resources().listener(new NetworkWhitelistJoinListener(networkWhitelist, context.platform().messages()));
            Runnable refresh = () -> networkWhitelist.refresh().exceptionally(error -> {
                context.logger().log(Level.WARNING, "Could not read the network whitelist", error);
                return null;
            });
            refresh.run();
            network.subscribe(NetworkWhitelistService.TOPIC, networkWhitelist);
            context.resources().closeable(() -> network.unsubscribe(NetworkWhitelistService.TOPIC, networkWhitelist));
            context.resources().task(context.platform().plugin().getServer().getScheduler().runTaskTimer(
                    context.platform().plugin(), refresh, NETWORK_REFRESH_TICKS, NETWORK_REFRESH_TICKS));
        }
        scheduleExpirySweep(context, repository, networkWhitelist);

        WhitelistGuiContext guiContext = new WhitelistGuiContext(context.platform().guiFramework(), context.platform().messages(),
                scheduler, context.platform().actionExecutor(), context.platform().settings(), whitelistService, networkWhitelist);
        WhitelistHomePage homePage = new WhitelistHomePage(guiContext);
        context.platform().guiPages().register(homePage);
        context.resources().closeable(() -> context.platform().guiPages().unregister(WhitelistHomePage.ID));

        WhitelistCommands.all(whitelistService, networkWhitelist).forEach(context::registerCommand);
    }

    /**
     * Network mode: takes over this server's entries from the single table
     * every server shared before 1.3.0 - the ones for players on this
     * server's native whitelist (see {@link WhitelistEntryRepository#adopt}).
     */
    private static void adoptSharedEntries(ModuleContext context, WhitelistEntryRepository repository) {
        Set<UUID> natives = Bukkit.getWhitelistedPlayers().stream().map(OfflinePlayer::getUniqueId).collect(Collectors.toSet());
        repository.adopt(ScopeKeys.LOCAL, natives).whenComplete((copied, error) -> {
            if (error != null) {
                context.logger().log(Level.WARNING, "Could not take over the whitelist entries stored before 1.3.0", error);
            } else if (copied > 0) {
                context.logger().info("Whitelist: took over " + copied + " entries stored before 1.3.0 for this server.");
            }
        });
    }

    private static void registerActions(
            ActionRegistry actions, TaskScheduler scheduler, WhitelistService whitelistService,
            NetworkWhitelistService networkWhitelist, NetworkService network) {
        String module = ID.key().name();

        actions.register(ActionDefinition.builder(new EnableWhitelistAction(scheduler))
                .permission(WhitelistPermissions.TOGGLE)
                .module(module)
                .auditSummary(in -> "Enabled the whitelist")
                .build());
        actions.register(ActionDefinition.builder(new DisableWhitelistAction(scheduler))
                .permission(WhitelistPermissions.TOGGLE)
                .module(module)
                .auditSummary(in -> "Disabled the whitelist")
                .build());

        actions.register(ActionDefinition.builder(new AddWhitelistEntryAction(whitelistService, WhitelistScope.SERVER))
                .permission(WhitelistPermissions.ADD)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.playerId(), in.playerName())))
                .validator(temporaryRequiresPermission())
                .auditArgs(in -> List.of(in.playerName()))
                .auditSummary(in -> "Whitelisted " + in.playerName() + (in.expiresAt() != null ? " until " + in.expiresAt() : ""))
                .auditDetails((in, result) -> AuditDetails.builder()
                        .reason(in.reason())
                        .newValue(in.expiresAt() != null ? "expires " + in.expiresAt() : "permanent")
                        .build())
                .build());

        actions.register(ActionDefinition.builder(new RemoveWhitelistEntryAction(whitelistService, WhitelistScope.SERVER))
                .permission(WhitelistPermissions.REMOVE)
                .module(module)
                .target(playerId -> Optional.of(ActionTarget.player(playerId, PlayerNames.of(playerId))))
                .auditArgs(playerId -> List.of(PlayerNames.of(playerId)))
                .auditSummary(playerId -> "Removed " + PlayerNames.of(playerId) + " from the whitelist")
                .build());

        actions.register(ActionDefinition.builder(new SetNetworkWhitelistAction(networkWhitelist, true))
                .permission(WhitelistPermissions.TOGGLE)
                .module(module)
                .validator(networkAllowed(network))
                .auditSummary(in -> "Enabled the network whitelist")
                .build());
        actions.register(ActionDefinition.builder(new SetNetworkWhitelistAction(networkWhitelist, false))
                .permission(WhitelistPermissions.TOGGLE)
                .module(module)
                .validator(networkAllowed(network))
                .auditSummary(in -> "Disabled the network whitelist")
                .build());
        actions.register(ActionDefinition.builder(new AddWhitelistEntryAction(networkWhitelist, WhitelistScope.NETWORK))
                .permission(WhitelistPermissions.ADD)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.playerId(), in.playerName())))
                .validator(both(networkAllowed(network), temporaryRequiresPermission()))
                .auditArgs(in -> List.of(in.playerName()))
                .auditSummary(in -> "Added " + in.playerName() + " to the network whitelist"
                        + (in.expiresAt() != null ? " until " + in.expiresAt() : ""))
                .auditDetails((in, result) -> AuditDetails.builder()
                        .reason(in.reason())
                        .newValue(in.expiresAt() != null ? "expires " + in.expiresAt() : "permanent")
                        .build())
                .build());
        actions.register(ActionDefinition.builder(new RemoveWhitelistEntryAction(networkWhitelist, WhitelistScope.NETWORK))
                .permission(WhitelistPermissions.REMOVE)
                .module(module)
                .validator(networkAllowed(network))
                .target(playerId -> Optional.of(ActionTarget.player(playerId, PlayerNames.of(playerId))))
                .auditArgs(playerId -> List.of(PlayerNames.of(playerId)))
                .auditSummary(playerId -> "Removed " + PlayerNames.of(playerId) + " from the network whitelist")
                .build());
    }

    /** Network actions: network mode on, and {@link WhitelistPermissions#NETWORK} on top of the action's own node. */
    private static <I> ActionValidator<I> networkAllowed(NetworkService network) {
        return (context, input) -> {
            if (!network.enabled()) {
                return Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION, MessageKey.of("whitelist.action.network-required")));
            }
            return context.actor().hasPermission(WhitelistPermissions.NETWORK)
                    ? Optional.empty()
                    : Optional.of(ValidationError.of(ActionResult.FailureReason.NOT_PERMITTED, MessageKey.of("error.no-permission")));
        };
    }

    private static <I> ActionValidator<I> both(ActionValidator<I> first, ActionValidator<I> second) {
        return (context, input) -> {
            Optional<ValidationError> error = first.validate(context, input);
            return error.isPresent() ? error : second.validate(context, input);
        };
    }

    private static ActionValidator<AddWhitelistEntryInput> temporaryRequiresPermission() {
        return (context, input) -> input.expiresAt() != null && !context.actor().hasPermission(WhitelistPermissions.TEMPORARY)
                ? Optional.of(ValidationError.of(ActionResult.FailureReason.NOT_PERMITTED, MessageKey.of("whitelist.action.temporary-not-permitted")))
                : Optional.empty();
    }

    private void scheduleExpirySweep(ModuleContext context, WhitelistEntryRepository repository, NetworkWhitelistService networkWhitelist) {
        WhitelistExpirySweeper sweeper = new WhitelistExpirySweeper(repository, context.platform().actionExecutor());
        Plugin plugin = context.platform().plugin();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> {
                    sweeper.sweep().exceptionally(error -> {
                        context.logger().warning("Whitelist expiry sweep failed: " + error.getMessage());
                        return 0;
                    });
                    networkWhitelist.removeExpired().exceptionally(error -> 0);
                },
                SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
        context.resources().task(task);
    }
}
