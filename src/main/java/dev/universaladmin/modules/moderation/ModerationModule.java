package dev.universaladmin.modules.moderation;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionEvent;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.ActionValidator;
import dev.universaladmin.action.AuditDetails;
import dev.universaladmin.action.ValidationError;
import dev.universaladmin.core.PlayerDirectory;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.core.ServiceRegistry;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.gui.ActionStatusToggle;
import dev.universaladmin.gui.GuiGlass;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.MenuGroup;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.modules.moderation.action.ApplyTemplateAction;
import dev.universaladmin.modules.moderation.action.BanAction;
import dev.universaladmin.modules.moderation.action.BanInput;
import dev.universaladmin.modules.moderation.action.EnterStaffModeAction;
import dev.universaladmin.modules.moderation.action.ExitStaffModeAction;
import dev.universaladmin.modules.moderation.action.FreezeAction;
import dev.universaladmin.modules.moderation.action.FreezeDisconnectNoticeAction;
import dev.universaladmin.modules.moderation.action.FreezeInput;
import dev.universaladmin.modules.moderation.action.GodmodeAction;
import dev.universaladmin.modules.moderation.action.IpBanAction;
import dev.universaladmin.modules.moderation.action.IpBanInput;
import dev.universaladmin.modules.moderation.action.KickAction;
import dev.universaladmin.modules.moderation.action.KickInput;
import dev.universaladmin.modules.moderation.action.ModerationActionIds;
import dev.universaladmin.modules.moderation.action.MuteAction;
import dev.universaladmin.modules.moderation.action.MuteInput;
import dev.universaladmin.modules.moderation.action.NoCollisionAction;
import dev.universaladmin.modules.moderation.action.RecoverStaffSnapshotAction;
import dev.universaladmin.modules.moderation.action.RemoveWarnAction;
import dev.universaladmin.modules.moderation.action.UnbanAction;
import dev.universaladmin.modules.moderation.action.UnfreezeAction;
import dev.universaladmin.modules.moderation.action.UnmuteAction;
import dev.universaladmin.modules.moderation.action.VanishAction;
import dev.universaladmin.modules.moderation.action.WarnAction;
import dev.universaladmin.modules.moderation.action.WarnInput;
import dev.universaladmin.modules.moderation.alts.AltActions;
import dev.universaladmin.modules.moderation.alts.AltService;
import dev.universaladmin.modules.moderation.alts.AltSupport;
import dev.universaladmin.modules.moderation.alts.AltsMigration;
import dev.universaladmin.modules.moderation.command.ModerationCommands;
import dev.universaladmin.modules.moderation.gui.ModeratePlayerMenuEntry;
import dev.universaladmin.modules.moderation.gui.ModerationGuiContext;
import dev.universaladmin.modules.moderation.gui.ModerationHomePage;
import dev.universaladmin.modules.moderation.gui.ModerationQuickEntry;
import dev.universaladmin.modules.moderation.importing.AdvancedBanImporter;
import dev.universaladmin.modules.moderation.importing.EssentialsImporter;
import dev.universaladmin.modules.moderation.importing.ImportAction;
import dev.universaladmin.modules.moderation.importing.ImportCommand;
import dev.universaladmin.modules.moderation.importing.ImportException;
import dev.universaladmin.modules.moderation.importing.ImportRecordsMigration;
import dev.universaladmin.modules.moderation.importing.ImportService;
import dev.universaladmin.modules.moderation.importing.ImportSummary;
import dev.universaladmin.modules.moderation.importing.LiteBansImporter;
import dev.universaladmin.modules.moderation.importing.NoteText;
import dev.universaladmin.modules.moderation.importing.PunishmentImporter;
import dev.universaladmin.modules.moderation.importing.VanillaImporter;
import dev.universaladmin.modules.moderation.jdbc.JdbcAltRepository;
import dev.universaladmin.modules.moderation.jdbc.JdbcImportRepository;
import dev.universaladmin.modules.moderation.jdbc.JdbcPlayerNoteRepository;
import dev.universaladmin.modules.moderation.jdbc.JdbcPunishmentRepository;
import dev.universaladmin.modules.moderation.jdbc.JdbcStaffModeSnapshotRepository;
import dev.universaladmin.modules.moderation.jdbc.JdbcVanishRepository;
import dev.universaladmin.modules.moderation.notes.NoteActions;
import dev.universaladmin.modules.moderation.notes.NotesSupport;
import dev.universaladmin.modules.moderation.notes.PlayerNoteRepository;
import dev.universaladmin.modules.moderation.notes.PlayerNotesMigration;
import dev.universaladmin.modules.moderation.templates.PunishmentTemplateService;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.ReloadConfigAction;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.status.MuteKind;
import dev.universaladmin.status.PlayerStatus;
import io.papermc.paper.ban.BanListType;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.ban.IpBanList;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Standalone punishment and staff-tools system - kick/ban/tempban/ipban/
 * mute/tempmute/warn/freeze plus unban/unmute/removewarn/unfreeze, Vanish,
 * Godmode, No-Collision, and Staff Mode (crash-safe snapshot/recovery,
 * held tool items), with join/chat enforcement, a GUI wizard, and full
 * audit coverage via {@link dev.universaladmin.action.ActionExecutor}. See
 * docs/user/modules/moderation.md and docs/user/modules/staff-tools.md. No
 * dependency on any external moderation/vanish/essentials plugin - this is
 * wiring only, same shape as {@code PlayersModule}.
 */
