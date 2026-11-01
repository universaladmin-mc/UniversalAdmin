package dev.universaladmin.modules.auditlog.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;

/**
 * An audit entry in the server's language: a short title and a sentence from
 * {@code audit.entry.<namespace>.<action-name-with-dashes>.title}/{@code .text}.
 * The sentence gets the actor as {@code {0}}, the target as {@code {1}} and
 * the action's stored values ({@code arg.0}, ...) from {@code {2}} on. An
 * entry whose sentence needs a value it does not have - written before the
 * value was stored, or by an action without a sentence - shows the English
 * summary it was saved with, an action without a title its type.
 */
final class AuditTexts {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.\\-]+");

    private AuditTexts() {
    }

    static Component title(MessageService messages, AuditEvent event) {
        MessageKey key = MessageKey.of(base(event) + ".title");
        return messages.has(key) ? ComponentMessages.render(messages.get(key)) : Component.text(event.type().toString());
    }

    static Component sentence(MessageService messages, AuditEvent event) {
        MessageKey key = MessageKey.of(base(event) + ".text");
        if (messages.has(key)) {
            List<Object> args = new ArrayList<>();
            args.add(event.actor().displayName());
            args.add(event.target() == null ? null : event.target().displayName());
            for (int i = 0; event.metadata().containsKey(ActionExecutor.AUDIT_ARG_PREFIX + i); i++) {
                args.add(arg(messages, String.valueOf(event.metadata().get(ActionExecutor.AUDIT_ARG_PREFIX + i))));
            }
            if (fits(messages.get(key), args)) {
                return ComponentMessages.render(messages.get(key, args.toArray()));
            }
        }
        return Component.text(event.summary());
    }

    /** Whether the stored metadata key is one of the sentence's values - not shown again among the details. */
    static boolean isArg(String metadataKey) {
        return metadataKey.startsWith(ActionExecutor.AUDIT_ARG_PREFIX);
    }

    private static String base(AuditEvent event) {
        return "audit.entry." + event.type().key().namespace() + "." + event.type().key().name().replace('.', '-');
    }

    /** Every placeholder the template uses has a value. */
    private static boolean fits(String template, List<Object> args) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index >= args.size() || args.get(index) == null) {
                return false;
            }
        }
        return true;
    }

    /** {@code lang:<key>} becomes the client's translation, {@code text:<key>} a text from the language files. */
    private static Object arg(MessageService messages, String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("lang:") && KEY.matcher(value.substring(5)).matches()) {
            return MessageService.markup("<lang:" + value.substring(5) + ">");
        }
        if (lower.startsWith("text:") && KEY.matcher(value.substring(5)).matches()) {
            MessageKey key = MessageKey.of(value.substring(5));
            if (messages.has(key)) {
                return MessageService.markup(messages.get(key));
            }
        }
        return value;
    }
}
