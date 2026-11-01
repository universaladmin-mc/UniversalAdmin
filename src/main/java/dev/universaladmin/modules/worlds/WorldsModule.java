package dev.universaladmin.modules.worlds;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.ActionValidator;
import dev.universaladmin.action.AuditDetails;
import dev.universaladmin.action.ValidationError;
import dev.universaladmin.gui.GuiGlass;
import dev.universaladmin.localization.GameNames;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.MenuGroup;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.modules.worlds.action.SetGameRuleAction;
import dev.universaladmin.modules.worlds.action.SetGameRuleInput;
import dev.universaladmin.modules.worlds.action.SetWorldBorderCenterAction;
import dev.universaladmin.modules.worlds.action.SetWorldBorderCenterInput;
import dev.universaladmin.modules.worlds.action.SetWorldBorderDamageAction;
import dev.universaladmin.modules.worlds.action.SetWorldBorderDamageInput;
import dev.universaladmin.modules.worlds.action.SetWorldBorderSizeAction;
import dev.universaladmin.modules.worlds.action.SetWorldBorderSizeInput;
import dev.universaladmin.modules.worlds.action.SetWorldBorderWarningAction;
import dev.universaladmin.modules.worlds.action.SetWorldBorderWarningInput;
import dev.universaladmin.modules.worlds.action.SetWorldDifficultyAction;
import dev.universaladmin.modules.worlds.action.SetWorldDifficultyInput;
import dev.universaladmin.modules.worlds.action.SetWorldSpawnAction;
import dev.universaladmin.modules.worlds.action.SetWorldSpawnInput;
import dev.universaladmin.modules.worlds.action.SetWorldTimeAction;
import dev.universaladmin.modules.worlds.action.SetWorldTimeInput;
import dev.universaladmin.modules.worlds.action.SetWorldWeatherAction;
import dev.universaladmin.modules.worlds.action.SetWorldWeatherInput;
import dev.universaladmin.modules.worlds.action.TeleportAdminToWorldSpawnAction;
import dev.universaladmin.modules.worlds.command.WorldCommands;
import dev.universaladmin.modules.worlds.gui.WorldsGuiContext;
import dev.universaladmin.modules.worlds.gui.WorldsHomePage;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * World Browser/Profile, world actions (teleport-to-spawn, spawn/time/
 * weather/difficulty, border management, gamerule management). Every piece
 * of state here is already Minecraft/Bukkit-persisted ({@code level.dat}) -
 * no database table, no migration, no repository; every action is a thin,
 * permission-checked, audited wrapper around a {@link org.bukkit.World}/
 * {@link org.bukkit.WorldBorder} call. Deliberately does not implement
 * delete/clone/reset-world - see docs/user/modules/worlds.md's "Dangerous
 * Features" section; those belong in a future "Advanced World Manager"
 * extension, not the core.
 */
public final class WorldsModule implements Module {

    public static final ModuleId ID = ModuleId.core("worlds");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Worlds")
            .description("World browser/profile, spawn/time/weather/difficulty, border and gamerule management.")
            .icon(new GuiIcon("grass_block", "Worlds"))
            .menu(WorldsHomePage.ID, WorldsPermissions.VIEW)
            .menuOrder(40)
            .menuGroup(MenuGroup.SERVER)
            .color(GuiGlass.GREEN)
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onEnable(ModuleContext context) {
        TaskScheduler scheduler = context.platform().scheduler();

        WorldsPermissions.registerAll(context.platform().permissions());

        WorldInfoService infoService = new WorldInfoService();
        registerActions(context.platform().actions(), scheduler);

        WorldsGuiContext guiContext = new WorldsGuiContext(context.platform().guiFramework(), context.platform().messages(),
                scheduler, context.platform().actionExecutor(), context.platform().settings(), infoService);
        WorldsHomePage homePage = new WorldsHomePage(guiContext);
        context.platform().guiPages().register(homePage);
        context.resources().closeable(() -> context.platform().guiPages().unregister(WorldsHomePage.ID));

        WorldCommands.all(infoService).forEach(context::registerCommand);
    }

