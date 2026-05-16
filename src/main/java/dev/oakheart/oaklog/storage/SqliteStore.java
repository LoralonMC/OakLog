package dev.oakheart.oaklog.storage;

import dev.oakheart.oaklog.config.ConfigManager;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SqliteStore implements DataStore {

    private final File dataFolder;
    private final ConfigManager configManager;
    private final Logger logger;
    private final ExecutorService executor;

    private final Map<String, PreparedStatement> statementCache = new HashMap<>();

    private Connection connection;
    private String dbUrl;
    private int cachedSchemaVersion;

    public SqliteStore(File dataFolder, ConfigManager configManager, Logger logger) {
        this.dataFolder = dataFolder;
        this.configManager = configManager;
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "OakLog-Database");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public boolean initialize() {
        try {
            openConnection();
            Schema.createTables(connection);
            cachedSchemaVersion = resolveSchemaVersion();
            logger.info("Database ready (schema version " + cachedSchemaVersion + ")");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize database", e);
            return false;
        }
    }

    @Override
    public int getSchemaVersion() {
        return cachedSchemaVersion;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        clearStatementCache();
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.warning("Error closing database connection: " + e.getMessage());
            }
            connection = null;
        }
    }

    protected ExecutorService getExecutor() {
        return executor;
    }

    protected synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            logger.warning("SQLite connection lost, attempting to reconnect...");
            clearStatementCache();
            openConnection();
        }
        return connection;
    }

    protected synchronized PreparedStatement prepareCached(String sql) throws SQLException {
        Connection conn = getConnection();
        PreparedStatement cached = statementCache.get(sql);
        if (cached != null && !cached.isClosed()) {
            cached.clearParameters();
            return cached;
        }
        PreparedStatement stmt = conn.prepareStatement(sql);
        statementCache.put(sql, stmt);
        return stmt;
    }

    private void openConnection() throws SQLException {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new SQLException("Could not create data folder: " + dataFolder);
        }

        File dbFile = new File(dataFolder, configManager.getStorageFilename());
        this.dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        this.connection = DriverManager.getConnection(dbUrl);
        configurePragmas(connection);
    }

    private void configurePragmas(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }
    }

    private int resolveSchemaVersion() throws SQLException {
        int current;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(version), 0) AS version FROM schema_version")) {
            current = rs.next() ? rs.getInt("version") : 0;
        }

        if (current == 0) {
            recordSchemaVersion(Schema.CURRENT_VERSION, "Initial schema");
            return Schema.CURRENT_VERSION;
        }

        if (current < Schema.CURRENT_VERSION) {
            logger.info("Migrating database from schema " + current + " to " + Schema.CURRENT_VERSION);
            recordSchemaVersion(Schema.CURRENT_VERSION, "Migration to " + Schema.CURRENT_VERSION);
            return Schema.CURRENT_VERSION;
        }

        return current;
    }

    private void recordSchemaVersion(int version, String description) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO schema_version (version, applied_at, description) VALUES (?, ?, ?)")) {
            stmt.setInt(1, version);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, description);
            stmt.executeUpdate();
        }
    }

    private void clearStatementCache() {
        for (PreparedStatement stmt : statementCache.values()) {
            try {
                stmt.close();
            } catch (SQLException ignored) {}
        }
        statementCache.clear();
    }
}
