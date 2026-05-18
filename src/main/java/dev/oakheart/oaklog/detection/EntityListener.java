package dev.oakheart.oaklog.detection;

import dev.oakheart.oaklog.progress.ProgressManager;
import dev.oakheart.oaklog.registry.EntryRegistry;
import dev.oakheart.oaklog.util.EntryKey;
import dev.oakheart.oaklog.variants.VariantResolver;
import dev.oakheart.oaklog.variants.VariantResolvers;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class EntityListener implements Listener {

    private final EntryRegistry registry;
    private final ProgressManager progressManager;
    private final VariantResolvers variantResolvers;

    public EntityListener(EntryRegistry registry, ProgressManager progressManager,
                          VariantResolvers variantResolvers) {
        this.registry = registry;
        this.progressManager = progressManager;
        this.variantResolvers = variantResolvers;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;
        markEntityCollected(killer, entity);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.NAME_TAG) return;
        markEntityCollected(player, event.getRightClicked());
    }

    private void markEntityCollected(Player player, Entity entity) {
        EntityType type = entity.getType();
        String key = "entity_" + type.getKey().getKey();

        VariantResolver resolver = variantResolvers.get(type);
        if (resolver != null) {
            String variant = resolver.resolve(entity);
            if (variant != null) {
                key = key + "_" + variant;
            }
        }

        Integer entryId = registry.idFor(EntryKey.VANILLA, key);
        if (entryId == null) return;

        progressManager.markCollected(player, entryId);
    }
}
