package dev.universaladmin.action;

import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Everything {@link ActionExecutor} needs to authorize/validate/audit an
 * {@link Action} - the unit modules actually register (see
 * {@link ActionRegistry#register}), not the bare {@link Action}. See
 * docs/architecture/actions.md.
 *
 * @param action          the wrapped business logic
 * @param permission      required to run this action, or {@code null} if none is required
 * @param validator       cheap synchronous input validation, run before {@code action}
 * @param targetExtractor derives a generic {@link ActionTarget} from the input, for self-target checks and the audit entry's target
 * @param selfTargetPolicy whether the actor may target themselves
 * @param enabledCheck    a fine-grained "is this feature currently enabled" gate, independent of the owning module being enabled at all
 * @param module          the owning module's key (e.g. {@code "players"}), or {@code null} for core/non-module actions - carried onto the audit entry
 * @param audited         whether a successful execution is recorded via {@link dev.universaladmin.audit.AuditService}
 * @param auditFailures   whether a failed execution (denied, invalid, or thrown) is also recorded - useful for security-relevant actions
 * @param auditSummary    builds the audit summary text from the input
 * @param auditDetails    builds the audit entry's reason/old-new-value/world-position/metadata/correlation id from input and result - see {@link AuditDetails}
 * @param auditArgs       the values the audit log's translated sentence needs besides actor and target - see {@link Builder#auditArgs}
 */
public record ActionDefinition<I, R>(
        Action<I, R> action,
        PermissionNode permission,
        ActionValidator<I> validator,
        Function<I, Optional<ActionTarget>> targetExtractor,
        SelfTargetPolicy selfTargetPolicy,
        BooleanSupplier enabledCheck,
        String module,
        boolean audited,
        boolean auditFailures,
        Function<I, String> auditSummary,
        BiFunction<I, ActionResult<R>, AuditDetails> auditDetails,
        Function<I, List<?>> auditArgs) {

    /** The shape before API 1.8 - no values for the translated sentence. */
    public ActionDefinition(
            Action<I, R> action, PermissionNode permission, ActionValidator<I> validator,
            Function<I, Optional<ActionTarget>> targetExtractor, SelfTargetPolicy selfTargetPolicy, BooleanSupplier enabledCheck,
            String module, boolean audited, boolean auditFailures, Function<I, String> auditSummary,
            BiFunction<I, ActionResult<R>, AuditDetails> auditDetails) {
        this(action, permission, validator, targetExtractor, selfTargetPolicy, enabledCheck, module, audited, auditFailures,
                auditSummary, auditDetails, input -> List.of());
    }

    public ActionId id() {
        return action.id();
    }

    public static <I, R> Builder<I, R> builder(Action<I, R> action) {
        return new Builder<>(action);
    }

    /**
     * Builds an {@link ActionDefinition}. Same purpose as {@code ModuleDescriptor.Builder}:
     * a readable way to construct a record with several optional fields,
     * with sensible defaults for actions that need none of them (see
     * {@code GetPlayerProfileAction}'s
     * registration for a minimal example, {@code ReloadConfigAction}'s
     * for a permission-only one).
     */
    public static final class Builder<I, R> {

        private final Action<I, R> action;
        private PermissionNode permission;
        private ActionValidator<I> validator = ActionValidator.none();
        private Function<I, Optional<ActionTarget>> targetExtractor = input -> Optional.empty();
        private SelfTargetPolicy selfTargetPolicy = SelfTargetPolicy.ALLOWED;
        private BooleanSupplier enabledCheck = () -> true;
        private String module;
        private boolean audited = true;
        private boolean auditFailures = false;
        private Function<I, String> auditSummary;
        private BiFunction<I, ActionResult<R>, AuditDetails> auditDetails = (input, result) -> AuditDetails.EMPTY;
        private Function<I, List<?>> auditArgs = input -> List.of();

        private Builder(Action<I, R> action) {
            this.action = action;
            // Default summary is generic on purpose - it is only ever used
            // unless a specific action overrides it with something more
            // readable via auditSummary(...).
            this.auditSummary = input -> action.id() + (input == null ? "" : (": " + input));
        }

        public Builder<I, R> permission(PermissionNode permission) {
            this.permission = permission;
            return this;
        }

        public Builder<I, R> validator(ActionValidator<I> validator) {
            this.validator = validator;
            return this;
        }

        public Builder<I, R> target(Function<I, Optional<ActionTarget>> targetExtractor) {
            this.targetExtractor = targetExtractor;
            return this;
        }

        public Builder<I, R> selfTargetPolicy(SelfTargetPolicy policy) {
            this.selfTargetPolicy = policy;
            return this;
        }

        /** Shorthand for {@code selfTargetPolicy(SelfTargetPolicy.FORBIDDEN)}. */
        public Builder<I, R> forbidSelfTarget() {
            return selfTargetPolicy(SelfTargetPolicy.FORBIDDEN);
        }

        public Builder<I, R> enabledWhen(BooleanSupplier enabledCheck) {
            this.enabledCheck = enabledCheck;
            return this;
        }

        /** The owning module's key (e.g. {@code "players"}), carried onto every audit entry this action produces. */
        public Builder<I, R> module(String module) {
            this.module = module;
            return this;
        }

        public Builder<I, R> auditSummary(Function<I, String> auditSummary) {
            this.auditSummary = auditSummary;
            return this;
        }

        /** Opts out of auditing successes - for read-only actions where every invocation would just be log noise. */
        public Builder<I, R> notAudited() {
            this.audited = false;
            return this;
        }

        /** Also records an audit entry (with {@code success=false}) for a denied/invalid/thrown execution. */
        public Builder<I, R> auditFailures() {
            this.auditFailures = true;
            return this;
        }

        public Builder<I, R> auditDetails(BiFunction<I, ActionResult<R>, AuditDetails> auditDetails) {
            this.auditDetails = auditDetails;
            return this;
        }

        /**
         * The values the audit log's sentence needs besides the actor
         * ({@code {0}}) and the target ({@code {1}}), in order as
         * {@code {2}}, {@code {3}}, ... - e.g. the reason of a kick. The
         * sentence itself comes from the language files
         * ({@code audit.entry.<namespace>.<action-name-with-dashes>.text}), so
         * it reads in the server's language, also for old entries; the
         * English {@link #auditSummary} stays stored for the console, Discord
         * and exports. A value {@code "lang:<key>"} is shown as the client's
         * own translation (Minecraft's names, e.g. {@code lang:gameMode.creative}),
         * {@code "text:<key>"} as a text from the language files.
         *
         * @since 1.8
         */
        public Builder<I, R> auditArgs(Function<I, List<?>> auditArgs) {
            this.auditArgs = auditArgs;
            return this;
        }

        public ActionDefinition<I, R> build() {
            return new ActionDefinition<>(action, permission, validator, targetExtractor, selfTargetPolicy,
                    enabledCheck, module, audited, auditFailures, auditSummary, auditDetails, auditArgs);
        }
    }
}
