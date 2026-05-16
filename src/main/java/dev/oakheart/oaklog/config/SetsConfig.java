package dev.oakheart.oaklog.config;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.SetDefinition;
import dev.oakheart.oaklog.model.SetMatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SetsConfig {

    private final OakLog plugin;
    private final Logger logger;
    private final Path file;

    private List<SetDefinition> sets = List.of();

    public SetsConfig(OakLog plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.file = plugin.getDataFolder().toPath().resolve("sets.yml");
    }

    public void load() {
        if (!Files.exists(file)) {
            plugin.saveResource("sets.yml", false);
        }

        try {
            dev.oakheart.config.ConfigManager cfg = dev.oakheart.config.ConfigManager.load(file);
            this.sets = parse(cfg);
            if (!sets.isEmpty()) {
                logger.info("Loaded " + sets.size() + " collection set(s) from sets.yml");
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load sets.yml", e);
            this.sets = List.of();
        }
    }

    public void reload() {
        load();
    }

    public List<SetDefinition> sets() {
        return Collections.unmodifiableList(sets);
    }

    private List<SetDefinition> parse(dev.oakheart.config.ConfigManager cfg) {
        List<SetDefinition> out = new ArrayList<>();
        for (String setId : cfg.getKeys(false)) {
            dev.oakheart.config.ConfigManager section = cfg.getSection(setId);
            if (section == null) continue;

            String displayName = section.getString("name", setId);
            String categoryStr = section.getString("category", "items");
            Category category = Category.fromId(categoryStr);
            if (category == null) {
                logger.warning("Set '" + setId + "' has unknown category '" + categoryStr + "'; skipping.");
                continue;
            }
            boolean tab = section.getBoolean("tab", false);

            List<SetMatcher> matchers = new ArrayList<>();
            dev.oakheart.config.ConfigManager match = section.getSection("match");
            if (match != null) {
                List<String> materials = match.getStringList("vanilla-materials");
                if (!materials.isEmpty()) {
                    List<String> norm = materials.stream()
                            .map(s -> s.toLowerCase(Locale.ROOT))
                            .toList();
                    matchers.add(new SetMatcher.VanillaMaterial(norm));
                }
                List<String> entryKeys = match.getStringList("entry-keys");
                if (!entryKeys.isEmpty()) {
                    matchers.add(new SetMatcher.EntryKeyGlob(entryKeys));
                }
                List<String> nexoIds = match.getStringList("nexo-ids");
                if (!nexoIds.isEmpty()) {
                    List<String> patterns = new ArrayList<>();
                    for (String id : nexoIds) patterns.add("nexo:" + id);
                    matchers.add(new SetMatcher.EntryKeyGlob(patterns));
                }
                List<String> eiIds = match.getStringList("executable-items");
                if (!eiIds.isEmpty()) {
                    List<String> patterns = new ArrayList<>();
                    for (String id : eiIds) patterns.add("executableitems:" + id);
                    matchers.add(new SetMatcher.EntryKeyGlob(patterns));
                }
            }

            if (matchers.isEmpty()) {
                logger.warning("Set '" + setId + "' has no matchers; skipping.");
                continue;
            }

            out.add(new SetDefinition(setId.toLowerCase(Locale.ROOT), displayName, category, tab, matchers));
        }
        return out;
    }
}
