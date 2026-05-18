package dev.oakheart.oaklog.integration.adapter;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.EntryDescriptor;
import dev.oakheart.oaklog.util.EntryKey;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Direct integration with Nexo via its public API. Compiled against
 * {@code com.nexomc:nexo} (compileOnly) so signatures are verified at build
 * time; the adapter only registers if the Nexo plugin is actually present at
 * runtime.
 * <p>
 * Nexo loads its item registry asynchronously after server startup. We listen
 * for {@code NexoItemsLoadedEvent} (wired by {@link OakLog}) and resync the
 * collection log entry registry once items are actually available.
 */
public class NexoAdapter implements EntryAdapter {

    private static final String NAMESPACE = "nexo";
    private static final String API_CLASS = "com.nexomc.nexo.api.NexoItems";

    private final OakLog plugin;
    private final Logger logger;

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
        try {
            Class.forName(API_CLASS);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public List<EntryDescriptor> enumerate() {
        try {
            // unexcludedItemNames() filters out items with `excludeFromInventory: true`
            // (GUI icons, HMCCosmetics UI items, collection-log helper icons, etc.)
            String[] namesArray = NexoItems.unexcludedItemNames();
            List<String> names = namesArray == null ? List.of() : java.util.Arrays.asList(namesArray);
            if (names.isEmpty()) {
                logger.info("Nexo adapter: 0 items reported (Nexo may still be loading — items will appear after NexoItemsLoadedEvent fires)");
                return List.of();
            }
            List<EntryDescriptor> out = new ArrayList<>(names.size());
            for (String name : names) {
                if (name == null || name.isBlank()) continue;
                String id = name.toLowerCase(Locale.ROOT);
                String display = displayNameFor(name, id);
                out.add(new EntryDescriptor(
                        NAMESPACE,
                        id,
                        Category.ITEMS,
                        display,
                        "PLAYER_HEAD",
                        null,
                        null
                ));
            }
            logger.info("Nexo adapter: enumerated " + out.size() + " items");
            return out;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Nexo enumerate failed", t);
            return List.of();
        }
    }

    private String displayNameFor(String rawId, String fallbackId) {
        try {
            ItemBuilder builder = NexoItems.itemFromId(rawId);
            if (builder == null) return EntryKey.displayNameFromKey(fallbackId);
            // Prefer the item_name component (used by Nexo) over the anvil-set custom name.
            net.kyori.adventure.text.Component name = builder.getItemName();
            if (name == null) name = builder.getCustomName();
            if (name == null) return EntryKey.displayNameFromKey(fallbackId);
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(name).trim();
            return plain.isEmpty() ? EntryKey.displayNameFromKey(fallbackId) : plain;
        } catch (Throwable t) {
            return EntryKey.displayNameFromKey(fallbackId);
        }
    }

    @Override
    public String detect(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        try {
            String id = NexoItems.idFromItem(stack);
            if (id == null || id.isBlank()) return null;
            return NAMESPACE + ":" + id.toLowerCase(Locale.ROOT);
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
        try {
            ItemBuilder builder = NexoItems.itemFromId(id);
            if (builder == null) return Optional.empty();
            ItemStack built = builder.build();
            if (built == null) return Optional.empty();
            return Optional.of(built);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }
}
