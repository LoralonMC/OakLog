package dev.oakheart.oaklog.config;

import dev.oakheart.oaklog.OakLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigManager {

    private static final Set<String> VALID_TOAST_STATES = Set.of("on", "off", "milestones");
    private static final Set<String> VALID_SEARCH_INPUTS = Set.of("anvil", "sign", "chat");

    private final OakLog plugin;
    private final Logger logger;
    private final Path configFile;
    private dev.oakheart.config.ConfigManager config;

    // General settings
    private boolean debug;
    private String commandAlias;

    // Detection
    private int biomeCheckInterval;
    private boolean structureCheckOnChunkChange;
    private List<String> excludedItems;
    private List<String> excludedEntries;

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
    private String searchInput;
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

        mergeDefaults();
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

    private void mergeDefaults() {
        try (var stream = plugin.getResource("config.yml")) {
            if (stream == null) return;
            var defaults = dev.oakheart.config.ConfigManager.fromStream(stream);
            if (config.mergeDefaults(defaults)) {
                config.save();
            }
        } catch (IOException e) {
            logger.warning("Failed to merge config defaults: " + e.getMessage());
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

        String input = configToValidate.getString("search.input", "anvil").toLowerCase(Locale.ROOT);
        if (!VALID_SEARCH_INPUTS.contains(input)) {
            warnings.add("search.input must be one of anvil/sign/chat, defaulting to anvil");
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
        structureCheckOnChunkChange = config.getBoolean("detection.structure-check-on-chunk-change", true);
        List<String> rawExcluded = config.getStringList("detection.excluded-items");
        excludedItems = rawExcluded.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        List<String> rawExcludedEntries = config.getStringList("detection.excluded-entries");
        excludedEntries = rawExcludedEntries.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();

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

        String rawInput = config.getString("search.input", "anvil").toLowerCase(Locale.ROOT);
        searchInput = VALID_SEARCH_INPUTS.contains(rawInput) ? rawInput : "anvil";
        searchMaxResults = Math.max(1, config.getInt("search.max-results", 36));

        leaderboardRefreshSeconds = Math.max(5, config.getInt("leaderboard.refresh-interval-seconds", 60));
        leaderboardTopSize = Math.max(1, config.getInt("leaderboard.top-size", 10));

        dateFormat = config.getString("date-format", "yyyy-MM-dd HH:mm");
        dateTimezone = config.getString("date-timezone", "system");
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

    public boolean isStructureCheckOnChunkChange() {
        return structureCheckOnChunkChange;
    }

    public List<String> getExcludedItems() {
        return Collections.unmodifiableList(excludedItems);
    }

    public List<String> getExcludedEntries() {
        return Collections.unmodifiableList(excludedEntries);
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

    public String getSearchInput() {
        return searchInput;
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
