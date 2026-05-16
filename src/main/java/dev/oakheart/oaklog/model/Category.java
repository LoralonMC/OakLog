package dev.oakheart.oaklog.model;

import java.util.Locale;

public enum Category {
    BLOCKS,
    ITEMS,
    ENTITIES,
    BIOMES,
    STRUCTURES,
    EFFECTS,
    ENCHANTMENTS;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Category fromId(String id) {
        if (id == null) return null;
        try {
            return Category.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