    private static void registerActions(ActionRegistry actions, TaskScheduler scheduler) {
        String module = ID.key().name();

        actions.register(ActionDefinition.builder(new TeleportAdminToWorldSpawnAction(scheduler))
                .permission(WorldsPermissions.TELEPORT)
                .module(module)
                .target(worldName -> Optional.of(ActionTarget.of("world", worldName, worldName)))
                .auditArgs(worldName -> List.of(worldName))
                .auditSummary(worldName -> "Teleported to " + worldName + "'s spawn")
                .build());

        actions.register(ActionDefinition.builder(new SetWorldSpawnAction(scheduler))
                .permission(WorldsPermissions.SPAWN)
                .module(module)
                .target(in -> Optional.of(ActionTarget.of("world", in.worldName(), in.worldName())))
                .auditArgs(in -> List.of(in.worldName()))
                .auditSummary(in -> "Set spawn for " + in.worldName())
                .build());

        actions.register(ActionDefinition.builder(new SetWorldTimeAction(scheduler))
                .permission(WorldsPermissions.TIME)
                .module(module)
                .target(in -> Optional.of(ActionTarget.of("world", in.worldName(), in.worldName())))
                .auditArgs(in -> List.of(in.worldName(), GameNames.clock(in.ticks())))
                .auditSummary(in -> "Set time for " + in.worldName() + " to " + in.ticks())
                .auditDetails((in, result) -> AuditDetails.builder().newValue(String.valueOf(in.ticks())).build())
                .build());

        actions.register(ActionDefinition.builder(new SetWorldWeatherAction(scheduler))
                .permission(WorldsPermissions.WEATHER)
                .module(module)
                .target(in -> Optional.of(ActionTarget.of("world", in.worldName(), in.worldName())))
                .auditArgs(in -> List.of(in.worldName(), "text:worlds.gui.weather." + in.state().name().toLowerCase(Locale.ROOT)))
                .auditSummary(in -> "Set weather for " + in.worldName() + " to " + in.state())
                .auditDetails((in, result) -> AuditDetails.builder().newValue(in.state().name()).build())
                .build());

        actions.register(ActionDefinition.builder(new SetWorldDifficultyAction(scheduler))
                .permission(WorldsPermissions.DIFFICULTY)
                .module(module)
                .target(in -> Optional.of(ActionTarget.of("world", in.worldName(), in.worldName())))
                .auditArgs(in -> List.of(in.worldName(), GameNames.auditArg(GameNames.difficulty(in.difficulty()))))
                .auditSummary(in -> "Set difficulty for " + in.worldName() + " to " + in.difficulty())
                .auditDetails((in, result) -> AuditDetails.builder().newValue(in.difficulty().name()).build())
                .build());

        actions.register(ActionDefinition.builder(new SetWorldBorderCenterAction(scheduler))
                .permission(WorldsPermissions.BORDER)
                .module(module)
                .target(in -> Optional.of(ActionTarget.of("world", in.worldName(), in.worldName())))
                .auditArgs(in -> List.of(in.worldName(), in.x(), in.z()))
                .auditSummary(in -> "Set border center for " + in.worldName() + " to " + in.x() + "," + in.z())
                .build());
        actions.register(ActionDefinition.builder(new SetWorldBorderSizeAction(scheduler))
                .permission(WorldsPermissions.BORDER)
                .module(module)
                .target(in -> Optional.of(ActionTarget.of("world", in.worldName(), in.worldName())))
                .auditArgs(in -> List.of(in.worldName(), in.size()))
                .auditSummary(in -> "Set border size for " + in.worldName() + " to " + in.size())
                .build());
        actions.register(ActionDefinition.builder(new SetWorldBorderDamageAction(scheduler))
                .permission(WorldsPermissions.BORDER)
                .module(module)
                .target(in -> Optional.of(ActionTarget.of("world", in.worldName(), in.worldName())))
                .auditArgs(in -> List.of(in.worldName()))
                .auditSummary(in -> "Set border damage for " + in.worldName())
                .build());
        actions.register(ActionDefinition.builder(new SetWorldBorderWarningAction(scheduler))
                .permission(WorldsPermissions.BORDER)
                .module(module)
                .target(in -> Optional.of(ActionTarget.of("world", in.worldName(), in.worldName())))
                .auditArgs(in -> List.of(in.worldName()))
                .auditSummary(in -> "Set border warning for " + in.worldName())
                .build());

        actions.register(ActionDefinition.builder(new SetGameRuleAction(scheduler))
                .permission(WorldsPermissions.GAMERULE)
                .module(module)
                .target(in -> Optional.of(ActionTarget.of("world", in.worldName(), in.worldName())))
                .validator(nonBlank(SetGameRuleInput::ruleName, "worlds.action.gamerule-name-required"))
                .auditArgs(in -> List.of(in.worldName(), in.ruleName(), in.value()))
                .auditSummary(in -> "Set gamerule " + in.ruleName() + " for " + in.worldName() + " to " + in.value())
                .auditDetails((in, result) -> AuditDetails.builder().newValue(in.value()).build())
                .build());
    }

    private static <I> ActionValidator<I> nonBlank(Function<I, String> extractor, String messageKey) {
        return (context, input) -> {
            String value = extractor.apply(input);
            return value == null || value.isBlank()
                    ? Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION, MessageKey.of(messageKey)))
                    : Optional.empty();
        };
    }
}
