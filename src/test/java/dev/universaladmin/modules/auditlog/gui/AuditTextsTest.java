package dev.universaladmin.modules.auditlog.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.audit.AuditEventType;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.permission.PermissionEvaluator;
import java.text.MessageFormat;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

/** The audit log reads in the server's language - and falls back to what was stored when it cannot. */
class AuditTextsTest {

    private static final Map<String, String> TEXTS = Map.of(
            "audit.entry.core.moderation-kick.title", "Kick",
            "audit.entry.core.moderation-kick.text", "{0} hat {2} gekickt: {3}",
            "audit.entry.core.players-heal.text", "{0} hat {1} geheilt",
            "audit.entry.core.players-gamemode.text", "{0} hat den Spielmodus von {1} auf {2} gesetzt",
            "audit.entry.core.worlds-weather-set.text", "{0} hat das Wetter in {2} auf {3} gestellt",
            "worlds.gui.weather.rain", "Regen");

    private static final MessageService MESSAGES = new MessageService() {
        @Override
        public String get(MessageKey key, Object... args) {
            String template = TEXTS.getOrDefault(key.value(), key.value());
            if (args.length == 0) {
                return template;
            }
            Object[] plain = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                plain[i] = args[i] instanceof MessageService.Markup markup ? markup.value() : args[i];
            }
            return MessageFormat.format(template, plain);
        }

        @Override
        public boolean has(MessageKey key) {
            return TEXTS.containsKey(key.value());
        }
    };

    private static AuditEvent event(String name, ActionTarget target, Map<String, Object> metadata, String summary) {
        AuditEvent.Builder builder = AuditEvent.builder(AuditEventType.core(name),
                Actor.player(UUID.randomUUID(), "Admin", PermissionEvaluator.allowAll()), Source.GUI, summary).metadata(metadata);
        if (target != null) {
            builder.target(target);
        }
        return builder.build();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void theSentenceUsesTheStoredValues() {
        AuditEvent kick = event("moderation.kick", null, Map.of("arg.0", "Notch", "arg.1", "Spam"), "Kicked Notch: Spam");

        assertEquals("Kick", plain(AuditTexts.title(MESSAGES, kick)));
        assertEquals("Admin hat Notch gekickt: Spam", plain(AuditTexts.sentence(MESSAGES, kick)));
    }

    @Test
    void anOldEntryWithoutItsValuesKeepsItsStoredSummary() {
        AuditEvent kick = event("moderation.kick", null, Map.of(), "Kicked Notch: Spam");

        assertEquals("Kicked Notch: Spam", plain(AuditTexts.sentence(MESSAGES, kick)));
    }

    @Test
    void theTargetIsTheSecondPlaceholderAndNeedsNoValueOfItsOwn() {
        AuditEvent heal = event("players.heal", ActionTarget.player(UUID.randomUUID(), "Notch"), Map.of(), "core:players.heal: ...");

        assertEquals("Admin hat Notch geheilt", plain(AuditTexts.sentence(MESSAGES, heal)));
    }

    @Test
    void textValuesComeFromTheLanguageFilesAndLangValuesStayTranslatable() {
        AuditEvent weather = event("worlds.weather.set", null, Map.of("arg.0", "world", "arg.1", "text:worlds.gui.weather.rain"), "x");
        assertEquals("Admin hat das Wetter in world auf Regen gestellt", plain(AuditTexts.sentence(MESSAGES, weather)));

        AuditEvent gamemode = event("players.gamemode", ActionTarget.player(UUID.randomUUID(), "Notch"),
                Map.of("arg.0", "lang:gameMode.creative"), "x");
        Component sentence = AuditTexts.sentence(MESSAGES, gamemode);
        boolean translatable = sentence.children().stream().anyMatch(child -> child instanceof TranslatableComponent t
                && t.key().equals("gameMode.creative"));
        assertEquals(true, translatable || sentence instanceof TranslatableComponent, plain(sentence));
    }

    @Test
    void anActionWithoutTextsShowsItsTypeAndSummary() {
        AuditEvent other = event("something.else", null, Map.of(), "Did something");

        assertEquals("core:something.else", plain(AuditTexts.title(MESSAGES, other)));
        assertEquals("Did something", plain(AuditTexts.sentence(MESSAGES, other)));
    }
}
