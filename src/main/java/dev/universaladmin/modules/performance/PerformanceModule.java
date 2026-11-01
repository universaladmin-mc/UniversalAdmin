package dev.universaladmin.modules.performance;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult;
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
import dev.universaladmin.modules.performance.action.ClearEntitiesAction;
import dev.universaladmin.modules.performance.command.PerformanceCommands;
import dev.universaladmin.modules.performance.gui.PerformanceEntityByTypePage;
import dev.universaladmin.modules.performance.gui.PerformanceEntityOverviewPage;
import dev.universaladmin.modules.performance.gui.PerformanceGuiContext;
import dev.universaladmin.modules.performance.gui.PerformanceHomePage;
import dev.universaladmin.modules.performance.gui.PerformanceWorldsPage;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Monitoring and diagnostics: a live TPS/MSPT/memory/world/entity dashboard,
 * a short in-memory history, staff alerts on threshold breaches, and a
 * deliberately narrow, confirmed, audited Entity Clear - never a "lag
 * cleaner" that acts on its own. Everything here reads Paper/JVM APIs
 * already in use elsewhere in the codebase ({@code Server#getTPS()}/{@code
 * getAverageTickTime()}, {@code World#getChunkCount()}/{@code getEntities()},
 * {@code Runtime}), sampled on a timer rather than recomputed per GUI
 * render - see {@link PerformanceSamplingService}. See
 * docs/user/modules/performance.md.
 */
public final class PerformanceModule implements Module {

    public static final ModuleId ID = ModuleId.core("performance");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Performance")
            .description("Performance monitoring: TPS/MSPT/memory dashboard, per-world and per-entity-type breakdown, staff alerts, confirmed Entity Clear.")
            .icon(new GuiIcon("comparator", "Performance"))
            .menu(PerformanceHomePage.ID, PerformancePermissions.VIEW)
            .menuOrder(60)
            .menuGroup(MenuGroup.SERVER)
            .color(GuiGlass.YELLOW)
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onEnable(ModuleContext context) {
        PerformanceSettings.registerAll(context.platform().settingRegistry());
        PerformancePermissions.registerAll(context.platform().permissions());

        SettingsService settings = context.platform().settings();
        TaskScheduler scheduler = context.platform().scheduler();
        Plugin plugin = context.platform().plugin();

        PerformanceSamplingService samplingService = new PerformanceSamplingService(
                context.platform()::status, context.platform().notifications(), context.platform().messages(),
                settings, context.logger());
        // Sampled once synchronously here (onEnable already runs on the main
        // thread during plugin startup, see Module's javadoc) so the
        // dashboard has real data on the very first open, instead of an
        // empty/zeroed snapshot until the first scheduled tick below fires.
        samplingService.sample();
        context.platform().services().register(PerformanceSamplingService.class, samplingService);

        scheduleSampling(context, plugin, settings, samplingService);
        registerActions(context.platform().actions(), scheduler, settings, context.logger());
        registerGui(context, samplingService);
        PerformanceCommands.all(samplingService).forEach(context::registerCommand);
    }

    private void scheduleSampling(
            ModuleContext context, Plugin plugin, SettingsService settings, PerformanceSamplingService samplingService) {
        long intervalTicks = Math.max(1, settings.get(CoreSettings.PERFORMANCE_REFRESH_INTERVAL).toMillis() / 50L);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, samplingService::sample, intervalTicks, intervalTicks);
        context.resources().task(task);
    }

    /** Package-private (not {@code private}) so {@code PerformanceActionsWiringTest} can exercise it without a full {@link ModuleContext} - same reasoning as {@code ModerationModule#registerActions}. */
    static void registerActions(ActionRegistry actions, TaskScheduler scheduler, SettingsService settings, Logger logger) {
        actions.register(ActionDefinition.builder(
                        new ClearEntitiesAction(scheduler, settings, logger))
                .permission(PerformancePermissions.ENTITY_CLEAR)
                .module(ID.key().name())
                .validator((actionContext, input) -> input.entityTypes().isEmpty()
                        ? Optional.of(ValidationError.of(
                                ActionResult.FailureReason.VALIDATION, MessageKey.of("performance.action.no-types-selected")))
                        : Optional.empty())
                .auditArgs(input -> List.of(input.entityTypes().stream().map(type -> type.name().toLowerCase(Locale.ROOT)).sorted()
                        .collect(Collectors.joining(", ")), input.worldName() == null ? "text:performance.audit.all-worlds" : input.worldName()))
                .auditSummary(input -> "Cleared entities ("
                        + input.entityTypes().stream().map(Enum::name).sorted().collect(Collectors.joining(", "))
                        + ") in " + (input.worldName() == null ? "all worlds" : input.worldName()))
                .auditDetails((input, result) -> AuditDetails.builder()
                        .newValue(result instanceof ActionResult.Success<Integer> success ? "removed=" + success.value() : null)
                        .world(input.worldName())
                        // A single delimited String, not a List: AuditDetails
                        // metadata only accepts String/Number/Boolean/null
                        // (see MetadataJson.encodeValue) - a List value threw
                        // and silently dropped the audit entry for every
                        // entity-clear action.
                        .metadata(Map.of("types", input.entityTypes().stream().map(Enum::name).sorted().collect(Collectors.joining(", "))))
                        .build())
                .build());
    }

    private void registerGui(ModuleContext context, PerformanceSamplingService samplingService) {
        PerformanceGuiContext guiContext = new PerformanceGuiContext(
                context.platform().guiFramework(), context.platform().messages(), context.platform().scheduler(),
                context.platform().actionExecutor(), context.platform().settings(), samplingService);

        context.platform().guiPages().register(new PerformanceHomePage(guiContext));
        context.resources().closeable(() -> context.platform().guiPages().unregister(PerformanceHomePage.ID));
        context.platform().guiPages().register(new PerformanceWorldsPage(guiContext));
        context.resources().closeable(() -> context.platform().guiPages().unregister(PerformanceWorldsPage.ID));
        context.platform().guiPages().register(new PerformanceEntityOverviewPage(guiContext));
        context.resources().closeable(() -> context.platform().guiPages().unregister(PerformanceEntityOverviewPage.ID));
        context.platform().guiPages().register(new PerformanceEntityByTypePage(guiContext));
        context.resources().closeable(() -> context.platform().guiPages().unregister(PerformanceEntityByTypePage.ID));
    }
}