public final class ModerationModule implements Module {

    /** Alt detection forgets old addresses once an hour. */
    private static final long ALT_SWEEP_TICKS = 20L * 60 * 60;

    /** The punishment templates file in the plugin folder. */
    static final String TEMPLATES_FILE = "punishment-templates.yml";

    public static final ModuleId ID = ModuleId.core("moderation");

    /** Runs {@link PunishmentService#expireOverdue()} once an hour - pure housekeeping, see {@link PunishmentRepository}. */
    private static final long CLEANUP_PERIOD_TICKS = 20L * 60 * 60;

    /** How often {@link StaffModeTargetTracker#tick()} refreshes the Player Inspector tool and checks for tool-kit tampering - twice a second, cheap enough given how few players are ever in Staff Mode at once. */
    private static final long STAFF_MODE_TRACKER_PERIOD_TICKS = 10L;

    /** How often {@link ModerationPlayerStatus} reloads online players' mutes and warnings - catches changes made on other servers. */
    private static final long STATUS_REFRESH_TICKS = 20L * 60;

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Moderation")
            .description("Kick/ban/tempban/ipban/mute/tempmute/warn/freeze tooling plus Vanish/Godmode/Staff Mode, with join/chat enforcement.")
            .icon(new GuiIcon("iron_sword", "Moderation"))
            .menu(ModerationHomePage.ID, ModerationPermissions.USE)
            .menuOrder(20)
            .menuGroup(MenuGroup.PLAYERS)
            .color(GuiGlass.RED)
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onLoad(ModuleContext context) {
        // See PlayersModule#onLoad's javadoc for why registration happens
        // here rather than in onEnable.
        context.platform().storage().migrations().register(new ModerationPunishmentMigration());
        context.platform().storage().migrations().register(new ModerationPunishmentIndexMigration());
        context.platform().storage().migrations().register(new PunishmentTemplateMigration());
        context.platform().storage().migrations().register(new MuteKindMigration());
        context.platform().storage().migrations().register(new AltsMigration());
        context.platform().storage().migrations().register(new VanishStateMigration());
        context.platform().storage().migrations().register(new StaffModeSnapshotMigration());
        context.registerMigration(new PlayerNotesMigration());
        context.registerMigration(new ImportRecordsMigration());
    }

