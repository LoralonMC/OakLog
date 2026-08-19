package dev.oakheart.oaklog.detection;

import dev.oakheart.oaklog.progress.ProgressManager;
import dev.oakheart.oaklog.registry.EntryRegistry;
import dev.oakheart.oaklog.util.EntryKey;
import dev.oakheart.oaklog.variants.VariantResolver;
import dev.oakheart.oaklog.variants.VariantResolvers;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
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
        Entity target = event.getRightClicked();

        // Boats and minecarts are collected by right-clicking (the natural
        // "interact" gesture — opening / mounting). No held item required.
        if (target instanceof Boat || target instanceof Minecart) {
            markEntityCollected(player, target);
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.NAME_TAG) return;
        // A blank name tag doesn't actually rename anything in vanilla — the
        // interact is a no-op. Require a customised tag so this matches the
        // in-game semantics of "naming the mob counts as collecting it".
        if (!hand.hasItemMeta() || hand.getItemMeta().displayName() == null) return;
        markEntityCollected(player, target);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        markEntityCollected(player, event.getEntity());
    }

    // AbstractHorse taming (horse, donkey, mule, llama, trader llama, camel)
    // historically doesn't fire EntityTameEvent on every Paper version — it's
    // gated on the riding-success path inside NMS. Catch it via the post-tame
    // state on the next interact tick: if the player right-clicks a horse
    // they already own, that's a definitive "I tamed this" signal.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractHorse(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity target = event.getRightClicked();
        if (!(target instanceof AbstractHorse horse)) return;
        if (!(target instanceof Tameable tameable)) return;
        if (!tameable.isTamed()) return;
        Player player = event.getPlayer();
        if (tameable.getOwner() == null || !tameable.getOwner().getUniqueId().equals(player.getUniqueId())) return;
        markEntityCollected(player, horse);
    }

    // Paintings are the one non-living entity with entries (see VanillaEnumerator's
    // isCollectableEntity), so none of the paths above reach them: EntityDeathEvent
    // only fires for a LivingEntity, and a painting is neither tameable nor rideable.
    // Hanging the painting is the gesture that reveals which variant you got, and
    // vanilla rolls it in Painting#create and applies the item's stored variant
    // before firing this event, so the art is final here.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (!(event.getEntity() instanceof Painting painting)) return;
        markEntityCollected(player, painting);
    }

    // Breaking one credits it too, which is what a player who found a painting
    // rather than placed it will try.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Painting painting)) return;
        markEntityCollected(player, painting);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        markEntityCollected(event.getPlayer(), event.getVillager());
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

        if (entity instanceof Ageable ageable && !ageable.isAdult()) {
            key = key + "_baby";
        }

        Integer entryId = registry.idFor(EntryKey.VANILLA, key);
        if (entryId == null) return;

        progressManager.markCollected(player, entryId);
    }
}
