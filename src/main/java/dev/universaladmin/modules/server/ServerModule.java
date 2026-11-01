package dev.universaladmin.modules.server;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionValidator;
import dev.universaladmin.action.AuditDetails;
import dev.universaladmin.action.ValidationError;
import dev.universaladmin.gui.GuiGlass;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.MenuGroup;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.modules.server.action.BroadcastActionbarAction;
import dev.universaladmin.modules.server.action.BroadcastMessageAction;
import dev.universaladmin.modules.server.action.BroadcastTitleAction;
import dev.universaladmin.modules.server.action.BroadcastTitleInput;
import dev.universaladmin.modules.server.action.CancelRestartAction;
import dev.universaladmin.modules.server.action.CancelShutdownAction;
import dev.universaladmin.modules.server.action.DisableMaintenanceAction;
import dev.universaladmin.modules.server.action.EnableMaintenanceAction;
import dev.universaladmin.modules.server.action.RestartAction;
import dev.universaladmin.modules.server.action.SetMaintenanceAllowedPlayersAction;
import dev.universaladmin.modules.server.action.ShutdownAction;
import dev.universaladmin.modules.server.command.ServerCommands;
import dev.universaladmin.modules.server.gui.ServerGuiContext;
import dev.universaladmin.modules.server.gui.ServerHomePage;
import dev.universaladmin.modules.server.gui.ServerMaintenancePage;
import dev.universaladmin.modules.server.jdbc.JdbcMaintenanceStateRepository;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.status.MaintenanceStatus;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;

/**
 * Server control: a live dashboard, broadcast/title/actionbar, maintenance
 * mode (its own persisted system - see {@link MaintenanceService}'s javadoc
 * for why it isn't a {@link dev.universaladmin.settings.SettingDefinition}),
 * and shutdown/restart with a dangerous confirmation and an optional,
 * configurable countdown ({@link ServerLifecycleService}). See
 * docs/user/modules/server.md, in particular its "Restart limitations"
 * section - Paper/Spigot's built-in restart mechanism performs a clean
 * shutdown, but whether the OS process actually relaunches depends on how
 * the server was started; this module can't guarantee that universally, so
 * it uses the platform's own mechanism and documents the limitation rather
 * than hardcoding a shell command.
 */
public final class ServerModule implements Module {

    public static final ModuleId ID = ModuleId.core("server");
    /** Network mode: how often the network's maintenance is re-read, in case a change message was missed. */
    private static final long MAINTENANCE_REFRESH_TICKS = 20L * 10;

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Server")
            .description("Server dashboard, broadcasts, maintenance mode, and confirmed shutdown/restart.")
            .icon(new GuiIcon("command_block", "Server"))
            .menu(ServerHomePage.ID, ServerPermissions.VIEW)
            .menuOrder(30)
            .menuGroup(MenuGroup.SERVER)
            .color(GuiGlass.CYAN)
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onLoad(ModuleContext context) {
        // See PlayersModule#onLoad's javadoc for why registration happens
        // here rather than in onEnable - DefaultMaintenanceService below
        // eagerly (async) reads server_maintenance_state the moment it's
        // constructed in onEnable, so that table must already exist by then.
        context.platform().storage().migrations().register(new ServerMaintenanceMigration());
        context.platform().storage().migrations().register(new MaintenanceScopeMigration());
    }

