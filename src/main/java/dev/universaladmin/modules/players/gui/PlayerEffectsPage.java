package dev.universaladmin.modules.players.gui;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.SelectionDialog;
import dev.universaladmin.localization.GameNames;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.modules.players.action.AddEffectInput;
import dev.universaladmin.modules.players.action.PlayerActionIds;
import dev.universaladmin.modules.players.action.PlayerTargetInput;
import dev.universaladmin.modules.players.action.RemoveEffectInput;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Active effects (display) plus Add/Remove/Clear - all three online-only, gated on the {@code players.effects.*} nodes. */
public final class PlayerEffectsPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("players.effects");

    private static final int ADD_SLOT = GuiLayout.CONTENT_END_SLOT - 2;
    private static final int REMOVE_SLOT = GuiLayout.CONTENT_END_SLOT - 1;
    private static final int CLEAR_SLOT = GuiLayout.CONTENT_END_SLOT;

    private final PlayerGuiContext ctx;
    private final UUID targetId;
    private final String targetName;

    public PlayerEffectsPage(PlayerGuiContext ctx, UUID targetId, String targetName) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    @Override
    protected boolean refreshable() {
        return false;
    }

    @Override
    protected Component title(Player viewer) {
        return text("players.gui.effects.title", targetName);
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        Player target = Bukkit.getPlayer(targetId);
        List<PotionEffect> effects = target != null ? List.copyOf(target.getActivePotionEffects()) : List.of();

        if (effects.isEmpty()) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(framework.icons().empty(), text("players.gui.effects.none")));
        } else {
            int slot = GuiLayout.CONTENT_START_SLOT;
            for (PotionEffect effect : effects) {
                if (slot > ADD_SLOT - 1) {
                    break;
                }
                view.place(slot++, GuiItem.of(Material.POTION,
                        Component.join(JoinConfiguration.noSeparators(), GameNames.effect(effect.getType()),
                                Component.text(" " + (effect.getAmplifier() + 1))),
                        List.of(Component.text((effect.getDuration() / 20) + "s"))));
            }
        }

        view.place(ADD_SLOT, new GuiButton(GuiItem.of(Material.BREWING_STAND, text("players.gui.effects.add")),
                PlayerPermissions.EFFECTS_ADD, clickCtx -> promptAddEffect(clickCtx.viewer())), viewer);
        view.place(REMOVE_SLOT, new GuiButton(GuiItem.of(Material.MILK_BUCKET, text("players.gui.effects.remove")),
                PlayerPermissions.EFFECTS_REMOVE, clickCtx -> promptRemoveEffect(clickCtx.viewer(), effects)), viewer);
        view.place(CLEAR_SLOT, new GuiButton(GuiItem.of(Material.BUCKET, text("players.gui.effects.clear")),
                PlayerPermissions.EFFECTS_CLEAR,
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.EFFECTS_CLEAR, new PlayerTargetInput(targetId))), viewer);
    }

    private void promptAddEffect(Player viewer) {
        List<PotionEffectType> types = Registry.EFFECT.stream()
                .sorted((a, b) -> a.getKey().getKey().compareTo(b.getKey().getKey()))
                .toList();
        SelectionDialog.open(viewer, framework, messages, ctx.scheduler(), text("players.gui.effects.add"), types,
                type -> GuiItem.of(Material.POTION, GameNames.effect(type)),
                (clickCtx, type) -> promptEffectDetails(viewer, type));
    }

    private void promptEffectDetails(Player viewer, PotionEffectType type) {
        GuiTextInput.request(viewer, text("players.gui.effects.add"), text("players.gui.effects.prompt-duration-amplifier"),
                "30 1", text("gui.confirm"), text("gui.cancel"),
                submitted -> {
                    AddEffectInput input = parseEffectInput(type, submitted);
                    if (input == null) {
                        PlayerGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                        return;
                    }
                    runAction(viewer, PlayerActionIds.EFFECTS_ADD, input);
                },
                () -> this.open(viewer));
    }

    private AddEffectInput parseEffectInput(PotionEffectType type, String submitted) {
        if (submitted == null) {
            return null;
        }
        String[] tokens = submitted.trim().split("\\s+");
        if (tokens.length != 2) {
            return null;
        }
        try {
            int seconds = Integer.parseInt(tokens[0]);
            int amplifier = Integer.parseInt(tokens[1]);
            if (seconds <= 0 || amplifier < 0) {
                return null;
            }
            return new AddEffectInput(targetId, type, seconds * 20, amplifier);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void promptRemoveEffect(Player viewer, List<PotionEffect> effects) {
        if (effects.isEmpty()) {
            PlayerGuiActions.notifyError(viewer, messages);
            return;
        }
        SelectionDialog.open(viewer, framework, messages, ctx.scheduler(), text("players.gui.effects.remove"), effects,
                effect -> GuiItem.of(Material.POTION, GameNames.effect(effect.getType())),
                (clickCtx, effect) -> runAction(viewer, PlayerActionIds.EFFECTS_REMOVE, new RemoveEffectInput(targetId, effect.getType())));
    }

    private <I> void runAction(Player viewer, ActionId id, I input) {
        ctx.actionExecutor().<I, Object>execute(id, PlayerGuiActions.contextFor(viewer), input)
                .whenComplete((result, error) -> ctx.scheduler().runOnMainThread(() -> {
                    if (!viewer.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        PlayerGuiActions.notifyError(viewer, messages);
                    } else {
                        PlayerGuiActions.notifyResult(viewer, messages, result);
                    }
                    this.open(viewer);
                }));
    }
}
