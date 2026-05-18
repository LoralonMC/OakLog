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
 * Reflection-based Nexo integration. Avoids a compile-time dep on the Nexo
 * jar so OakLog ships clean and only "activates" if Nexo is loaded.
 */
public class NexoAdapter implements EntryAdapter {

    private static final String NAMESPACE = "nexo";

    private final OakLog plugin;
    private final Logger logger;

    private Class<?> nexoItemsClass;
    private Method itemNamesMethod;
    private Method idFromItemMethod;
    private boolean warned;

    public NexoAdapter(OakLog plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public String id() {
        return "nexo";
    }

    @Override
    public boolean isAvailable() {
        if (plugin.getServer().getPluginManager().getPlugin("Nexo") == null) {
            return false;
        }
        try {
            nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            idFromItemMethod = findMethod(nexoItemsClass, new String[]{"idFromItem"},
                    new Class<?>[]{ItemStack.class});
            itemNamesMethod = findMethod(nexoItemsClass,
                    new String[]{"itemNames", "entries", "idList", "getItemNames"},
                    new Class<?>[0]);
            return idFromItemMethod != null;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Nexo adapter unavailable: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return false;
        }
    }

    private static Method findMethod(Class<?> cls, String[] names, Class<?>[] params) {
        for (String name : names) {
            try {
                return cls.getMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                // try next name
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    @Override
    public List<EntryDescriptor> enumerate() {
        if (itemNamesMethod == null) return List.of();
        try {
            Object result = itemNamesMethod.invoke(null);
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
                        "PLAYER_HEAD",
                        null,
                        null
                ));
            }
            logger.info("Nexo adapter: enumerated " + out.size() + " items");
            return out;
        } catch (Exception e) {
            warnOnce("Nexo enumerate() failed: " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public String detect(ItemStack stack) {
        if (idFromItemMethod == null) return null;
        try {
            Object result = idFromItemMethod.invoke(null, stack);
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
            warnOnce("Nexo idFromItem() failed: " + e.getMessage());
            return null;
        }
    }

    private void warnOnce(String message) {
        if (warned) return;
        logger.log(Level.WARNING, message);
        warned = true;
    }
}
