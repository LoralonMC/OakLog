package dev.oakheart.oaklog.leaderboard;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderboardManager {

    private final OakLog plugin;
    private final DataStore dataStore;
    private final int topSize;
    private final int refreshSeconds;

    private volatile List<DataStore.LeaderboardRow> topOverall = List.of();
    private final Map<Category, List<DataStore.LeaderboardRow>> topByCategory = new EnumMap<>(Category.class);
    private final Map<UUID, Integer> rankCache = new ConcurrentHashMap<>();
    /**
     * Overall count + latest timestamp for every player, refreshed asynchronously.
     *
     * <p>This is what keeps placeholders off the database. Topper asks for
     * {@code %oaklog_total_collected%} for every offline player it knows about, and
     * {@link DataStore#collectedCountOverall} is {@code synchronized} alongside 19 other methods on
     * the same monitor, so each miss parked the server thread behind the async flush. Measured at
     * 42% of all tick-spike time before this cache existed.
     */
    private volatile Map<UUID, DataStore.LeaderboardRow> countCache = Map.of();
    private BukkitTask refreshTask;

    public LeaderboardManager(OakLog plugin, DataStore dataStore, int topSize, int refreshSeconds) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.topSize = Math.max(topSize, 10);
        this.refreshSeconds = Math.max(refreshSeconds, 5);
    }

    public void start() {
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::refresh, 1L, refreshSeconds * 20L);
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public void invalidate() {
        rankCache.clear();
    }

    public List<DataStore.LeaderboardRow> topOverall() {
        return topOverall;
    }

    public List<DataStore.LeaderboardRow> topByCategory(Category category) {
        return topByCategory.getOrDefault(category, List.of());
    }

    public int rankOverall(UUID uuid) {
        Integer cached = rankCache.get(uuid);
        if (cached != null) return cached;

        DataStore.LeaderboardRow self = lookup(uuid);
        int playerCount = self == null ? 0 : self.collectedCount();
        long playerLatest = self == null ? 0L : self.latestCollectedAt();
        // Rank = (number of players strictly ahead) + 1, where "ahead" uses the
        // same rule as the board's ORDER BY: higher count, or an equal count
        // reached earlier. The two definitions must match or ties diverge —
        // without the tiebreak every full-log completer read as #1 here while
        // the GUI numbered them by arbitrary list position. Counts via the cached
        // top list, so it degrades to "outside top N" beyond it. The player's own
        // count now comes from the all-players cache rather than a per-call query.
        int rank = 1;
        for (DataStore.LeaderboardRow row : topOverall) {
            if (row.playerUuid().equals(uuid)) break;
            if (row.collectedCount() > playerCount
                    || (row.collectedCount() == playerCount && row.latestCollectedAt() < playerLatest)) {
                rank++;
            }
        }
        rankCache.put(uuid, rank);
        return rank;
    }

    public int collectedOverall(UUID uuid) {
        DataStore.LeaderboardRow row = lookup(uuid);
        return row == null ? 0 : row.collectedCount();
    }

    /**
     * Cache read, with a database fallback that is only ever taken off the main thread.
     *
     * <p>The guard matters more than the fallback: a cold cache returning 0 for a few seconds is a
     * cosmetic glitch, whereas a synchronized query on the server thread is a tick spike.
     */
    private DataStore.LeaderboardRow lookup(UUID uuid) {
        DataStore.LeaderboardRow row = countCache.get(uuid);
        if (row != null || Bukkit.isPrimaryThread()) {
            return row;
        }
        int count = dataStore.collectedCountOverall(uuid);
        return count == 0 ? null : new DataStore.LeaderboardRow(uuid, count, dataStore.latestCollectedAt(uuid));
    }

    private void refresh() {
        countCache = dataStore.allCollectedCounts();
        topOverall = dataStore.topOverall(topSize);
        for (Category category : Category.values()) {
            topByCategory.put(category, dataStore.topByCategory(category, topSize));
        }
        rankCache.clear();
    }
}
