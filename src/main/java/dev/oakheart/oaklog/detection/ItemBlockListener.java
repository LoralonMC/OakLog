package dev.oakheart.oaklog.detection;

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
    private final ProgressManager progressManager;

    public ItemBlockListener(EntryRegistry registry, ProgressManager progressManager) {
        this.registry = registry;
        this.progressManager = progressManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSlotChange(PlayerInventorySlotChangeEvent event) {
        ItemStack newItem = event.getNewItemStack();
        if (newItem == null || newItem.getType().isAir()) return;

        Material material = newItem.getType();
        Integer entryId = registry.idForVanillaMaterial(material);
        if (entryId == null) return;

        Player player = event.getPlayer();
        if (player == null) return;

        progressManager.markCollected(player, entryId);
    }
}
