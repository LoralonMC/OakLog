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
    private Method getExecutableItemMethod;
    private Method getByIdMethod;
    private Method getIdOnInterfaceMethod;
    private Method buildItemOnInterfaceMethod;
    private boolean warned;

    // Cache of id → fresh ItemStack populated by detect() — gives the GUI
    // the real EI icon after the first time we see each item.
    private final java.util.Map<String, ItemStack> iconCache = new java.util.concurrent.ConcurrentHashMap<>();

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

            // Per the EI docs: getExecutableItem(ItemStack) returns
            // Optional<ExecutableItemInterface>; the interface exposes getId().
            getExecutableItemMethod = findMethod(manager.getClass(),
                    new String[]{"getExecutableItem"},
                    new Class<?>[]{ItemStack.class});
            idsListMethod = findMethod(manager.getClass(),
                    new String[]{"getExecutableItemIdsList"},
                    new Class<?>[0]);
            getByIdMethod = findMethod(manager.getClass(),
                    new String[]{"getExecutableItem", "getExecutableItemById", "getById"},
                    new Class<?>[]{String.class});

            if (getExecutableItemMethod == null) {
                logger.warning("ExecutableItems: getExecutableItem(ItemStack) not found on "
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

    @Override
    public Optional<ItemStack> resolveLiveIcon(String entryNamespacedKey) {
        if (entryNamespacedKey == null || !entryNamespacedKey.startsWith(NAMESPACE + ":")) {
            return Optional.empty();
        }

        String id = entryNamespacedKey.substring(NAMESPACE.length() + 1);

        // Cache fast path — populated by detect() the first time the player
        // touches the item. Means new players see Nether Star until they
        // (or anyone else) pick up the item once; then it renders correctly.
        ItemStack cached = iconCache.get(id);
        if (cached != null) {
            return Optional.of(cached.clone());
        }

        if (manager == null || getByIdMethod == null) return Optional.empty();
        try {
            Object result = getByIdMethod.invoke(manager, id);
            Object eiInterface;
            if (result instanceof Optional<?> opt) {
                if (opt.isEmpty()) return Optional.empty();
                eiInterface = opt.get();
            } else if (result == null) {
                return Optional.empty();
            } else {
                eiInterface = result;
            }

            // The official ExecutableItemInterface declares
            //   ItemStack buildItem(int amount, Optional<Player> creator);
            // Try that first, then the zero-arg and single-int fallbacks for
            // older builds.
            Object built = tryBuildItem(eiInterface);
            if (built instanceof ItemStack stack) {
                iconCache.putIfAbsent(id, stack.clone());
                return Optional.of(stack);
            }
            return Optional.empty();
        } catch (Throwable t) {
            warnOnce("ExecutableItems resolveLiveIcon failed: " + t.getMessage());
            return Optional.empty();
        }
    }

    private Object tryBuildItem(Object eiInterface) throws Exception {
        // Method 1: buildItem(int, Optional<Player>) — documented signature
        if (buildItemOnInterfaceMethod == null) {
            buildItemOnInterfaceMethod = findMethod(eiInterface.getClass(),
                    new String[]{"buildItem"},
                    new Class<?>[]{int.class, Optional.class});
            if (buildItemOnInterfaceMethod != null) {
                return buildItemOnInterfaceMethod.invoke(eiInterface, 1, Optional.empty());
            }
        } else if (buildItemOnInterfaceMethod.getParameterCount() == 2) {
            return buildItemOnInterfaceMethod.invoke(eiInterface, 1, Optional.empty());
        }

        // Method 2: zero-arg buildItem / getItemStack / build / getItem
        Method zeroArg = findMethod(eiInterface.getClass(),
                new String[]{"buildItem", "getItemStack", "build", "getItem"},
                new Class<?>[0]);
        if (zeroArg != null) {
            buildItemOnInterfaceMethod = zeroArg;
            return zeroArg.invoke(eiInterface);
        }

        // Method 3: single-int buildItem
        Method intArg = findMethod(eiInterface.getClass(),
                new String[]{"buildItem", "getItemStack"},
                new Class<?>[]{int.class});
        if (intArg != null) {
            buildItemOnInterfaceMethod = intArg;
            return intArg.invoke(eiInterface, 1);
        }
        return null;
    }

    private Plugin findHostPlugin() {
        // SCore houses the API class; ExecutableItems registers the items.
        // Prefer SCore (the API container) and fall back to EI's own plugin.
        Plugin scoreCandidate = null;
        Plugin eiCandidate = null;
        for (Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
            String mainClass = p.getClass().getName();
            if (mainClass.startsWith("com.ssomar.score")) {
                scoreCandidate = p;
            } else if (mainClass.startsWith("com.ssomar.executableitems")) {
                eiCandidate = p;
            }
        }
        return scoreCandidate != null ? scoreCandidate : eiCandidate;
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
        if (manager == null || getExecutableItemMethod == null) return null;
        try {
            Object result = getExecutableItemMethod.invoke(manager, stack);
            if (!(result instanceof Optional<?> opt) || opt.isEmpty()) return null;

            Object eiInterface = opt.get();
            if (getIdOnInterfaceMethod == null) {
                getIdOnInterfaceMethod = findMethod(eiInterface.getClass(),
                        new String[]{"getId", "getID", "id"},
                        new Class<?>[0]);
                if (getIdOnInterfaceMethod == null) {
                    warnOnce("ExecutableItems: ExecutableItemInterface has no getId() method");
                    return null;
                }
            }
            Object idObj = getIdOnInterfaceMethod.invoke(eiInterface);
            if (idObj == null) return null;
            String id = idObj.toString();
            if (id.isBlank()) return null;
            String lowerId = id.toLowerCase(Locale.ROOT);

            // Cache the live ItemStack so the GUI can show the real icon next time.
            iconCache.putIfAbsent(lowerId, stack.clone());

            return NAMESPACE + ":" + lowerId;
        } catch (Exception e) {
            warnOnce("ExecutableItems detect failed: " + e.getMessage());
            return null;
        }
    }

    private void warnOnce(String message) {
        if (warned) return;
        logger.log(Level.WARNING, message);
        warned = true;
    }
}
