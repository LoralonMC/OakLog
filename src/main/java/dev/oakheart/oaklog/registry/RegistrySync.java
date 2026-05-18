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

        List<EntryDescriptor> raw = new ArrayList<>(vanillaEnumerator.enumerate());
        raw.addAll(adapterRegistry.enumerateAll());

        // Dedupe by namespacedKey AND filter against excluded-entries patterns.
        List<String> excluded = exclusionPatterns();
        List<EntryDescriptor> live = new ArrayList<>(raw.size());
        Set<String> seen = new HashSet<>();
        int duplicateCount = 0;
        int excludedCount = 0;
        for (EntryDescriptor d : raw) {
            String nk = EntryKey.join(d.namespace(), d.key());
            if (matchesExcluded(nk, excluded)) {
                excludedCount++;
                continue;
            }
            if (seen.add(nk)) {
                live.add(d);
            } else {
                if (duplicateCount < 10) {
                    logger.warning("Duplicate enumerator entry for " + nk + " (" + d.category() + ") — keeping first");
                }
                duplicateCount++;
            }
        }
        if (duplicateCount > 10) {
            logger.warning("...and " + (duplicateCount - 10) + " more duplicates suppressed.");
        }
        if (excludedCount > 0) {
            logger.info("Excluded " + excludedCount + " entries via detection.excluded-entries patterns");
        }

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

    private List<String> exclusionPatterns() {
        try {
            // Pull the config off the data store's plugin reference via the
            // package-internal hook in OakLog. We avoid a direct ConfigManager
            // dep here to keep RegistrySync's constructor signature stable.
            return ((dev.oakheart.oaklog.storage.SqliteStore) dataStore)
                    .configManagerHookForRegistrySync().getExcludedEntries();
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static boolean matchesExcluded(String namespacedKey, List<String> patterns) {
        if (patterns.isEmpty()) return false;
        String lower = namespacedKey.toLowerCase(java.util.Locale.ROOT);
        for (String pattern : patterns) {
            if (EntryKey.globMatches(pattern, lower)) return true;
        }
        return false;
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
