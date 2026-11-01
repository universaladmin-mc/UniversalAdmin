package dev.universaladmin.modules.nick;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.AuditDetails;
import dev.universaladmin.action.ValidationError;
import dev.universaladmin.core.PlayerDirectory;
import dev.universaladmin.gui.GuiGlass;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.MenuGroup;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.modules.nick.action.NickActions;
import dev.universaladmin.modules.nick.command.NickCommands;
import dev.universaladmin.modules.nick.gui.NickHomePage;
import dev.universaladmin.modules.nick.gui.NickProfileEntry;
import dev.universaladmin.modules.nick.jdbc.JdbcNickRepository;
import dev.universaladmin.network.NetworkListener;
import dev.universaladmin.status.Nicks;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * The nick system: staff play under another name and skin - in chat, the
 * tab list and above their head - while UniversalAdmin keeps their real
 * identity in its records and shows it to staff who may see it. A nick
 * survives rejoining and follows its player through a network. See
 * docs/architecture/decisions/0025-nick-system.md.
 */
public final class NickModule implements Module {

    public static final ModuleId ID = ModuleId.core("nick");

    /** How often the stored nicks are reloaded in network mode - for the nicks of players on other servers. */
    private static final long RELOAD_TICKS = 20L * 60;

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Nick")
            .description("Staff play under another name and skin; the real name stays visible to staff who may see it.")
            .icon(new GuiIcon("name_tag", "Nick"))
            .menu(NickHomePage.ID, NickPermissions.USE)
            .menuOrder(38)
            .menuGroup(MenuGroup.PLAYERS)
            .color(GuiGlass.MAGENTA)
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onEnable(ModuleContext context) {
        var platform = context.platform();
        NickSettings.all().forEach(context::registerSetting);
        NickPermissions.all().forEach(context::registerPermission);
        context.registerMigration(new NickMigration());

        NickRuntimeState state = new NickRuntimeState();
        NickView view = new NickView(state, platform.network());
        NickService service = new NickService(new JdbcNickRepository(platform.storage().dataSource(), platform.scheduler()),
                state, platform.network(), platform.settings(), platform.messages(), platform.scheduler(), platform.plugin(),
                context.logger());
        context.registerService(Nicks.class, view);
        String module = ID.key().name();

        context.registerAction(ActionDefinition.builder(new NickActions.SetNick(service, view,
                        new PaperSkinSource(platform.scheduler(), platform.version(), context.logger()),
                        platform.settings(), platform.network(), platform.scheduler(),
                        () -> platform.services().get(PlayerDirectory.class), new Random()))
                .permission(NickPermissions.USE)
                .module(module)
                .validator((ctx, input) -> {
                    if (input.name() != null && !NickNames.valid(input.name())) {
                        return Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION,
                                MessageKey.of("nick.invalid-name"), input.name()));
                    }
                    return othersAllowed(ctx.actor(), input.targetId());
                })
                .target(input -> Optional.of(ActionTarget.player(input.targetId(), input.targetName())))
                .auditArgs(input -> List.of(input.name() == null ? "text:nick.audit.random" : input.name()))
                .auditSummary(input -> input.name() == null ? "Took a random nick" : "Took the nick " + input.name())
                .auditDetails((input, result) -> result instanceof ActionResult.Success<NickRecord> success
                        ? AuditDetails.builder().newValue(success.value().name())
                                .metadata(success.value().skinOf() == null ? Map.of() : Map.of("skin", success.value().skinOf()))
                                .build()
                        : AuditDetails.EMPTY)
                .build());
        context.registerAction(ActionDefinition.builder(new NickActions.ClearNick(service, platform.messages(), platform.scheduler()))
                .permission(NickPermissions.USE)
                .module(module)
                .validator((ctx, target) -> othersAllowed(ctx.actor(), target.id()))
                .target(target -> Optional.of(ActionTarget.player(target.id(), target.name())))
                .auditArgs(target -> List.of(target.name()))
                .auditSummary(target -> "Removed the nick of " + target.name())
                .auditDetails((target, result) -> result instanceof ActionResult.Success<NickRecord> success
                        ? AuditDetails.builder().oldValue(success.value().name()).build()
                        : AuditDetails.EMPTY)
                .build());

        context.registerListener(new NickListener(service, state, view, platform.network(), platform.actionExecutor(),
                platform.messages(), context.logger()));
        NetworkListener remoteChange = service::onNetworkChange;
        platform.network().subscribe(NickService.NETWORK_TOPIC, remoteChange);
        context.resources().closeable(() -> platform.network().unsubscribe(NickService.NETWORK_TOPIC, remoteChange));
        if (platform.network().enabled()) {
            context.resources().task(platform.plugin().getServer().getScheduler().runTaskTimer(
                    platform.plugin(), service::reload, RELOAD_TICKS, RELOAD_TICKS));
        }

        context.registerPage(new NickHomePage(platform.guiFramework(), platform.messages(), view, platform.actionExecutor(),
                platform.scheduler()));
        context.registerPlayerMenuEntry(new NickProfileEntry(view, platform.guiFramework(), platform.messages(),
                platform.actionExecutor(), platform.scheduler()));
        NickCommands.all(view, platform.settings()).forEach(context::registerCommand);
        context.registerStatusToggle(new NickStatusToggle(view, platform));

        // Players who are online while the module starts (a reload of UniversalAdmin) get their nick back -
        // a tick later, once the migrations of this start have run. When it stops, everyone is shown as
        // themselves until it runs again.
        context.resources().task(platform.plugin().getServer().getScheduler().runTask(platform.plugin(), service::wearStored));
        context.resources().closeable(service::takeAllOff);
    }

    /** Someone else's nick needs {@code universaladmin.nick.others}; the console and system tasks have every permission. */
    private static Optional<ValidationError> othersAllowed(Actor actor, UUID targetId) {
        if (targetId.equals(actor.playerId()) || actor.hasPermission(NickPermissions.OTHERS)) {
            return Optional.empty();
        }
        return Optional.of(ValidationError.of(ActionResult.FailureReason.NOT_PERMITTED, MessageKey.of("nick.others-denied")));
    }
}
