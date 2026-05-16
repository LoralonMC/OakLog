package dev.oakheart.oaklog;

import dev.oakheart.message.MessageManager;
import dev.oakheart.oaklog.config.ConfigManager;
import dev.oakheart.oaklog.storage.SqliteStore;
import dev.oakheart.util.DebugLogger;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class OakLog extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DebugLogger debugLogger;
    private SqliteStore dataStore;

    @Override
    public void onEnable() {
        try {
            initializeComponents();

            getLogger().info("OakLog enabled!");
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
    }

    public boolean reload() {
        if (!configManager.reload()) {
            return false;
        }
        messageManager.reload();

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

    public void debug(String message) {
        debugLogger.log(message);
    }
}
