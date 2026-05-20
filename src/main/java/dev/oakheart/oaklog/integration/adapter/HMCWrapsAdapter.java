package dev.oakheart.oaklog.integration.adapter;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.EntryDescriptor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HMCWraps integration. Each wrap defined in
 * {@code plugins/HMCWraps/wraps/*.yml} becomes one entry. A wrap is
 * "collected" when the physical wrap token enters the player's inventory.
 * <p>
 * Detection uses {@code Wrapper.getPhysicalWrapper(ItemStack)} which returns
 * the wrap UUID stamped into a physical wrap token, or {@code null} for any
 * other item (including items that have a wrap APPLIED — those are detected
 * via {@code getWrap(ItemStack)} and intentionally ignored here).
 * <p>
 * Icons are built via {@code SerializableItem.toItem(HMCWraps, Player,
 * Material)} on the wrap's {@code PhysicalWrap} so the Nexo skin / display
 * name / lore configured in the wrap YAML is reproduced exactly.
 */
public class HMCWrapsAdapter implements EntryAdapter {

    private static final String NAMESPACE = "hmcwraps";

    private final OakLog plugin;
    private final Logger logger;

    private Plugin host;
    private Object wrapper;
    private Object wrapsLoader;
    private Object collectionHelper;

    private Class<?> hmcWrapsCls;

    private Method getPhysicalWrapper;
    private Method getWraps;
    private Method wrapGetPhysical;
    private Method collectionHelperGetMaterial;
    private Method serializableItemToItem;
    private Method wrapGetWrapName;
    private Method serializableItemGetName;

    private final Map<String, ItemStack> iconCache = new ConcurrentHashMap<>();

    public HMCWrapsAdapter(OakLog plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public String id() {
        return NAMESPACE;
    }

    @Override
    public boolean isAvailable() {
        host = plugin.getServer().getPluginManager().getPlugin("HMCWraps");
        if (host == null) return false;
        try {
            ClassLoader cl = host.getClass().getClassLoader();

            hmcWrapsCls = Class.forName("de.skyslycer.hmcwraps.HMCWraps", false, cl);
            Class<?> wrapperCls = Class.forName("de.skyslycer.hmcwraps.wrap.Wrapper", false, cl);
            Class<?> wrapsLoaderCls = Class.forName("de.skyslycer.hmcwraps.wrap.WrapsLoader", false, cl);
            Class<?> wrapCls = Class.forName("de.skyslycer.hmcwraps.serialization.wrap.Wrap", false, cl);
            Class<?> physicalWrapCls = Class.forName(
                    "de.skyslycer.hmcwraps.serialization.wrap.PhysicalWrap", false, cl);
            Class<?> serializableItemCls = Class.forName(
                    "de.skyslycer.hmcwraps.serialization.item.SerializableItem", false, cl);
            Class<?> collectionHelperCls = Class.forName(
                    "de.skyslycer.hmcwraps.wrap.CollectionHelper", false, cl);

            wrapper = hmcWrapsCls.getMethod("getWrapper").invoke(host);
            wrapsLoader = hmcWrapsCls.getMethod("getWrapsLoader").invoke(host);
            collectionHelper = hmcWrapsCls.getMethod("getCollectionHelper").invoke(host);
            if (wrapper == null || wrapsLoader == null || collectionHelper == null) return false;

            getPhysicalWrapper = wrapperCls.getMethod("getPhysicalWrapper", ItemStack.class);
            getWraps = wrapsLoaderCls.getMethod("getWraps");
            wrapGetPhysical = wrapCls.getMethod("getPhysical");
            collectionHelperGetMaterial = collectionHelperCls.getMethod("getMaterial", wrapCls);
            serializableItemToItem = serializableItemCls.getMethod(
                    "toItem", hmcWrapsCls, Player.class, Material.class);
            wrapGetWrapName = wrapCls.getMethod("getWrapName");
            serializableItemGetName = serializableItemCls.getMethod("getName");
            // Touch PhysicalWrap so a missing class fails isAvailable instead of enumerate
            physicalWrapCls.getSuperclass();
            return true;
        } catch (Throwable t) {
            logger.log(Level.FINE, "HMCWraps adapter unavailable: "
                    + t.getClass().getSimpleName() + " " + t.getMessage());
            return false;
        }
    }

    @Override
    public List<EntryDescriptor> enumerate() {
        if (wrapper == null || wrapsLoader == null) return List.of();
        // Most wraps reference Nexo items as their visual source. If Nexo
        // hasn't loaded yet, every toItem() call falls back to plain material.
        // Return empty and let NexoLoadListener trigger a re-sync once Nexo
        // finishes loading.
        if (nexoNotReady()) {
            logger.info("HMCWraps adapter: deferring — Nexo not ready yet");
            return List.of();
        }
        iconCache.clear();
        try {
            Object wrapsObj = getWraps.invoke(wrapsLoader);
            if (!(wrapsObj instanceof Map<?, ?> wraps) || wraps.isEmpty()) {
                logger.info("HMCWraps adapter: no wraps defined");
                return List.of();
            }

            List<EntryDescriptor> out = new ArrayList<>();
            for (Map.Entry<?, ?> e : wraps.entrySet()) {
                String uuid = String.valueOf(e.getKey());
                Object wrap = e.getValue();
                if (wrap == null || uuid.isBlank()) continue;

                Material baseMaterial = resolveMaterial(wrap);
                if (baseMaterial == null) baseMaterial = Material.PAPER;

                ItemStack icon = buildIcon(wrap, baseMaterial);
                if (icon == null || icon.getType() == Material.AIR) {
                    continue;
                }
                iconCache.put(uuid, icon);

                String displayName = extractDisplayName(wrap, icon, uuid);

                out.add(new EntryDescriptor(
                        NAMESPACE,
                        uuid.toLowerCase(Locale.ROOT),
                        Category.ITEMS,
                        displayName,
                        icon.getType().name(),
                        null,
                        null
                ));
            }
            logger.info("HMCWraps adapter: enumerated " + out.size() + " wraps");
            return out;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "HMCWraps enumerate failed", t);
            return List.of();
        }
    }

    @Override
    public String detect(ItemStack stack) {
        if (wrapper == null || getPhysicalWrapper == null) return null;
        if (stack == null || stack.getType() == Material.AIR) return null;
        if (!stack.hasItemMeta()) return null;
        try {
            Object result = getPhysicalWrapper.invoke(wrapper, stack);
            if (!(result instanceof String uuid) || uuid.isBlank()) return null;
            return NAMESPACE + ":" + uuid.toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public Optional<ItemStack> resolveLiveIcon(String entryNamespacedKey) {
        if (entryNamespacedKey == null || !entryNamespacedKey.startsWith(NAMESPACE + ":")) {
            return Optional.empty();
        }
        String uuid = entryNamespacedKey.substring(NAMESPACE.length() + 1);
        ItemStack cached = iconCache.get(uuid);
        return cached == null ? Optional.empty() : Optional.of(cached.clone());
    }

    private Material resolveMaterial(Object wrap) {
        try {
            Object mat = collectionHelperGetMaterial.invoke(collectionHelper, wrap);
            if (mat instanceof Material m && m != Material.AIR) return m;
        } catch (Throwable ignored) {}
        return null;
    }

    private ItemStack buildIcon(Object wrap, Material baseMaterial) {
        // Prefer the physical wrap section (the consumable wrap token's
        // visual). Fall back to the wrap's top-level fields when no
        // dedicated physical: block exists.
        try {
            Object physical = wrapGetPhysical.invoke(wrap);
            if (physical != null) {
                Object built = serializableItemToItem.invoke(physical, host, null, baseMaterial);
                if (built instanceof ItemStack stack && stack.getType() != Material.AIR) {
                    return stack;
                }
            }
        } catch (Throwable t) {
            logger.log(Level.FINE, "HMCWraps: physical.toItem failed for wrap, falling back", t);
        }
        try {
            Object built = serializableItemToItem.invoke(wrap, host, null, baseMaterial);
            if (built instanceof ItemStack stack) return stack;
        } catch (Throwable t) {
            logger.log(Level.FINE, "HMCWraps: wrap.toItem failed", t);
        }
        return null;
    }

    private String extractDisplayName(Object wrap, ItemStack icon, String fallback) {
        try {
            if (icon != null && icon.hasItemMeta()) {
                ItemMeta meta = icon.getItemMeta();
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
        try {
            Object physical = wrapGetPhysical.invoke(wrap);
            String raw = null;
            if (physical != null) {
                raw = (String) serializableItemGetName.invoke(physical);
            }
            if (raw == null || raw.isBlank()) {
                raw = (String) wrapGetWrapName.invoke(wrap);
            }
            if (raw == null || raw.isBlank()) {
                raw = (String) serializableItemGetName.invoke(wrap);
            }
            if (raw != null && !raw.isBlank()) {
                Component parsed = MiniMessage.miniMessage().deserialize(raw);
                String plain = PlainTextComponentSerializer.plainText().serialize(parsed).trim();
                if (!plain.isEmpty()) return plain;
            }
        } catch (Throwable ignored) {}
        return capitalize(fallback);
    }

    private boolean nexoNotReady() {
        if (plugin.getServer().getPluginManager().getPlugin("Nexo") == null) return false;
        try {
            return com.nexomc.nexo.api.NexoItems.itemNames().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length());
        boolean boundary = true;
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c) || c == '_' || c == '-') {
                out.append(' ');
                boundary = true;
            } else {
                out.append(boundary ? Character.toUpperCase(c) : Character.toLowerCase(c));
                boundary = false;
            }
        }
        return out.toString();
    }
}
