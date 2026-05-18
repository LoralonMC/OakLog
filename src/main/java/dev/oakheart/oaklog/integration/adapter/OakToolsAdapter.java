package dev.oakheart.oaklog.integration.adapter;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.EntryDescriptor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
 * Integration with OakTools via reflection on its ToolType enum and
 * ItemFactory. Enumerates every tool type at startup so they appear in the
 * log immediately.
 */
public class OakToolsAdapter implements EntryAdapter {

    private static final String NAMESPACE = "oaktools";
    private static final NamespacedKey PDC_KEY =
            new NamespacedKey(NAMESPACE, "tool_type");

    private final OakLog plugin;
    private final Logger logger;

    private Plugin host;
    private Object itemFactory;
    private Method createToolMethod;
    private Object[] toolTypeValues;
    private Method toolTypeNameMethod;

    private final Map<String, ItemStack> iconCache = new ConcurrentHashMap<>();

    public OakToolsAdapter(OakLog plugin) {
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
            Method getItemFactory = host.getClass().getMethod("getItemFactory");
            itemFactory = getItemFactory.invoke(host);
            if (itemFactory == null) return false;

            Class<?> toolTypeClass = Class.forName("dev.oakheart.oaktools.model.ToolType", false, cl);
            toolTypeValues = (Object[]) toolTypeClass.getMethod("values").invoke(null);
            toolTypeNameMethod = toolTypeClass.getMethod("name");

            createToolMethod = itemFactory.getClass().getMethod("createTool", toolTypeClass);
            return true;
        } catch (Throwable t) {
            logger.log(Level.FINE, "OakTools adapter unavailable: " + t.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public List<EntryDescriptor> enumerate() {
        if (itemFactory == null || toolTypeValues == null) return List.of();
        if (nexoNotReady()) {
            logger.fine("OakTools adapter: deferring enumeration until Nexo loads");
            return List.of();
        }
        try {
            List<EntryDescriptor> out = new ArrayList<>();
            for (Object toolType : toolTypeValues) {
                String enumName = (String) toolTypeNameMethod.invoke(toolType);
                String id = enumName.toLowerCase(Locale.ROOT);

                // SICKLE has six per-material variants in Nexo (wooden,
                // stone, iron, golden, diamond, netherite) but OakTools
                // models it as a single ToolType. Skip it so the Nexo
                // sickle entries surface in the log instead of one
                // untextured "Sickle".
                if (id.equals("sickle")) continue;

                ItemStack tool = buildTool(toolType);
                String displayName;
                String materialName;
                if (tool != null && tool.hasItemMeta()) {
                    iconCache.put(id, tool);
                    ItemMeta meta = tool.getItemMeta();
                    net.kyori.adventure.text.Component name = meta.displayName();
                    if (name == null) {
                        try { name = meta.itemName(); } catch (NoSuchMethodError ignored) {}
                    }
                    displayName = name == null
                            ? humanize(id)
                            : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                    .plainText().serialize(name).trim();
                    if (displayName.isEmpty()) displayName = humanize(id);
                    materialName = tool.getType().name();
                } else {
                    displayName = humanize(id);
                    materialName = "STICK";
                }

                out.add(new EntryDescriptor(
                        NAMESPACE,
                        id,
                        Category.ITEMS,
                        displayName,
                        materialName,
                        null,
                        null
                ));
            }
            logger.info("OakTools adapter: enumerated " + out.size() + " tools");
            return out;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "OakTools enumerate failed", t);
            return List.of();
        }
    }

    @Override
    public String detect(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) return null;
            String toolType = meta.getPersistentDataContainer().get(PDC_KEY, PersistentDataType.STRING);
            if (toolType == null || toolType.isBlank()) return null;
            return NAMESPACE + ":" + toolType.toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
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

    private ItemStack buildTool(Object toolType) {
        try {
            Object result = createToolMethod.invoke(itemFactory, toolType);
            return result instanceof ItemStack stack ? stack : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String humanize(String s) {
        String[] parts = s.split("_");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append(' ');
            String p = parts[i];
            if (p.isEmpty()) continue;
            out.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) out.append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private Plugin findHostPlugin() {
        for (Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
            if (p.getClass().getName().startsWith("dev.oakheart.oaktools")) return p;
        }
        return null;
    }

    private boolean nexoNotReady() {
        if (plugin.getServer().getPluginManager().getPlugin("Nexo") == null) return false;
        try {
            return com.nexomc.nexo.api.NexoItems.itemNames().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }
}
