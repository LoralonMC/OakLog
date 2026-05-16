package dev.oakheart.oaklog.registry;

import dev.oakheart.oaklog.config.SetsConfig;
import dev.oakheart.oaklog.enumeration.SetComputer;
import dev.oakheart.oaklog.enumeration.VanillaEnumerator;
import dev.oakheart.oaklog.integration.adapter.AdapterRegistry;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.EntryDescriptor;
import dev.oakheart.oaklog.storage.DataStore;
import dev.oakheart.oaklog.util.EntryKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class RegistrySync {

    private final DataStore dataStore;
    private final EntryRegistry registry;
    private final VanillaEnumerator vanillaEnumerator;
    private final AdapterRegistry adapterRegistry;
    private final SetsConfig setsConfig;
    private final SetComputer setComputer;
    private final Logger logger;

    public RegistrySync(DataStore dataStore, EntryRegistry registry,
                        VanillaEnumerator vanillaEnumerator,
                        AdapterRegistry adapterRegistry,
                        SetsConfig setsConfig, SetComputer setComputer,
                        Logger logger) {
        this.dataStore = dataStore;
        this.registry = registry;
        this.vanillaEnumerator = vanillaEnumerator;
        this.adapterRegistry = adapterRegistry;
        this.setsConfig = setsConfig;
        this.setComputer = setComputer;
        this.logger = logger;
    }

    public SyncResult run() {
        List<Entry> existing = dataStore.loadAllEntries();
        Map<String, Entry> existingByKey = new HashMap<>();
        for (Entry e : existing) {
            existingByKey.put(e.namespacedKey(), e);
        }

        List<EntryDescriptor> live = new ArrayList<>(vanillaEnumerator.enumerate());
        live.addAll(adapterRegistry.enumerateAll());

        Set<String> liveKeys = new HashSet<>();
        List<EntryDescriptor> toInsert = new ArrayList<>();
        List<Integer> toUnretire = new ArrayList<>();
        List<DisplayUpdate> toUpdate = new ArrayList<>();

        for (EntryDescriptor d : live) {
            String key = EntryKey.join(d.namespace(), d.key());
            liveKeys.add(key);

            Entry current = existingByKey.get(key);
            if (current == null) {
                toInsert.add(d);
            } else {
                if (current.isRetired()) {
                    toUnretire.add(current.id());
                }
                if (!sameDisplay(current, d)) {
                    toUpdate.add(new DisplayUpdate(current.id(), d));
                }
            }
        }

        List<Integer> toRetire = new ArrayList<>();
        for (Entry e : existing) {
            if (!liveKeys.contains(e.namespacedKey()) && !e.isRetired()) {
                toRetire.add(e.id());
            }
        }

        // Apply DB changes
        List<Entry> inserted = dataStore.insertEntries(toInsert);
        for (DisplayUpdate u : toUpdate) {
            dataStore.updateEntryDisplay(u.id, u.descriptor.displayName(),
                    u.descriptor.iconMaterial(), u.descriptor.iconModelData(),
                    u.descriptor.detectionMeta());
        }
        if (!toUnretire.isEmpty()) {
            dataStore.unretireEntries(toUnretire);
        }
        if (!toRetire.isEmpty()) {
            dataStore.retireEntries(toRetire);
        }

        // Refresh registry from updated DB state
        List<Entry> reloaded = dataStore.loadAllEntries();
        registry.replaceAll(reloaded);

        // Recompute set memberships from the fresh registry
        int memberships = setComputer.recompute(setsConfig.sets());

        SyncResult result = new SyncResult(inserted.size(), toRetire.size(),
                toUnretire.size(), toUpdate.size(), reloaded.size());
        logger.info(String.format(
                "Registry sync complete: %d added, %d retired, %d revived, %d updated, %d total entries (%d live), %d set memberships",
                result.added, result.retired, result.revived, result.updated,
                result.totalEntries, registry.liveCount(), memberships));
        return result;
    }

    private static boolean sameDisplay(Entry existing, EntryDescriptor desc) {
        return safeEquals(existing.displayName(), desc.displayName())
                && safeEquals(existing.iconMaterial(), desc.iconMaterial())
                && safeEquals(existing.iconModelData(), desc.iconModelData())
                && safeEquals(existing.detectionMeta(), desc.detectionMeta());
    }

    private static boolean safeEquals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private record DisplayUpdate(int id, EntryDescriptor descriptor) {}

    public record SyncResult(int added, int retired, int revived, int updated, int totalEntries) {}
}
