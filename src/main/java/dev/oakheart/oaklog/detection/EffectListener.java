package dev.oakheart.oaklog.detection;

import dev.oakheart.oaklog.progress.ProgressManager;
import dev.oakheart.oaklog.registry.EntryRegistry;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class EffectListener implements Listener {

    private final EntryRegistry registry;
    private final ProgressManager progressManager;

    public EffectListener(EntryRegistry registry, ProgressManager progressManager) {
        this.registry = registry;
        this.progressManager = progressManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) return;

        PotionEffect added = event.getNewEffect();
        if (added == null) return;
        PotionEffectType type = added.getType();

        Registry<PotionEffectType> effects =
                RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT);
        NamespacedKey nk = effects.getKey(type);
        if (nk == null) return;

        Integer entryId = registry.idFor(nk.getNamespace(), "effect_" + nk.getKey());
        if (entryId == null) return;

        progressManager.markCollected(player, entryId);
    }
}
