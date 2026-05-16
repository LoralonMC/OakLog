package dev.oakheart.oaklog.integration.adapter;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.EntryDescriptor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads {@code custom-entries.yml} for admin-defined PDC-keyed items.
 * Each entry declares one PDC key/value pair; items with a matching PDC are
 * routed to that entry.
 */
public class PdcAdapter implements EntryAdapter {

    private final OakLog plugin;
    private final Logger logger;
    private final Path file;

    private List<CustomEntry> entries = List.of();

    public PdcAdapter(OakLog plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.file = plugin.getDataFolder().toPath().resolve("custom-entries.yml");
    }

    @Override
    public String id() {
        return "pdc";
    }

    @Override
    public List<EntryDescriptor> enumerate() {
        load();
        List<EntryDescriptor> out = new ArrayList<>();
        for (CustomEntry ce : entries) {
            out.add(new EntryDescriptor(
                    ce.namespace,
                    ce.key,
                    ce.category,
                    ce.displayName,
                    ce.iconMaterial,
                    ce.iconModelData,
                    null
            ));
        }
        return out;
    }

    @Override
    public String detect(ItemStack stack) {
        if (entries.isEmpty()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        for (CustomEntry ce : entries) {
            String value;
            try {
                value = pdc.get(ce.pdcKey, PersistentDataType.STRING);
            } catch (IllegalArgumentException e) {
                value = null;
            }
            if (value == null) continue;
            if (ce.pdcValue == null || ce.pdcValue.equals("*") || ce.pdcValue.equals(value)) {
                return ce.namespace + ":" + ce.key;
            }
        }
        return null;
    }

    private void load() {
        if (!Files.exists(file)) {
            plugin.saveResource("custom-entries.yml", false);
        }

        List<CustomEntry> parsed = new ArrayList<>();
        try {
            dev.oakheart.config.ConfigManager cfg = dev.oakheart.config.ConfigManager.load(file);
            List<java.util.Map<String, Object>> raw = cfg.getMapList("entries");
            for (int i = 0; i < raw.size(); i++) {
                CustomEntry ce = parseEntry(raw.get(i), i);
                if (ce != null) parsed.add(ce);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load custom-entries.yml", e);
        }
        this.entries = parsed;
        if (!parsed.isEmpty()) {
            logger.info("PDC adapter: loaded " + parsed.size() + " custom entries");
        }
    }

    private CustomEntry parseEntry(java.util.Map<String, Object> map, int index) {
        String namespace = asString(map.get("namespace"));
        String key = asString(map.get("key"));
        String categoryStr = asString(map.get("category"));
        String displayName = asString(map.get("display-name"));
        String iconMaterial = asString(map.get("icon-material"));
        Object iconModelDataObj = map.get("icon-model-data");
        Integer iconModelData = iconModelDataObj instanceof Number n ? n.intValue() : null;
        String pdcKeyRaw = asString(map.get("pdc-key"));
        Object valueObj = map.get("pdc-value");
        String pdcValue = valueObj == null ? null : valueObj.toString();

        if (namespace == null || key == null || categoryStr == null || displayName == null || pdcKeyRaw == null) {
            logger.warning("custom-entries.yml entry #" + index + " is missing required fields; skipping.");
            return null;
        }

        Category category = Category.fromId(categoryStr);
        if (category == null) {
            logger.warning("custom-entries.yml entry #" + index + " has unknown category '" + categoryStr + "'; skipping.");
            return null;
        }

        NamespacedKey pdcKey;
        try {
            String[] parts = pdcKeyRaw.split(":", 2);
            pdcKey = new NamespacedKey(parts[0].toLowerCase(Locale.ROOT),
                    parts.length > 1 ? parts[1] : pdcKeyRaw);
        } catch (Exception e) {
            logger.warning("custom-entries.yml entry #" + index + " has invalid PDC key '" + pdcKeyRaw + "'; skipping.");
            return null;
        }

        return new CustomEntry(namespace.toLowerCase(Locale.ROOT), key.toLowerCase(Locale.ROOT),
                category, displayName, iconMaterial, iconModelData, pdcKey, pdcValue);
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private record CustomEntry(
            String namespace,
            String key,
            Category category,
            String displayName,
            String iconMaterial,
            Integer iconModelData,
            NamespacedKey pdcKey,
            String pdcValue
    ) {}
}
