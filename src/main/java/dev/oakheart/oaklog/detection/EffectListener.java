package dev.oakheart.oaklog.detection;

import dev.oakheart.oaklog.progress.ProgressManager;
import dev.oakheart.oaklog.registry.EntryRegistry;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
        markEffect(player, added.getType());
    }

    // ----- Instantaneous effects -----
    //
    // Instant Health and Instant Damage never reach EntityPotionEffectEvent. Vanilla
    // splits the two cases in PotionContents#applyToLivingEntity: an instantaneous
    // effect goes straight to MobEffect#applyInstantenousEffect, and only a timed one
    // is routed through LivingEntity#addEffect, which is where CraftBukkit fires the
    // event. So the only way to see one is to read the potion that carried it.
    //
    // Drinking, being caught in a splash, and standing in a lingering cloud are the
    // three ways a player meets one. Timed effects from those same potions are left to
    // the handler above rather than marked twice.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!(event.getItem().getItemMeta() instanceof PotionMeta meta)) return;
        markInstant(event.getPlayer(), meta.getAllEffects());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        Collection<PotionEffect> effects = event.getPotion().getEffects();
        if (effects.isEmpty()) return;
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (!(affected instanceof Player player)) continue;
            // A plugin can zero out a single entity's share of the splash; treat that
            // as "not hit" rather than crediting the effect.
            if (event.getIntensity(affected) <= 0.0) continue;
            markInstant(player, effects);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAreaEffectCloud(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        List<PotionEffect> effects = new ArrayList<>(cloud.getCustomEffects());
        PotionType base = cloud.getBasePotionType();
        if (base != null) {
            effects.addAll(base.getPotionEffects());
        }
        if (effects.isEmpty()) return;
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (!(affected instanceof Player player)) continue;
            markInstant(player, effects);
        }
    }

    private void markInstant(Player player, Collection<PotionEffect> effects) {
        for (PotionEffect effect : effects) {
            PotionEffectType type = effect.getType();
            if (!type.isInstant()) continue;
            markEffect(player, type);
        }
    }

    private void markEffect(Player player, PotionEffectType type) {
        Registry<PotionEffectType> effects =
                RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT);
        NamespacedKey nk = effects.getKey(type);
        if (nk == null) return;

        Integer entryId = registry.idFor(nk.getNamespace(), "effect_" + nk.getKey());
        if (entryId == null) return;

        progressManager.markCollected(player, entryId);
    }
}
