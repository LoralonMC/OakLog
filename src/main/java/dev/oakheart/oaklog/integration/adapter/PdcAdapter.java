package dev.oakheart.oaklog.integration.adapter;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.EntryDescriptor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PDC-driven custom item adapter with two modes:
 * <ul>
 *   <li><b>Explicit entries</b> declared under {@code entries:} in
 *       custom-entries.yml. Pre-registered at startup; full control over
 *       namespace, key, display name, icon.</li>
 *   <li><b>Auto-discover patterns</b> declared under {@code auto-discover:}.
 *       Watches a list of PDC keys; the first time a player picks up an item
 *       carrying one of those keys, a new entry is auto-registered using the
 *       item's own display name, icon material, and meta. Works out of the
 *       box for any PDC-tagged plugin (OakPets, OakTags, OakTools,
 *       PhoenixCrates, Shopkeepers, NekoTraps, etc.).</li>
 * </ul>
 */
public class PdcAdapter implements EntryAdapter {

    private final OakLog plugin;
    private final Logger logger;
    private final Path file;

    private List<CustomEntry> explicit = List.of();
    private List<AutoDiscoverPattern> autoDiscover = List.of();

    // Per-id cache of the live ItemStack the player carried when an entry
    // was auto-discovered. Used so the GUI / toast show the original icon.
    private final Map<String, ItemStack> iconCache = new ConcurrentHashMap<>();

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
        for (CustomEntry ce : explicit) {
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
        if (stack == null || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Explicit entries first
        for (CustomEntry ce : explicit) {
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

        // Auto-discover next
        for (AutoDiscoverPattern p : autoDiscover) {
            String value;
            try {
                value = pdc.get(p.pdcKey, PersistentDataType.STRING);
            } catch (IllegalArgumentException e) {
                value = null;
            }
            if (value == null || value.isBlank()) continue;
            String id = value.toLowerCase(Locale.ROOT);

            String key = p.namespace + ":" + id;
            iconCache.putIfAbsent(id + "@" + p.namespace, stack.clone());

            // Register the entry dynamically if not yet known.
            ensureRegistered(p, id, stack);
            return key;
        }
        return null;
    }

    @Override
    public Optional<ItemStack> resolveLiveIcon(String entryNamespacedKey) {
        if (entryNamespacedKey == null) return Optional.empty();
        int colon = entryNamespacedKey.indexOf(':');
        if (colon < 0) return Optional.empty();
        String ns = entryNamespacedKey.substring(0, colon);
        String id = entryNamespacedKey.substring(colon + 1);
        ItemStack cached = iconCache.get(id + "@" + ns);
        return cached == null ? Optional.empty() : Optional.of(cached.clone());
    }

    private synchronized void ensureRegistered(AutoDiscoverPattern pattern, String id, ItemStack stack) {
        String namespacedKey = pattern.namespace + ":" + id;
        if (plugin.getEntryRegistry().idFor(namespacedKey) != null) {
            return;
        }
        String display = extractDisplayName(stack, id);
        String materialName = stack.getType().name();
        Integer cmd = null;
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasCustomModelDataComponent()) {
                var floats = meta.getCustomModelDataComponent().getFloats();
                if (!floats.isEmpty()) {
                    cmd = floats.get(0).intValue();
                }
            }
        } catch (Throwable ignored) {}

        EntryDescriptor descriptor = new EntryDescriptor(
                pattern.namespace,
                id,
                pattern.category,
                display,
                materialName,
                cmd,
                null
        );

        List<Entry> inserted = plugin.getDataStore().insertEntries(List.of(descriptor));
        if (inserted.isEmpty()) return;
        Entry entry = inserted.get(0);
        plugin.getEntryRegistry().addLive(entry);
        plugin.debug("Auto-discovered entry: " + namespacedKey + " (display='" + display + "')");
    }

