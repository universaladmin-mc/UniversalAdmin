package dev.universaladmin.modules.players;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.potion.PotionEffect;

/**
 * Full live projection for {@code PlayerProfilePage} - built on demand by
 * {@link PlayerService#snapshot(UUID)}, never persisted (see
 * docs/user/modules/players.md for why: everything here either comes
 * straight from Bukkit at render time, or - for {@code totalPlaytime}/
 * {@code firstJoin}/{@code lastSeen} - is already tracked by Bukkit itself).
 * Every field below the identity/online fields is {@code null} (or {@link
 * #activeEffects()} empty) when it genuinely isn't available - offline
 * players have no live world/health/gamemode/ping/effects without NMS, which
 * this project doesn't use. IP address is deliberately not here at all; see
 * {@code GetPlayerIpAddressAction}.
 */
public record PlayerSnapshot(
        UUID id,
        String name,
        boolean online,
        Instant firstJoin,
        Instant lastSeen,
        Duration totalPlaytime,
        Duration sessionDuration,
        String world,
        Double x,
        Double y,
        Double z,
        GameMode gamemode,
        Double health,
        Double maxHealth,
        Integer food,
        Float saturation,
        Float experienceProgress,
        Integer level,
        Integer ping,
        String locale,
        List<PotionEffect> activeEffects,
        String respawnLocation) {

    public PlayerSnapshot {
        activeEffects = List.copyOf(activeEffects);
    }
}
