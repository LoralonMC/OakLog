package dev.oakheart.oaklog.storage;

import dev.oakheart.oaklog.config.ConfigManager;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.EntryDescriptor;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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

    // ========================================
    //          Entry Registry CRUD
    // ========================================

    @Override
    public synchronized List<Entry> loadAllEntries() {
        String sql = """
                SELECT id, namespace, key, category, display_name, icon_material,
                       icon_model_data, detection_meta, retired_at, first_seen_at
                FROM entries
                """;
        List<Entry> entries = new ArrayList<>();
        try {
            Connection conn = getConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    entries.add(readEntry(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load entries", e);
        }
        return entries;
    }

    @Override
    public synchronized List<Entry> insertEntries(List<EntryDescriptor> descriptors) {
        if (descriptors.isEmpty()) return List.of();

        String sql = """
                INSERT INTO entries
                (namespace, key, category, display_name, icon_material, icon_model_data,
                 detection_meta, first_seen_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        List<Entry> result = new ArrayList<>(descriptors.size());
        long now = System.currentTimeMillis();

        try {
            Connection conn = getConnection();
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                for (EntryDescriptor d : descriptors) {
                    stmt.setString(1, d.namespace());
                    stmt.setString(2, d.key());
                    stmt.setString(3, d.category().id());
                    stmt.setString(4, d.displayName());
                    if (d.iconMaterial() != null) stmt.setString(5, d.iconMaterial());
                    else stmt.setNull(5, java.sql.Types.VARCHAR);
                    if (d.iconModelData() != null) stmt.setInt(6, d.iconModelData());
                    else stmt.setNull(6, java.sql.Types.INTEGER);
                    if (d.detectionMeta() != null) stmt.setString(7, d.detectionMeta());
                    else stmt.setNull(7, java.sql.Types.VARCHAR);
                    stmt.setLong(8, now);
                    stmt.executeUpdate();

                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            int id = keys.getInt(1);
                            result.add(new Entry(id, d.namespace(), d.key(), d.category(),
                                    d.displayName(), d.iconMaterial(), d.iconModelData(),
                                    d.detectionMeta(), null, now));
                        }
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to insert entries", e);
            return List.of();
        }
        return result;
    }

    @Override
    public synchronized void updateEntryDisplay(int id, String displayName, String iconMaterial,
                                                Integer iconModelData, String detectionMeta) {
        String sql = """
                UPDATE entries
                SET display_name = ?, icon_material = ?, icon_model_data = ?, detection_meta = ?
                WHERE id = ?
                """;
        try {
            PreparedStatement stmt = prepareCached(sql);
            stmt.setString(1, displayName);
            if (iconMaterial != null) stmt.setString(2, iconMaterial);
            else stmt.setNull(2, java.sql.Types.VARCHAR);
            if (iconModelData != null) stmt.setInt(3, iconModelData);
            else stmt.setNull(3, java.sql.Types.INTEGER);
            if (detectionMeta != null) stmt.setString(4, detectionMeta);
            else stmt.setNull(4, java.sql.Types.VARCHAR);
            stmt.setInt(5, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update entry display for id " + id, e);
        }
    }

    @Override
    public synchronized void retireEntries(Collection<Integer> ids) {
        if (ids.isEmpty()) return;
        bulkSetRetired(ids, System.currentTimeMillis());
    }

    @Override
    public synchronized void unretireEntries(Collection<Integer> ids) {
        if (ids.isEmpty()) return;
        bulkSetRetired(ids, null);
    }

    private void bulkSetRetired(Collection<Integer> ids, Long retiredAt) {
        String sql = "UPDATE entries SET retired_at = ? WHERE id = ?";
        try {
            Connection conn = getConnection();
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int id : ids) {
                    if (retiredAt != null) stmt.setLong(1, retiredAt);
                    else stmt.setNull(1, java.sql.Types.BIGINT);
                    stmt.setInt(2, id);
                    stmt.addBatch();
                }
                stmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to bulk update retired_at", e);
        }
    }

    private static Entry readEntry(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String namespace = rs.getString("namespace");
        String key = rs.getString("key");
        Category category = Category.fromId(rs.getString("category"));
        String displayName = rs.getString("display_name");
        String iconMaterial = rs.getString("icon_material");
        int icmRaw = rs.getInt("icon_model_data");
        Integer iconModelData = rs.wasNull() ? null : icmRaw;
        String detectionMeta = rs.getString("detection_meta");
        long retRaw = rs.getLong("retired_at");
        Long retiredAt = rs.wasNull() ? null : retRaw;
        long firstSeenAt = rs.getLong("first_seen_at");
        return new Entry(id, namespace, key, category, displayName, iconMaterial,
                iconModelData, detectionMeta, retiredAt, firstSeenAt);
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
