package dev.oakheart.oaklog.integration.adapter;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.EntryDescriptor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shopkeepers integration. The configured shop creation item is exposed via
 * the (non-API but public-static)
 * {@code com.nisovin.shopkeepers.shopcreation.ShopCreationItem} class.
 * The adapter reflects on {@code create()} to build the canonical item for
 * the log icon and {@code isShopCreationItem(ItemStack)} for detection.
 */
public class ShopkeepersAdapter implements EntryAdapter {

    private static final String NAMESPACE = "shopkeepers";
    private static final String ENTRY_KEY = "shop_creation_item";
    private static final String FULL_KEY = NAMESPACE + ":" + ENTRY_KEY;
    private static final String CLASS_NAME =
            "com.nisovin.shopkeepers.shopcreation.ShopCreationItem";

    private final OakLog plugin;
    private final Logger logger;

    private Plugin host;
    private Method createMethod;
    private Method isShopCreationItemMethod;
    private ItemStack cachedIcon;

    public ShopkeepersAdapter(OakLog plugin) {
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
            Class<?> creationClass = Class.forName(CLASS_NAME, false, cl);
            createMethod = creationClass.getMethod("create");
            isShopCreationItemMethod = creationClass.getMethod("isShopCreationItem", ItemStack.class);
            return true;
        } catch (Throwable t) {
            logger.log(Level.FINE, "Shopkeepers adapter unavailable: "
                    + t.getClass().getSimpleName() + " " + t.getMessage());
            return false;
        }
    }

    @Override
    public List<EntryDescriptor> enumerate() {
        if (createMethod == null) return List.of();
        try {
            Object result = createMethod.invoke(null);
            String materialName = "VILLAGER_SPAWN_EGG";
            String displayName = "Shop Creation Item";
            if (result instanceof ItemStack stack) {
                cachedIcon = stack;
                materialName = stack.getType().name();
                String live = liveDisplayName(stack);
                if (live != null) displayName = live;
            }
            logger.info("Shopkeepers adapter: enumerated 1 shop creation item");
            return List.of(new EntryDescriptor(
                    NAMESPACE,
                    ENTRY_KEY,
                    Category.ITEMS,
                    displayName,
                    materialName,
                    null,
                    null
            ));
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Shopkeepers enumerate failed", t);
            return List.of();
        }
    }

    @Override
    public String detect(ItemStack stack) {
        if (isShopCreationItemMethod == null || stack == null) return null;
        if (!stack.hasItemMeta()) return null;
        try {
            Object result = isShopCreationItemMethod.invoke(null, stack);
            if (result instanceof Boolean b && b) {
                cachedIcon = stack.clone();
                return FULL_KEY;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @Override
    public Optional<ItemStack> resolveLiveIcon(String entryNamespacedKey) {
        if (!FULL_KEY.equals(entryNamespacedKey)) return Optional.empty();
        return cachedIcon == null ? Optional.empty() : Optional.of(cachedIcon.clone());
    }

    private String liveDisplayName(ItemStack stack) {
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
                    return plain.isEmpty() ? null : plain;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Plugin findHostPlugin() {
        Plugin p = plugin.getServer().getPluginManager().getPlugin("Shopkeepers");
        if (p != null) return p;
        for (Plugin candidate : plugin.getServer().getPluginManager().getPlugins()) {
            if (candidate.getClass().getName().startsWith("com.nisovin.shopkeepers")) return candidate;
        }
        return null;
    }
}
