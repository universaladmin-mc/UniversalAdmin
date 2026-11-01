package dev.universaladmin.modules.players.action;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult.FailureReason;
import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.ActionValidator;
import dev.universaladmin.action.ValidationError;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.localization.GameNames;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffect;

/**
 * Registers every "resolve one online player and mutate/read them"
 * {@link ActionDefinition} the Players module offers - everything backed by
 * {@link OnlinePlayerAction} (see that class for why teleport and
 * inventory-contents actions aren't here). One place instead of ~17 nearly
 * identical registration blocks inline in {@code PlayersModule}.
 */
public final class PlayerActionRegistrar {

    private static final String MODULE = "players";

    private PlayerActionRegistrar() {
    }

    public static void registerAll(ActionRegistry actions, TaskScheduler scheduler) {
        actions.register(heal(scheduler));
        actions.register(feed(scheduler));
        actions.register(extinguish(scheduler));
        actions.register(clearEffects(scheduler));
        actions.register(addEffect(scheduler));
        actions.register(removeEffect(scheduler));
        actions.register(setHealth(scheduler));
        actions.register(setFood(scheduler));
        actions.register(setXp(scheduler));
        actions.register(setLevel(scheduler));
        actions.register(toggleFly(scheduler));
        actions.register(setFlySpeed(scheduler));
        actions.register(setWalkSpeed(scheduler));
        actions.register(toggleGlow(scheduler));
        actions.register(toggleGravity(scheduler));
        actions.register(toggleCollision(scheduler));
        actions.register(setGamemode(scheduler));
    }

