package dev.universaladmin.action;

import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.audit.AuditEventType;
import dev.universaladmin.audit.AuditService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The single path every frontend (GUI, command, future web API, extensions)
 * runs an {@link Action} through - see docs/architecture/actions.md. Wraps
 * the {@link ActionDefinition} registered under an {@link ActionId} with:
 *
 * <ol>
 *   <li>authorization (permission, feature-enabled, self-target)
 *   <li>input validation ({@link ActionValidator})
 *   <li>the action itself
 *   <li>an audit hook via {@link AuditService} - success always (unless opted
 *       out), failure only if the action opted in via {@link ActionDefinition.Builder#auditFailures()}
 *   <li>{@link ActionEvent}s before and after, for anything observing the pipeline
 * </ol>
 *
 * None of this is duplicated per frontend, and a frontend calling {@link Action#execute}
 * directly instead of going through here is the one thing docs/architecture/actions.md
 * calls out as not following the pattern.
 */
public final class ActionExecutor {

    /**
     * The audit metadata key prefix of the values for the translated sentence:
     * {@code arg.0} is {@code {2}} in the template - see
     * {@link ActionDefinition.Builder#auditArgs}.
     *
     * @since 1.8
     */
    public static final String AUDIT_ARG_PREFIX = "arg.";

    private final ActionRegistry registry;
    private final AuditService auditService;
    private final Logger logger;
    private final List<ActionEventListener> listeners = new CopyOnWriteArrayList<>();

    public ActionExecutor(ActionRegistry registry, AuditService auditService, Logger logger) {
        this.registry = registry;
        this.auditService = auditService;
        this.logger = logger;
    }

    /** Extension/web hook for observing action execution - see {@link ActionEvent}. */
    public void subscribe(ActionEventListener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(ActionEventListener listener) {
        listeners.remove(listener);
    }

    public <I, R> CompletableFuture<ActionResult<R>> execute(ActionRequest<I> request) {
        return execute(request.id(), request.context(), request.input());
    }

    public <I, R> CompletableFuture<ActionResult<R>> execute(ActionId id, ActionContext context, I input) {
        publish(new ActionEvent.Executing<>(id, context, input));

        Optional<ActionDefinition<I, R>> definitionLookup = registry.get(id);
        if (definitionLookup.isEmpty()) {
            // No ActionDefinition means no permission/module/auditFailures to
            // consult - an unknown id is never audited, only logged via the event.
            ActionResult.Failure<R> failure = failure(
                    ActionResult.FailureReason.NOT_FOUND, "No action registered for " + id);
            publish(new ActionEvent.Failed<>(id, context, input, failure));
            return CompletableFuture.completedFuture(failure);
        }
        ActionDefinition<I, R> definition = definitionLookup.get();

        Optional<ActionResult.Failure<R>> preFailure = preValidate(definition, context, input);
        if (preFailure.isPresent()) {
            return finish(definition, context, input, preFailure.get());
        }

        CompletableFuture<ActionResult<R>> actionResult;
        try {
            actionResult = definition.action().execute(context, input).thenApply(result -> logInternalError(definition, result));
        } catch (RuntimeException e) {
            actionResult = CompletableFuture.completedFuture(handleException(definition, e));
        }
        return actionResult
                .exceptionally(throwable -> handleException(definition, throwable))
                .thenCompose(result -> finish(definition, context, input, result));
    }

    /**
     * Runs the {@link ReversibleAction#undo} of a previously-executed action,
     * if it registered as one. No undo *history* yet (see {@link ReversibleAction});
     * the caller is responsible for remembering what to undo.
     */
    public <I, R> CompletableFuture<ActionResult<Void>> undo(ActionId id, ActionContext context, I input, R result) {
        Optional<ActionDefinition<I, R>> definitionLookup = registry.get(id);
        if (definitionLookup.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, "No action registered for " + id));
        }
        ActionDefinition<I, R> definition = definitionLookup.get();
        if (definition.permission() != null && !context.actor().hasPermission(definition.permission())) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionResult.FailureReason.NOT_PERMITTED,
                    "Actor " + context.actor().displayName() + " is missing permission " + definition.permission() + " to undo " + id));
        }
        if (!(definition.action() instanceof ReversibleAction<I, R> reversible)) {
            return CompletableFuture.completedFuture(
                    ActionResult.failure(ActionResult.FailureReason.VALIDATION, "Action " + id + " is not reversible"));
        }
        try {
            return reversible.undo(context, input, result);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Action " + id + " threw while undoing", e);
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionResult.FailureReason.INTERNAL_ERROR, "Unexpected error undoing " + id));
        }
    }

    private <I, R> Optional<ActionResult.Failure<R>> preValidate(ActionDefinition<I, R> definition, ActionContext context, I input) {
        if (definition.permission() != null && !context.actor().hasPermission(definition.permission())) {
            return Optional.of(failure(ActionResult.FailureReason.NOT_PERMITTED,
                    "Actor " + context.actor().displayName() + " is missing permission " + definition.permission()));
        }
        if (!definition.enabledCheck().getAsBoolean()) {
            return Optional.of(failure(
                    ActionResult.FailureReason.FEATURE_DISABLED, "Action " + definition.id() + " is currently disabled"));
        }
        Optional<ActionTarget> target = definition.targetExtractor().apply(input);
        if (definition.selfTargetPolicy() == SelfTargetPolicy.FORBIDDEN && isSelfTarget(context.actor(), target)) {
            return Optional.of(failure(ActionResult.FailureReason.VALIDATION, "Cannot target yourself with " + definition.id()));
        }
        Optional<ValidationError> validationError = definition.validator().validate(context, input);
        if (validationError.isPresent()) {
            ValidationError error = validationError.get();
            return Optional.of(new ActionResult.Failure<>(
                    error.reason(), error.message(), error.messageKey(), error.messageArgs(), Map.of()));
        }
        return Optional.empty();
    }

    private boolean isSelfTarget(Actor actor, Optional<ActionTarget> target) {
        if (actor.type() != ActorType.PLAYER || actor.playerId() == null) {
            return false;
        }
        return target
                .filter(t -> ActionTarget.PLAYER_TYPE.equals(t.type()))
                .map(t -> t.id().equals(actor.playerId().toString()))
                .orElse(false);
    }

    /** Common tail for every outcome (pre-validation failure, action success/failure): audit, then publish, then return. */
    private <I, R> CompletableFuture<ActionResult<R>> finish(
            ActionDefinition<I, R> definition, ActionContext context, I input, ActionResult<R> result) {
        return auditIfEnabled(definition, context, input, result)
                .handle((ignored, auditError) -> {
                    if (auditError != null) {
                        logger.log(Level.WARNING, "Failed to record audit event for " + definition.id(), auditError);
                    }
                    publish(result instanceof ActionResult.Success<R> success
                            ? new ActionEvent.Executed<>(definition.id(), context, input, success)
                            : new ActionEvent.Failed<>(definition.id(), context, input, (ActionResult.Failure<R>) result));
                    return result;
                });
    }

    private <I, R> CompletableFuture<Void> auditIfEnabled(
            ActionDefinition<I, R> definition, ActionContext context, I input, ActionResult<R> result) {
        boolean success = result instanceof ActionResult.Success<R>;
        boolean shouldAudit = success ? definition.audited() : definition.auditFailures();
        if (!shouldAudit) {
            return CompletableFuture.completedFuture(null);
        }
        AuditEventType type = AuditEventType.of(definition.id().key().namespace(), definition.id().key().name());
        AuditDetails details = definition.auditDetails().apply(input, result);
        AuditEvent.Builder entry = AuditEvent.builder(type, context.actor(), context.source(), definition.auditSummary().apply(input))
                .module(definition.module())
                .success(success)
                .reason(details.reason() != null ? details.reason() : defaultReason(result))
                .oldValue(details.oldValue())
                .newValue(details.newValue())
                .world(details.world())
                .position(details.position())
                .metadata(withArgs(details.metadata(), definition.auditArgs().apply(input)))
                .correlationId(details.correlationId());
        definition.targetExtractor().apply(input).ifPresent(entry::target);
        return auditService.record(entry.build());
    }

    /** The values for the translated sentence as {@code arg.0}, {@code arg.1}, ... - see {@link ActionDefinition.Builder#auditArgs}. */
    private static Map<String, Object> withArgs(Map<String, Object> metadata, List<?> args) {
        if (args == null || args.isEmpty()) {
            return metadata;
        }
        Map<String, Object> merged = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        for (int i = 0; i < args.size(); i++) {
            merged.put(AUDIT_ARG_PREFIX + i, args.get(i) == null ? "" : String.valueOf(args.get(i)));
        }
        return merged;
    }

    private <R> String defaultReason(ActionResult<R> result) {
        if (!(result instanceof ActionResult.Failure<R> failure)) {
            return null;
        }
        if (failure.message() != null) {
            return failure.message();
        }
        return failure.messageKey() != null ? failure.messageKey().value() : failure.reason().name();
    }

    /**
     * An internal error the action reported itself (rather than threw) goes to
     * the server log: players and the console are only told "the details are in
     * the server log". Thrown exceptions are logged by {@link #handleException}.
     */
    private <I, R> ActionResult<R> logInternalError(ActionDefinition<I, R> definition, ActionResult<R> result) {
        if (result instanceof ActionResult.Failure<R> failure && failure.reason() == ActionResult.FailureReason.INTERNAL_ERROR) {
            logger.warning(() -> "Action " + definition.id() + " failed: "
                    + (failure.message() == null ? "no details given" : failure.message()));
        }
        return result;
    }

    private <I, R> ActionResult.Failure<R> handleException(ActionDefinition<I, R> definition, Throwable throwable) {
        logger.log(Level.SEVERE, "Action " + definition.id() + " threw during execution", throwable);
        return failure(ActionResult.FailureReason.INTERNAL_ERROR, "Unexpected error executing " + definition.id());
    }

    private <R> ActionResult.Failure<R> failure(ActionResult.FailureReason reason, String message) {
        return new ActionResult.Failure<>(reason, message, null, List.of(), Map.of());
    }

    private void publish(ActionEvent event) {
        for (ActionEventListener listener : listeners) {
            try {
                listener.onActionEvent(event);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Action event listener threw", e);
            }
        }
    }
}
