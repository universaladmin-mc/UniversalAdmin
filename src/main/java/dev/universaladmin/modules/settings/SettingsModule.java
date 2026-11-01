package dev.universaladmin.modules.settings;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.AuditDetails;
import dev.universaladmin.gui.GuiGlass;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.MenuGroup;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.modules.settings.command.SettingsCommand;
import dev.universaladmin.modules.settings.gui.SettingsGuiContext;
import dev.universaladmin.modules.settings.gui.SettingsHomePage;
import dev.universaladmin.settings.SetSettingAction;
import dev.universaladmin.settings.SettingChange;
import java.util.List;
import java.util.Optional;

/**
 * The in-game editor over the core {@link dev.universaladmin.settings.SettingsService}:
 * a settings GUI grouped by section and {@code /admin settings}. Every
 * registered setting - core, built-in module or extension - is editable,
 * except sensitive ones (the database password), which stay file-only.
 * Changes are validated, written to {@code config.yml} with its comments
 * intact, applied live where possible and audited.
 */
public final class SettingsModule implements Module {

    public static final ModuleId ID = ModuleId.core("settings");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Settings")
            .description("View and change every UniversalAdmin setting in-game.")
            .icon(new GuiIcon("anvil", "Settings"))
            .menu(SettingsHomePage.ID, SettingsPermissions.MANAGE)
            .menuOrder(90)
            .menuGroup(MenuGroup.SYSTEM)
            .color(GuiGlass.GRAY)
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onEnable(ModuleContext context) {
        context.registerPermission(SettingsPermissions.manage());

        context.registerAction(ActionDefinition.builder(new SetSettingAction(
                        context.platform().settings(), context.platform().settingRegistry(), context.platform().scheduler()))
                .permission(SettingsPermissions.MANAGE)
                .module(ID.key().name())
                .target(input -> Optional.of(ActionTarget.of("setting", input.path(), input.path())))
                .auditArgs(input -> List.of(input.path()))
                .auditSummary(input -> input.value() == null ? "Reset setting " + input.path() : "Changed setting " + input.path())
                .auditFailures()
                .auditDetails((input, result) -> result instanceof ActionResult.Success<SettingChange> success
                        ? AuditDetails.builder().oldValue(success.value().oldValue()).newValue(success.value().newValue()).build()
                        : AuditDetails.EMPTY)
                .build());

        SettingsGuiContext gui = new SettingsGuiContext(context.platform().guiFramework(), context.platform().messages(),
                context.platform().scheduler(), context.platform().actionExecutor(), context.platform().settings(),
                context.platform().settingRegistry());
        context.registerPage(new SettingsHomePage(gui));
        context.registerCommand(new SettingsCommand(gui, context.platform().guiPages()));
    }
}
