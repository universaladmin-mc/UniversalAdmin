package dev.universaladmin.modules.performance.gui;

import dev.universaladmin.gui.AbstractListGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.localization.GameNames;
import dev.universaladmin.modules.performance.EntityTypeCount;
import dev.universaladmin.modules.performance.PerformancePermissions;
import dev.universaladmin.modules.performance.action.ClearEntitiesAction;
import dev.universaladmin.modules.performance.action.ClearEntitiesInput;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * {@code performance.entities.by-type} - every currently-loaded non-player
 * entity type, sorted by count. Selecting a tile (only if the viewer holds
 * {@link PerformancePermissions#ENTITY_CLEAR} and the type isn't in {@link
 * dev.universaladmin.modules.performance.PerformanceSettings#ENTITY_CLEAR_PROTECTED_TYPES})
 * previews and confirms clearing that one type across every loaded world -
 * see {@link ClearEntitiesAction}.
 *
 * <p>Backed by the cached {@link
 * dev.universaladmin.modules.performance.PerformanceSamplingService#entityOverview()},
 * not a fresh scan - {@link #loadItems} just wraps the already-computed list
 * in a completed future to reuse {@link AbstractListGuiPage}'s pagination.
 */
public final class PerformanceEntityByTypePage extends AbstractListGuiPage<EntityTypeCount> {

    public static final GuiPageId ID = GuiPageId.core("performance.entities.by-type");

    private final PerformanceGuiContext ctx;

    public PerformanceEntityByTypePage(PerformanceGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages(), ctx.scheduler());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("performance.gui.entities.by-type-title");
    }

    @Override
    protected CompletableFuture<List<EntityTypeCount>> loadItems(Player viewer) {
        return CompletableFuture.completedFuture(ctx.samplingService().entityOverview().byType());
    }

    @Override
    protected GuiItem render(EntityTypeCount item) {
        boolean protectedType = ctx.samplingService().isProtected(item.type());
        List<Component> lore = protectedType
                ? List.of(text("performance.gui.entities.count", item.count()), text("performance.gui.entities.protected"))
                : List.of(text("performance.gui.entities.count", item.count()), text("performance.gui.entities.click-to-clear"));
        return GuiItem.of(entityIcon(item.type()), GameNames.entity(item.type()), lore);
    }

    @Override
    protected void onSelect(GuiClickContext clickContext, EntityTypeCount item) {
        Player viewer = clickContext.viewer();
        if (!viewer.hasPermission(PerformancePermissions.ENTITY_CLEAR.value()) || ctx.samplingService().isProtected(item.type())) {
            return;
        }
        int preview = ctx.samplingService().previewClearCount(EnumSet.of(item.type()), null);
        if (preview == 0) {
            viewer.sendMessage(text("performance.gui.entities.nothing-to-clear"));
            return;
        }
        ConfirmationDialog.open(viewer, framework, messages, text("performance.gui.entities.clear-type", item.type().name()),
                List.of(text("performance.gui.entities.clear-type-confirm", preview, item.type().name())),
                ConfirmationDialog.DangerLevel.DANGEROUS,
                confirmCtx -> PerformanceGuiActions.<ClearEntitiesInput>runAction(ctx, viewer, ClearEntitiesAction.ID,
                        new ClearEntitiesInput(EnumSet.of(item.type()), null), () -> this.open(viewer)),
                confirmCtx -> this.open(viewer));
    }

    /** Best-effort mob icon via the matching spawn egg, falling back to a generic icon for types with none (items, projectiles, ...). */
    private Material entityIcon(EntityType type) {
        Material spawnEgg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return spawnEgg != null ? spawnEgg : Material.ARMOR_STAND;
    }
}
