package dev.universaladmin.modules.players;

import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

/**
 * Application service for the Players module - the layer between frontends
 * (GUI/command/action) and {@link PlayerProfileRepository}. This is where
 * "what does it mean to see a player join" business logic lives, not in a
 * Bukkit {@code PlayerJoinEvent} listener.
 *
 * <p>{@link #snapshot(UUID)} is the one method that reaches past the
 * repository into live Bukkit state - see {@link PlayerSnapshot}'s javadoc
 * for why that data isn't persisted. It always resolves online/offline on
 * the main thread first ({@link Bukkit#getPlayer(UUID)} touches live entity
 * state) and only hops onto the async executor for the offline,
 * file-backed {@link OfflinePlayer} read - see docs/architecture/threading.md.
 */
public final class PlayerService {

    private final PlayerProfileRepository repository;
    private final TaskScheduler scheduler;
    private final PlayerSessionTracker sessionTracker;

    public PlayerService(PlayerProfileRepository repository, TaskScheduler scheduler, PlayerSessionTracker sessionTracker) {
        this.repository = repository;
        this.scheduler = scheduler;
        this.sessionTracker = sessionTracker;
    }

    public CompletableFuture<PlayerProfile> getOrCreateProfile(UUID playerId, String currentName) {
        return repository.findById(playerId).thenCompose(existing -> {
            Instant now = Instant.now();
            PlayerProfile profile = existing
                    .map(p -> p.withLastSeen(currentName, now))
                    .orElseGet(() -> new PlayerProfile(playerId, currentName, now, now));
            return repository.save(profile);
        });
    }

    /**
     * Adds a profile for each player the database has never seen (imported
     * punishments), first join and last seen set to when the source last saw
     * them; known players are left unchanged. Several names for one UUID: the
     * most recent wins.
     */
    public CompletableFuture<Integer> addIfAbsent(Map<PlayerRef, Instant> lastSeen) {
        Map<UUID, PlayerProfile> profiles = new HashMap<>();
        lastSeen.forEach((player, seenAt) -> profiles.merge(player.id(),
                new PlayerProfile(player.id(), player.name(), seenAt, seenAt),
                (old, fresh) -> fresh.lastSeen().isAfter(old.lastSeen()) ? fresh : old));
        return repository.insertIfAbsent(profiles.values());
    }

    public CompletableFuture<Optional<PlayerProfile>> findProfile(UUID playerId) {
        return repository.findById(playerId);
    }

    /** Bounded/filtered/sorted profile lookup backing the Offline/Search/Recently-Seen GUI lists. */
    public CompletableFuture<List<PlayerProfile>> search(PlayerSearchQuery query) {
        return repository.search(query);
    }

    /**
     * Builds a {@link PlayerSnapshot} for the Profile page. Empty only if
     * {@code playerId} has never played on this server (not offline vs.
     * online - both of those produce a snapshot).
     */
    public CompletableFuture<Optional<PlayerSnapshot>> snapshot(UUID playerId) {
        CompletableFuture<Optional<PlayerSnapshot>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online != null) {
                future.complete(Optional.of(buildOnlineSnapshot(online)));
                return;
            }
            scheduler.supplyAsync(() -> buildOfflineSnapshot(playerId)).whenComplete((snapshot, error) -> {
                if (error != null) {
                    future.completeExceptionally(error);
                } else {
                    future.complete(snapshot);
                }
            });
        });
        return future;
    }

    private PlayerSnapshot buildOnlineSnapshot(Player player) {
        Location location = player.getLocation();
        Location respawn = player.getRespawnLocation();
        List<PotionEffect> effects = List.copyOf(player.getActivePotionEffects());
        long firstPlayed = player.getFirstPlayed();
        return new PlayerSnapshot(
                player.getUniqueId(),
                player.getName(),
                true,
                firstPlayed > 0 ? Instant.ofEpochMilli(firstPlayed) : null,
                Instant.now(),
                // Despite its name, PLAY_ONE_MINUTE is tracked in ticks (1/20s),
                // same unit as every other Bukkit tick-based statistic.
                Duration.ofSeconds(player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L),
                sessionTracker.sessionDuration(player.getUniqueId()).orElse(null),
                location.getWorld() != null ? location.getWorld().getName() : null,
                location.getX(),
                location.getY(),
                location.getZ(),
                player.getGameMode(),
                player.getHealth(),
                maxHealthOf(player),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExp(),
                player.getLevel(),
                player.getPing(),
                player.locale().toString(),
                effects,
                respawn != null ? formatLocation(respawn) : null);
    }

    private Optional<PlayerSnapshot> buildOfflineSnapshot(UUID playerId) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        if (!offline.hasPlayedBefore()) {
            return Optional.empty();
        }
        long firstPlayed = offline.getFirstPlayed();
        long lastSeen = offline.getLastSeen();
        Location respawn = offline.getRespawnLocation();
        return Optional.of(new PlayerSnapshot(
                playerId,
                offline.getName() != null ? offline.getName() : playerId.toString(),
                false,
                firstPlayed > 0 ? Instant.ofEpochMilli(firstPlayed) : null,
                lastSeen > 0 ? Instant.ofEpochMilli(lastSeen) : null,
                Duration.ofSeconds(offline.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L),
                null,
                null, null, null, null,
                null,
                null, null,
                null, null,
                null, null,
                null,
                null,
                List.of(),
                respawn != null ? formatLocation(respawn) : null));
    }

    private static Double maxHealthOf(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute != null ? attribute.getValue() : null;
    }

    private static String formatLocation(Location location) {
        String world = location.getWorld() != null ? location.getWorld().getName() : "?";
        return "%s @ %.1f, %.1f, %.1f".formatted(world, location.getX(), location.getY(), location.getZ());
    }
}