    @Override
    public void onEnable(ModuleContext context) {
        TaskScheduler scheduler = context.platform().scheduler();
        SettingsService settings = context.platform().settings();
        NotificationService notifications = context.platform().notifications();
        Plugin plugin = context.platform().plugin();

        ServerSettings.registerAll(context.platform().settingRegistry());
        ServerPermissions.registerAll(context.platform().permissions());

        NetworkService network = context.platform().network();
        MaintenanceStateRepository maintenanceRepository =
                new JdbcMaintenanceStateRepository(context.platform().storage().dataSource(), scheduler);
        DefaultMaintenanceService maintenanceService =
                new DefaultMaintenanceService(maintenanceRepository, settings, scheduler, context.logger(), network);
        context.platform().services().register(MaintenanceService.class, maintenanceService);
        context.registerService(MaintenanceStatus.class, maintenanceService::active);
        context.resources().listener(new MaintenanceJoinListener(maintenanceService));
        if (network.enabled()) {
            // The network's maintenance, switched on another server: at once by
            // message, and re-read every few seconds in case one was missed.
            network.subscribe(DefaultMaintenanceService.NETWORK_TOPIC, maintenanceService);
            context.resources().closeable(() -> network.unsubscribe(DefaultMaintenanceService.NETWORK_TOPIC, maintenanceService));
            context.resources().task(plugin.getServer().getScheduler().runTaskTimer(plugin,
                    () -> maintenanceService.refresh().exceptionally(error -> {
                        context.logger().log(Level.FINE, "Could not re-read the maintenance state", error);
                        return null;
                    }), MAINTENANCE_REFRESH_TICKS, MAINTENANCE_REFRESH_TICKS));
        }

        ServerLifecycleService lifecycleService =
                new ServerLifecycleService(plugin, context.resources(), notifications, context.platform().messages(), settings);
        ServerDashboardService dashboardService = new ServerDashboardService(context.platform()::status);

        registerActions(context.platform().actions(), scheduler, notifications, maintenanceService, lifecycleService, network);

        ServerGuiContext guiContext = new ServerGuiContext(context.platform().guiFramework(), context.platform().messages(),
                scheduler, context.platform().actionExecutor(), settings, dashboardService, maintenanceService, lifecycleService);
        ServerHomePage homePage = new ServerHomePage(guiContext);
        context.platform().guiPages().register(homePage);
        context.resources().closeable(() -> context.platform().guiPages().unregister(ServerHomePage.ID));
        context.platform().guiPages().register(new ServerMaintenancePage(guiContext));
        context.resources().closeable(() -> context.platform().guiPages().unregister(ServerMaintenancePage.ID));

        ServerCommands.all(dashboardService, maintenanceService).forEach(context::registerCommand);
    }

