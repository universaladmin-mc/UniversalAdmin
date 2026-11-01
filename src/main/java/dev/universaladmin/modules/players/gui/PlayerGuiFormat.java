package dev.universaladmin.modules.players.gui;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small display-formatting helpers shared by the Players GUI pages - no logic, purely presentation. */
final class PlayerGuiFormat {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    private PlayerGuiFormat() {
    }

    static String instant(Instant instant) {
        return instant == null ? "-" : TIME_FORMAT.format(instant);
    }

    static String duration(Duration duration) {
        if (duration == null) {
            return "-";
        }
        long totalSeconds = Math.max(0, duration.toSeconds());
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0 ? "%dh %dm".formatted(hours, minutes) : "%dm %ds".formatted(minutes, seconds);
    }

    static String coordinates(Double x, Double y, Double z) {
        return (x == null || y == null || z == null) ? "-" : String.format(Locale.ROOT, "%.1f, %.1f, %.1f", x, y, z);
    }
}
