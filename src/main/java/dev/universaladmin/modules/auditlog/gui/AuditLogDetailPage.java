package dev.universaladmin.modules.auditlog.gui;

import dev.universaladmin.action.ActorType;
import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiGlass;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiRows;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.localization.MessageService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Static detail view of one {@link AuditEvent} - Actor/Action/Target/Time/
 * Source/Result/Reason/Old-New/Metadata, as required by docs/user/audit-log.md.
 * Not registered in {@link dev.universaladmin.gui.GuiRegistry}: like
 * {@link dev.universaladmin.gui.ConfirmationDialog}, it is ephemeral - built
 * fresh with the specific event to show every time {@link AuditLogListPage}
 * opens one.
 */
public final class AuditLogDetailPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("audit-log.detail");

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault());

    private final AuditEvent event;

    public AuditLogDetailPage(GuiFramework framework, MessageService messages, AuditEvent event) {
        super(ID, framework, messages);
        this.event = event;
    }

    @Override
    protected Component title(Player viewer) {
        return text("audit.gui.detail-title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        List<GuiButton> entry = new ArrayList<>();
        entry.add(GuiButton.display(GuiItem.of(Material.WRITABLE_BOOK,
                AuditTexts.title(messages, event).colorIfAbsent(NamedTextColor.WHITE),
                List.of(AuditTexts.sentence(messages, event).colorIfAbsent(NamedTextColor.GRAY),
                        Component.text(event.type().toString(), NamedTextColor.DARK_GRAY)))));
        entry.add(GuiButton.display(actorField()));
        if (event.target() != null) {
            entry.add(GuiButton.display(field(Material.TARGET, "audit.gui.field.target",
                    event.target().displayName() + " (" + event.target().type() + ": " + event.target().id() + ")")));
        }
        entry.add(GuiButton.display(field(Material.CLOCK, "audit.gui.field.time", TIME_FORMAT.format(event.timestamp()))));
        entry.add(GuiButton.display(field(Material.COMPASS, "audit.gui.field.source",
                text("audit.gui.source." + event.source().name().toLowerCase(Locale.ROOT)))));
        entry.add(GuiButton.display(field(event.success() ? Material.LIME_DYE : Material.RED_DYE, "audit.gui.field.result",
                text(event.success() ? "audit.gui.result.success" : "audit.gui.result.failure"))));
        GuiRows.place(view, viewer, 0, 0, GuiGlass.LIGHT_BLUE, text("audit.gui.row.entry"), entry);

        List<GuiButton> details = new ArrayList<>();
        if (event.reason() != null) {
            details.add(GuiButton.display(field(Material.PAPER, "audit.gui.field.reason", event.reason())));
        }
        if (event.oldValue() != null || event.newValue() != null) {
            details.add(GuiButton.display(field(Material.HOPPER, "audit.gui.field.old-new",
                    (event.oldValue() == null ? "?" : event.oldValue()) + " -> " + (event.newValue() == null ? "?" : event.newValue()))));
        }
        if (event.world() != null || event.position() != null) {
            String location = (event.world() == null ? "" : event.world())
                    + (event.position() == null ? "" : " " + formatPosition(event));
            details.add(GuiButton.display(field(Material.MAP, "audit.gui.field.location", location.trim())));
        }
        if (event.module() != null) {
            details.add(GuiButton.display(field(Material.CHEST, "audit.gui.field.module", event.module())));
        }
        if (event.metadata().keySet().stream().anyMatch(key -> !AuditTexts.isArg(key))) {
            details.add(GuiButton.display(metadataField()));
        }
        if (event.correlationId() != null) {
            details.add(GuiButton.display(field(Material.STRING, "audit.gui.field.correlation-id", event.correlationId())));
        }
        GuiRows.place(view, viewer, 1, 0, GuiGlass.LIGHT_BLUE, text("audit.gui.row.details"), details);
    }

    private String formatPosition(AuditEvent event) {
        var position = event.position();
        return "%.1f, %.1f, %.1f".formatted(position.x(), position.y(), position.z());
    }

    private GuiItem actorField() {
        Component value = Component.text(event.actor().displayName() + " (" + event.actor().type() + ")", NamedTextColor.GRAY);
        if (event.actor().type() == ActorType.PLAYER && event.actor().playerId() != null) {
            return GuiItem.playerHead(Bukkit.getOfflinePlayer(event.actor().playerId()), text("audit.gui.field.actor"), List.of(value));
        }
        return field(Material.PLAYER_HEAD, "audit.gui.field.actor", value);
    }

    private GuiItem field(Material material, String labelKey, Component value) {
        return GuiItem.of(material, text(labelKey), List.of(value));
    }

    private GuiItem field(Material material, String labelKey, String value) {
        return field(material, labelKey, Component.text(value, NamedTextColor.GRAY));
    }

    private GuiItem metadataField() {
        List<Component> lore = new ArrayList<>();
        for (Map.Entry<String, Object> entry : event.metadata().entrySet()) {
            if (AuditTexts.isArg(entry.getKey())) {
                continue;
            }
            lore.add(Component.text(entry.getKey() + ": " + entry.getValue(), NamedTextColor.GRAY));
        }
        return GuiItem.of(Material.BOOK, text("audit.gui.field.metadata"), lore);
    }
}
