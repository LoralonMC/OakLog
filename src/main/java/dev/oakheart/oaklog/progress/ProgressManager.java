package dev.oakheart.oaklog.progress;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.config.ConfigManager;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.PlayerProgress;
import dev.oakheart.oaklog.model.PlayerProgress.ToastState;
import dev.oakheart.oaklog.model.RecentEntry;
import dev.oakheart.oaklog.registry.EntryRegistry;
import dev.oakheart.oaklog.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProgressManager implements Listener {

    private final OakLog plugin;
    private final ConfigManager configManager;
    private final DataStore dataStore;
    private final EntryRegistry registry;

    private final Map<UUID, PlayerProgress> online = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, Long>> dirty = new ConcurrentHashMap<>();

    private BukkitTask flushTask;

    public ProgressManager(OakLog plugin, ConfigManager configManager, DataStore dataStore, EntryRegistry registry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataStore = dataStore;
        this.registry = registry;
    }

    public void start() {
        long interval = configManager.getFlushIntervalTicks();
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushDirty, interval, interval);
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flushAllSync();
        online.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Integer, Long> collected = dataStore.loadCollected(uuid);
            List<RecentEntry> recent = dataStore.loadRecent(uuid, PlayerProgress.RECENT_BUFFER_SIZE);
            ToastState toast = dataStore.loadToastState(uuid);
            if (toast == null) {
                toast = ToastState.fromString(configManager.getToastDefaultState());
            }
            PlayerProgress progress = new PlayerProgress(uuid, collected, recent, toast);
            // Publish on the main thread and only while the player is still
            // online: a join-and-instant-quit otherwise re-inserts the entry
            // AFTER the quit handler removed it, leaking it forever.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!event.getPlayer().isOnline()) return;
                online.put(uuid, progress);
                plugin.debug("Loaded progress for " + event.getPlayer().getName() + ": "
                        + progress.collectedCount() + " entries collected");
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Drain on the main thread, write off it: a synchronous DB transaction
        // per quit stacks up badly during quit storms (restart broadcast).
        Map<Integer, Long> pending = dirty.remove(uuid);
        if (pending != null && !pending.isEmpty()) {
            Map<UUID, Map<Integer, Long>> single = Map.of(uuid, pending);
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin,
                        () -> dataStore.batchInsertCollected(single));
            } else {
                dataStore.batchInsertCollected(single);
            }
        }
        if (configManager.isCacheEvictOnQuit()) {
            online.remove(uuid);
        }
    }

    public PlayerProgress get(UUID uuid) {
        return online.get(uuid);
    }

    public boolean markCollected(Player player, int entryId) {
        UUID uuid = player.getUniqueId();
        PlayerProgress progress = online.get(uuid);
        if (progress == null) return false;

        if (configManager.isGamemodeIgnored(player.getGameMode())) {
            plugin.debug(player.getName() + " is in ignored game mode "
                    + player.getGameMode() + "; skipping collection of entry " + entryId);
            return false;
        }

        long now = System.currentTimeMillis();
        if (!progress.markCollected(entryId, now)) {
            return false;
        }

        markDirty(uuid, entryId, now);

        Entry entry = registry.get(entryId);
        if (entry != null) {
            Bukkit.getPluginManager().callEvent(new ProgressEvent(player, entry, now));
        }
        return true;
    }

    /**
     * Mark an entry collected without firing a ProgressEvent. Used for
     * backfill paths (e.g. silently catching up a player's already-completed
     * advancements on first join after the advancements category was added).
     * Returns false when the player isn't currently loaded — callers should
     * tolerate that (the player will simply be re-checked on next join).
     */
    public boolean markCollectedSilent(UUID uuid, int entryId) {
        PlayerProgress progress = online.get(uuid);
        if (progress == null) return false;
        long now = System.currentTimeMillis();
        if (!progress.markCollected(entryId, now)) return false;
        markDirty(uuid, entryId, now);
        return true;
    }

    public void grant(UUID uuid, int entryId) {
        long now = System.currentTimeMillis();
        PlayerProgress progress = online.get(uuid);
        if (progress != null) {
            progress.markCollected(entryId, now);
        }
        markDirty(uuid, entryId, now);
    }

    /**
     * Records a pending write for the flush task. The put must be re-checked:
     * the async flush drains buckets via {@code dirty.remove(uuid)}, and a drain
     * that lands between the computeIfAbsent and the put leaves this write in an
     * orphaned map that is never flushed. PlayerProgress already reports the
     * entry as collected in memory, so the row would never be retried — the
     * collection silently vanishes on relog. A rare duplicate insert from the
     * re-home is harmless; the store upserts.
     */
    private void markDirty(UUID uuid, int entryId, long timestamp) {
        Map<Integer, Long> bucket = dirty.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        bucket.put(entryId, timestamp);
        if (dirty.get(uuid) != bucket) {
            dirty.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(entryId, timestamp);
        }
    }

    public void revoke(UUID uuid, int entryId) {
        PlayerProgress progress = online.get(uuid);
        if (progress != null) {
            progress.removeCollected(entryId);
        }
        Map<Integer, Long> pending = dirty.get(uuid);
        if (pending != null) {
            pending.remove(entryId);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> dataStore.deleteCollected(uuid, entryId));
    }

    public int wipe(UUID uuid) {
        PlayerProgress progress = online.get(uuid);
        if (progress != null) {
            // Replace with a fresh empty progress instead of removing: nothing
            // reloads the entry while the player stays online, so removal
            // silently disables all collection tracking until they relog.
            online.put(uuid, new PlayerProgress(uuid, new HashMap<>(),
                    new java.util.ArrayList<>(), progress.toastState()));
        }
        dirty.remove(uuid);
        return dataStore.deleteAllCollected(uuid);
    }

    public void setToastState(UUID uuid, ToastState state) {
        PlayerProgress progress = online.get(uuid);
        if (progress != null) {
            progress.setToastState(state);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> dataStore.saveToastState(uuid, state));
    }

    /**
     * Live entries this player has collected.
     *
     * <p>Retired entries are excluded. The player keeps the collection row (so it
     * counts again if the entry ever comes back), but it must not be counted here:
     * the totals this is displayed against come from {@code liveCount()}, which
     * excludes retired entries, so counting them would show more collected than
     * exist and push percentages past 100%.
     */
    public int collectedCount(UUID uuid) {
        PlayerProgress progress = online.get(uuid);
        if (progress == null) return 0;
        int count = 0;
        for (Entry entry : registry.all()) {
            if (!entry.isRetired() && progress.isCollected(entry.id())) count++;
        }
        return count;
    }

    /** As {@link #collectedCount(UUID)}, for a single category. */
    public int collectedCount(UUID uuid, Category category) {
        PlayerProgress progress = online.get(uuid);
        if (progress == null) return 0;
        int count = 0;
        for (Entry entry : registry.byCategory(category)) {
            if (!entry.isRetired() && progress.isCollected(entry.id())) count++;
        }
        return count;
    }

    private void flushDirty() {
        if (dirty.isEmpty()) return;
        // Drain per-key with remove(): the old snapshot-then-clear() wiped any
        // entry inserted between the copy and the clear without ever writing it,
        // and markCollected never retries (memory already says collected).
        Map<UUID, Map<Integer, Long>> snapshot = new HashMap<>();
        for (UUID uuid : dirty.keySet()) {
            Map<Integer, Long> pending = dirty.remove(uuid);
            if (pending != null && !pending.isEmpty()) {
                snapshot.put(uuid, pending);
            }
        }
        if (!snapshot.isEmpty()) {
            dataStore.batchInsertCollected(snapshot);
        }
    }

    private void flushAllSync() {
        if (dirty.isEmpty()) return;
        Map<UUID, Map<Integer, Long>> snapshot = new HashMap<>(dirty);
        dirty.clear();
        dataStore.batchInsertCollected(snapshot);
    }
}
