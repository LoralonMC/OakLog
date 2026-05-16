package dev.oakheart.oaklog.detection;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.config.ConfigManager;
import dev.oakheart.oaklog.progress.ProgressManager;
import dev.oakheart.oaklog.registry.EntryRegistry;
import dev.oakheart.oaklog.util.EntryKey;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class BiomeListener {

    private final OakLog plugin;
    private final ConfigManager configManager;
    private final EntryRegistry registry;
    private final ProgressManager progressManager;

    private BukkitTask task;

    public BiomeListener(OakLog plugin, ConfigManager configManager,
                         EntryRegistry registry, ProgressManager progressManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.registry = registry;
        this.progressManager = progressManager;
    }

    public void start() {
        int interval = configManager.getBiomeCheckInterval();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAll, interval, interval);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void checkAll() {
        Registry<Biome> biomes = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Biome biome = player.getWorld().getBiome(player.getLocation());
            NamespacedKey nk = biomes.getKey(biome);
            if (nk == null) continue;

            Integer entryId = registry.idFor(nk.getNamespace(), "biome_" + nk.getKey());
            if (entryId == null) continue;

            progressManager.markCollected(player, entryId);
        }
    }
}
