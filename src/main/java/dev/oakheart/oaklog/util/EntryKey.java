package dev.oakheart.oaklog.util;

import java.util.Locale;

public final class EntryKey {

    public static final String VANILLA = "minecraft";

    private EntryKey() {}

    public static String normalize(String key) {
        if (key == null) return null;
        return key.toLowerCase(Locale.ROOT).trim();
    }

    public static String join(String namespace, String key) {
        return normalize(namespace) + ":" + normalize(key);
    }

    public static String[] split(String namespacedKey) {
        if (namespacedKey == null) return null;
        int idx = namespacedKey.indexOf(':');
        if (idx < 0) {
            return new String[]{VANILLA, normalize(namespacedKey)};
        }
        return new String[]{
                normalize(namespacedKey.substring(0, idx)),
                normalize(namespacedKey.substring(idx + 1))
        };
    }

    public static String fromMaterial(String materialName) {
        return normalize(materialName);
    }

    public static boolean globMatches(String pattern, String value) {
        if (pattern == null || value == null) return false;
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

    public static String displayNameFromKey(String key) {
        if (key == null || key.isEmpty()) return key;
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            String p = parts[i];
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
