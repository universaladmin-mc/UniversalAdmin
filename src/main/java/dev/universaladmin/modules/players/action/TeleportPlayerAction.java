package dev.universaladmin.modules.players.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActorType;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * All six teleport variants (see {@link TeleportKind}) behind one action/
 * permission - resolves the 1-2 needed {@link Player}s on the main thread
 * (required for both entity access and {@link Player#teleportAsync}) and
 * fails {@code VALIDATION}/{@code NOT_FOUND} instead of throwing for every
 * "missing" case (offline target/reference, no respawn location, unknown
 * world).
 */
public final class TeleportPlayerAction implements Action<TeleportInput, Void> {

    public static final ActionId ID = PlayerActionIds.TELEPORT;

    private final TaskScheduler scheduler;

    public TeleportPlayerAction(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, TeleportInput input) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> resolveAndTeleport(context, input, future));
        return future;
    }

    private void resolveAndTeleport(ActionContext context, TeleportInput input, CompletableFuture<ActionResult<Void>> future) {
        Player target = Bukkit.getPlayer(input.targetId());
        if (target == null) {
            future.complete(offline());
            return;
        }
        switch (input.kind()) {
            case ADMIN_TO_PLAYER -> withActor(context, future, admin -> teleport(admin, target.getLocation(), future,
                    "players.result.teleport.to-player", target.getName()));
            case BRING_TO_ADMIN -> withActor(context, future, admin -> teleport(target, admin.getLocation(), future,
                    "players.result.teleport.bring", target.getName()));
            case PLAYER_TO_PLAYER -> {
                Player reference = input.referenceId() != null ? Bukkit.getPlayer(input.referenceId()) : null;
                if (reference == null) {
                    future.complete(offline());
                    return;
                }
                teleport(target, reference.getLocation(), future,
                        "players.result.teleport.player-to-player", target.getName(), reference.getName());
            }
            case WORLD_SPAWN -> teleport(target, target.getWorld().getSpawnLocation(), future,
                    "players.result.teleport.spawn", target.getName());
            case BED_RESPAWN -> {
                Location respawn = target.getRespawnLocation();
                if (respawn == null) {
                    future.complete(ActionResult.failure(
                            ActionResult.FailureReason.VALIDATION, MessageKey.of("players.action.no-respawn-location")));
                    return;
                }
                teleport(target, respawn, future, "players.result.teleport.respawn", target.getName());
            }
            case COORDINATES -> {
                World world = input.worldName() != null ? Bukkit.getWorld(input.worldName()) : target.getWorld();
                if (world == null) {
                    future.complete(ActionResult.failure(
                            ActionResult.FailureReason.VALIDATION, MessageKey.of("players.action.unknown-world"), input.worldName()));
                    return;
                }
                teleport(target, new Location(world, input.x(), input.y(), input.z()), future,
                        "players.result.teleport.coordinates", target.getName());
            }
        }
    }

    private void withActor(ActionContext context, CompletableFuture<ActionResult<Void>> future, java.util.function.Consumer<Player> onActor) {
        Player admin = context.actor().type() == ActorType.PLAYER && context.actor().playerId() != null
                ? Bukkit.getPlayer(context.actor().playerId())
                : null;
        if (admin == null) {
            future.complete(offline());
            return;
        }
        onActor.accept(admin);
    }

    /** @param messageKey the success message; its arguments are {@code names} followed by the destination ("world 120 64 -30") */
    private void teleport(Player player, Location destination, CompletableFuture<ActionResult<Void>> future,
            String messageKey, String... names) {
        Object[] args = new Object[names.length + 1];
        System.arraycopy(names, 0, args, 0, names.length);
        args[names.length] = PlayerResults.location(destination);
        player.teleportAsync(destination).thenAccept(success -> future.complete(Boolean.TRUE.equals(success)
                ? ActionResult.success(null, MessageKey.of(messageKey), args)
                : ActionResult.failure(ActionResult.FailureReason.INTERNAL_ERROR, "Teleport did not complete")));
    }

    private ActionResult<Void> offline() {
        return ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("players.action.offline"));
    }
}