    @Override
    public void onEnable(ModuleContext context) {
        TaskScheduler scheduler = context.platform().scheduler();
        MessageService messages = context.platform().messages();
        Plugin plugin = context.platform().plugin();
        ServiceRegistry services = context.platform().services();

        PunishmentRepository repository = new JdbcPunishmentRepository(context.platform().storage().dataSource(), scheduler);
        PunishmentService punishmentService = new PunishmentService(repository);
        services.register(PunishmentService.class, punishmentService);

        // See ModerationPolicy's javadoc: pick up a policy a dependency module
        // already registered (a future rank/hierarchy extension), or publish
        // the no-op default - either way, every punishing action below reads
        // whatever ends up registered here.
        Optional<ModerationPolicy> existingPolicy = services.get(ModerationPolicy.class);
        ModerationPolicy policy = existingPolicy.orElseGet(ModerationPolicy::allowAll);
        if (existingPolicy.isEmpty()) {
            services.register(ModerationPolicy.class, policy);
        }
        Optional<VanishVisibilityPolicy> existingVisibilityPolicy = services.get(VanishVisibilityPolicy.class);
        VanishVisibilityPolicy visibilityPolicy = existingVisibilityPolicy.orElseGet(VanishVisibilityPolicy::bypassPermissionOnly);
        if (existingVisibilityPolicy.isEmpty()) {
            services.register(VanishVisibilityPolicy.class, visibilityPolicy);
        }

        // In-memory runtime state every hot-path listener reads instead of
        // the database - see VanishRuntimeState/FreezeRuntimeState/
        // GodmodeState/CollisionState/StaffModeState javadocs.
        VanishRuntimeState vanishRuntimeState = new VanishRuntimeState();
        FreezeRuntimeState freezeRuntimeState = new FreezeRuntimeState();
        GodmodeState godmodeState = new GodmodeState();
        CollisionState collisionState = new CollisionState();
        StaffModeState staffModeState = new StaffModeState();

        VanishRepository vanishRepository = new JdbcVanishRepository(context.platform().storage().dataSource(), scheduler);
        VanishService vanishService = new VanishService(vanishRepository, vanishRuntimeState, visibilityPolicy,
                collisionState, staffModeState, messages, context.platform().settings(), plugin);

        StaffModeSnapshotRepository staffModeSnapshotRepository =
                new JdbcStaffModeSnapshotRepository(context.platform().storage().dataSource(), scheduler);
        StaffToolItems toolItems = new StaffToolItems(plugin, messages);
        StaffModeService staffModeService = new StaffModeService(staffModeSnapshotRepository, staffModeState, godmodeState,
                collisionState, vanishService, toolItems, context.platform().settings(), scheduler);

        NetworkService network = context.platform().network();
        PunishmentEnforcer enforcer = new PunishmentEnforcer(messages, context.platform().settings(), network, freezeRuntimeState);

        context.resources().listener(new ModerationJoinListener(punishmentService, freezeRuntimeState, enforcer));
        context.resources().listener(new GodmodeSyncListener(godmodeState, plugin));
        context.resources().listener(new ModerationChatListener(punishmentService, enforcer));
        context.resources().listener(new VanishReconnectListener(vanishService, vanishRuntimeState, network));
        context.resources().listener(new VanishEnforcementListener(vanishRuntimeState, context.platform().settings()));
        context.resources().listener(new FreezeGuardListener(freezeRuntimeState, context.platform().settings()));
        context.resources().listener(new FreezeDisconnectListener(freezeRuntimeState, context.platform().actionExecutor()));
        context.resources().listener(new StaffModeRecoveryListener(staffModeService, messages));

        ModerationSettings.registerAll(context.platform().settingRegistry());
        ModerationPermissions.registerAll(context.platform().permissions());

        registerActions(context.platform().actions(), scheduler, punishmentService, messages, policy,
                freezeRuntimeState, vanishService, godmodeState, collisionState, staffModeState,
                context.platform().settings(), staffModeService, context.platform().notifications(), enforcer, network,
                new TargetNames(network, context.platform().services()));
        registerStatusToggles(context, vanishService, staffModeState, godmodeState);
        PunishmentTemplateService templates = registerTemplates(context, punishmentService);
        ModerationGuiContext guiContext = registerGui(
                context, punishmentService, vanishService, godmodeState, collisionState, staffModeState, templates);
        ModerationQuickEntry.all(guiContext, freezeRuntimeState::isFrozen).forEach(context::registerPlayerMenuEntry);
        context.registerPlayerMenuEntry(ModerationQuickEntry.history(guiContext));

        // StaffModeGuardListener needs the GUI context (Player Inspector/
        // Inventory Inspector open GUI pages) - registered after the context exists.
        context.resources().listener(new StaffModeGuardListener(
                staffModeState, toolItems, freezeRuntimeState, context.platform().actionExecutor(), guiContext,
                context.platform().settings(), context.platform().services()));

        ModerationCommands.all(new ModerationCommands.Dependencies(
                punishmentService, staffModeService, guiContext, context.platform().settings(), messages, templates, services))
                .forEach(context::registerCommand);

        registerNotes(context);
        registerAlts(context, punishmentService);
        registerImport(context);
        StaffPunishmentNotifier notifier = new StaffPunishmentNotifier(messages, context.platform().settings(), scheduler);
        context.subscribe(notifier);
        // Read-only status for scoreboards, tab lists, placeholders and the
        // voice chat extension.
        ModerationPlayerStatus status = new ModerationPlayerStatus(
                punishmentService, vanishRuntimeState, staffModeState, freezeRuntimeState, godmodeState);

        // Network mode: punishments issued on another server take effect
        // here too (kick, ban screen, mute notice, freeze, the player's
        // status) and are announced to the staff on this server.
        NetworkModerationBridge bridge = new NetworkModerationBridge(
                network, punishmentService, enforcer, notifier, scheduler, status::refreshIfOnline);
        context.subscribe(bridge);
        network.subscribe(NetworkModerationBridge.TOPIC, bridge);
        context.resources().closeable(() -> network.unsubscribe(NetworkModerationBridge.TOPIC, bridge));

        context.resources().listener(status);
        context.subscribeAudit(status);
        plugin.getServer().getOnlinePlayers().forEach(player -> status.track(player.getUniqueId()));
        context.registerService(PlayerStatus.class, status);
        context.resources().task(plugin.getServer().getScheduler().runTaskTimer(
                plugin, status::refreshAll, STATUS_REFRESH_TICKS, STATUS_REFRESH_TICKS));

        FreezeDisplay freezeDisplay = new FreezeDisplay(plugin, freezeRuntimeState, context.platform().settings(), messages);
        context.resources().task(plugin.getServer().getScheduler().runTaskTimer(plugin, freezeDisplay, 20L, 20L));
        context.resources().closeable(freezeDisplay);

        scheduleExpirySweep(context, punishmentService);
        scheduleStaffModeTracker(context, staffModeState, toolItems, freezeRuntimeState, vanishService, context.platform().settings(), messages);
    }

