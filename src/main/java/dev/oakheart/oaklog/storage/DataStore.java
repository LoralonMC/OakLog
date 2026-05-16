package dev.oakheart.oaklog.storage;

import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.EntryDescriptor;

import java.util.Collection;
import java.util.List;

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
}