    private static ActionDefinition<PlayerTargetInput, Void> heal(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Void>(
                PlayerActionIds.HEAL, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                    player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
                    return null;
                }).describedAs(MessageKey.of("players.result.heal"),
                        (player, in, result) -> new Object[] {player.getName(), PlayerResults.number(player.getHealth())});
        return simple(action, PlayerPermissions.HEAL, PlayerTargetInput::targetId, "Healed");
    }

    private static ActionDefinition<PlayerTargetInput, Void> feed(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Void>(
                PlayerActionIds.FEED, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    player.setFoodLevel(20);
                    player.setSaturation(20f);
                    return null;
                }).describedAs(MessageKey.of("players.result.feed"), (player, in, result) -> new Object[] {player.getName()});
        return simple(action, PlayerPermissions.FEED, PlayerTargetInput::targetId, "Fed");
    }

    private static ActionDefinition<PlayerTargetInput, Void> extinguish(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Void>(
                PlayerActionIds.EXTINGUISH, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    player.setFireTicks(0);
                    return null;
                }).describedAs(MessageKey.of("players.result.extinguish"), (player, in, result) -> new Object[] {player.getName()});
        return simple(action, PlayerPermissions.EXTINGUISH, PlayerTargetInput::targetId, "Extinguished");
    }

    private static ActionDefinition<PlayerTargetInput, Integer> clearEffects(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Integer>(
                PlayerActionIds.EFFECTS_CLEAR, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    int removed = player.getActivePotionEffects().size();
                    player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
                    return removed;
                }).describedAs(MessageKey.of("players.result.effects-cleared"),
                        (player, in, removed) -> new Object[] {player.getName(), Integer.toString(removed)});
        return simple(action, PlayerPermissions.EFFECTS_CLEAR, PlayerTargetInput::targetId, "Cleared the effects of");
    }

    private static ActionDefinition<AddEffectInput, Void> addEffect(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<AddEffectInput, Void>(
                PlayerActionIds.EFFECTS_ADD, scheduler, AddEffectInput::targetId, (player, in) -> {
                    player.addPotionEffect(new PotionEffect(in.type(), in.durationTicks(), in.amplifier(), false, true, true));
                    return null;
                }).describedAs(MessageKey.of("players.result.effect-added"), (player, in, result) -> new Object[] {
                        player.getName(), PlayerResults.effect(in.type()), Integer.toString(in.amplifier() + 1),
                        PlayerResults.duration(in.durationTicks())});
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.EFFECTS_ADD)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator((context, in) -> in.durationTicks() <= 0
                        ? Optional.of(ValidationError.of(FailureReason.VALIDATION, MessageKey.of("players.action.invalid-duration")))
                        : Optional.empty())
                .auditArgs(in -> List.of(GameNames.auditArg(GameNames.effect(in.type())), in.amplifier() + 1))
                .auditSummary(in -> "Added " + in.type().getKey().getKey() + " " + (in.amplifier() + 1) + " to " + PlayerNames.of(in.targetId()))
                .build();
    }

    private static ActionDefinition<RemoveEffectInput, Void> removeEffect(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<RemoveEffectInput, Void>(
                PlayerActionIds.EFFECTS_REMOVE, scheduler, RemoveEffectInput::targetId,
                (player, in) -> {
                    player.removePotionEffect(in.type());
                    return null;
                }).describedAs(MessageKey.of("players.result.effect-removed"),
                        (player, in, result) -> new Object[] {player.getName(), PlayerResults.effect(in.type())});
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.EFFECTS_REMOVE)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .auditArgs(in -> List.of(GameNames.auditArg(GameNames.effect(in.type()))))
                .auditSummary(in -> "Removed " + in.type().getKey().getKey() + " from " + PlayerNames.of(in.targetId()))
                .build();
    }

    private static ActionDefinition<SetDoubleValueInput, Void> setHealth(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetDoubleValueInput, Void>(
                PlayerActionIds.SET_HEALTH, scheduler, SetDoubleValueInput::targetId, (player, in) -> {
                    player.setHealth(in.value());
                    return null;
                }).describedAs(MessageKey.of("players.result.health-set"),
                        (player, in, result) -> new Object[] {player.getName(), PlayerResults.number(player.getHealth())});
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.SET_HEALTH)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator(rangeValidator(SetDoubleValueInput::value, 0.0, 2048.0))
                .auditArgs(in -> List.of(in.value()))
                .auditSummary(in -> "Set health of " + PlayerNames.of(in.targetId()) + " to " + in.value())
                .build();
    }

    private static ActionDefinition<SetIntValueInput, Void> setFood(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetIntValueInput, Void>(
                PlayerActionIds.SET_FOOD, scheduler, SetIntValueInput::targetId, (player, in) -> {
                    player.setFoodLevel(in.value());
                    return null;
                }).describedAs(MessageKey.of("players.result.food-set"),
                        (player, in, result) -> new Object[] {player.getName(), Integer.toString(player.getFoodLevel())});
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.SET_FOOD)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator(intRangeValidator(SetIntValueInput::value, 0, 20))
                .auditArgs(in -> List.of(in.value()))
                .auditSummary(in -> "Set food of " + PlayerNames.of(in.targetId()) + " to " + in.value())
                .build();
    }

    private static ActionDefinition<SetFloatValueInput, Void> setXp(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetFloatValueInput, Void>(
                PlayerActionIds.SET_XP, scheduler, SetFloatValueInput::targetId, (player, in) -> {
                    player.setExp(in.value());
                    return null;
                }).describedAs(MessageKey.of("players.result.xp-set"),
                        (player, in, result) -> new Object[] {player.getName(), Long.toString(Math.round(player.getExp() * 100.0))});
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.SET_XP)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator(rangeValidator(in -> (double) in.value(), 0.0, 1.0))
                .auditArgs(in -> List.of(Math.round(in.value() * 100) + "%"))
                .auditSummary(in -> "Set XP progress of " + PlayerNames.of(in.targetId()) + " to " + in.value())
                .build();
    }

    private static ActionDefinition<SetIntValueInput, Void> setLevel(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetIntValueInput, Void>(
                PlayerActionIds.SET_LEVEL, scheduler, SetIntValueInput::targetId, (player, in) -> {
                    player.setLevel(in.value());
                    return null;
                }).describedAs(MessageKey.of("players.result.level-set"),
                        (player, in, result) -> new Object[] {player.getName(), Integer.toString(player.getLevel())});
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.SET_LEVEL)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator(intRangeValidator(SetIntValueInput::value, 0, Integer.MAX_VALUE))
                .auditArgs(in -> List.of(in.value()))
                .auditSummary(in -> "Set level of " + PlayerNames.of(in.targetId()) + " to " + in.value())
                .build();
    }

    private static ActionDefinition<PlayerTargetInput, Boolean> toggleFly(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Boolean>(
                PlayerActionIds.FLY_TOGGLE, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    boolean next = !player.getAllowFlight();
                    player.setAllowFlight(next);
                    if (!next) {
                        player.setFlying(false);
                    }
                    return next;
                }).describedAs(MessageKey.of("players.result.fly"),
                        (player, in, enabled) -> new Object[] {player.getName(), PlayerResults.status(enabled)});
        return simple(action, PlayerPermissions.FLY_TOGGLE, PlayerTargetInput::targetId, "Toggled flight for");
    }

    private static ActionDefinition<SetFloatValueInput, Void> setFlySpeed(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetFloatValueInput, Void>(
                PlayerActionIds.FLY_SPEED, scheduler, SetFloatValueInput::targetId, (player, in) -> {
                    player.setFlySpeed(in.value());
                    return null;
                }).describedAs(MessageKey.of("players.result.fly-speed"),
                        (player, in, result) -> new Object[] {player.getName(), PlayerResults.number(in.value() * 10.0)});
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.FLY_SPEED)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator(rangeValidator(in -> (double) in.value(), -1.0, 1.0))
                .auditArgs(in -> List.of(in.value()))
                .auditSummary(in -> "Set fly speed of " + PlayerNames.of(in.targetId()) + " to " + in.value())
                .build();
    }

    private static ActionDefinition<SetFloatValueInput, Void> setWalkSpeed(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetFloatValueInput, Void>(
                PlayerActionIds.WALK_SPEED, scheduler, SetFloatValueInput::targetId, (player, in) -> {
                    player.setWalkSpeed(in.value());
                    return null;
                }).describedAs(MessageKey.of("players.result.walk-speed"),
                        (player, in, result) -> new Object[] {player.getName(), PlayerResults.number(in.value() * 10.0)});
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.WALK_SPEED)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator(rangeValidator(in -> (double) in.value(), -1.0, 1.0))
                .auditArgs(in -> List.of(in.value()))
                .auditSummary(in -> "Set walk speed of " + PlayerNames.of(in.targetId()) + " to " + in.value())
                .build();
    }

    private static ActionDefinition<PlayerTargetInput, Boolean> toggleGlow(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Boolean>(
                PlayerActionIds.GLOW_TOGGLE, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    boolean next = !player.isGlowing();
                    player.setGlowing(next);
                    return next;
                }).describedAs(MessageKey.of("players.result.glow"),
                        (player, in, enabled) -> new Object[] {player.getName(), PlayerResults.status(enabled)});
        return simple(action, PlayerPermissions.GLOW, PlayerTargetInput::targetId, "Toggled glowing for");
    }

    private static ActionDefinition<PlayerTargetInput, Boolean> toggleGravity(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Boolean>(
                PlayerActionIds.GRAVITY_TOGGLE, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    boolean next = !player.hasGravity();
                    player.setGravity(next);
                    return next;
                }).describedAs(MessageKey.of("players.result.gravity"),
                        (player, in, enabled) -> new Object[] {player.getName(), PlayerResults.status(enabled)});
        return simple(action, PlayerPermissions.GRAVITY, PlayerTargetInput::targetId, "Toggled gravity for");
    }

    private static ActionDefinition<PlayerTargetInput, Boolean> toggleCollision(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Boolean>(
                PlayerActionIds.COLLISION_TOGGLE, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    boolean next = !player.isCollidable();
                    player.setCollidable(next);
                    return next;
                }).describedAs(MessageKey.of("players.result.collision"),
                        (player, in, enabled) -> new Object[] {player.getName(), PlayerResults.status(enabled)});
        return simple(action, PlayerPermissions.COLLISION, PlayerTargetInput::targetId, "Toggled collision for");
    }

    private static ActionDefinition<SetGamemodeInput, Void> setGamemode(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetGamemodeInput, Void>(
                PlayerActionIds.GAMEMODE, scheduler, SetGamemodeInput::targetId, (player, in) -> {
                    player.setGameMode(in.gamemode());
                    return null;
                }).describedAs(MessageKey.of("players.result.gamemode"),
                        (player, in, result) -> new Object[] {player.getName(), PlayerResults.gamemode(player.getGameMode())});
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.GAMEMODE)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .auditArgs(in -> List.of(GameNames.auditArg(GameNames.gameMode(in.gamemode()))))
                .auditSummary(in -> "Set gamemode of " + PlayerNames.of(in.targetId()) + " to " + in.gamemode())
                .build();
    }

    /** {@code done} starts the English audit summary, e.g. "Healed" - the log shows the translated sentence. */
    private static <I, R> ActionDefinition<I, R> simple(
            OnlinePlayerAction<I, R> action, PermissionNode permission, Function<I, UUID> targetId, String done) {
        return ActionDefinition.builder(action)
                .permission(permission)
                .module(MODULE)
                .target(in -> Optional.of(target(targetId.apply(in))))
                .auditSummary(in -> done + " " + PlayerNames.of(targetId.apply(in)))
                .build();
    }

    private static ActionTarget target(UUID playerId) {
        return ActionTarget.player(playerId, PlayerNames.of(playerId));
    }

    private static <I> ActionValidator<I> rangeValidator(ToDoubleFunction<I> value, double min, double max) {
        return (context, input) -> {
            double v = value.applyAsDouble(input);
            return (v < min || v > max)
                    ? Optional.of(ValidationError.of(FailureReason.VALIDATION, MessageKey.of("players.action.out-of-range"), min, max))
                    : Optional.empty();
        };
    }

    private static <I> ActionValidator<I> intRangeValidator(ToIntFunction<I> value, int min, int max) {
        return (context, input) -> {
            int v = value.applyAsInt(input);
            return (v < min || v > max)
                    ? Optional.of(ValidationError.of(FailureReason.VALIDATION, MessageKey.of("players.action.out-of-range"), min, max))
                    : Optional.empty();
        };
    }
}
