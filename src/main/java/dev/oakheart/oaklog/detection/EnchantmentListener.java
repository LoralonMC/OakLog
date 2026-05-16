package dev.oakheart.oaklog.detection;

import dev.oakheart.oaklog.progress.ProgressManager;
import dev.oakheart.oaklog.registry.EntryRegistry;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class EnchantmentListener implements Listener {

    private final EntryRegistry registry;
    private final ProgressManager progressManager;

    public EnchantmentListener(EntryRegistry registry, ProgressManager progressManager) {
        this.registry = registry;
        this.progressManager = progressManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSlotChange(PlayerInventorySlotChangeEvent event) {
        ItemStack item = event.getNewItemStack();
        if (item == null || item.getType().isAir()) return;

        Player player = event.getPlayer();
        Registry<Enchantment> enchants =
                RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);

        // Regular enchantments on the item
        Map<Enchantment, Integer> applied = item.getEnchantments();
        for (Enchantment ench : applied.keySet()) {
            recordEnchantment(player, enchants, ench);
        }

        // Stored enchantments on an enchanted book
        if (item.getType() == Material.ENCHANTED_BOOK) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof EnchantmentStorageMeta storage) {
                for (Enchantment ench : storage.getStoredEnchants().keySet()) {
                    recordEnchantment(player, enchants, ench);
                }
            }
        }
    }

    private void recordEnchantment(Player player, Registry<Enchantment> enchants, Enchantment ench) {
        NamespacedKey nk = enchants.getKey(ench);
        if (nk == null) return;

        Integer entryId = registry.idFor(nk.getNamespace(), "enchantment_" + nk.getKey());
        if (entryId == null) return;

        progressManager.markCollected(player, entryId);
    }
}
