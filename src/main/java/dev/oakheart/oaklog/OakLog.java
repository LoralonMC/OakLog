package dev.oakheart.oaklog;

import dev.oakheart.message.MessageManager;
import dev.oakheart.oaklog.config.ConfigManager;
import dev.oakheart.oaklog.audit.AuditLog;
import dev.oakheart.oaklog.integration.NexoLoadListener;
import dev.oakheart.oaklog.integration.OakLogPlaceholders;
import dev.oakheart.oaklog.toast.ToastService;
import org.bstats.bukkit.Metrics;
import dev.oakheart.oaklog.ui.search.AnvilSearchInput;
import dev.oakheart.oaklog.ui.search.ChatSearchInput;
import dev.oakheart.oaklog.ui.search.SearchInput;
import dev.oakheart.oaklog.ui.search.SignSearchInput;
import dev.oakheart.oaklog.commands.OakLogCommand;
import dev.oakheart.oaklog.config.SetsConfig;
import dev.oakheart.oaklog.detection.BiomeListener;
import dev.oakheart.oaklog.detection.EffectListener;
import dev.oakheart.oaklog.detection.EnchantmentListener;
import dev.oakheart.oaklog.detection.EntityListener;
import dev.oakheart.oaklog.detection.ItemBlockListener;
import dev.oakheart.oaklog.detection.StructureListener;
import dev.oakheart.oaklog.enumeration.BiomeGrouping;
import dev.oakheart.oaklog.enumeration.SetComputer;
import dev.oakheart.oaklog.enumeration.VanillaEnumerator;
import dev.oakheart.oaklog.integration.adapter.AdapterRegistry;
import dev.oakheart.oaklog.integration.adapter.ExecutableItemsAdapter;
import dev.oakheart.oaklog.integration.adapter.NexoAdapter;
import dev.oakheart.oaklog.integration.adapter.PdcAdapter;
import dev.oakheart.oaklog.leaderboard.LeaderboardManager;
import dev.oakheart.oaklog.progress.ProgressManager;
import dev.oakheart.oaklog.registry.EntryRegistry;
import dev.oakheart.oaklog.registry.RegistrySync;
import dev.oakheart.oaklog.storage.SqliteStore;
import dev.oakheart.oaklog.ui.GuiListener;
import dev.oakheart.oaklog.ui.IconRenderer;
import dev.oakheart.oaklog.util.DateFormat;
import dev.oakheart.oaklog.variants.VariantResolvers;
import dev.oakheart.util.DebugLogger;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class OakLog extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 30000; // Update with real ID after submission

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DebugLogger debugLogger;
    private SqliteStore dataStore;
    private BiomeGrouping biomeGrouping;
    private VariantResolvers variantResolvers;
    private VanillaEnumerator vanillaEnumerator;
    private EntryRegistry entryRegistry;
    private RegistrySync registrySync;
    private ProgressManager progressManager;
    private AdapterRegistry adapterRegistry;
    private SetsConfig setsConfig;
    private SetComputer setComputer;
    private LeaderboardManager leaderboardManager;
    private BiomeListener biomeListener;
    private AuditLog auditLog;
    private ToastService toastService;
    private SearchInput searchInput;
    private DateFormat dateFormat;
    private IconRenderer iconRenderer;

    @Override
    public void onEnable() {
        try {
            initializeComponents();
            syncRegistry();
            registerListeners();
            registerCommands();
            progressManager.start();
            leaderboardManager.start();
            biomeListener.start();
            initializeMetrics();
            registerPlaceholders();

            getLogger().info("OakLog enabled with " + entryRegistry.liveCount() + " live entries.");
            if (configManager.isDebug()) {
                getLogger().info("Debug mode is enabled.");
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable OakLog", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (biomeListener != null) {
            biomeListener.shutdown();
        }
        if (leaderboardManager != null) {
            leaderboardManager.shutdown();
        }
        if (progressManager != null) {
            progressManager.shutdown();
        }
        if (dataStore != null) {
            dataStore.close();
        }
        getLogger().info("OakLog disabled.");
    }

    private void initializeComponents() {
        configManager = new ConfigManager(this);
        configManager.load();

        debugLogger = new DebugLogger(getLogger(), configManager::isDebug);

        messageManager = new MessageManager(this, getLogger());
        messageManager.load();

        dataStore = new SqliteStore(getDataFolder(), configManager, getLogger());
        if (!dataStore.initialize()) {
            throw new IllegalStateException("Failed to initialize OakLog database. See log for details.");
        }

        biomeGrouping = new BiomeGrouping(this);
        biomeGrouping.load();

        variantResolvers = new VariantResolvers();
        vanillaEnumerator = new VanillaEnumerator(configManager, variantResolvers);
        entryRegistry = new EntryRegistry();

        adapterRegistry = new AdapterRegistry(getLogger());
        adapterRegistry.register(new NexoAdapter(this));
        adapterRegistry.register(new ExecutableItemsAdapter(this));
        adapterRegistry.register(new PdcAdapter(this));

        setsConfig = new SetsConfig(this);
        setsConfig.load();
        setComputer = new SetComputer(dataStore, entryRegistry);

        registrySync = new RegistrySync(dataStore, entryRegistry, vanillaEnumerator,
                adapterRegistry, setsConfig, setComputer, getLogger());
        progressManager = new ProgressManager(this, configManager, dataStore, entryRegistry);
        leaderboardManager = new LeaderboardManager(this, dataStore,
                configManager.getLeaderboardTopSize(), configManager.getLeaderboardRefreshSeconds());
        dateFormat = new DateFormat(configManager);
        iconRenderer = new IconRenderer(messageManager, dateFormat, adapterRegistry);
        auditLog = new AuditLog(this);

        toastService = new ToastService(this, configManager, entryRegistry);
        toastService.initialize();

        SearchInput chat = new ChatSearchInput(this);
        searchInput = switch (configManager.getSearchInput()) {
            case "sign" -> new SignSearchInput(this, chat);
            case "anvil" -> new AnvilSearchInput(this, chat);
            default -> chat;
        };
    }

    private void syncRegistry() {
        registrySync.run();
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(progressManager, this);
        getServer().getPluginManager().registerEvents(
                new ItemBlockListener(this, entryRegistry, adapterRegistry, progressManager), this);
        getServer().getPluginManager().registerEvents(
                new EntityListener(entryRegistry, progressManager, variantResolvers), this);
        getServer().getPluginManager().registerEvents(
                new EffectListener(entryRegistry, progressManager), this);
        getServer().getPluginManager().registerEvents(
                new EnchantmentListener(entryRegistry, progressManager), this);
        getServer().getPluginManager().registerEvents(
                new StructureListener(configManager, entryRegistry, progressManager), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(toastService, this);

        if (getServer().getPluginManager().getPlugin("Nexo") != null) {
            try {
                getServer().getPluginManager().registerEvents(new NexoLoadListener(this), this);
            } catch (Throwable t) {
                getLogger().warning("Could not register Nexo load listener: " + t.getMessage());
            }
        }

        biomeListener = new BiomeListener(this, configManager, entryRegistry, progressManager);
    }

    private void registerCommands() {
        new OakLogCommand(this, iconRenderer).register();
    }

    private void initializeMetrics() {
        new Metrics(this, BSTATS_PLUGIN_ID);
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        new OakLogPlaceholders(this).register();
        getLogger().info("PlaceholderAPI expansion registered.");
    }

    public boolean reload() {
        if (!configManager.reload()) {
            return false;
        }
        messageManager.reload();
        biomeGrouping.reload();
        setsConfig.reload();
        registrySync.run();
        leaderboardManager.invalidate();

        if (configManager.isDebug()) {
            getLogger().info("Debug mode is enabled.");
        }
        return true;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DebugLogger getDebugLogger() {
        return debugLogger;
    }

    public SqliteStore getDataStore() {
        return dataStore;
    }

    public BiomeGrouping getBiomeGrouping() {
        return biomeGrouping;
    }

    public EntryRegistry getEntryRegistry() {
        return entryRegistry;
    }

    public RegistrySync getRegistrySync() {
        return registrySync;
    }

    public VariantResolvers getVariantResolvers() {
        return variantResolvers;
    }

    public ProgressManager getProgressManager() {
        return progressManager;
    }

    public IconRenderer getIconRenderer() {
        return iconRenderer;
    }

    public DateFormat getDateFormatter() {
        return dateFormat;
    }

    public AdapterRegistry getAdapterRegistry() {
        return adapterRegistry;
    }

    public SetsConfig getSetsConfig() {
        return setsConfig;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public AuditLog getAuditLog() {
        return auditLog;
    }

    public ToastService getToastService() {
        return toastService;
    }

    public SearchInput getSearchInput() {
        return searchInput;
    }

    public void debug(String message) {
        debugLogger.log(message);
    }
}
