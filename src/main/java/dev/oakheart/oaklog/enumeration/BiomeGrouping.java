package dev.oakheart.oaklog.enumeration;

import dev.oakheart.oaklog.OakLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BiomeGrouping {

    public static final String DEFAULT_GROUP = "overworld/other";

    private final OakLog plugin;
    private final Logger logger;
    private final Path file;
    private Map<String, String> biomeToGroup = Map.of();

    public BiomeGrouping(OakLog plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.file = plugin.getDataFolder().toPath().resolve("biome-groups.yml");
    }

    public void load() {
        if (!Files.exists(file)) {
            plugin.saveResource("biome-groups.yml", false);
        }

        try {
            dev.oakheart.config.ConfigManager cfg = dev.oakheart.config.ConfigManager.load(file);
            mergeDefaults(cfg);
            biomeToGroup = parse(cfg);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load biome-groups.yml; falling back to defaults", e);
            biomeToGroup = Map.of();
        }
    }

    public void reload() {
        load();
    }

    public String groupFor(String biomeKey) {
        if (biomeKey == null) return DEFAULT_GROUP;
        String normalized = biomeKey.toLowerCase(Locale.ROOT);
        return biomeToGroup.getOrDefault(normalized, DEFAULT_GROUP);
    }

    private void mergeDefaults(dev.oakheart.config.ConfigManager cfg) {
        try (var stream = plugin.getResource("biome-groups.yml")) {
            if (stream == null) return;
            var defaults = dev.oakheart.config.ConfigManager.fromStream(stream);
            if (cfg.mergeDefaults(defaults)) {
                cfg.save();
            }
        } catch (IOException e) {
            logger.warning("Failed to merge biome-groups defaults: " + e.getMessage());
        }
    }

    private Map<String, String> parse(dev.oakheart.config.ConfigManager cfg) {
        Map<String, String> result = new HashMap<>();

        dev.oakheart.config.ConfigManager overworld = cfg.getSection("overworld");
        if (overworld != null) {
            for (String climateKey : overworld.getKeys(false)) {
                List<String> biomes = overworld.getStringList(climateKey);
                String group = "overworld/" + climateKey.toLowerCase(Locale.ROOT);
                for (String biome : biomes) {
                    if (biome == null) continue;
                    result.put(stripNamespace(biome).toLowerCase(Locale.ROOT), group);
                }
            }
        }

        List<String> nether = cfg.getStringList("nether");
        for (String biome : nether) {
            if (biome == null) continue;
            result.put(stripNamespace(biome).toLowerCase(Locale.ROOT), "nether");
        }

        List<String> end = cfg.getStringList("end");
        for (String biome : end) {
            if (biome == null) continue;
            result.put(stripNamespace(biome).toLowerCase(Locale.ROOT), "end");
        }

        return result;
    }

    private static String stripNamespace(String raw) {
        int idx = raw.indexOf(':');
        return idx < 0 ? raw : raw.substring(idx + 1);
    }
}