    private String extractDisplayName(ItemStack stack, String fallbackId) {
        try {
            if (stack.hasItemMeta()) {
                ItemMeta meta = stack.getItemMeta();
                Component name = meta.displayName();
                if (name == null) {
                    try { name = meta.itemName(); }
                    catch (NoSuchMethodError ignored) {}
                }
                if (name != null) {
                    String plain = PlainTextComponentSerializer.plainText().serialize(name).trim();
                    if (!plain.isEmpty()) return plain;
                }
            }
        } catch (Throwable ignored) {}
        return capitalize(fallbackId.replace('_', ' '));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length());
        boolean boundary = true;
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c)) { out.append(c); boundary = true; continue; }
            out.append(boundary ? Character.toUpperCase(c) : Character.toLowerCase(c));
            boundary = false;
        }
        return out.toString();
    }

    private void load() {
        if (!Files.exists(file)) {
            plugin.saveResource("custom-entries.yml", false);
        }
        List<CustomEntry> parsedExplicit = new ArrayList<>();
        List<AutoDiscoverPattern> parsedAuto = new ArrayList<>();
        try {
            dev.oakheart.config.ConfigManager cfg = dev.oakheart.config.ConfigManager.load(file);

            // entries: explicit, fully-configured
            List<Map<String, Object>> entriesList = cfg.getMapList("entries");
            for (int i = 0; i < entriesList.size(); i++) {
                CustomEntry ce = parseExplicit(entriesList.get(i), i);
                if (ce != null) parsedExplicit.add(ce);
            }

            // auto-discover: dynamic registration patterns
            List<Map<String, Object>> autoList = cfg.getMapList("auto-discover");
            for (int i = 0; i < autoList.size(); i++) {
                AutoDiscoverPattern ad = parseAutoDiscover(autoList.get(i), i);
                if (ad != null) parsedAuto.add(ad);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load custom-entries.yml", e);
        }
        this.explicit = parsedExplicit;
        this.autoDiscover = parsedAuto;
        if (!parsedExplicit.isEmpty() || !parsedAuto.isEmpty()) {
            logger.info("PDC adapter: " + parsedExplicit.size() + " explicit entries, "
                    + parsedAuto.size() + " auto-discover patterns");
        }
    }

    private CustomEntry parseExplicit(Map<String, Object> map, int index) {
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
            logger.warning("custom-entries.yml entries[" + index + "] missing required fields; skipping.");
            return null;
        }
        Category category = Category.fromId(categoryStr);
        if (category == null) {
            logger.warning("custom-entries.yml entries[" + index + "] has unknown category '"
                    + categoryStr + "'; skipping.");
            return null;
        }
        NamespacedKey pdcKey;
        try {
            String[] parts = pdcKeyRaw.split(":", 2);
            pdcKey = new NamespacedKey(parts[0].toLowerCase(Locale.ROOT),
                    parts.length > 1 ? parts[1] : pdcKeyRaw);
        } catch (Exception e) {
            logger.warning("custom-entries.yml entries[" + index + "] has invalid PDC key '"
                    + pdcKeyRaw + "'; skipping.");
            return null;
        }
        return new CustomEntry(namespace.toLowerCase(Locale.ROOT), key.toLowerCase(Locale.ROOT),
                category, displayName, iconMaterial, iconModelData, pdcKey, pdcValue);
    }

    private AutoDiscoverPattern parseAutoDiscover(Map<String, Object> map, int index) {
        String namespace = asString(map.get("namespace"));
        String pdcKeyRaw = asString(map.get("pdc-key"));
        String categoryStr = asString(map.get("category"));
        if (categoryStr == null) categoryStr = "items";

        if (namespace == null || pdcKeyRaw == null) {
            logger.warning("custom-entries.yml auto-discover[" + index + "] missing namespace or pdc-key; skipping.");
            return null;
        }
        Category category = Category.fromId(categoryStr);
        if (category == null) {
            logger.warning("custom-entries.yml auto-discover[" + index + "] has unknown category '"
                    + categoryStr + "'; skipping.");
            return null;
        }
        NamespacedKey pdcKey;
        try {
            String[] parts = pdcKeyRaw.split(":", 2);
            pdcKey = new NamespacedKey(parts[0].toLowerCase(Locale.ROOT),
                    parts.length > 1 ? parts[1] : pdcKeyRaw);
        } catch (Exception e) {
            logger.warning("custom-entries.yml auto-discover[" + index + "] has invalid PDC key '"
                    + pdcKeyRaw + "'; skipping.");
            return null;
        }
        return new AutoDiscoverPattern(namespace.toLowerCase(Locale.ROOT), pdcKey, category);
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

    private record AutoDiscoverPattern(
            String namespace,
            NamespacedKey pdcKey,
            Category category
    ) {}
}
