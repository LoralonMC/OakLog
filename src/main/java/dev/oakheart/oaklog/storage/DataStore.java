package dev.oakheart.oaklog.storage;

import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.EntryDescriptor;
import dev.oakheart.oaklog.model.PlayerProgress;
import dev.oakheart.oaklog.model.RecentEntry;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DataStore {

    boolean initialize();

    int getSchemaVersion();

    void close();

    // --- Entry registry CRUD ---

    List<Entry> loadAllEntries();

    /**
     * Inserts new entries within a single transaction.
     * Returns the inserted entries with their assigned numeric IDs in the same order.
     */
    List<Entry> insertEntries(List<EntryDescriptor> descriptors);

    /**
     * Updates display/icon/detection-meta fields for existing entries. Used to refresh
     * display data when adapters re-enumerate after a reload. Does not touch retirement.
     */
    void updateEntryDisplay(int id, String displayName, String iconMaterial, Integer iconModelData, String detectionMeta);

    /**
     * Marks the given entry IDs as retired (sets retired_at to the current time).
     */
    void retireEntries(Collection<Integer> ids);

    /**
     * Clears the retired_at marker on the given entry IDs (they have reappeared).
     */
    void unretireEntries(Collection<Integer> ids);

    // --- Per-player progress ---

    /**
     * Loads collected entry IDs and their collection timestamps for a player.
     */
    Map<Integer, Long> loadCollected(UUID playerUuid);

    /**
     * Loads the N most recent collections for a player, ordered newest first.
     */
    List<RecentEntry> loadRecent(UUID playerUuid, int limit);

    /**
     * Loads the toast preference for a player. Returns null if the player has
     * never saved a setting (caller should apply the configured default).
     */
    PlayerProgress.ToastState loadToastState(UUID playerUuid);

    /**
     * Saves the toast preference for a player.
     */
    void saveToastState(UUID playerUuid, PlayerProgress.ToastState state);

    /**
     * Inserts the given (player, entry, timestamp) tuples in a single transaction.
     * Existing rows for the same (player, entry) are left untouched (INSERT OR IGNORE).
     */
    void batchInsertCollected(Map<UUID, Map<Integer, Long>> dirtyByPlayer);

    /**
     * Deletes a single collected row.
     */
    void deleteCollected(UUID playerUuid, int entryId);

    /**
     * Deletes all collected rows for the given player.
     */
    int deleteAllCollected(UUID playerUuid);
}
