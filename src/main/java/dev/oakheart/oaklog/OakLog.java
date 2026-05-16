package dev.oakheart.oaklog;

import dev.oakheart.message.MessageManager;
import dev.oakheart.oaklog.config.ConfigManager;
import dev.oakheart.oaklog.commands.OakLogCommand;
import dev.oakheart.oaklog.detection.ItemBlockListener;
import dev.oakheart.oaklog.enumeration.BiomeGrouping;
import dev.oakheart.oaklog.enumeration.VanillaEnumerator;
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
        registrySync = new RegistrySync(dataStore, entryRegistry, vanillaEnumerator, getLogger());
        progressManager = new ProgressManager(this, configManager, dataStore, entryRegistry);
        dateFormat = new DateFormat(configManager);
        iconRenderer = new IconRenderer(messageManager, dateFormat);
    }

    private void syncRegistry() {
        registrySync.run();
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(progressManager, this);
        getServer().getPluginManager().registerEvents(
                new ItemBlockListener(entryRegistry, progressManager), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
    }

    private void registerCommands() {
        new OakLogCommand(this, iconRenderer).register();
    }

    public boolean reload() {
        if (!configManager.reload()) {
            return false;
        }
        messageManager.reload();
        biomeGrouping.reload();
        registrySync.run();

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

    public void debug(String message) {
        debugLogger.log(message);
    }
}
