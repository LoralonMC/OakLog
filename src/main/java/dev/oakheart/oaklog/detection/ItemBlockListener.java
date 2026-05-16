package dev.oakheart.oaklog.detection;

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

    private final EntryRegistry registry;
    private final AdapterRegistry adapterRegistry;
    private final ProgressManager progressManager;

    public ItemBlockListener(EntryRegistry registry, AdapterRegistry adapterRegistry, ProgressManager progressManager) {
        this.registry = registry;
        this.adapterRegistry = adapterRegistry;
        this.progressManager = progressManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSlotChange(PlayerInventorySlotChangeEvent event) {
        ItemStack newItem = event.getNewItemStack();
        if (newItem == null || newItem.getType().isAir()) return;

        Player player = event.getPlayer();
        if (player == null) return;

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
