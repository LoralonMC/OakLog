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
            online.put(uuid, progress);
            plugin.debug("Loaded progress for " + event.getPlayer().getName() + ": "
                    + progress.collectedCount() + " entries collected");
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        flushPlayerSync(uuid);
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

        long now = System.currentTimeMillis();
        if (!progress.markCollected(entryId, now)) {
            return false;
        }

        dirty.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(entryId, now);

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
        dirty.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(entryId, now);
        return true;
    }

    public void grant(UUID uuid, int entryId) {
        long now = System.currentTimeMillis();
        PlayerProgress progress = online.get(uuid);
        if (progress != null) {
            progress.markCollected(entryId, now);
        }
        dirty.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(entryId, now);
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
            online.remove(uuid);
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

    public int collectedCount(UUID uuid) {
        PlayerProgress progress = online.get(uuid);
        return progress == null ? 0 : progress.collectedCount();
    }

    public int collectedCount(UUID uuid, Category category) {
        PlayerProgress progress = online.get(uuid);
        if (progress == null) return 0;
        int count = 0;
        for (Entry entry : registry.byCategory(category)) {
            if (progress.isCollected(entry.id())) count++;
        }
        return count;
    }

    private void flushDirty() {
        if (dirty.isEmpty()) return;
        Map<UUID, Map<Integer, Long>> snapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<Integer, Long>> e : dirty.entrySet()) {
            Map<Integer, Long> copy = new HashMap<>(e.getValue());
            if (!copy.isEmpty()) {
                snapshot.put(e.getKey(), copy);
            }
        }
        dirty.clear();
        if (!snapshot.isEmpty()) {
            dataStore.batchInsertCollected(snapshot);
        }
    }

    private void flushPlayerSync(UUID uuid) {
        Map<Integer, Long> pending = dirty.remove(uuid);
        if (pending == null || pending.isEmpty()) return;
        Map<UUID, Map<Integer, Long>> single = Map.of(uuid, pending);
        dataStore.batchInsertCollected(single);
    }

    private void flushAllSync() {
        if (dirty.isEmpty()) return;
        Map<UUID, Map<Integer, Long>> snapshot = new HashMap<>(dirty);
        dirty.clear();
        dataStore.batchInsertCollected(snapshot);
    }
}
