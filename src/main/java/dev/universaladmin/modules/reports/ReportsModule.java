package dev.universaladmin.modules.reports;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionTarget;
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
import dev.universaladmin.modules.reports.action.CreateReportAction;
import dev.universaladmin.modules.reports.action.TeleportToReportAction;
import dev.universaladmin.modules.reports.action.UpdateReportAction;
import dev.universaladmin.modules.reports.command.ReportCommands;
import dev.universaladmin.modules.reports.gui.ReportListPage;
import dev.universaladmin.modules.reports.gui.ReportsGui;
import dev.universaladmin.modules.reports.gui.ReportsGuiContext;
import dev.universaladmin.modules.reports.gui.ReportsPlayerMenuEntry;
import dev.universaladmin.modules.reports.jdbc.JdbcReportRepository;
import dev.universaladmin.network.NetworkListener;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.status.ReportOverview;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Player reports: {@code /report <player> <reason>} for everyone, with a
 * cooldown and an open-report limit against spam; instant, clickable staff
 * notifications; a reports GUI with claim/resolve/reject and teleports; a
 * "Reports" button on every player profile; and a reminder for staff who
 * join while reports are open. Wiring only - see {@link ReportService} for
 * the rules.
 */
public final class ReportsModule implements Module {

    public static final ModuleId ID = ModuleId.core("reports");

    private static final int MAX_REASON_LENGTH = 256;

    /** How often the open-report count for displays is reloaded (also catches reports filed on other servers). */
    private static final long OPEN_REPORTS_REFRESH_TICKS = 20L * 15;

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Reports")
            .description("Players report rule-breakers with /report; staff claim and resolve reports in a GUI.")
            .icon(new GuiIcon("bell", "Reports"))
            .menu(ReportListPage.ID, ReportsPermissions.VIEW)
            .menuOrder(25)
            .menuGroup(MenuGroup.PLAYERS)
            .color(GuiGlass.ORANGE)
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onLoad(ModuleContext context) {
        context.registerMigration(new ReportsMigration());
        context.registerMigration(new ReportsServerMigration());
    }

    @Override
    public void onEnable(ModuleContext context) {
        var platform = context.platform();
        SettingsService settings = platform.settings();
        ReportsSettings.all().forEach(context::registerSetting);
        ReportsPermissions.all().forEach(context::registerPermission);

        ReportService reports = new ReportService(new JdbcReportRepository(platform.storage().dataSource(), platform.scheduler()));
        context.registerService(ReportService.class, reports);
        // Open-report count for scoreboards and placeholders: refreshed after
        // every report action and every few seconds (reports from other servers).
        AtomicInteger openReports = new AtomicInteger();
        Runnable refreshOpenReports = () -> reports.openCount().thenAccept(openReports::set);
        context.registerService(ReportOverview.class, openReports::get);
        context.subscribeAudit(event -> {
            if (ID.key().name().equals(event.module())) {
                refreshOpenReports.run();
            }
        });
        refreshOpenReports.run();
        context.resources().task(platform.plugin().getServer().getScheduler().runTaskTimer(
                platform.plugin(), refreshOpenReports, OPEN_REPORTS_REFRESH_TICKS, OPEN_REPORTS_REFRESH_TICKS));

        String module = ID.key().name();
        CreateReportAction create = new CreateReportAction(
                reports, settings, platform.notifications(), platform.messages(), platform.scheduler(), platform.network());
        // Network mode: reports filed on the other servers alert the staff here too.
        NetworkListener remoteReports = message -> create.announce(message.get("reporter", "?"), message.get("target", "?"),
                message.get("reason", ""), Long.parseLong(message.get("id", "0")), message.source());
        platform.network().subscribe(CreateReportAction.NETWORK_TOPIC, remoteReports);
        context.resources().closeable(() -> platform.network().unsubscribe(CreateReportAction.NETWORK_TOPIC, remoteReports));
        context.registerAction(ActionDefinition.builder(create)
                .permission(ReportsPermissions.CREATE)
                .module(module)
                .target(input -> Optional.of(ActionTarget.player(input.targetId(), input.targetName())))
                .forbidSelfTarget()
                .validator(reasonValidator(settings))
                .auditArgs(input -> List.of(input.targetName(), input.reason()))
                .auditSummary(input -> "Reported " + input.targetName() + ": " + input.reason())
                .auditDetails((input, result) -> result instanceof ActionResult.Success<?> success
                        && success.value() instanceof Report report
                        ? AuditDetails.builder().correlationId("report#" + report.id()).build()
                        : AuditDetails.EMPTY)
                .build());
        for (UpdateReportAction.Kind kind : UpdateReportAction.Kind.values()) {
            context.registerAction(ActionDefinition.builder(new UpdateReportAction(
                            kind, reports, settings, platform.notifications(), platform.messages(), platform.scheduler()))
                    .permission(ReportsPermissions.MANAGE)
                    .module(module)
                    .target(input -> Optional.of(ActionTarget.of("report", String.valueOf(input.reportId()), "#" + input.reportId())))
                    .auditArgs(input -> List.of(input.note() == null || input.note().isBlank() ? "-" : input.note()))
                    .auditSummary(input -> kind.name().charAt(0) + kind.name().substring(1).toLowerCase(java.util.Locale.ROOT)
                            + " report #" + input.reportId() + (input.note() == null || input.note().isBlank() ? "" : ": " + input.note()))
                    .auditDetails((input, result) -> AuditDetails.builder().correlationId("report#" + input.reportId()).build())
                    .build());
        }
        context.registerAction(ActionDefinition.builder(new TeleportToReportAction(reports, platform.scheduler(), platform.network()))
                .permission(ReportsPermissions.MANAGE)
                .module(module)
                .notAudited()
                .build());

        ReportsGuiContext guiContext = new ReportsGuiContext(platform.guiFramework(), platform.messages(), platform.scheduler(),
                platform.actionExecutor(), reports, platform.playerMenu());
        context.registerPage(new ReportListPage(guiContext));
        context.registerPlayerMenuEntry(new ReportsPlayerMenuEntry(guiContext));
        ReportCommands.all(reports, new ReportsGui(guiContext)).forEach(context::registerCommand);

        context.registerListener(new ReportReminderListener(reports, settings, platform.messages(), platform.scheduler()));
    }

    /** Non-blank, at least {@code reports.min-reason-length}, at most {@value #MAX_REASON_LENGTH} characters. */
    private static ActionValidator<CreateReportAction.Input> reasonValidator(SettingsService settings) {
        return (context, input) -> {
            String reason = input.reason() == null ? "" : input.reason().trim();
            int minimum = settings.get(ReportsSettings.MIN_REASON_LENGTH);
            if (reason.length() < Math.max(1, minimum)) {
                return Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION,
                        MessageKey.of("reports.action.reason-too-short"), Math.max(1, minimum)));
            }
            if (reason.length() > MAX_REASON_LENGTH) {
                return Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION,
                        MessageKey.of("reports.action.reason-too-long"), MAX_REASON_LENGTH));
            }
            return Optional.empty();
        };
    }
}
