package dev.oakheart.oaklog.enumeration;

import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.SetDefinition;
import dev.oakheart.oaklog.model.SetMatcher;
import dev.oakheart.oaklog.registry.EntryRegistry;
import dev.oakheart.oaklog.storage.DataStore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SetComputer {

    private final DataStore dataStore;
    private final EntryRegistry registry;

    public SetComputer(DataStore dataStore, EntryRegistry registry) {
        this.dataStore = dataStore;
        this.registry = registry;
    }

    /**
     * Recomputes set membership from the current entry registry and the given
     * set definitions, writes it to entry_sets, and refreshes the registry's
     * in-memory mirror.
     */
    public int recompute(List<SetDefinition> sets) {
        Map<Integer, Set<String>> memberships = new HashMap<>();

        for (Entry entry : registry.all()) {
            if (entry.isRetired()) continue;
            for (SetDefinition def : sets) {
                for (SetMatcher matcher : def.matchers()) {
                    if (matcher.matches(entry)) {
                        memberships.computeIfAbsent(entry.id(), k -> new HashSet<>()).add(def.id());
                        break;
                    }
                }
            }
        }

        dataStore.replaceEntrySets(memberships);
        registry.replaceSets(memberships);

        int membershipsCount = memberships.values().stream().mapToInt(Set::size).sum();
        return membershipsCount;
    }
}
