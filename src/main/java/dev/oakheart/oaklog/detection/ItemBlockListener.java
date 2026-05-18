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

        // Skip resync / count-change events. ItemStack.isSimilar compares
        // material AND meta but ignores amount — so resyncs (same item +
        // meta) and stack growth (same item, more of it) are filtered out,
        // while swapping one custom item for another with the same material
        // (e.g. two different Nexo PLAYER_HEAD plushies) still fires.
        if (oldItem != null && !oldItem.getType().isAir() && oldItem.isSimilar(newItem)) {
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
