package dev.oakheart.oaklog.detection;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.integration.adapter.AdapterRegistry;
import dev.oakheart.oaklog.progress.ProgressManager;
import dev.oakheart.oaklog.registry.EntryRegistry;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class ItemBlockListener implements Listener {

    private final OakLog plugin;
    private final EntryRegistry registry;
    private final AdapterRegistry adapterRegistry;
    private final ProgressManager progressManager;

    public ItemBlockListener(OakLog plugin, EntryRegistry registry, AdapterRegistry adapterRegistry, ProgressManager progressManager) {
        this.plugin = plugin;
        this.registry = registry;
        this.adapterRegistry = adapterRegistry;
        this.progressManager = progressManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSlotChange(PlayerInventorySlotChangeEvent event) {
        ItemStack newItem = event.getNewItemStack();
        ItemStack oldItem = event.getOldItemStack();
        if (newItem == null || newItem.getType().isAir()) return;

        // Skip resync / count-change events. We want to fire only when an item
        // *type* actually appears (slot was empty or held a different type
        // before). This rejects "your stack of dirt went from 32 to 48" and
        // the spurious resync events Paper sometimes fires on container open.
        if (oldItem != null && !oldItem.getType().isAir()
                && oldItem.getType() == newItem.getType()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) return;

        if (plugin.getConfigManager().isDebug()) {
            plugin.debug("Slot change: player=" + player.getName()
                    + " slot=" + event.getSlot()
                    + " old=" + (oldItem == null ? "null" : oldItem.getType())
                    + " new=" + newItem.getType());
        }

        // Check adapter detection first (custom items take precedence over vanilla material)
        String adapterKey = adapterRegistry.detect(newItem);
        if (adapterKey != null) {
            Integer entryId = registry.idFor(adapterKey);
            if (entryId != null) {
                progressManager.markCollected(player, entryId);
                return;
            }
        }

        // Fall back to vanilla material fast path
        Material material = newItem.getType();
        Integer entryId = registry.idForVanillaMaterial(material);
        if (entryId == null) return;

        progressManager.markCollected(player, entryId);
    }
}
