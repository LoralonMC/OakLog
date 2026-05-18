package dev.oakheart.oaklog.registry;

import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.util.EntryKey;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EntryRegistry {

    private final Map<Integer, Entry> entriesById = new HashMap<>();
    private final Map<String, Integer> entryIdByNamespacedKey = new HashMap<>();
    private final Map<Material, Integer> entryIdByVanillaMaterial = new HashMap<>();
    private final EnumMap<Category, List<Integer>> entryIdsByCategory = new EnumMap<>(Category.class);
    private final Map<Integer, Set<String>> setsByEntryId = new HashMap<>();
    private final Map<String, List<Integer>> entriesBySetId = new HashMap<>();
    private int liveCount;

    public synchronized void replaceAll(List<Entry> entries) {
        entriesById.clear();
        entryIdByNamespacedKey.clear();
        entryIdByVanillaMaterial.clear();
        entryIdsByCategory.clear();
        for (Category category : Category.values()) {
            entryIdsByCategory.put(category, new ArrayList<>());
        }

        int live = 0;
        for (Entry entry : entries) {
            entriesById.put(entry.id(), entry);
            entryIdByNamespacedKey.put(entry.namespacedKey(), entry.id());
            entryIdsByCategory.get(entry.category()).add(entry.id());

            if (!entry.isRetired()) {
                live++;
                if (EntryKey.VANILLA.equals(entry.namespace())) {
                    indexVanillaMaterial(entry);
                }
            }
        }
        this.liveCount = live;

        // Sort each category's id list by entry display name (case-insensitive)
        // so vanilla and custom items mix naturally in the GUI.
        java.util.Comparator<Integer> byDisplay = (a, b) -> {
            Entry ea = entriesById.get(a);
            Entry eb = entriesById.get(b);
            if (ea == null || eb == null) return 0;
            return ea.displayName().compareToIgnoreCase(eb.displayName());
        };
        for (List<Integer> ids : entryIdsByCategory.values()) {
            ids.sort(byDisplay);
        }
    }

    private void indexVanillaMaterial(Entry entry) {
        if (entry.category() != Category.BLOCKS && entry.category() != Category.ITEMS) return;
        try {
            Material mat = Material.valueOf(entry.key().toUpperCase(java.util.Locale.ROOT));
            entryIdByVanillaMaterial.put(mat, entry.id());
        } catch (IllegalArgumentException ignored) {
            // Material no longer exists (e.g. removed in a future Minecraft version)
        }
    }

    public synchronized Entry get(int id) {
        return entriesById.get(id);
    }

    public synchronized Integer idFor(String namespace, String key) {
        return entryIdByNamespacedKey.get(EntryKey.join(namespace, key));
    }

    public synchronized Integer idFor(String namespacedKey) {
        return entryIdByNamespacedKey.get(namespacedKey);
    }

    public synchronized Integer idForVanillaMaterial(Material material) {
        return entryIdByVanillaMaterial.get(material);
    }

    public synchronized List<Entry> all() {
        return List.copyOf(entriesById.values());
    }

    public synchronized List<Entry> byCategory(Category category) {
        List<Integer> ids = entryIdsByCategory.getOrDefault(category, Collections.emptyList());
        List<Entry> result = new ArrayList<>(ids.size());
        for (int id : ids) {
            Entry e = entriesById.get(id);
            if (e != null) result.add(e);
        }
        return result;
    }

    public synchronized int liveCount() {
        return liveCount;
    }

    public synchronized int totalCount() {
        return entriesById.size();
    }

    public synchronized int liveCountFor(Category category) {
        int count = 0;
        for (int id : entryIdsByCategory.getOrDefault(category, Collections.emptyList())) {
            Entry e = entriesById.get(id);
            if (e != null && !e.isRetired()) count++;
        }
        return count;
    }

    public synchronized void replaceSets(Map<Integer, Set<String>> memberships) {
        setsByEntryId.clear();
        entriesBySetId.clear();
        for (Map.Entry<Integer, Set<String>> e : memberships.entrySet()) {
            int entryId = e.getKey();
            Set<String> sets = Set.copyOf(e.getValue());
            setsByEntryId.put(entryId, sets);
            for (String setId : sets) {
                entriesBySetId.computeIfAbsent(setId, k -> new ArrayList<>()).add(entryId);
            }
        }
    }

    public synchronized Set<String> setsFor(int entryId) {
        return setsByEntryId.getOrDefault(entryId, Set.of());
    }

    public synchronized List<Integer> entriesInSet(String setId) {
        return List.copyOf(entriesBySetId.getOrDefault(setId, List.of()));
    }

    public synchronized List<Entry> entriesInSetResolved(String setId) {
        List<Integer> ids = entriesBySetId.getOrDefault(setId, List.of());
        List<Entry> out = new ArrayList<>(ids.size());
        for (int id : ids) {
            Entry e = entriesById.get(id);
            if (e != null && !e.isRetired()) out.add(e);
        }
        return out;
    }
}
