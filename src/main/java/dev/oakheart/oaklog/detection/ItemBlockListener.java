package dev.oakheart.oaklog.detection;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.integration.adapter.AdapterRegistry;
import dev.oakheart.oaklog.integration.adapter.EntryAdapter;
import dev.oakheart.oaklog.progress.ProgressManager;
import dev.oakheart.oaklog.registry.EntryRegistry;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

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

        // Walk the adapter chain. Each adapter that returns a key is checked
        // against the registry; the first key with a real entry wins.
        //
        // If an adapter returns a key for an excluded entry (e.g.
        // oaktools:sickle skipped because Nexo handles the variants), we keep
        // walking the chain so a later adapter can still claim the item.
        //
        // BUT: if any adapter recognized the item at all (returned a non-null
        // key), we do NOT fall back to vanilla material / goat-horn / potion
        // detection. Adapter recognition is an identity claim — a Nexo
        // "Forest Elytra" is not a vanilla elytra just because no other
        // adapter picked it up. Excluding a custom item means "don't track
        // this", not "log it as the underlying material instead".
        boolean adapterRecognized = false;
        String lastExcludedKey = null;
        for (EntryAdapter adapter : adapterRegistry.adapters()) {
            String key;
            try {
                key = adapter.detect(newItem);
            } catch (Throwable t) {
                continue;
            }
            if (key == null) continue;
            adapterRecognized = true;
            Integer entryId = registry.idFor(key);
            if (entryId != null) {
                progressManager.markCollected(player, entryId);
                return;
            }
            lastExcludedKey = key;
        }
        if (adapterRecognized) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.debug("Item recognized by an adapter as '" + lastExcludedKey
                        + "' but the entry is excluded — logging nothing (no vanilla fallback).");
            }
            return;
        }

        // No adapter claimed this item — proceed with vanilla detection paths.

        // Goat Horn variants: same Material, different instruments.
        Material material = newItem.getType();
        if (material == Material.GOAT_HORN) {
            Integer goatHornEntry = resolveGoatHornEntry(newItem);
            if (goatHornEntry != null) {
                progressManager.markCollected(player, goatHornEntry);
                return;
            }
        }
        // Potions / tipped arrows: split by base potion type.
        if (material == Material.POTION || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION
                || material == Material.TIPPED_ARROW) {
            Integer potionEntry = resolvePotionEntry(newItem, material);
            if (potionEntry != null) {
                progressManager.markCollected(player, potionEntry);
                return;
            }
        }

        // Fall back to vanilla material fast path
        Integer entryId = registry.idForVanillaMaterial(material);
        if (entryId == null) return;

        progressManager.markCollected(player, entryId);
    }

    private Integer resolvePotionEntry(ItemStack stack, Material material) {
        try {
            if (!(stack.getItemMeta() instanceof PotionMeta potionMeta)) return null;
            PotionType type = potionMeta.getBasePotionType();
            if (type == null) return null;
            NamespacedKey typeKey = type.getKey();
            String prefix = material.getKey().getKey();
            return registry.idFor(typeKey.getNamespace(), prefix + "_" + typeKey.getKey());
        } catch (Throwable t) {
            return null;
        }
    }

    private Integer resolveGoatHornEntry(ItemStack stack) {
        try {
            if (!(stack.getItemMeta() instanceof MusicInstrumentMeta meta)) return null;
            MusicInstrument instrument = meta.getInstrument();
            if (instrument == null) return null;
            Registry<MusicInstrument> reg = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.INSTRUMENT);
            NamespacedKey nk = reg.getKey(instrument);
            if (nk == null) return null;
            return registry.idFor(nk.getNamespace(), "goat_horn_" + nk.getKey().replace("_goat_horn", ""));
        } catch (Throwable t) {
            return null;
        }
    }
}
