package dev.oakheart.oaklog.integration.adapter;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.EntryDescriptor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PhoenixCrates integration via its public API
 * ({@code com.phoenixplugins.phoenixcrates.api.PhoenixCratesAPI}).
 * <p>
 * PhoenixCrates' API exposes {@code KeysManager.getRegisteredKeys()} returning
 * {@code Key} objects, and {@code Key.getPlainItem().build()} returns the
 * ItemStack form of each key. There's no documented "is this item a key"
 * method, so detection uses a signature
 * ({@code Material} + custom-model-data + plain display name) computed once
 * at startup; pickup-time items get the same signature and look up the id.
 */
public class PhoenixCratesAdapter implements EntryAdapter {

    private static final String NAMESPACE = "phoenixcrates";

    private final OakLog plugin;
    private final Logger logger;

    private Plugin host;
    private Object keysManager;
    private Method getRegisteredKeysMethod;
    private Method keyGetIdentifierMethod;
    private Method keyGetPlainItemMethod;
    private Method itemBuildMethod;

    private final Map<String, ItemStack> iconCache = new ConcurrentHashMap<>();
    // Signature (material|cmd|plain-name) -> entry id, populated at enumerate time
    private final Map<String, String> signatureToId = new HashMap<>();

    public PhoenixCratesAdapter(OakLog plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public String id() {
        return NAMESPACE;
    }

    @Override
    public boolean isAvailable() {
        host = findHostPlugin();
        if (host == null) return false;
        try {
            ClassLoader cl = host.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("com.phoenixplugins.phoenixcrates.api.PhoenixCratesAPI", false, cl);
            keysManager = apiClass.getMethod("getKeysManager").invoke(null);
            if (keysManager == null) return false;

            Class<?> keysManagerClass = Class.forName("com.phoenixplugins.phoenixcrates.api.managers.KeysManager", false, cl);
            getRegisteredKeysMethod = keysManagerClass.getMethod("getRegisteredKeys");

            Class<?> keyClass = Class.forName("com.phoenixplugins.phoenixcrates.api.key.Key", false, cl);
            keyGetIdentifierMethod = findMethod(keyClass, new String[]{"getIdentifier", "identifier"}, new Class<?>[0]);
            keyGetPlainItemMethod = keyClass.getMethod("getPlainItem");

            Class<?> itemClass = Class.forName("com.phoenixplugins.phoenixcrates.api.internal.Item", false, cl);
            itemBuildMethod = itemClass.getMethod("build");

            // Identifier method isn't on the public Key interface; pull from impl.
            if (keyGetIdentifierMethod == null) {
                try {
                    Class<?> implClass = Class.forName("com.phoenixplugins.phoenixcrates.managers.keys.Key", false, cl);
                    keyGetIdentifierMethod = implClass.getMethod("getIdentifier");
                } catch (ClassNotFoundException ignored) {}
            }
            return keyGetPlainItemMethod != null && itemBuildMethod != null;
        } catch (Throwable t) {
            logger.log(Level.FINE, "PhoenixCrates adapter unavailable: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return false;
        }
    }

    @Override
    public List<EntryDescriptor> enumerate() {
        if (keysManager == null) return List.of();
        signatureToId.clear();
        try {
            Object result = getRegisteredKeysMethod.invoke(keysManager);
            if (!(result instanceof Collection<?> coll)) return List.of();

            List<EntryDescriptor> out = new ArrayList<>();
            for (Object key : coll) {
                String identifier = keyGetIdentifierMethod != null
                        ? (String) keyGetIdentifierMethod.invoke(key)
                        : null;
                if (identifier == null || identifier.isBlank()) continue;
                String id = identifier.toLowerCase(Locale.ROOT);

                Object item = keyGetPlainItemMethod.invoke(key);
                if (item == null) continue;
                Object built = itemBuildMethod.invoke(item);
                if (!(built instanceof ItemStack stack)) continue;

                iconCache.put(id, stack);
                String signature = signatureOf(stack);
                if (signature != null) {
                    signatureToId.put(signature, id);
                }

                String displayName = extractDisplayName(stack, identifier);
                out.add(new EntryDescriptor(
                        NAMESPACE,
                        id,
                        Category.ITEMS,
                        displayName,
                        stack.getType() == null ? "TRIPWIRE_HOOK" : stack.getType().name(),
                        null,
                        null
                ));
            }
            logger.info("PhoenixCrates adapter: enumerated " + out.size() + " crate keys");
            return out;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "PhoenixCrates enumerate failed", t);
            return List.of();
        }
    }

    @Override
    public String detect(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        String signature = signatureOf(stack);
        if (signature == null) return null;
        String id = signatureToId.get(signature);
        return id == null ? null : NAMESPACE + ":" + id;
    }

    @Override
    public Optional<ItemStack> resolveLiveIcon(String entryNamespacedKey) {
        if (entryNamespacedKey == null || !entryNamespacedKey.startsWith(NAMESPACE + ":")) {
            return Optional.empty();
        }
        String id = entryNamespacedKey.substring(NAMESPACE.length() + 1);
        ItemStack cached = iconCache.get(id);
        return cached == null ? Optional.empty() : Optional.of(cached.clone());
    }

    private String signatureOf(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        Material material = stack.getType();
        Integer cmd = null;
        String name = null;
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (meta.hasCustomModelData()) cmd = meta.getCustomModelData();
                net.kyori.adventure.text.Component nameComp = meta.displayName();
                if (nameComp == null) {
                    try { nameComp = meta.itemName(); }
                    catch (NoSuchMethodError ignored) {}
                }
                if (nameComp != null) {
                    name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText().serialize(nameComp).trim();
                }
            }
        } catch (Throwable ignored) {}
        return material.name() + "|" + cmd + "|" + (name == null ? "" : name);
    }

    private String extractDisplayName(ItemStack stack, String fallback) {
        try {
            if (stack.hasItemMeta()) {
                ItemMeta meta = stack.getItemMeta();
                net.kyori.adventure.text.Component name = meta.displayName();
                if (name == null) {
                    try { name = meta.itemName(); }
                    catch (NoSuchMethodError ignored) {}
                }
                if (name != null) {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText().serialize(name).trim();
                    if (!plain.isEmpty()) return plain;
                }
            }
        } catch (Throwable ignored) {}
        return capitalize(fallback);
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

    private static Method findMethod(Class<?> cls, String[] names, Class<?>[] params) {
        for (String name : names) {
            try {
                return cls.getMethod(name, params);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private Plugin findHostPlugin() {
        for (Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
            if (p.getClass().getName().startsWith("com.phoenixplugins.phoenixcrates")) return p;
        }
        return null;
    }
}
