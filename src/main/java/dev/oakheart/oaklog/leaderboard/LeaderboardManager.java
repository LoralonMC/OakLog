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
    private BukkitTask refreshTask;

    public LeaderboardManager(OakLog plugin, DataStore dataStore, int topSize, int refreshSeconds) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.topSize = Math.max(topSize, 10);
        this.refreshSeconds = Math.max(refreshSeconds, 5);
    }

    public void start() {
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::refresh, 20L, refreshSeconds * 20L);
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

        int playerCount = dataStore.collectedCountOverall(uuid);
        long playerLatest = dataStore.latestCollectedAt(uuid);
        // Rank = (number of players strictly ahead) + 1, where "ahead" uses the
        // same rule as the board's ORDER BY: higher count, or an equal count
        // reached earlier. The two definitions must match or ties diverge —
        // without the tiebreak every full-log completer read as #1 here while
        // the GUI numbered them by arbitrary list position. We don't have a
        // cheap "all players" query, so this counts via the cached top list
        // and degrades to "outside top N" beyond it.
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
        return dataStore.collectedCountOverall(uuid);
    }

    private void refresh() {
        topOverall = dataStore.topOverall(topSize);
        for (Category category : Category.values()) {
            topByCategory.put(category, dataStore.topByCategory(category, topSize));
        }
        rankCache.clear();
    }
}
