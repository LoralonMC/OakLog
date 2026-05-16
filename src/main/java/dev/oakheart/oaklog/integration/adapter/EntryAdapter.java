package dev.oakheart.oaklog.integration.adapter;

import dev.oakheart.oaklog.model.EntryDescriptor;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Pluggable source of custom entries. Each adapter knows how to:
 * <ol>
 *   <li>{@link #enumerate()} — return all entries it contributes at startup/reload</li>
 *   <li>{@link #detect(ItemStack)} — recognise an item it produced and return its
 *       fully-qualified entry key ({@code namespace:key})</li>
 * </ol>
 * Adapters guard their work with {@link #isAvailable()} so missing soft-dep
 * plugins cause a no-op rather than an error.
 */
public interface EntryAdapter {

    /**
     * Adapter identifier, used in logs and as the namespace for entries
     * produced by this adapter.
     */
    String id();

    /**
     * Returns whether the adapter is usable in this server context (e.g. its
     * backing plugin is present).
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Called once on plugin enable and on every {@code /oaklog reload}.
     * Returns all entries this adapter contributes.
     */
    List<EntryDescriptor> enumerate();

    /**
     * Inspects an item and returns its canonical entry key
     * ({@code namespace:key}), or {@code null} if the item is not one this
     * adapter recognises. Hot path — must be cheap.
     */
    String detect(ItemStack stack);
}
