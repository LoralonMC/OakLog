package dev.oakheart.oaklog.integration.adapter;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.EntryDescriptor;
import dev.oakheart.oaklog.util.EntryKey;
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
 * Reflection-based ExecutableItems integration.
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
        if (plugin.getServer().getPluginManager().getPlugin("ExecutableItems") == null) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("com.ssomar.score.api.executableitems.ExecutableItemsAPI");
            Method getManagerMethod = null;
            for (Method m : apiClass.getMethods()) {
                if (m.getName().equals("getExecutableItemsManager") && m.getParameterCount() == 0) {
                    getManagerMethod = m;
                    break;
                }
            }
            if (getManagerMethod == null) return false;
            manager = getManagerMethod.invoke(null);
            if (manager == null) return false;

            for (Method m : manager.getClass().getMethods()) {
                if (idsListMethod == null
                        && (m.getName().equals("getExecutableItemIdsList")
                            || m.getName().equals("getExecutableItems")
                            || m.getName().equals("getAllIds"))
                        && m.getParameterCount() == 0
                        && Collection.class.isAssignableFrom(m.getReturnType())) {
                    idsListMethod = m;
                }
                if (idFromItemMethod == null
                        && (m.getName().equals("getExecutableItemIdFromItemStack")
                            || m.getName().equals("getIdFromItemStack"))
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == ItemStack.class) {
                    idFromItemMethod = m;
                }
            }
            return idFromItemMethod != null;
        } catch (Exception e) {
            return false;
        }
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
