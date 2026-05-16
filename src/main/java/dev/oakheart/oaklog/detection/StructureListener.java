package dev.oakheart.oaklog.detection;

import dev.oakheart.oaklog.config.ConfigManager;
import dev.oakheart.oaklog.progress.ProgressManager;
import dev.oakheart.oaklog.registry.EntryRegistry;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;

import java.util.Collection;

public class StructureListener implements Listener {

    private final ConfigManager configManager;
    private final EntryRegistry registry;
    private final ProgressManager progressManager;

    public StructureListener(ConfigManager configManager, EntryRegistry registry,
                             ProgressManager progressManager) {
        this.configManager = configManager;
        this.registry = registry;
        this.progressManager = progressManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!configManager.isStructureCheckOnChunkChange()) return;
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;

        Player player = event.getPlayer();
        Chunk chunk = event.getTo().getChunk();
        Collection<GeneratedStructure> structures = chunk.getStructures();
        if (structures.isEmpty()) return;

        Registry<Structure> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);

        for (GeneratedStructure generated : structures) {
            Structure structure = generated.getStructure();
            NamespacedKey nk = reg.getKey(structure);
            if (nk == null) continue;
            Integer entryId = registry.idFor(nk.getNamespace(), "structure_" + nk.getKey());
            if (entryId == null) continue;
            progressManager.markCollected(player, entryId);
        }
    }
}
