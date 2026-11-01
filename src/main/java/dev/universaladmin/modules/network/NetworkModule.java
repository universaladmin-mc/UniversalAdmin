package dev.universaladmin.modules.network;

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
import dev.universaladmin.modules.network.action.NetworkActions;
import dev.universaladmin.modules.network.command.NetworkCommands;
import dev.universaladmin.modules.network.gui.NetworkBringEntry;
import dev.universaladmin.modules.network.gui.NetworkGuiContext;
import dev.universaladmin.modules.network.gui.NetworkHomePage;
import dev.universaladmin.modules.network.gui.NetworkProfileEntry;
import dev.universaladmin.modules.network.gui.NetworkSendEntry;
import dev.universaladmin.network.NetworkListener;
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkService;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The network menu and commands: every server with its players, where a
 * player is, moving players and staff between servers, and alerts on every
 * server. Only registered while network mode is on (see docs/user/network.md);
 * the cross-server parts of the other modules - bans, staff chat, reports -
 * live in those modules and use {@link NetworkService} directly.
 */
public final class NetworkModule implements Module {

    public static final ModuleId ID = ModuleId.core("network");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Network")
            .description("All servers of the proxy network, where players are, and moving players between servers.")
            .icon(new GuiIcon("ender_eye", "Network"))
            .menu(NetworkHomePage.ID, NetworkPermissions.VIEW)
            .menuOrder(15)
            .menuGroup(MenuGroup.SERVER)
            .color(GuiGlass.BLUE)
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onEnable(ModuleContext context) {
        var platform = context.platform();
        NetworkService network = platform.network();
        NetworkPermissions.all().forEach(context::registerPermission);
        String module = ID.key().name();

        context.registerAction(ActionDefinition.builder(new NetworkActions.Goto(network, platform.scheduler()))
                .permission(NetworkPermissions.GOTO).module(module)
                .auditArgs(name -> List.of(name))
                .auditSummary(name -> "Went to " + name + " (network)")
                .auditDetails((name, result) -> serverOf(result))
                .build());
        context.registerAction(ActionDefinition.builder(new NetworkActions.Bring(network, platform.scheduler()))
                .permission(NetworkPermissions.SEND).module(module)
                .target(name -> targetOf(network, name))
                .auditArgs(name -> List.of(name, network.serverName()))
                .auditSummary(name -> "Brought " + name + " to " + network.serverName())
                .auditDetails((name, result) -> serverOf(result))
                .build());
        context.registerAction(ActionDefinition.builder(new NetworkActions.Send(network, platform.scheduler()))
                .permission(NetworkPermissions.SEND).module(module)
                .target(input -> targetOf(network, input.playerName()))
                .auditArgs(input -> List.of(input.playerName(), input.server()))
                .auditSummary(input -> "Sent " + input.playerName() + " to " + input.server())
                .build());
        context.registerAction(ActionDefinition.builder(new NetworkActions.Connect(network, platform.scheduler()))
                .permission(NetworkPermissions.GOTO).module(module)
                .auditArgs(server -> List.of(server))
                .auditSummary(server -> "Connected to " + server)
                .build());
        NetworkActions.Alert alert = new NetworkActions.Alert(network, platform.messages(), platform.scheduler());
        context.registerAction(ActionDefinition.builder(alert)
                .permission(NetworkPermissions.ALERT).module(module)
                .auditArgs(message -> List.of(message))
                .auditSummary(message -> "Network alert: " + message)
                .build());
        NetworkListener remoteAlert = message -> alert.deliver(message.get("message", ""));
        network.subscribe(NetworkActions.ALERT_TOPIC, remoteAlert);
        context.resources().closeable(() -> network.unsubscribe(NetworkActions.ALERT_TOPIC, remoteAlert));

        NetworkGuiContext gui = new NetworkGuiContext(platform.guiFramework(), platform.messages(), platform.scheduler(),
                platform.actionExecutor(), network, platform.services());
        context.registerPage(new NetworkHomePage(gui));
        context.registerPlayerMenuEntry(new NetworkProfileEntry(gui));
        context.registerPlayerMenuEntry(new NetworkBringEntry(gui));
        context.registerPlayerMenuEntry(new NetworkSendEntry(gui));
        NetworkCommands.all(network).forEach(context::registerCommand);
    }

    /** The moved player as audit target - only known with a UUID (not for players on servers without UniversalAdmin). */
    private static Optional<ActionTarget> targetOf(NetworkService network, String name) {
        return network.player(name).filter(player -> player.id() != null).map(player -> ActionTarget.player(player.id(), player.name()));
    }

    /** Which server a goto/bring went through - for the audit entry. */
    private static <R> AuditDetails serverOf(ActionResult<R> result) {
        return result instanceof ActionResult.Success<R> success && success.value() instanceof NetworkPlayer player
                ? AuditDetails.builder().metadata(Map.of("server", player.server())).build()
                : AuditDetails.EMPTY;
    }
}