    /** Vanish, staff mode and godmode in the main menu's "My status" row. */
    private static void registerStatusToggles(ModuleContext context, VanishService vanish, StaffModeState staffMode, GodmodeState godmode) {
        var platform = context.platform();
        context.registerStatusToggle(new ActionStatusToggle("core:moderation.vanish", 10, ModerationPermissions.VANISH,
                viewer -> vanish.isVanished(viewer.getUniqueId()), viewer -> text(platform, "moderation.toggle.vanish"),
                ModerationActionIds.VANISH, ModerationActionIds.VANISH, platform.actionExecutor(), platform.scheduler(), platform.messages()));
        context.registerStatusToggle(new ActionStatusToggle("core:moderation.staffmode", 20, ModerationPermissions.STAFFMODE,
                viewer -> staffMode.isActive(viewer.getUniqueId()), viewer -> text(platform, "moderation.toggle.staff-mode"),
                ModerationActionIds.STAFF_MODE_ENTER, ModerationActionIds.STAFF_MODE_EXIT, platform.actionExecutor(), platform.scheduler(),
                platform.messages()));
        context.registerStatusToggle(new ActionStatusToggle("core:moderation.godmode", 30, ModerationPermissions.GODMODE,
                viewer -> godmode.isEnabled(viewer.getUniqueId()), viewer -> text(platform, "moderation.toggle.godmode"),
                ModerationActionIds.GODMODE, ModerationActionIds.GODMODE, platform.actionExecutor(), platform.scheduler(), platform.messages()));
    }

    private static Component text(UniversalAdmin platform, String key) {
        return ComponentMessages.render(platform.messages().get(MessageKey.of(key)));
    }

