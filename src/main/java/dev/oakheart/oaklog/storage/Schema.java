package dev.oakheart.oaklog.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class Schema {

    public static final int CURRENT_VERSION = 2;

    private Schema() {}

    public static final String CREATE_ENTRIES = """
            CREATE TABLE IF NOT EXISTS entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                namespace TEXT NOT NULL,
                key TEXT NOT NULL,
                category TEXT NOT NULL,
                display_name TEXT NOT NULL,
                icon_material TEXT,
                icon_model_data INTEGER,
                detection_meta TEXT,
                retired_at INTEGER,
                first_seen_at INTEGER NOT NULL,
                UNIQUE (namespace, key)
            )
            """;

    public static final String CREATE_COLLECTED = """
            CREATE TABLE IF NOT EXISTS collected (
                player_uuid TEXT NOT NULL,
                entry_id INTEGER NOT NULL,
                collected_at INTEGER NOT NULL,
                PRIMARY KEY (player_uuid, entry_id),
                FOREIGN KEY (entry_id) REFERENCES entries(id)
            )
            """;

    public static final String CREATE_COLLECTED_RECENT_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_collected_recent
            ON collected(player_uuid, collected_at DESC)
            """;

    public static final String CREATE_ENTRY_SETS = """
            CREATE TABLE IF NOT EXISTS entry_sets (
                entry_id INTEGER NOT NULL,
                set_id TEXT NOT NULL,
                PRIMARY KEY (entry_id, set_id),
                FOREIGN KEY (entry_id) REFERENCES entries(id)
            )
            """;

    public static final String CREATE_ENTRY_SETS_SET_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_entry_sets_set
            ON entry_sets(set_id)
            """;

    public static final String CREATE_PLAYER_SETTINGS = """
            CREATE TABLE IF NOT EXISTS player_settings (
                player_uuid TEXT PRIMARY KEY,
                toast_enabled INTEGER NOT NULL DEFAULT 0,
                toast_milestones_only INTEGER NOT NULL DEFAULT 0
            )
            """;

    public static final String CREATE_SCHEMA_VERSION = """
            CREATE TABLE IF NOT EXISTS schema_version (
                version INTEGER PRIMARY KEY,
                applied_at INTEGER NOT NULL,
                description TEXT
            )
            """;

    public static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_ENTRIES);
            stmt.execute(CREATE_COLLECTED);
            stmt.execute(CREATE_COLLECTED_RECENT_INDEX);
            stmt.execute(CREATE_ENTRY_SETS);
            stmt.execute(CREATE_ENTRY_SETS_SET_INDEX);
            stmt.execute(CREATE_PLAYER_SETTINGS);
            stmt.execute(CREATE_SCHEMA_VERSION);
        }
    }
}
