package dev.oakheart.oaklog.storage;

import dev.oakheart.oaklog.config.ConfigManager;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.EntryDescriptor;
import dev.oakheart.oaklog.model.PlayerProgress;
import dev.oakheart.oaklog.model.RecentEntry;

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
import java.util.UUID;
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

    // ========================================
    //          Per-player progress CRUD
    // ========================================

    @Override
    public synchronized Map<Integer, Long> loadCollected(UUID playerUuid) {
        String sql = "SELECT entry_id, collected_at FROM collected WHERE player_uuid = ?";
        Map<Integer, Long> result = new HashMap<>();
        try {
            PreparedStatement stmt = prepareCached(sql);
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("entry_id"), rs.getLong("collected_at"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load collected entries for " + playerUuid, e);
        }
        return result;
    }

    @Override
    public synchronized List<RecentEntry> loadRecent(UUID playerUuid, int limit) {
        String sql = """
                SELECT entry_id, collected_at
                FROM collected
                WHERE player_uuid = ?
                ORDER BY collected_at DESC
                LIMIT ?
                """;
        List<RecentEntry> out = new ArrayList<>();
        try {
            PreparedStatement stmt = prepareCached(sql);
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new RecentEntry(rs.getInt("entry_id"), rs.getLong("collected_at")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load recent entries for " + playerUuid, e);
        }
        return out;
    }

    @Override
    public synchronized PlayerProgress.ToastState loadToastState(UUID playerUuid) {
        String sql = "SELECT toast_enabled, toast_milestones_only FROM player_settings WHERE player_uuid = ?";
        try {
            PreparedStatement stmt = prepareCached(sql);
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                boolean enabled = rs.getInt("toast_enabled") != 0;
                boolean milestones = rs.getInt("toast_milestones_only") != 0;
                if (!enabled) return PlayerProgress.ToastState.OFF;
                return milestones ? PlayerProgress.ToastState.MILESTONES : PlayerProgress.ToastState.ON;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load toast state for " + playerUuid, e);
            return null;
        }
    }

    @Override
    public synchronized void saveToastState(UUID playerUuid, PlayerProgress.ToastState state) {
        String sql = """
                INSERT INTO player_settings (player_uuid, toast_enabled, toast_milestones_only)
                VALUES (?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    toast_enabled = excluded.toast_enabled,
                    toast_milestones_only = excluded.toast_milestones_only
                """;
        boolean enabled = state != PlayerProgress.ToastState.OFF;
        boolean milestones = state == PlayerProgress.ToastState.MILESTONES;
        try {
            PreparedStatement stmt = prepareCached(sql);
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, enabled ? 1 : 0);
            stmt.setInt(3, milestones ? 1 : 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save toast state for " + playerUuid, e);
        }
    }

    @Override
    public synchronized void batchInsertCollected(Map<UUID, Map<Integer, Long>> dirtyByPlayer) {
        if (dirtyByPlayer.isEmpty()) return;
        String sql = """
                INSERT INTO collected (player_uuid, entry_id, collected_at)
                VALUES (?, ?, ?)
                ON CONFLICT(player_uuid, entry_id) DO NOTHING
                """;
        try {
            Connection conn = getConnection();
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Map.Entry<UUID, Map<Integer, Long>> outer : dirtyByPlayer.entrySet()) {
                    String uuidStr = outer.getKey().toString();
                    for (Map.Entry<Integer, Long> inner : outer.getValue().entrySet()) {
                        stmt.setString(1, uuidStr);
                        stmt.setInt(2, inner.getKey());
                        stmt.setLong(3, inner.getValue());
                        stmt.addBatch();
                    }
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
            logger.log(Level.SEVERE, "Failed to flush collected entries", e);
        }
    }

    @Override
    public synchronized void deleteCollected(UUID playerUuid, int entryId) {
        String sql = "DELETE FROM collected WHERE player_uuid = ? AND entry_id = ?";
        try {
            PreparedStatement stmt = prepareCached(sql);
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, entryId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete collected entry for " + playerUuid, e);
        }
    }

    @Override
    public synchronized void replaceEntrySets(Map<Integer, java.util.Set<String>> entryToSets) {
        try {
            Connection conn = getConnection();
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM entry_sets");
            }

            String sql = "INSERT INTO entry_sets (entry_id, set_id) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Map.Entry<Integer, java.util.Set<String>> e : entryToSets.entrySet()) {
                    int entryId = e.getKey();
                    for (String setId : e.getValue()) {
                        stmt.setInt(1, entryId);
                        stmt.setString(2, setId);
                        stmt.addBatch();
                    }
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
            logger.log(Level.SEVERE, "Failed to replace entry_sets", e);
        }
    }

    @Override
    public synchronized Map<Integer, java.util.Set<String>> loadAllSetMemberships() {
        Map<Integer, java.util.Set<String>> out = new HashMap<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT entry_id, set_id FROM entry_sets")) {
            while (rs.next()) {
                int entryId = rs.getInt("entry_id");
                String setId = rs.getString("set_id");
                out.computeIfAbsent(entryId, k -> new java.util.HashSet<>()).add(setId);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load entry_sets", e);
        }
        return out;
    }

    @Override
    public synchronized List<LeaderboardRow> topOverall(int limit) {
        String sql = """
                SELECT c.player_uuid AS uuid, COUNT(*) AS count
                FROM collected c
                JOIN entries e ON c.entry_id = e.id
                WHERE e.retired_at IS NULL
                GROUP BY c.player_uuid
                ORDER BY count DESC
                LIMIT ?
                """;
        return runLeaderboardQuery(sql, stmt -> stmt.setInt(1, limit));
    }

    @Override
    public synchronized List<LeaderboardRow> topByCategory(dev.oakheart.oaklog.model.Category category, int limit) {
        String sql = """
                SELECT c.player_uuid AS uuid, COUNT(*) AS count
                FROM collected c
                JOIN entries e ON c.entry_id = e.id
                WHERE e.retired_at IS NULL AND e.category = ?
                GROUP BY c.player_uuid
                ORDER BY count DESC
                LIMIT ?
                """;
        return runLeaderboardQuery(sql, stmt -> {
            stmt.setString(1, category.id());
            stmt.setInt(2, limit);
        });
    }

    @Override
    public synchronized int collectedCountOverall(UUID playerUuid) {
        String sql = """
                SELECT COUNT(*) AS count
                FROM collected c
                JOIN entries e ON c.entry_id = e.id
                WHERE c.player_uuid = ? AND e.retired_at IS NULL
                """;
        try {
            PreparedStatement stmt = prepareCached(sql);
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to count collected for " + playerUuid, e);
            return 0;
        }
    }

    private List<LeaderboardRow> runLeaderboardQuery(String sql, StatementBinder binder) {
        List<LeaderboardRow> rows = new ArrayList<>();
        try {
            PreparedStatement stmt = prepareCached(sql);
            binder.bind(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    int count = rs.getInt("count");
                    rows.add(new LeaderboardRow(uuid, count));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed leaderboard query", e);
        }
        return rows;
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement stmt) throws SQLException;
    }

    @Override
    public synchronized int deleteAllCollected(UUID playerUuid) {
        String sql = "DELETE FROM collected WHERE player_uuid = ?";
        try {
            PreparedStatement stmt = prepareCached(sql);
            stmt.setString(1, playerUuid.toString());
            return stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to wipe collected entries for " + playerUuid, e);
            return 0;
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
