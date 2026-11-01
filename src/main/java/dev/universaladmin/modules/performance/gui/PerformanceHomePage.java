package dev.universaladmin.modules.performance.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiGlass;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiRows;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.performance.PerformanceSettings;
import dev.universaladmin.modules.performance.PerformanceSnapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code performance.home} - the Performance dashboard: read-only tiles from
 * {@link dev.universaladmin.modules.performance.PerformanceSamplingService}'s
 * cache (never recomputed on render, see that class's javadoc), plus
 * navigation into the per-world and entity-overview pages. Same
 * "no shortcuts for a built-in module" shape as {@code ServerHomePage}.
 */
public final class PerformanceHomePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("performance.home");

    private final PerformanceGuiContext ctx;

    public PerformanceHomePage(PerformanceGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("performance.gui.home.title");
    }

    /** Every value here changes while you look. */
    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        PerformanceSnapshot snapshot = ctx.samplingService().snapshot();
        GuiRows.place(view, viewer, 0, 0, GuiGlass.LIGHT_BLUE, text("performance.gui.home.row.now"), tiles(snapshot));
        GuiRows.place(view, viewer, 1, 0, GuiGlass.LIGHT_BLUE, text("performance.gui.home.row.health"), lights(snapshot));
        GuiRows.place(view, viewer, 2, 0, GuiGlass.BLUE, text("performance.gui.home.row.details"), List.of(
                GuiButton.of(GuiItem.of(Material.FILLED_MAP, text("performance.gui.home.worlds-nav"),
                        List.of(text("performance.gui.home.worlds-nav-lore"), text("gui.hint.open"))),
                        clickCtx -> clickCtx.open(new PerformanceWorldsPage(ctx))),
                GuiButton.of(GuiItem.of(Material.SPAWNER, text("performance.gui.home.entities-nav"),
                        List.of(text("performance.gui.home.entities-nav-lore"), text("gui.hint.open"))),
                        clickCtx -> clickCtx.open(new PerformanceEntityOverviewPage(ctx)))));
    }

    /** The value in the name - readable without hovering. */
    private List<GuiButton> tiles(PerformanceSnapshot snapshot) {
        OptionalDouble avgTps5m = ctx.samplingService().history().averageTps();
        List<Component> tpsLore = new ArrayList<>();
        tpsLore.add(Component.text(String.format(Locale.ROOT, "1m %.2f · 5m %.2f · 15m %.2f",
                snapshot.tps1m(), snapshot.tps5m(), snapshot.tps15m()), NamedTextColor.GRAY));
        avgTps5m.ifPresent(average -> tpsLore.add(text("performance.gui.home.tps-history-avg", String.format(Locale.ROOT, "%.2f", average))));
        tpsLore.add(text("performance.gui.home.tps-hint"));
        return List.of(
                tile(Material.COMPASS, "performance.gui.home.tps", String.format(Locale.ROOT, "%.1f", snapshot.tps1m()), tpsLore),
                tile(Material.CLOCK, "performance.gui.home.mspt", String.format(Locale.ROOT, "%.1f ms", snapshot.mspt()),
                        List.of(text("performance.gui.home.mspt-hint"))),
                tile(Material.REDSTONE, "performance.gui.home.memory", String.format(Locale.ROOT, "%.0f%%", snapshot.usedMemoryPercent()),
                        List.of(Component.text(formatMegabytes(snapshot.usedMemoryBytes()) + " / " + formatMegabytes(snapshot.maxMemoryBytes()) + " MB",
                                NamedTextColor.GRAY))),
                tile(Material.PLAYER_HEAD, "performance.gui.home.players", String.valueOf(snapshot.onlinePlayers()), List.of()),
                tile(Material.GRASS_BLOCK, "performance.gui.home.chunks", String.valueOf(snapshot.loadedChunks()), List.of()),
                tile(Material.ZOMBIE_HEAD, "performance.gui.home.entities", String.valueOf(snapshot.entityCount()), List.of()),
                tile(Material.FILLED_MAP, "performance.gui.home.worlds", String.valueOf(snapshot.worldCount()), List.of()),
                tile(Material.RECOVERY_COMPASS, "performance.gui.home.uptime", formatUptime(snapshot.uptime()), List.of()));
    }

    /**
     * Green, yellow or red under TPS, MSPT and memory - measured against the
     * thresholds the staff alerts use (performance.alerts.*): red is past the
     * threshold, yellow close to it.
     */
    private List<GuiButton> lights(PerformanceSnapshot snapshot) {
        double tpsLimit = ctx.settings().get(PerformanceSettings.ALERT_TPS_THRESHOLD);
        double msptLimit = ctx.settings().get(PerformanceSettings.ALERT_MSPT_THRESHOLD_MS);
        double memoryLimit = ctx.settings().get(PerformanceSettings.ALERT_MEMORY_THRESHOLD_PERCENT);
        Light tps = snapshot.tps1m() < tpsLimit ? Light.BAD : snapshot.tps1m() < tpsLimit + (20 - tpsLimit) / 2 ? Light.CLOSE : Light.GOOD;
        Light mspt = snapshot.mspt() > msptLimit ? Light.BAD : snapshot.mspt() > msptLimit * 0.8 ? Light.CLOSE : Light.GOOD;
        Light memory = snapshot.usedMemoryPercent() > memoryLimit ? Light.BAD
                : snapshot.usedMemoryPercent() > memoryLimit - 10 ? Light.CLOSE : Light.GOOD;
        return List.of(light(tps, "performance.gui.home.tps", String.format(Locale.ROOT, "< %.1f", tpsLimit)),
                light(mspt, "performance.gui.home.mspt", String.format(Locale.ROOT, "> %.0f ms", msptLimit)),
                light(memory, "performance.gui.home.memory", String.format(Locale.ROOT, "> %.0f%%", memoryLimit)));
    }

    private enum Light {
        GOOD(GuiGlass.LIME, "good"),
        CLOSE(GuiGlass.YELLOW, "close"),
        BAD(GuiGlass.RED, "bad");

        private final GuiGlass glass;
        private final String key;

        Light(GuiGlass glass, String key) {
            this.glass = glass;
            this.key = key;
        }
    }

    private GuiButton light(Light light, String labelKey, String limit) {
        Component name = Component.join(JoinConfiguration.noSeparators(), text(labelKey),
                Component.text(": ", NamedTextColor.DARK_GRAY), text("performance.gui.home.light." + light.key));
        return GuiButton.display(GuiItem.glass(light.glass, name, List.of(text("performance.gui.home.light.limit", limit))));
    }

    private GuiButton tile(Material material, String labelKey, String value, List<Component> lore) {
        Component name = Component.join(JoinConfiguration.noSeparators(), text(labelKey),
                Component.text(": ", NamedTextColor.DARK_GRAY), Component.text(value, NamedTextColor.WHITE));
        return GuiButton.display(GuiItem.of(material, name, lore));
    }

    private String formatUptime(Duration uptime) {
        long seconds = uptime.toSeconds();
        return "%dh %dm %ds".formatted(seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private String formatMegabytes(long bytes) {
        return String.valueOf(bytes / (1024 * 1024));
    }
}