    /** Package-private (not {@code private}) so {@code ModerationActionsWiringTest} can exercise it without a full {@link ModuleContext}. */
    static void registerActions(
            ActionRegistry actions, TaskScheduler scheduler, PunishmentService punishmentService, MessageService messages,
            ModerationPolicy policy, FreezeRuntimeState freezeRuntimeState, VanishService vanishService, GodmodeState godmodeState,
            CollisionState collisionState, StaffModeState staffModeState, SettingsService settings,
            StaffModeService staffModeService, NotificationService notifications, PunishmentEnforcer enforcer, NetworkService network,
            TargetNames names) {
        String module = ID.key().name();

        actions.register(ActionDefinition.builder(new KickAction(scheduler, punishmentService, enforcer, policy, network))
                .permission(ModerationPermissions.KICK)
                .module(module)
                .target(ModerationModule::targetOf)
                .forbidSelfTarget()
                .validator(reasonRequired(KickInput::reason))
                .auditArgs(in -> List.of(PlayerNames.of(in.targetId()), in.reason()))
                .auditSummary(in -> "Kicked " + PlayerNames.of(in.targetId()) + ": " + in.reason() + templateSuffix(in.template()))
                .build());

        actions.register(ActionDefinition.builder(new BanAction(ModerationActionIds.BAN, scheduler, punishmentService, enforcer, policy, names))
                .permission(ModerationPermissions.BAN)
                .module(module)
                .target(ModerationModule::banTargetOf)
                .forbidSelfTarget()
                .validator(reasonRequired(BanInput::reason))
                .auditArgs(in -> List.of(PlayerNames.of(in.targetId()), in.reason()))
                .auditSummary(in -> "Banned " + PlayerNames.of(in.targetId()) + ": " + in.reason() + templateSuffix(in.template()))
                .build());
        actions.register(ActionDefinition.builder(new BanAction(ModerationActionIds.TEMP_BAN, scheduler, punishmentService, enforcer, policy, names))
                .permission(ModerationPermissions.TEMPBAN)
                .module(module)
                .target(ModerationModule::banTargetOf)
                .forbidSelfTarget()
                .validator(and(reasonRequired(BanInput::reason), durationRequired(BanInput::expiresAt)))
                .auditArgs(in -> List.of(PlayerNames.of(in.targetId()), in.reason(), until(in.expiresAt())))
                .auditSummary(in -> "Temp-banned " + PlayerNames.of(in.targetId()) + ": " + in.reason() + templateSuffix(in.template()))
                .build());
        actions.register(ActionDefinition.builder(new IpBanAction(scheduler, punishmentService, enforcer, policy, network))
                .permission(ModerationPermissions.IPBAN)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.targetId(), PlayerNames.of(in.targetId()))))
                .forbidSelfTarget()
                .validator(reasonRequired(IpBanInput::reason))
                .auditArgs(in -> List.of(PlayerNames.of(in.targetId()), in.reason()))
                .auditSummary(in -> "IP-banned " + PlayerNames.of(in.targetId()) + ": " + in.reason())
                .build());

        actions.register(ActionDefinition.builder(new MuteAction(ModerationActionIds.MUTE, scheduler, punishmentService, enforcer, policy, names))
                .permission(ModerationPermissions.MUTE)
                .module(module)
                .target(ModerationModule::muteTargetOf)
                .forbidSelfTarget()
                .validator(reasonRequired(MuteInput::reason))
                .auditArgs(in -> List.of(PlayerNames.of(in.targetId()), in.reason()))
                .auditSummary(in -> "Muted " + PlayerNames.of(in.targetId()) + kindSuffix(in.kind()) + ": " + in.reason()
                        + templateSuffix(in.template()))
                .build());
        actions.register(ActionDefinition.builder(new MuteAction(ModerationActionIds.TEMP_MUTE, scheduler, punishmentService, enforcer, policy, names))
                .permission(ModerationPermissions.TEMPMUTE)
                .module(module)
                .target(ModerationModule::muteTargetOf)
                .forbidSelfTarget()
                .validator(and(reasonRequired(MuteInput::reason), durationRequired(MuteInput::expiresAt)))
                .auditArgs(in -> List.of(PlayerNames.of(in.targetId()), in.reason(), until(in.expiresAt())))
                .auditSummary(in -> "Temp-muted " + PlayerNames.of(in.targetId()) + kindSuffix(in.kind()) + ": " + in.reason()
                        + templateSuffix(in.template()))
                .build());

        actions.register(ActionDefinition.builder(new WarnAction(scheduler, punishmentService, enforcer, policy, names))
                .permission(ModerationPermissions.WARN)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.targetId(), PlayerNames.of(in.targetId()))))
                .forbidSelfTarget()
                .validator(reasonRequired(WarnInput::reason))
                .auditArgs(in -> List.of(PlayerNames.of(in.targetId()), in.reason()))
                .auditSummary(in -> "Warned " + PlayerNames.of(in.targetId()) + ": " + in.reason() + templateSuffix(in.template()))
                .build());

        actions.register(ActionDefinition.builder(new FreezeAction(scheduler, punishmentService, enforcer, policy, names))
                .permission(ModerationPermissions.FREEZE)
                .module(module)
                .target(ModerationModule::freezeTargetOf)
                .forbidSelfTarget()
                .validator(reasonRequired(FreezeInput::reason))
                .auditArgs(in -> List.of(PlayerNames.of(in.targetId()), in.reason()))
                .auditSummary(in -> "Froze " + PlayerNames.of(in.targetId()) + ": " + in.reason())
                .build());

        actions.register(ActionDefinition.builder(new UnbanAction(punishmentService))
                .permission(ModerationPermissions.UNBAN)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.targetId(), PlayerNames.of(in.targetId()))))
                .auditArgs(in -> List.of(PlayerNames.of(in.targetId())))
                .auditSummary(in -> "Unbanned " + PlayerNames.of(in.targetId()))
                .build());
        actions.register(ActionDefinition.builder(new UnmuteAction(punishmentService))
                .permission(ModerationPermissions.UNMUTE)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.targetId(), PlayerNames.of(in.targetId()))))
                .auditArgs(in -> List.of(PlayerNames.of(in.targetId())))
                .auditSummary(in -> "Unmuted " + PlayerNames.of(in.targetId()))
                .build());
        actions.register(ActionDefinition.builder(new RemoveWarnAction(punishmentService))
                .permission(ModerationPermissions.REMOVE_WARN)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.targetId(), "warn#" + in.warnId())))
                .auditArgs(in -> List.of(PlayerNames.of(in.targetId()), in.warnId()))
                .auditSummary(in -> "Removed warning #" + in.warnId() + " from " + PlayerNames.of(in.targetId()))
                .build());
        actions.register(ActionDefinition.builder(new UnfreezeAction(punishmentService, freezeRuntimeState))
                .permission(ModerationPermissions.UNFREEZE)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.targetId(), PlayerNames.of(in.targetId()))))
                .auditArgs(in -> List.of(PlayerNames.of(in.targetId())))
                .auditSummary(in -> "Unfroze " + PlayerNames.of(in.targetId()))
                .build());

        // Self-directed: no target beyond the actor (see the Void-input
        // Action classes' own javadoc for why no wrapper input record exists).
        actions.register(ActionDefinition.builder(new VanishAction(scheduler, vanishService))
                .permission(ModerationPermissions.VANISH)
                .module(module)
                .auditSummary(in -> "Toggled vanish")
                .build());
        actions.register(ActionDefinition.builder(new GodmodeAction(scheduler, godmodeState))
                .permission(ModerationPermissions.GODMODE)
                .module(module)
                .auditSummary(in -> "Toggled godmode")
                .build());
        actions.register(ActionDefinition.builder(new NoCollisionAction(scheduler, collisionState, vanishService, staffModeState, settings))
                .permission(ModerationPermissions.COLLISION)
                .module(module)
                .auditSummary(in -> "Toggled no-collision")
                .build());
        actions.register(ActionDefinition.builder(new EnterStaffModeAction(scheduler, staffModeService))
                .permission(ModerationPermissions.STAFFMODE)
                .module(module)
                .auditSummary(in -> "Entered staff mode")
                .build());
        actions.register(ActionDefinition.builder(new ExitStaffModeAction(scheduler, staffModeService))
                .permission(ModerationPermissions.STAFFMODE)
                .module(module)
                .auditSummary(in -> "Exited staff mode")
                .build());
        actions.register(ActionDefinition.builder(new RecoverStaffSnapshotAction(staffModeService))
                .permission(ModerationPermissions.STAFFMODE_RECOVER)
                .module(module)
                .target(targetId -> Optional.of(ActionTarget.player(targetId, PlayerNames.of(targetId))))
                .auditArgs(targetId -> List.of(PlayerNames.of(targetId)))
                .auditSummary(targetId -> "Recovered staff-mode snapshot for " + PlayerNames.of(targetId))
                .build());
        actions.register(ActionDefinition.builder(new FreezeDisconnectNoticeAction(notifications, messages))
                .module(module)
                .target(targetId -> Optional.of(ActionTarget.player(targetId, PlayerNames.of(targetId))))
                .auditArgs(targetId -> List.of(PlayerNames.of(targetId)))
                .auditSummary(targetId -> "Player disconnected while frozen: " + PlayerNames.of(targetId))
                .build());
    }

    private static Optional<ActionTarget> targetOf(KickInput input) {
        return Optional.of(ActionTarget.player(input.targetId(), PlayerNames.of(input.targetId())));
    }

    private static Optional<ActionTarget> banTargetOf(BanInput input) {
        return Optional.of(ActionTarget.player(input.targetId(), PlayerNames.of(input.targetId())));
    }

    private static Optional<ActionTarget> muteTargetOf(MuteInput input) {
        return Optional.of(ActionTarget.player(input.targetId(), PlayerNames.of(input.targetId())));
    }

    private static Optional<ActionTarget> freezeTargetOf(FreezeInput input) {
        return Optional.of(ActionTarget.player(input.targetId(), PlayerNames.of(input.targetId())));
    }


    /** {@code " (voice chat only)"}/{@code " (chat only)"} for a mute that is not a full one, else empty. */
    private static String kindSuffix(MuteKind kind) {
        return switch (kind) {
            case CHAT -> " (chat only)";
            case VOICE -> " (voice chat only)";
            case FULL -> "";
        };
    }

    /** {@code " (template spam)"} for a punishment issued through a template, else empty. */
    /** How long a temporary punishment runs, for the audit sentence - "-" without an end. */
    private static String until(Instant expiresAt) {
        return expiresAt == null ? "-" : DurationParser.formatRemaining(expiresAt);
    }

    private static String templateSuffix(String template) {
        return template == null ? "" : " (template " + template + ")";
    }
    private static <I> ActionValidator<I> reasonRequired(java.util.function.Function<I, String> reason) {
        return (context, input) -> {
            String value = reason.apply(input);
            return (value == null || value.isBlank())
                    ? Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION, MessageKey.of("moderation.action.reason-required")))
                    : Optional.empty();
        };
    }

    private static <I> ActionValidator<I> durationRequired(java.util.function.Function<I, java.time.Instant> expiresAt) {
        return (context, input) -> expiresAt.apply(input) == null
                ? Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION, MessageKey.of("moderation.action.duration-required")))
                : Optional.empty();
    }

    /** {@link ActionValidator} has no built-in combinator (it's a plain {@code @FunctionalInterface}) - this is the local one. */
    private static <I> ActionValidator<I> and(ActionValidator<I> first, ActionValidator<I> second) {
        return (context, input) -> {
            Optional<ValidationError> firstError = first.validate(context, input);
            return firstError.isPresent() ? firstError : second.validate(context, input);
        };
    }

    private ModerationGuiContext registerGui(
            ModuleContext context, PunishmentService punishmentService, VanishService vanishService,
            GodmodeState godmodeState, CollisionState collisionState, StaffModeState staffModeState,
            PunishmentTemplateService templates) {
        ModerationGuiContext guiContext = new ModerationGuiContext(
                context.platform().guiFramework(), context.platform().messages(), context.platform().scheduler(),
                punishmentService, context.platform().actionExecutor(), context.platform().settings(),
                vanishService, godmodeState, collisionState, staffModeState, templates, context.platform().services());

        ModerationHomePage homePage = new ModerationHomePage(guiContext);
        context.platform().guiPages().register(homePage);
        context.resources().closeable(() -> context.platform().guiPages().unregister(ModerationHomePage.ID));

        // The "Moderate" button on every player profile - contributed through
        // the platform's PlayerMenuRegistry, so the Players module never
        // imports anything from this module (and an extension can add its
        // own buttons the same way).
        context.registerPlayerMenuEntry(new ModeratePlayerMenuEntry(guiContext));
        return guiContext;
    }

    /**
     * Punishment templates: {@value #TEMPLATES_FILE} (written once from the
     * jar, in the configured language), the {@code /punish} action, and a
     * re-read on {@code /admin reload}.
     */
    private PunishmentTemplateService registerTemplates(ModuleContext context, PunishmentService punishments) {
        var platform = context.platform();
        Path file = platform.plugin().getDataFolder().toPath().resolve(TEMPLATES_FILE);
        writeDefaultTemplates(platform.plugin(), file, platform.settings().get(CoreSettings.LANGUAGE), context.logger());
        PunishmentTemplateService templates = new PunishmentTemplateService(punishments, context.logger());
        templates.reload(file);
        context.subscribe(event -> {
            if (event instanceof ActionEvent.Executed<?, ?> executed && executed.id().equals(ReloadConfigAction.ID)) {
                templates.reload(file);
            }
        });
        context.registerAction(ActionDefinition.builder(
                        new ApplyTemplateAction(templates, platform.actionExecutor(), platform.messages(), platform.scheduler()))
                .permission(ModerationPermissions.PUNISH)
                .module(ID.key().name())
                .target(in -> Optional.of(ActionTarget.player(in.targetId(), PlayerNames.of(in.targetId()))))
                .forbidSelfTarget()
                // The punishment it issues is audited by its own action.
                .notAudited()
                .build());
        return templates;
    }

    private static void writeDefaultTemplates(Plugin plugin, Path file, String language, Logger logger) {
        if (Files.exists(file)) {
            return;
        }
        String resource = "punishment-templates/" + ("de_DE".equals(language) ? "de_DE" : "en_US") + ".yml";
        try (InputStream defaults = plugin.getResource(resource)) {
            if (defaults == null) {
                logger.warning("Missing " + resource + " in the plugin jar - no punishment templates.");
                return;
            }
            Files.createDirectories(file.getParent());
            Files.copy(defaults, file);
        } catch (IOException e) {
            logger.warning("Could not write " + file.getFileName() + ": " + e.getMessage());
        }
    }

    /**
     * Alt detection (opt-in): the address table, view/forget actions (both
     * audited), the profile button, {@code /admin alts}, the login check and
     * an hourly sweep that forgets addresses older than the retention period.
     */
    private void registerAlts(ModuleContext context, PunishmentService punishments) {
        var platform = context.platform();
        AltService alts = new AltService(new JdbcAltRepository(platform.storage().dataSource(), platform.scheduler()),
                punishments, platform.settings());
        String module = ID.key().name();
        context.registerAction(ActionDefinition.builder(new AltActions.View(alts))
                .permission(ModerationPermissions.ALTS)
                .module(module)
                .target(input -> Optional.of(ActionTarget.player(input.playerId(), input.playerName())))
                // Who looked at whose connections - the list itself stays out of the audit log.
                .auditArgs(input -> List.of(input.playerName()))
                .auditSummary(input -> "Viewed possible alts of " + input.playerName())
                .build());
        context.registerAction(ActionDefinition.builder(new AltActions.Forget(alts))
                .permission(ModerationPermissions.ALTS_FORGET)
                .module(module)
                .target(input -> Optional.of(ActionTarget.player(input.playerId(), input.playerName())))
                .auditArgs(input -> List.of(input.playerName()))
                .auditSummary(input -> "Forgot the stored addresses of " + input.playerName())
                .build());

        AltSupport.Context ctx = new AltSupport.Context(platform.guiFramework(), platform.messages(), platform.scheduler(),
                platform.actionExecutor(), alts, platform.settings(), platform.notifications(), platform.network(), context.logger());
        context.registerPlayerMenuEntry(AltSupport.menuEntry(ctx));
        context.registerCommand(new AltSupport.AltsCommand(ctx));
        AltSupport.JoinListener listener = new AltSupport.JoinListener(ctx);
        context.resources().listener(listener);
        platform.network().subscribe(AltSupport.NETWORK_TOPIC, listener);
        context.resources().closeable(() -> platform.network().unsubscribe(AltSupport.NETWORK_TOPIC, listener));
        context.resources().task(platform.plugin().getServer().getScheduler().runTaskTimer(platform.plugin(),
                () -> alts.sweep().exceptionally(error -> {
                    context.logger().log(Level.WARNING, "Could not forget old addresses", error);
                    return 0;
                }), ALT_SWEEP_TICKS, ALT_SWEEP_TICKS));
    }

    /** Staff notes: table, add/remove actions, the profile button, {@code /admin notes} and the join alert. */
    private void registerNotes(ModuleContext context) {
        var platform = context.platform();
        PlayerNoteRepository notes = new JdbcPlayerNoteRepository(platform.storage().dataSource(), platform.scheduler());
        String module = ID.key().name();
        context.registerAction(ActionDefinition.builder(new NoteActions.Add(notes))
                .permission(ModerationPermissions.NOTES)
                .module(module)
                .target(input -> Optional.of(ActionTarget.player(input.targetId(), input.targetName())))
                .validator((ctx, input) -> {
                    String text = input.text() == null ? "" : input.text().trim();
                    if (text.isEmpty()) {
                        return Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION, MessageKey.of("moderation.notes.empty")));
                    }
                    return text.length() > NoteActions.MAX_LENGTH
                            ? Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION,
                                    MessageKey.of("moderation.notes.too-long"), NoteActions.MAX_LENGTH))
                            : Optional.empty();
                })
                .auditArgs(input -> List.of(input.targetName(), input.text()))
                .auditSummary(input -> "Added a note about " + input.targetName() + ": " + input.text())
                .build());
        context.registerAction(ActionDefinition.builder(new NoteActions.Remove(notes))
                .permission(ModerationPermissions.NOTES)
                .module(module)
                .target(id -> Optional.of(ActionTarget.of("note", String.valueOf(id), "note #" + id)))
                .auditArgs(id -> List.of(id))
                .auditSummary(id -> "Removed note #" + id)
                .build());

        NotesSupport.Context notesContext = new NotesSupport.Context(platform.guiFramework(), platform.messages(),
                platform.scheduler(), platform.actionExecutor(), notes, platform.settings(), platform.notifications());
        context.registerPlayerMenuEntry(NotesSupport.menuEntry(notesContext));
        context.registerCommand(new NotesSupport.NotesCommand(notesContext));
        context.registerListener(new NotesSupport.JoinAlertListener(notesContext));
    }

    /**
     * {@code /admin import}: punishments from LiteBans, AdvancedBan, the
     * vanilla ban lists and EssentialsX - see docs/user/import.md.
     */
    private void registerImport(ModuleContext context) {
        var platform = context.platform();
        Server server = platform.plugin().getServer();
        Path pluginsFolder = platform.plugin().getDataFolder().toPath().toAbsolutePath().getParent();
        Path serverFolder = Path.of("").toAbsolutePath();
        boolean offlineMode = !server.getOnlineMode()
                && !(server.getServerConfig().isProxyEnabled() && server.getServerConfig().isProxyOnlineMode());
        MessageService messages = platform.messages();
        NoteText noteText = (key, args) -> messages.get(MessageKey.of(key), args);
        TaskScheduler scheduler = platform.scheduler();

        List<PunishmentImporter> importers = List.of(
                new LiteBansImporter(pluginsFolder, serverFolder.resolve("libraries")),
                new AdvancedBanImporter(pluginsFolder, offlineMode, noteText),
                new VanillaImporter(List.of(serverFolder, server.getWorldContainer().toPath().toAbsolutePath()),
                        (players, addresses) -> removeFromVanillaBanLists(server, scheduler, players, addresses)),
                new EssentialsImporter(pluginsFolder, noteText));
        ImportService service = new ImportService(importers,
                new JdbcImportRepository(platform.storage().dataSource(), scheduler),
                () -> platform.services().get(PlayerDirectory.class), context.logger());

        context.registerAction(ActionDefinition.builder(new ImportAction(service, scheduler))
                .permission(ModerationPermissions.IMPORT)
                .module(ID.key().name())
                .target(input -> Optional.of(ActionTarget.of("import", input.source(), sourceName(service, input.source()))))
                .auditArgs(input -> List.of(sourceName(service, input.source())))
                .auditSummary(input -> "Imported punishments from " + sourceName(service, input.source()))
                .auditDetails((input, result) -> result instanceof ActionResult.Success<ImportSummary> success
                        ? AuditDetails.builder().metadata(Map.of(
                                "punishments", success.value().totalPunishments(),
                                "notes", success.value().notes(),
                                "players", success.value().players(),
                                "skipped", success.value().totalSkipped(),
                                "already_imported", success.value().alreadyImported())).build()
                        : AuditDetails.EMPTY)
                .build());
        context.registerCommand(new ImportCommand(service, scheduler, context.logger()));
    }

    /** "EssentialsX" for "essentials" - the source as the audit log shows it. */
    private static String sourceName(ImportService service, String id) {
        return service.importer(id).map(PunishmentImporter::displayName).orElse(id);
    }

    /** Takes moved entries off the server's own ban lists (the vanilla import moves them) - Bukkit API, so on the main thread. */
    private static void removeFromVanillaBanLists(Server server, TaskScheduler scheduler, List<UUID> players, List<String> addresses)
            throws ImportException {
        try {
            scheduler.supplyOnMainThread(() -> {
                ProfileBanList profiles = server.getBanList(BanListType.PROFILE);
                players.forEach(id -> profiles.pardon(server.createPlayerProfile(id)));
                IpBanList ips = server.getBanList(BanListType.IP);
                for (String address : addresses) {
                    try {
                        ips.pardon(InetAddress.getByName(address));
                    } catch (UnknownHostException e) {
                        // Not an address after all - nothing on the list to remove.
                    }
                }
                return null;
            }).join();
        } catch (CompletionException e) {
            throw new ImportException("moderation.import.error.vanilla-remove", e.getCause(), e.getCause().getMessage());
        }
    }

    private void scheduleExpirySweep(ModuleContext context, PunishmentService punishmentService) {
        Plugin plugin = context.platform().plugin();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> punishmentService.expireOverdue().exceptionally(error -> {
                    context.logger().warning("Punishment expiry sweep failed: " + error.getMessage());
                    return 0;
                }),
                CLEANUP_PERIOD_TICKS, CLEANUP_PERIOD_TICKS);
        context.resources().task(task);
    }

    private void scheduleStaffModeTracker(
            ModuleContext context, StaffModeState staffModeState, StaffToolItems toolItems,
            FreezeRuntimeState freezeRuntimeState, VanishService vanishService, SettingsService settings, MessageService messages) {
        StaffModeTargetTracker tracker =
                new StaffModeTargetTracker(staffModeState, toolItems, freezeRuntimeState, vanishService, settings, messages,
                        context.platform().statusToggles());
        Plugin plugin = context.platform().plugin();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, tracker::tick, STAFF_MODE_TRACKER_PERIOD_TICKS, STAFF_MODE_TRACKER_PERIOD_TICKS);
        context.resources().task(task);
    }
}
