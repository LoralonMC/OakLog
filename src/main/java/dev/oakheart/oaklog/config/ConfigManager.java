package dev.oakheart.oaklog.config;

import dev.oakheart.oaklog.OakLog;
import org.bukkit.GameMode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigManager {

    private static final Set<String> VALID_TOAST_STATES = Set.of("on", "off", "milestones");

    private final OakLog plugin;
    private final Logger logger;
    private final Path configFile;
    private dev.oakheart.config.ConfigManager config;

    // General settings
    private boolean debug;
    private String commandAlias;

    // Detection
    private int biomeCheckInterval;
    private boolean structureDetectionEnabled;
    private int structureCheckInterval;
    private int structureSmallFootprint;
    private Set<GameMode> ignoredGamemodes;
    private List<String> excludedItems;
    private List<String> excludedEntries;

    // Custom category (server content split out of ITEMS; display name is configurable)
    private boolean customCategoryEnabled;
    private List<String> customCategoryNamespaces;
    private java.util.Map<String, String> categoryDisplayNames = java.util.Map.of();
    private java.util.Map<String, String> categoryIcons = java.util.Map.of();

    // Storage
    private String storageFilename;
    private int flushIntervalTicks;
    private boolean cacheEvictOnQuit;

    // Toasts
    private String toastDefaultState;
    private int toastMilestoneEveryN;
    private boolean toastOnSetComplete;
    private boolean toastOnCategoryComplete;
    private boolean toastOnFullLogComplete;

    // Search
    private int searchMaxResults;

    // Leaderboard
    private int leaderboardRefreshSeconds;
    private int leaderboardTopSize;

    // Date formatting
    private String dateFormat;
    private String dateTimezone;

    public ConfigManager(OakLog plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = plugin.getDataFolder().toPath().resolve("config.yml");
    }

    public void load() {
        if (!configFile.toFile().exists()) {
            plugin.saveResource("config.yml", false);
        }

        try {
            config = dev.oakheart.config.ConfigManager.load(configFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }

        evolveConfig();
        validate(config);
        cacheValues();
    }

    public boolean reload() {
        dev.oakheart.config.ConfigManager newConfig;
        try {
            newConfig = dev.oakheart.config.ConfigManager.load(configFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to reload config.yml", e);
            return false;
        }

        if (!validate(newConfig)) {
            logger.warning("Configuration reload failed validation. Keeping previous configuration.");
            return false;
        }

        this.config = newConfig;
        cacheValues();
        logger.info("Configuration reloaded successfully.");
        return true;
    }

    private void evolveConfig() {
        try (var stream = plugin.getResource("config.yml")) {
            if (stream == null) return;
            var defaults = dev.oakheart.config.ConfigManager.fromStream(stream);
            // Additive: insert missing keys at the right position with their comments.
            boolean merged = config.mergeDefaults(defaults);
            // Documentation: 3-way sync of changed comments onto existing keys, never
            // clobbering admin-customized comments (seeds baseline on first run).
            boolean synced = config.syncComments(defaults,
                    configFile.getParent().resolve(".oakheart/config-baseline.yml"));
            if (merged || synced) {
                config.save();
                logger.info("Config evolved (merged defaults / synced comments).");
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to evolve config", e);
        }
    }

    private boolean validate(dev.oakheart.config.ConfigManager configToValidate) {
        List<String> warnings = new ArrayList<>();

        int biomeInterval = configToValidate.getInt("detection.biome-check-interval", 20);
        if (biomeInterval <= 0) {
            warnings.add("detection.biome-check-interval must be > 0, defaulting to 20");
        }

        int flushInterval = configToValidate.getInt("storage.flush-interval-ticks", 200);
        if (flushInterval <= 0) {
            warnings.add("storage.flush-interval-ticks must be > 0, defaulting to 200");
        }

        String toastDefault = configToValidate.getString("toasts.default-state", "off").toLowerCase(Locale.ROOT);
        if (!VALID_TOAST_STATES.contains(toastDefault)) {
            warnings.add("toasts.default-state must be one of on/off/milestones, defaulting to off");
        }

        int milestoneN = configToValidate.getInt("toasts.milestones.every-n-entries", 25);
        if (milestoneN <= 0) {
            warnings.add("toasts.milestones.every-n-entries must be > 0, defaulting to 25");
        }

        int maxResults = configToValidate.getInt("search.max-results", 36);
        if (maxResults <= 0) {
            warnings.add("search.max-results must be > 0, defaulting to 36");
        }

        int refresh = configToValidate.getInt("leaderboard.refresh-interval-seconds", 60);
        if (refresh <= 0) {
            warnings.add("leaderboard.refresh-interval-seconds must be > 0, defaulting to 60");
        }

        int topSize = configToValidate.getInt("leaderboard.top-size", 10);
        if (topSize <= 0) {
            warnings.add("leaderboard.top-size must be > 0, defaulting to 10");
        }

        String filename = configToValidate.getString("storage.filename", "oaklog.db");
        if (filename == null || filename.isBlank()) {
            warnings.add("storage.filename must not be blank, defaulting to oaklog.db");
        }

        for (String name : configToValidate.getStringList("detection.ignored-gamemodes")) {
            try {
                GameMode.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warnings.add("detection.ignored-gamemodes contains unknown game mode '" + name + "', ignoring it");
            }
        }

        if (!warnings.isEmpty()) {
            logger.warning("=== Configuration Warnings ===");
            warnings.forEach(w -> logger.warning("  - " + w));
            logger.warning("==============================");
        }

        return true;
    }

    private void cacheValues() {
        debug = config.getBoolean("debug", false);
        commandAlias = config.getString("command-alias", "log");

        biomeCheckInterval = Math.max(1, config.getInt("detection.biome-check-interval", 20));
        structureDetectionEnabled = config.getBoolean("detection.structure-check-on-chunk-change", true);
        structureCheckInterval = Math.max(0, config.getInt("detection.structure-check-interval", 20));
        structureSmallFootprint = Math.max(0, config.getInt("detection.structure-small-footprint", 16));
        List<String> rawExcluded = config.getStringList("detection.excluded-items");
        excludedItems = rawExcluded.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        List<String> rawExcludedEntries = config.getStringList("detection.excluded-entries");
        excludedEntries = rawExcludedEntries.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        ignoredGamemodes = parseGamemodes(config.getStringList("detection.ignored-gamemodes"));

        customCategoryEnabled = config.getBoolean("custom-category.enabled", true);
        List<String> rawNamespaces = config.getStringList("custom-category.namespaces");
        customCategoryNamespaces = rawNamespaces.isEmpty()
                ? List.of("*")
                : rawNamespaces.stream().map(n -> n.toLowerCase(Locale.ROOT)).toList();

        java.util.Map<String, String> names = new java.util.HashMap<>();
        java.util.Map<String, String> icons = new java.util.HashMap<>();
        dev.oakheart.config.ConfigManager cats = config.getSection("categories");
        if (cats != null) {
            for (String id : cats.getKeys(false)) {
                dev.oakheart.config.ConfigManager sec = cats.getSection(id);
                if (sec == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                String display = sec.getString("display-name", null);
                if (display != null && !display.isBlank()) names.put(key, display);
                String icon = sec.getString("icon", null);
                if (icon != null && !icon.isBlank()) icons.put(key, icon);
            }
        }
        categoryDisplayNames = java.util.Map.copyOf(names);
        categoryIcons = java.util.Map.copyOf(icons);

        storageFilename = config.getString("storage.filename", "oaklog.db");
        if (storageFilename == null || storageFilename.isBlank()) {
            storageFilename = "oaklog.db";
        }
        flushIntervalTicks = Math.max(20, config.getInt("storage.flush-interval-ticks", 200));
        cacheEvictOnQuit = config.getBoolean("storage.cache-evict-on-quit", true);

        String rawToastState = config.getString("toasts.default-state", "off").toLowerCase(Locale.ROOT);
        toastDefaultState = VALID_TOAST_STATES.contains(rawToastState) ? rawToastState : "off";
        toastMilestoneEveryN = Math.max(1, config.getInt("toasts.milestones.every-n-entries", 25));
        toastOnSetComplete = config.getBoolean("toasts.milestones.on-set-complete", true);
        toastOnCategoryComplete = config.getBoolean("toasts.milestones.on-category-complete", true);
        toastOnFullLogComplete = config.getBoolean("toasts.milestones.on-full-log-complete", true);

        searchMaxResults = Math.max(1, config.getInt("search.max-results", 36));

        leaderboardRefreshSeconds = Math.max(5, config.getInt("leaderboard.refresh-interval-seconds", 60));
        leaderboardTopSize = Math.max(1, config.getInt("leaderboard.top-size", 10));

        dateFormat = config.getString("date-format", "yyyy-MM-dd HH:mm");
        dateTimezone = config.getString("date-timezone", "system");
    }

    private Set<GameMode> parseGamemodes(List<String> names) {
        Set<GameMode> modes = EnumSet.noneOf(GameMode.class);
        for (String name : names) {
            try {
                modes.add(GameMode.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Unknown names are reported as warnings in validate(); skip them here.
            }
        }
        return modes;
    }

    // --- Getters ---

    public dev.oakheart.config.ConfigManager getConfig() {
        return config;
    }

    public boolean isDebug() {
        return debug;
    }

    public String getCommandAlias() {
        return commandAlias;
    }

    public int getBiomeCheckInterval() {
        return biomeCheckInterval;
    }

    /** Master switch for structure detection (both the event samples and the poll). */
    public boolean isStructureDetectionEnabled() {
        return structureDetectionEnabled;
    }

    /** Poll interval in ticks for the "am I standing in a structure" check; 0 disables the poll. */
    public int getStructureCheckInterval() {
        return structureCheckInterval;
    }

    /**
     * Max horizontal size (blocks) at which a structure is matched on X/Z only, because
     * its recorded bounding box can sit at the wrong height. 0 always requires full 3D.
     */
    public int getStructureSmallFootprint() {
        return structureSmallFootprint;
    }

    public Set<GameMode> getIgnoredGamemodes() {
        return Collections.unmodifiableSet(ignoredGamemodes);
    }

    public boolean isGamemodeIgnored(GameMode mode) {
        return ignoredGamemodes.contains(mode);
    }

    public List<String> getExcludedItems() {
        return Collections.unmodifiableList(excludedItems);
    }

    public List<String> getExcludedEntries() {
        return Collections.unmodifiableList(excludedEntries);
    }

    /** Whether server content is split out of ITEMS into its own CUSTOM category. */
    public boolean isCustomCategoryEnabled() {
        return customCategoryEnabled;
    }

    /**
     * Namespaces routed into CUSTOM. A single "*" (the default) means every
     * namespace except vanilla, which is what the split is for.
     */
    public List<String> getCustomCategoryNamespaces() {
        return Collections.unmodifiableList(customCategoryNamespaces);
    }

    /** Configured display name for a category, or null to use the built-in one. */
    public String getCategoryDisplayName(dev.oakheart.oaklog.model.Category category) {
        return category == null ? null : categoryDisplayNames.get(category.id());
    }

    /**
     * Configured icon for a category, or null for the built-in one. May be a
     * Material name or a custom item key such as {@code nexo:my_item}.
     */
    public String getCategoryIcon(dev.oakheart.oaklog.model.Category category) {
        return category == null ? null : categoryIcons.get(category.id());
    }

    public String getStorageFilename() {
        return storageFilename;
    }

    public int getFlushIntervalTicks() {
        return flushIntervalTicks;
    }

    public boolean isCacheEvictOnQuit() {
        return cacheEvictOnQuit;
    }

    public String getToastDefaultState() {
        return toastDefaultState;
    }

    public int getToastMilestoneEveryN() {
        return toastMilestoneEveryN;
    }

    public boolean isToastOnSetComplete() {
        return toastOnSetComplete;
    }

    public boolean isToastOnCategoryComplete() {
        return toastOnCategoryComplete;
    }

    public boolean isToastOnFullLogComplete() {
        return toastOnFullLogComplete;
    }

    public int getSearchMaxResults() {
        return searchMaxResults;
    }

    public int getLeaderboardRefreshSeconds() {
        return leaderboardRefreshSeconds;
    }

    public int getLeaderboardTopSize() {
        return leaderboardTopSize;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public String getDateTimezone() {
        return dateTimezone;
    }
}
