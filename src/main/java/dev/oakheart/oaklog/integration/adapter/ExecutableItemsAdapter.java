package dev.oakheart.oaklog.integration.adapter;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.EntryDescriptor;
import dev.oakheart.oaklog.util.EntryKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reflection-based ExecutableItems integration. Resolves the plugin via
 * package-prefix scan rather than plugin-name lookup since the EI plugin can
 * register under several names depending on bundling (ExecutableItems, EI,
 * etc.) and Paper's plugin classloader isolation hides classes from
 * Class.forName called with OakLog's classloader.
 */
public class ExecutableItemsAdapter implements EntryAdapter {

    private static final String NAMESPACE = "executableitems";

    private final OakLog plugin;
    private final Logger logger;

    private Object manager;
    private Method idsListMethod;
    private Method idFromItemMethod;
    private boolean warned;

    public ExecutableItemsAdapter(OakLog plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public String id() {
        return "executableitems";
    }

    @Override
    public boolean isAvailable() {
        Plugin host = findHostPlugin();
        if (host == null) {
            logger.fine("ExecutableItems adapter: no plugin with class in com.ssomar.executableitems found");
            return false;
        }
        try {
            ClassLoader hostLoader = host.getClass().getClassLoader();
            Class<?> apiClass;
            try {
                apiClass = Class.forName("com.ssomar.score.api.executableitems.ExecutableItemsAPI",
                        false, hostLoader);
            } catch (ClassNotFoundException e) {
                logger.log(Level.WARNING,
                        "ExecutableItems found (" + host.getName() + ") but API class "
                        + "com.ssomar.score.api.executableitems.ExecutableItemsAPI not on its classpath — "
                        + "version mismatch. Falling back to no enumeration; pickup detection disabled.");
                return false;
            }
            Method getManagerMethod = apiClass.getMethod("getExecutableItemsManager");
            manager = getManagerMethod.invoke(null);
            if (manager == null) {
                logger.warning("ExecutableItems: getExecutableItemsManager() returned null");
                return false;
            }

            idFromItemMethod = findMethod(manager.getClass(),
                    new String[]{"getExecutableItemIdFromItemStack", "getIdFromItemStack"},
                    new Class<?>[]{ItemStack.class});
            idsListMethod = findMethod(manager.getClass(),
                    new String[]{"getExecutableItemIdsList", "getExecutableItems", "getAllIds"},
                    new Class<?>[0]);

            if (idFromItemMethod == null) {
                logger.warning("ExecutableItems: no compatible detect method found on "
                        + manager.getClass().getName());
                return false;
            }
            logger.info("ExecutableItems adapter resolved via plugin '" + host.getName() + "'");
            return true;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "ExecutableItems adapter unavailable: "
                    + t.getClass().getSimpleName() + " " + t.getMessage());
            return false;
        }
    }

    private Plugin findHostPlugin() {
        for (Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
            String mainClass = p.getClass().getName();
            if (mainClass.startsWith("com.ssomar.executableitems")) {
                return p;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> cls, String[] names, Class<?>[] params) {
        for (String name : names) {
            try {
                return cls.getMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                // try next name
            } catch (Throwable t) {
                // class signature reference can't be loaded; treat as no-match
                return null;
            }
        }
        return null;
    }

    @Override
    public List<EntryDescriptor> enumerate() {
        if (manager == null || idsListMethod == null) return List.of();
        try {
            Object result = idsListMethod.invoke(manager);
            if (!(result instanceof Collection<?> coll)) return List.of();

            List<EntryDescriptor> out = new ArrayList<>();
            for (Object idObj : coll) {
                if (idObj == null) continue;
                String id = idObj.toString().toLowerCase(Locale.ROOT);
                if (id.isBlank()) continue;
                out.add(new EntryDescriptor(
                        NAMESPACE,
                        id,
                        Category.ITEMS,
                        EntryKey.displayNameFromKey(id),
                        "NETHER_STAR",
                        null,
                        null
                ));
            }
            logger.info("ExecutableItems adapter: enumerated " + out.size() + " items");
            return out;
        } catch (Exception e) {
            warnOnce("ExecutableItems enumerate failed: " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public String detect(ItemStack stack) {
        if (manager == null || idFromItemMethod == null) return null;
        try {
            Object result = idFromItemMethod.invoke(manager, stack);
            if (result == null) return null;
            String id;
            if (result instanceof Optional<?> opt) {
                if (opt.isEmpty()) return null;
                id = opt.get().toString();
            } else {
                id = result.toString();
            }
            if (id == null || id.isBlank()) return null;
            return NAMESPACE + ":" + id.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            warnOnce("ExecutableItems idFromItem failed: " + e.getMessage());
            return null;
        }
    }

    private void warnOnce(String message) {
        if (warned) return;
        logger.log(Level.WARNING, message);
        warned = true;
    }
}
