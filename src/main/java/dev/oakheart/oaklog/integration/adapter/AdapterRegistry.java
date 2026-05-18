package dev.oakheart.oaklog.integration.adapter;

import dev.oakheart.oaklog.model.EntryDescriptor;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class AdapterRegistry {

    private final List<EntryAdapter> adapters = new ArrayList<>();
    private final Logger logger;

    public AdapterRegistry(Logger logger) {
        this.logger = logger;
    }

    public void register(EntryAdapter adapter) {
        if (adapter == null) return;
        boolean available;
        try {
            available = adapter.isAvailable();
        } catch (Throwable t) {
            logger.warning("Adapter '" + adapter.id() + "' isAvailable() threw "
                    + t.getClass().getSimpleName() + " — skipping. (" + t.getMessage() + ")");
            return;
        }
        if (!available) {
            logger.info("Adapter '" + adapter.id() + "' is not available (backing plugin missing) — skipping.");
            return;
        }
        adapters.add(adapter);
        logger.info("Adapter '" + adapter.id() + "' registered.");
    }

    public List<EntryAdapter> adapters() {
        return Collections.unmodifiableList(adapters);
    }

    public List<EntryDescriptor> enumerateAll() {
        List<EntryDescriptor> out = new ArrayList<>();
        for (EntryAdapter adapter : adapters) {
            try {
                out.addAll(adapter.enumerate());
            } catch (Exception e) {
                logger.warning("Adapter '" + adapter.id() + "' threw during enumerate: " + e.getMessage());
            }
        }
        return out;
    }

    public Optional<ItemStack> resolveLiveIcon(String entryNamespacedKey) {
        if (entryNamespacedKey == null) return Optional.empty();
        for (EntryAdapter adapter : adapters) {
            try {
                Optional<ItemStack> icon = adapter.resolveLiveIcon(entryNamespacedKey);
                if (icon.isPresent()) return icon;
            } catch (Throwable t) {
                logger.warning("Adapter '" + adapter.id() + "' threw during resolveLiveIcon: " + t.getMessage());
            }
        }
        return Optional.empty();
    }

    public String detect(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        for (EntryAdapter adapter : adapters) {
            try {
                String key = adapter.detect(stack);
                if (key != null) return key;
            } catch (Exception e) {
                logger.warning("Adapter '" + adapter.id() + "' threw during detect: " + e.getMessage());
            }
        }
        return null;
    }
}
