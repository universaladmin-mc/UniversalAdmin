package dev.universaladmin.bootstrap;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.audit.AuditSchemaMigration;
import dev.universaladmin.audit.AuditSchemaMigrationV2;
import dev.universaladmin.audit.AuditService;
import dev.universaladmin.audit.DefaultAuditService;
import dev.universaladmin.audit.jdbc.JdbcAuditEventRepository;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.command.CommandRegistry;
import dev.universaladmin.command.RemoteCommandService;
import dev.universaladmin.command.RemoteCommands;
import dev.universaladmin.command.UniversalAdminCommand;
import dev.universaladmin.command.builtin.CorePermissions;
import dev.universaladmin.command.builtin.HelpCommand;
import dev.universaladmin.command.builtin.MenuCommand;
import dev.universaladmin.command.builtin.ModulesCommand;
import dev.universaladmin.command.builtin.ReloadCommand;
import dev.universaladmin.command.builtin.StatusCommand;
import dev.universaladmin.command.builtin.UpdateCommand;
import dev.universaladmin.command.paper.ShortcutCommands;
import dev.universaladmin.config.ConfigCompletion;
import dev.universaladmin.config.ConfigMigrationRunner;
import dev.universaladmin.core.ServiceRegistry;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.core.UniversalAdminProvider;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiListener;
import dev.universaladmin.gui.GuiRegistry;
import dev.universaladmin.gui.GuiSessionManager;
import dev.universaladmin.gui.LiveRefresh;
import dev.universaladmin.gui.MainMenuPage;
import dev.universaladmin.gui.MaterialIconProvider;
import dev.universaladmin.gui.ModuleGuiStyle;
import dev.universaladmin.gui.PlayerMenuRegistry;
import dev.universaladmin.gui.StaffWelcome;
import dev.universaladmin.gui.StatusLine;
import dev.universaladmin.gui.StatusToggleRegistry;
import dev.universaladmin.localization.LanguageFiles;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.localization.YamlLocaleMessageService;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleManager;
import dev.universaladmin.module.ModuleRegistry;
import dev.universaladmin.modules.auditlog.AuditLogModule;
import dev.universaladmin.modules.chat.ChatModule;
import dev.universaladmin.modules.moderation.ModerationModule;
import dev.universaladmin.modules.network.NetworkModule;
import dev.universaladmin.modules.nick.NickModule;
import dev.universaladmin.modules.performance.PerformanceModule;
import dev.universaladmin.modules.players.PlayersModule;
import dev.universaladmin.modules.reports.ReportsModule;
import dev.universaladmin.modules.server.ServerModule;
import dev.universaladmin.modules.settings.SettingsModule;
import dev.universaladmin.modules.whitelist.WhitelistModule;
import dev.universaladmin.modules.worlds.WorldsModule;
import dev.universaladmin.network.DefaultNetworkService;
import dev.universaladmin.network.NetworkPresenceListener;
import dev.universaladmin.network.NetworkSchemaMigration;
import dev.universaladmin.network.NetworkVanishMigration;
import dev.universaladmin.network.jdbc.JdbcNetworkRepository;
import dev.universaladmin.notification.InGameNotificationService;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.permission.PermissionRegistry;
import dev.universaladmin.scheduler.PaperTaskScheduler;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.ReloadConfigAction;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.settings.YamlSettingsService;
import dev.universaladmin.status.PlayerStatus;
import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.DatabaseType;
import dev.universaladmin.storage.jdbc.JdbcStorageService;
import dev.universaladmin.telemetry.PlayerCounts;
import dev.universaladmin.telemetry.TelemetryBootstrap;
import dev.universaladmin.telemetry.TelemetryEnvironment;
import dev.universaladmin.update.ApplyUpdateAction;
import dev.universaladmin.update.UpdateCheckBootstrap;
import dev.universaladmin.update.UpdateDownloader;
import dev.universaladmin.update.UpdateJoinListener;
import dev.universaladmin.update.UpdateTarget;
import dev.universaladmin.update.UpdateTargets;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The Bukkit/Paper entry point. Its only job is composition: build every
 * critical core service by hand (there is no DI framework, see
 * {@link UniversalAdmin}), drive the built-in modules through
 * {@link ModuleManager}, and register the handful of things that must be
 * registered with Bukkit itself (the command, permissions).
 *
 * <p>No business logic belongs in this class. If you're tempted to add an
 * {@code if} here that isn't wiring, it almost certainly belongs in a module,
 * service, or action instead.
 *
 * <h2>Lifecycle</h2>
 *
 * <ul>
 *   <li>{@link #onLoad()} - runs before any plugin's {@code onEnable}, per
 *       Bukkit's own plugin lifecycle. UniversalAdmin has nothing that must
 *       happen this early (no service it needs to register with another
 *       plugin before enable), so this is intentionally thin.
 *   <li>{@link #onEnable()} bootstrap phase - constructs every
 *       <b>critical</b> core component (config file + migration, settings,
 *       scheduler, storage + DB migrations, the shared registries,
 *       audit/message/notification services). If any of this throws, startup
 *       aborts: the exception is logged with context and the plugin disables
 *       itself. There is nothing a partially-initialized UniversalAdmin
 *       could safely do. A bad *value* in config.yml is different - see
 *       {@link YamlSettingsService}, which falls back to defaults instead of
 *       throwing, so a typo in {@code gui.page-size} never reaches here.
 *   <li>{@link #onEnable()} module phase - {@link ModuleManager#loadAll()}
 *       then {@link ModuleManager#enableAll()}, for whichever built-in
 *       modules are enabled in {@code modules.*} (see {@link CoreSettings}).
 *       A built-in module is <b>not</b> critical: if one fails, it is
 *       isolated ({@code FAILED}, logged in full) and every other module
 *       still gets to load/enable. See docs/architecture/modules.md for the
 *       full critical/non-critical breakdown.
 *   <li>{@link #onDisable()} - stops telemetry, disables modules (releasing
 *       their resources via {@link dev.universaladmin.module.ModuleResources}
 *       even if a module's own {@code onDisable} throws), then closes storage
 *       and the scheduler. Best-effort: one step failing does not skip the
 *       rest.
 * </ul>
 */
public final class UniversalAdminPlugin extends JavaPlugin {

    /** Shared between {@link #bootstrapCore} (permission registration) and {@link #startUpdateChecks} (who gets notified). */
    private static final PermissionNode UPDATE_NOTIFY_PERMISSION = PermissionNode.core("update.notify");

    /** Locales shipped inside the jar - see {@link YamlLocaleMessageService} for the override layering. */
    private static final List<String> BUNDLED_LOCALES = List.of("en_US", "de_DE");

    private TaskScheduler scheduler;
    private JdbcStorageService storage;
    private ModuleManager moduleManager;
    private UniversalAdmin platform;
    private DefaultExtensionManager extensions;
    private BukkitPermissionSync permissionSync;
    private TelemetryBootstrap telemetry;
    private UpdateCheckBootstrap updateChecks;
    /** What the update check and /admin update cover - UniversalAdmin, then extensions as they register. */
    private UpdateTargets updateTargets;
    private UpdateCheckBootstrap.ReleaseClients releaseClients;
    private UpdateDownloader updateDownloader;
    private DefaultNetworkService network;

    @Override
    public void onLoad() {
        // Nothing needs to happen before other plugins' onEnable today (e.g.
        // registering a Bukkit ServicesManager entry) - heavier init happens
        // in onEnable, which is the Bukkit convention. Kept as an explicit,
        // documented phase rather than omitted, so the lifecycle is
        // deliberate rather than accidental.
        getLogger().fine("UniversalAdmin loading.");
    }

    @Override
    public void onEnable() {
        Instant startedAt = Instant.now();

        UniversalAdmin platform;
        try {
            platform = bootstrapCore(startedAt);
            this.platform = platform;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE,
                    "UniversalAdmin failed to start: a critical core component could not be "
                            + "initialized. The plugin will be disabled.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        moduleManager = new ModuleManager(platform);
        extensions.attach(moduleManager);
        registerBuiltInModules(moduleManager, platform.settings());
        moduleManager.loadAll();

        // Every built-in module registers its own Migration(s) during its
        // onLoad (see adding-module.md step 3) - never onEnable, precisely so
        // this second runPending() call, sitting *between* loadAll() and
        // enableAll(), has already applied every module-owned table
        // (player_profiles, punishments, server_maintenance_state,
        // whitelist_entries, vanish_state, ...) before any module's onEnable
        // runs. That ordering matters: several modules construct a service in
        // onEnable that reads its own table immediately, including
        // asynchronously (e.g. ServerModule's DefaultMaintenanceService) - if
        // migrations were applied only after every module finished enabling,
        // that first read would race the table's own creation and lose.
        // MigrationRunner.runPending() is idempotent (see
        // MigrationRunnerTest), so this only ever applies what's newly
        // registered, never re-applies anything bootstrapCore's earlier call
        // already handled. Still synchronous/main-thread, still before
        // players can join, same justification as the first call.
        storage.migrations().runPending();

        moduleManager.enableAll();

        permissionSync.sync();
        // Extensions (plugins with depend: [UniversalAdmin]) enable after
        // this point and register their modules through the provider/API.
        getServer().getPluginManager().registerEvents(extensions, this);
        UniversalAdminProvider.register(platform);
        getServer().getServicesManager().register(UniversalAdmin.class, platform, this, ServicePriority.Normal);
        registerCommand(platform);
        getServer().getPluginManager().registerEvents(new StartupSummaryListener(platform), this);

        getServer().getScheduler().runTaskTimer(this,
                new StatusLine(getServer(), platform.statusToggles(), platform.settings(), getLogger()), 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, new LiveRefresh(getServer(), getLogger()), 20L, 20L);
        getServer().getPluginManager().registerEvents(new StaffWelcome(this, platform.messages(), platform.settings()), this);
        startTelemetry(platform);
        startUpdateChecks(platform);
        logStartupSummary(platform);
    }

    @Override
    public void onDisable() {
        if (platform != null) {
            UniversalAdminProvider.unregister(platform);
            getServer().getServicesManager().unregisterAll(this);
        }
        if (updateChecks != null) {
            updateChecks.close();
        } else if (releaseClients != null) {
            releaseClients.close();
        }
        if (telemetry != null) {
            telemetry.close();
        }
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        if (network != null) {
            network.stop();
        }
        if (storage != null) {
            storage.close();
        }
        if (scheduler != null) {
            scheduler.close();
        }
    }

    /**
     * Builds every critical core component and the {@link UniversalAdmin}
     * composition root. "Critical" here means: nothing built-in or external
     * can function without it, so a failure here is a whole-plugin failure,
     * not something to isolate per-module. See docs/architecture/modules.md.
     */
    /** The config.yml inside the jar, comments included - what {@link ConfigCompletion} adds from. */
    private YamlConfiguration bundledConfig() {
        YamlConfiguration bundled = new YamlConfiguration();
        try (InputStream in = getResource("config.yml")) {
            if (in != null) {
                bundled.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().log(Level.WARNING, "Could not read the bundled config.yml - no new options added", e);
        }
        return bundled;
    }

    private UniversalAdmin bootstrapCore(Instant startedAt) {
        saveDefaultConfig();
        // lang/<locale>.yml holds only the server owner's own texts; an old
        // full copy is cleaned once - see LanguageFiles and ADR 0026.
        LanguageFiles.prepare(getDataFolder().toPath().resolve("lang"), BUNDLED_LOCALES, this::getResource, getLogger());

        // config-version migration runs before anything reads the file, so
        // an old user config.yml is upgraded in place rather than silently
        // reinterpreted (or overwritten) under the current schema. Reused by
        // /admin reload - see ReloadConfigAction.
        ConfigMigrationRunner migrationRunner = ConfigMigrationRunner.withBuiltInMigrations(getLogger());
        reloadConfig();
        boolean changed = migrationRunner.run(getConfig());
        // Options added in this version are written into an existing file with
        // their explanation, so they can be found next to the others.
        List<String> added = ConfigCompletion.complete(getConfig(), bundledConfig());
        if (!added.isEmpty()) {
            getLogger().info(() -> "Added " + added.size() + " new option(s) to config.yml: " + String.join(", ", added));
        }
        if (changed || !added.isEmpty()) {
            saveConfig();
        }

        SettingRegistry settingRegistry = new SettingRegistry();
        CoreSettings.registerAll(settingRegistry);
        SettingsService settingsService = new YamlSettingsService(settingRegistry, this::getConfig, this::saveConfig, getLogger());

        scheduler = new PaperTaskScheduler(this);
        DatabaseConfig databaseConfig = CoreSettings.readDatabaseConfig(settingsService);
        storage = new JdbcStorageService(databaseConfig, getDataFolder().toPath(), getLogger());
        storage.migrations().register(new AuditSchemaMigration());
        storage.migrations().register(new AuditSchemaMigrationV2());
        storage.migrations().register(new NetworkSchemaMigration());
        storage.migrations().register(new NetworkVanishMigration());

        ServiceRegistry services = new ServiceRegistry();
        ActionRegistry actions = new ActionRegistry();
        GuiRegistry guiPages = new GuiRegistry();
        GuiSessionManager guiSessions = new GuiSessionManager();
        ModuleRegistry moduleRegistry = new ModuleRegistry();
        MaterialIconProvider icons = new MaterialIconProvider(getLogger());
        GuiFramework guiFramework = new GuiFramework(guiSessions, icons, new ModuleGuiStyle(moduleRegistry, icons, guiPages));
        // The one GuiListener for the whole plugin - see docs/development/gui-framework.md.
        getServer().getPluginManager().registerEvents(new GuiListener(guiSessions, this), this);
        PermissionRegistry permissions = new PermissionRegistry();
        permissionSync = new BukkitPermissionSync(getServer().getPluginManager(), permissions);
        updateTargets = new UpdateTargets();
        updateTargets.add(new UpdateTarget("UniversalAdmin", UpdateTargets.CORE_REPOSITORY, UpdateTargets.CORE_ASSET_PREFIX,
                getPluginMeta().getVersion(), getFile().toPath()));
        releaseClients = new UpdateCheckBootstrap.ReleaseClients(getPluginMeta().getVersion());
        updateDownloader = new UpdateDownloader(() -> getServer().getUpdateFolderFile().toPath());
        extensions = new DefaultExtensionManager(moduleRegistry, storage.migrations(), permissionSync, updateTargets, getLogger());
        network = createNetwork(settingsService, services);
        DefaultNetworkService networkService = network;
        AuditService auditService = new DefaultAuditService(new JdbcAuditEventRepository(storage.dataSource(), scheduler),
                settingsService, () -> networkService.enabled() ? networkService.serverName() : null);
        ActionExecutor actionExecutor = new ActionExecutor(actions, auditService, getLogger());
        MessageService messages = new YamlLocaleMessageService(
                getDataFolder().toPath().resolve("lang"), this::getResource, BUNDLED_LOCALES, settingsService, getLogger());
        StatusToggleRegistry statusToggles = new StatusToggleRegistry();
        NotificationService notifications = new InGameNotificationService(statusToggles);

        UniversalAdmin platform = new UniversalAdminPlatform(
                this, getPluginMeta().getVersion(), startedAt, getLogger(), settingRegistry, settingsService,
                scheduler, storage, services, actions, actionExecutor, guiPages, guiFramework, new PlayerMenuRegistry(), statusToggles,
                new CommandRegistry(), extensions, permissions, moduleRegistry, auditService, messages, notifications, network);

        // First of two runPending() calls (see the second one in onEnable(),
        // after moduleManager.enableAll()): this one only covers the two
        // core audit migrations registered just above, since no module has
        // registered its own migrations yet at this point in bootstrap.
        // Migrations run synchronously here, before any module is considered
        // loaded and before players can join - one of the two places in the
        // plugin deliberately allowed to block the main thread on IO. See
        // docs/architecture/threading.md. A migration failure is critical:
        // no module can safely trust the schema, so it must abort startup
        // (this method is called from the critical bootstrap try/catch).
        storage.migrations().runPending();

        // After the migrations: the network tables exist now. Modules
        // subscribe to network topics during their own onEnable.
        network.start();
        getServer().getPluginManager().registerEvents(new NetworkPresenceListener(network), this);

        platform.permissions().register(new PermissionDefinition(
                PermissionNode.core("reload"), "Reload UniversalAdmin's configuration",
                dev.universaladmin.permission.PermissionDefault.OP));
        Supplier<FileConfiguration> configReloader = () -> {
            reloadConfig();
            return getConfig();
        };
        actions.register(ActionDefinition.builder(new ReloadConfigAction(
                        configReloader, this::saveConfig, migrationRunner, settingsService, messages::reload, scheduler))
                .permission(PermissionNode.core("reload"))
                .build());

        platform.permissions().register(new PermissionDefinition(
                PermissionNode.core("menu.open"), "Open the /admin main menu",
                dev.universaladmin.permission.PermissionDefault.OP));

        platform.permissions().register(new PermissionDefinition(
                UPDATE_NOTIFY_PERMISSION, "Receive a notice when a new UniversalAdmin version is available",
                dev.universaladmin.permission.PermissionDefault.OP));
        platform.permissions().register(new PermissionDefinition(
                PermissionNode.core("update.apply"), "Download the latest UniversalAdmin and extension releases via /admin update",
                dev.universaladmin.permission.PermissionDefault.OP));
        actions.register(ActionDefinition.builder(new ApplyUpdateAction(scheduler, updateTargets, releaseClients, updateDownloader,
                        (name, version) -> {
                            if (updateChecks != null) {
                                updateChecks.service().markStaged(name, version);
                            }
                        }, getLogger()))
                .permission(PermissionNode.core("update.apply"))
                .build());
        guiPages.register(new MainMenuPage(guiFramework, messages, moduleRegistry, guiPages, platform.statusToggles(),
                services, platform.network()));
        registerCoreCommands(platform);

        return platform;
    }

    /**
     * Network mode needs a database every server shares - with SQLite each
     * server has its own file, so it stays off (and says why).
     */
    private DefaultNetworkService createNetwork(SettingsService settings, ServiceRegistry services) {
        boolean requested = settings.get(CoreSettings.NETWORK_ENABLED);
        boolean shared = storage.type() == DatabaseType.MYSQL;
        if (requested && !shared) {
            getLogger().severe("network.enabled is true, but network mode needs a MySQL/MariaDB database shared by all "
                    + "servers (database.type: mysql) - network mode stays off.");
        }
        return new DefaultNetworkService(this, new JdbcNetworkRepository(storage.dataSource(), scheduler), scheduler, getLogger(),
                getPluginMeta().getVersion(), requested && shared, settings.get(CoreSettings.NETWORK_SERVER_NAME),
                settings.get(CoreSettings.NETWORK_POLL_INTERVAL), settings.get(CoreSettings.NETWORK_PROXY_SERVER_LIST),
                // Vanish belongs to the Moderation module; its PlayerStatus answers from memory.
                playerId -> services.get(PlayerStatus.class).map(status -> status.vanished(playerId)).orElse(false));
    }

    /** The core's own {@code /admin} subcommands; every module adds its own in its {@code onEnable}. */
    private void registerCoreCommands(UniversalAdmin platform) {
        CorePermissions.registerAll(platform.permissions());
        CommandRegistry commands = platform.commands();
        HelpCommand help = new HelpCommand(commands);
        commands.register(help);
        commands.register(new MenuCommand(platform, help));
        commands.register(new StatusCommand(platform));
        commands.register(new ModulesCommand(platform.moduleRegistry()));
        commands.register(new ReloadCommand());
        commands.register(new UpdateCommand());
    }

    private void registerBuiltInModules(ModuleManager moduleManager, SettingsService settings) {
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_PLAYERS_ENABLED, PlayersModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_MODERATION_ENABLED, ModerationModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_SERVER_ENABLED, ServerModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_WORLDS_ENABLED, WorldsModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_WHITELIST_ENABLED, WhitelistModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_PERFORMANCE_ENABLED, PerformanceModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_AUDIT_ENABLED, AuditLogModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_SETTINGS_ENABLED, SettingsModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_REPORTS_ENABLED, ReportsModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_CHAT_ENABLED, ChatModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_NICK_ENABLED, NickModule::new);
        // Network mode on (network.enabled + a shared MySQL database): the
        // network menu and commands. Off: nothing to show.
        if (network.enabled()) {
            moduleManager.register(new NetworkModule());
        }
    }

    private void registerIfEnabled(
            ModuleManager moduleManager, SettingsService settings, SettingKey<Boolean> toggle, Supplier<Module> factory) {
        if (settings.get(toggle)) {
            moduleManager.register(factory.get());
        } else {
            getLogger().info(() -> "Module disabled via config (" + toggle.configPath() + "): skipping.");
        }
    }


    /**
     * Wires {@code /admin} (plugin.yml) to the dispatcher and registers the
     * optional top-level shortcuts ({@code /kick}, {@code /vanish}, ...)
     * through Paper's command lifecycle - see {@link ShortcutCommands}.
     */
    private void registerCommand(UniversalAdmin platform) {
        UniversalAdminCommand dispatcher = new UniversalAdminCommand(platform.commands(), new CommandInvocation.CommandEnvironment(
                platform.messages(), platform.actionExecutor(), platform.scheduler(), getLogger()));
        var command = getCommand("admin");
        if (command != null) {
            command.setExecutor(dispatcher);
            command.setTabCompleter(dispatcher);
        }
        // /admin for users outside the game - the Discord bot, later the web panel.
        platform.services().register(RemoteCommands.class, new RemoteCommandService(dispatcher, platform.scheduler()));
        ShortcutCommands.install(this, dispatcher, platform.settings(), platform.messages(), getLogger());
    }

    /**
     * Starts the anonymous usage statistics after a successful enable - see
     * {@link TelemetryBootstrap} for the three possible outcomes (off, no
     * endpoint configured, actually sending) and docs/user/telemetry.md for
     * what a heartbeat contains.
     *
     * <p>Wrapped in its own try/catch on top of {@code TelemetryBootstrap}'s
     * own guarantees: usage statistics are the least important thing this
     * plugin does, and must never be able to affect a server that has
     * otherwise started fine.
     */
    private void startTelemetry(UniversalAdmin platform) {
        try {
            TelemetryEnvironment environment = new TelemetryEnvironment(
                    getPluginMeta().getVersion(),
                    getServer().getMinecraftVersion(),
                    TelemetryEnvironment.currentJavaMajorVersion());
            telemetry = TelemetryBootstrap.start(
                    platform.settings(),
                    platform.scheduler(),
                    getDataFolder().toPath(),
                    environment,
                    // Read on the main thread by TelemetryService - counts only,
                    // never player identities.
                    () -> new PlayerCounts(getServer().getOnlinePlayers().size(), getServer().getMaxPlayers()),
                    randomGenerator(),
                    getLogger());
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING,
                    "Anonymous usage statistics could not be started. The server is unaffected; "
                            + "set telemetry.enabled: false in config.yml to stop trying.", e);
        }
    }

    /**
     * Not {@code RandomGenerator.getDefault()}: on Java 21 it looks the
     * algorithm up through a service loader that cannot see it from inside
     * Paper, and throws.
     */
    private static RandomGenerator randomGenerator() {
        return new Random();
    }

    /**
     * Starts the background "is a newer UniversalAdmin release out" check -
     * see {@link UpdateCheckBootstrap} for the two possible outcomes (checks
     * disabled entirely vs. a periodic background timer) and {@code
     * ApplyUpdateAction} (registered in {@link #bootstrapCore}) for what
     * {@code /admin update} itself does. Wrapped the same way {@link
     * #startTelemetry} is: this must never be able to affect a server that
     * has otherwise started fine.
     */
    private void startUpdateChecks(UniversalAdmin platform) {
        try {
            updateChecks = UpdateCheckBootstrap.start(
                    platform.settings(), platform.scheduler(), updateTargets, releaseClients, updateDownloader,
                    platform.notifications(), platform.messages(), UPDATE_NOTIFY_PERMISSION, randomGenerator(),
                    getLogger());
            getServer().getPluginManager().registerEvents(new UpdateJoinListener(this,
                    () -> updateChecks == null ? null : updateChecks.service(), UPDATE_NOTIFY_PERMISSION), this);
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING,
                    "Update checks could not be started. The server is unaffected; "
                            + "set update.check-for-updates: false in config.yml to stop trying.", e);
        }
    }

    private void logStartupSummary(UniversalAdmin platform) {
        var status = platform.status();
        getLogger().info(platform.messages().get(MessageKey.of("plugin.enabled")));
        getLogger().info(() -> "Active modules (" + status.activeModules().size() + "): " + status.activeModules());
        if (!status.failedModules().isEmpty()) {
            getLogger().warning(() -> "Failed modules: " + status.failedModules());
        }
    }

}
