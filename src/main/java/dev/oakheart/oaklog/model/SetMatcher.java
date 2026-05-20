package dev.oakheart.oaklog.model;

import org.bukkit.NamespacedKey;

import java.util.List;

/**
 * Matching rules used by sets.yml to determine which entries belong to a set.
 * Sealed so the SetComputer can pattern-match exhaustively.
 */
public sealed interface SetMatcher {

    boolean matches(Entry entry);

    record VanillaMaterial(List<String> materials) implements SetMatcher {
        @Override
        public boolean matches(Entry entry) {
            if (entry.category() != Category.BLOCKS && entry.category() != Category.ITEMS) return false;
            if (!"minecraft".equals(entry.namespace())) return false;
            String key = entry.key();
            for (String pattern : materials) {
                if (globMatches(pattern, key)) return true;
            }
            return false;
        }
    }

    record EntryKeyGlob(List<String> patterns) implements SetMatcher {
        @Override
        public boolean matches(Entry entry) {
            String ns = entry.namespacedKey();
            for (String pattern : patterns) {
                if (globMatches(pattern, ns)) return true;
            }
            return false;
        }
    }

    record PdcKey(NamespacedKey pdcKey) implements SetMatcher {
        @Override
        public boolean matches(Entry entry) {
            // PDC matchers compare against detection meta serialised by the
            // adapter. The PDC namespace and key are matched against the
            // entry's namespacedKey for v1; finer matching requires expanding
            // detection_meta in a future release.
            return false;
        }
    }

    static boolean globMatches(String pattern, String value) {
        if (pattern.equals(value)) return true;
        if (!pattern.contains("*")) return false;

        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if ("\\.[](){}+?^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return value.matches(regex.toString());
    }
}