    private static void registerActions(
            ActionRegistry actions, TaskScheduler scheduler, NotificationService notifications,
            MaintenanceService maintenanceService, ServerLifecycleService lifecycleService, NetworkService network) {
        String module = ID.key().name();

        actions.register(ActionDefinition.builder(new BroadcastMessageAction(scheduler, notifications))
                .permission(ServerPermissions.BROADCAST)
                .module(module)
                .validator(nonBlank("server.action.message-required"))
                .auditArgs(in -> List.of(in))
                .auditSummary(in -> "Broadcast message: " + in)
                .build());
        actions.register(ActionDefinition.builder(new BroadcastTitleAction(scheduler, notifications))
                .permission(ServerPermissions.BROADCAST)
                .module(module)
                .validator(nonBlank(BroadcastTitleInput::title, "server.action.title-required"))
                .auditArgs(in -> List.of(in.title()))
                .auditSummary(in -> "Broadcast title: " + in.title())
                .build());
        actions.register(ActionDefinition.builder(new BroadcastActionbarAction(scheduler, notifications))
                .permission(ServerPermissions.BROADCAST)
                .module(module)
                .validator(nonBlank("server.action.message-required"))
                .auditArgs(in -> List.of(in))
                .auditSummary(in -> "Broadcast actionbar: " + in)
                .build());

        actions.register(ActionDefinition.builder(new EnableMaintenanceAction(maintenanceService, MaintenanceScope.SERVER))
                .permission(ServerPermissions.MAINTENANCE)
                .module(module)
                .auditArgs(in -> List.of(orDash(in.reason())))
                .auditSummary(in -> "Enabled maintenance mode" + reasonSuffix(in.reason()))
                .auditDetails((in, result) -> AuditDetails.builder().reason(in.reason()).newValue("enabled").build())
                .build());
        actions.register(ActionDefinition.builder(new DisableMaintenanceAction(maintenanceService, MaintenanceScope.SERVER))
                .permission(ServerPermissions.MAINTENANCE)
                .module(module)
                .auditSummary(in -> "Disabled maintenance mode")
                .auditDetails((in, result) -> AuditDetails.builder().newValue("disabled").build())
                .build());
        actions.register(ActionDefinition.builder(new SetMaintenanceAllowedPlayersAction(maintenanceService, MaintenanceScope.SERVER))
                .permission(ServerPermissions.MAINTENANCE)
                .module(module)
                .auditArgs(in -> List.of(in.size()))
                .auditSummary(in -> "Updated maintenance allow-list (" + players(in.size()) + ")")
                .build());

        actions.register(ActionDefinition.builder(new EnableMaintenanceAction(maintenanceService, MaintenanceScope.NETWORK))
                .permission(ServerPermissions.MAINTENANCE_NETWORK)
                .module(module)
                .validator(requireNetwork(network))
                .auditArgs(in -> List.of(orDash(in.reason())))
                .auditSummary(in -> "Enabled network maintenance mode" + reasonSuffix(in.reason()))
                .auditDetails((in, result) -> AuditDetails.builder().reason(in.reason()).newValue("enabled").build())
                .build());
        actions.register(ActionDefinition.builder(new DisableMaintenanceAction(maintenanceService, MaintenanceScope.NETWORK))
                .permission(ServerPermissions.MAINTENANCE_NETWORK)
                .module(module)
                .validator(requireNetwork(network))
                .auditSummary(in -> "Disabled network maintenance mode")
                .auditDetails((in, result) -> AuditDetails.builder().newValue("disabled").build())
                .build());
        actions.register(ActionDefinition.builder(new SetMaintenanceAllowedPlayersAction(maintenanceService, MaintenanceScope.NETWORK))
                .permission(ServerPermissions.MAINTENANCE_NETWORK)
                .module(module)
                .validator(requireNetwork(network))
                .auditArgs(in -> List.of(in.size()))
                .auditSummary(in -> "Updated network maintenance allow-list (" + players(in.size()) + ")")
                .build());

        actions.register(ActionDefinition.builder(new ShutdownAction(scheduler, lifecycleService))
                .permission(ServerPermissions.SHUTDOWN)
                .module(module)
                .auditArgs(in -> List.of(orDash(in)))
                .auditSummary(in -> "Shut down the server" + reasonSuffix(in))
                .build());
        actions.register(ActionDefinition.builder(new CancelShutdownAction(scheduler, lifecycleService))
                .permission(ServerPermissions.SHUTDOWN)
                .module(module)
                .auditSummary(in -> "Cancelled pending shutdown")
                .build());
        actions.register(ActionDefinition.builder(new RestartAction(scheduler, lifecycleService))
                .permission(ServerPermissions.RESTART)
                .module(module)
                .auditArgs(in -> List.of(orDash(in)))
                .auditSummary(in -> "Restarted the server" + reasonSuffix(in))
                .build());
        actions.register(ActionDefinition.builder(new CancelRestartAction(scheduler, lifecycleService))
                .permission(ServerPermissions.RESTART)
                .module(module)
                .auditSummary(in -> "Cancelled pending restart")
                .build());
    }

    /** A reason for the audit sentence - "-" when none was given. */
    private static String orDash(String reason) {
        return reason == null || reason.isBlank() ? "-" : reason;
    }

    private static String players(int count) {
        return count + (count == 1 ? " player" : " players");
    }

    private static String reasonSuffix(String reason) {
        return blank(reason) ? "" : ": " + reason;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <I> ActionValidator<I> requireNetwork(NetworkService network) {
        return (context, input) -> network.enabled()
                ? Optional.empty()
                : Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION, MessageKey.of("server.action.network-required")));
    }

    private static ActionValidator<String> nonBlank(String messageKey) {
        return nonBlank(Function.identity(), messageKey);
    }

    private static <I> ActionValidator<I> nonBlank(Function<I, String> extractor, String messageKey) {
        return (context, input) -> blank(extractor.apply(input))
                ? Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION, MessageKey.of(messageKey)))
                : Optional.empty();
    }
}
