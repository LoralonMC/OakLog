package dev.oakheart.oaklog.detection;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.progress.ProgressManager;
import dev.oakheart.oaklog.registry.EntryRegistry;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Iterator;
import java.util.UUID;

public class AdvancementListener implements Listener {

    // ProgressManager loads player progress asynchronously on PlayerJoinEvent.
    // Defer the silent backfill long enough for that load to finish so
    // markCollectedSilent() finds the in-memory PlayerProgress already
    // populated. 60 ticks (3s) is well above the typical SQLite load time
    // and still gives the player a coherent state by the time they open
    // any GUI.
    private static final long BACKFILL_DELAY_TICKS = 60L;

    private final OakLog plugin;
    private final EntryRegistry registry;
    private final ProgressManager progressManager;

    public AdvancementListener(OakLog plugin, EntryRegistry registry, ProgressManager progressManager) {
        this.plugin = plugin;
        this.registry = registry;
        this.progressManager = progressManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Advancement adv = event.getAdvancement();
        if (!isTrackable(adv)) return;
        Integer entryId = entryIdFor(adv);
        if (entryId == null) return;
        progressManager.markCollected(event.getPlayer(), entryId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> backfill(player, uuid), BACKFILL_DELAY_TICKS);
    }

    private void backfill(Player player, UUID uuid) {
        if (!player.isOnline()) return;
        Iterator<Advancement> it = Bukkit.advancementIterator();
        int marked = 0;
        while (it.hasNext()) {
            Advancement adv;
            try {
                adv = it.next();
            } catch (Throwable t) {
                continue;
            }
            if (!isTrackable(adv)) continue;
            try {
                if (!player.getAdvancementProgress(adv).isDone()) continue;
            } catch (Throwable t) {
                continue;
            }
            Integer entryId = entryIdFor(adv);
            if (entryId == null) continue;
            if (progressManager.markCollectedSilent(uuid, entryId)) marked++;
        }
        if (marked > 0) {
            plugin.debug("Backfilled " + marked + " advancement(s) for " + player.getName());
        }
    }

    private Integer entryIdFor(Advancement adv) {
        NamespacedKey nk = adv.getKey();
        return registry.idFor(nk.getNamespace(), "advancement_" + nk.getKey());
    }

    private static boolean isTrackable(Advancement adv) {
        String path = adv.getKey().getKey();
        if (path.startsWith("recipes/")) return false;
        if (path.endsWith("/root")) return false;
        try {
            return adv.getDisplay() != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
