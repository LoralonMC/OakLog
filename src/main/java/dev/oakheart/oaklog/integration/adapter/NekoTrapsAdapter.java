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
 * NekoTraps integration. The plugin has a single "Animal Trap" item, exposed
 * via {@code Manager.getItemTrap()} and {@code Manager.isTrap(ItemStack)}.
 * One entry is registered at startup.
 */
public class NekoTrapsAdapter implements EntryAdapter {

    private static final String NAMESPACE = "nekotraps";
    private static final String ENTRY_KEY = "animal_trap";
    private static final String FULL_KEY = NAMESPACE + ":" + ENTRY_KEY;

    private final OakLog plugin;
    private final Logger logger;

    private Plugin host;
    private Object manager;
    private Method getItemTrapMethod;
    private Method isTrapMethod;
    private ItemStack cachedIcon;

    public NekoTrapsAdapter(OakLog plugin) {
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
            Method getManager = host.getClass().getMethod("getManager");
            manager = getManager.invoke(host);
            if (manager == null) return false;
            getItemTrapMethod = manager.getClass().getMethod("getItemTrap");
            isTrapMethod = manager.getClass().getMethod("isTrap", ItemStack.class);
            return true;
        } catch (Throwable t) {
            logger.log(Level.FINE, "NekoTraps adapter unavailable: " + t.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public List<EntryDescriptor> enumerate() {
        if (manager == null) return List.of();
        try {
            Object built = getItemTrapMethod.invoke(manager);
            String materialName = "STRING";
            String displayName = "Animal Trap";
            if (built instanceof ItemStack stack) {
                cachedIcon = stack;
                materialName = stack.getType().name();
                String live = liveDisplayName(stack);
                if (live != null) displayName = live;
            }
            logger.info("NekoTraps adapter: enumerated 1 trap item");
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
            logger.log(Level.WARNING, "NekoTraps enumerate failed", t);
            return List.of();
        }
    }

    @Override
    public String detect(ItemStack stack) {
        if (manager == null || isTrapMethod == null || stack == null) return null;
        try {
            Object result = isTrapMethod.invoke(manager, stack);
            if (result instanceof Boolean b && b) {
                // Refresh icon cache with whatever the player held — captures
                // any config-driven changes since startup.
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
        for (Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
            if (p.getClass().getName().startsWith("net.kokoricraft.nekotraps")) return p;
        }
        return null;
    }
}
