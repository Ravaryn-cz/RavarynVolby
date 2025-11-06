package cz.domca.elections.database;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import cz.domca.elections.WeeklyElectionsPlugin;

public class DatabaseManager {
    
    private final WeeklyElectionsPlugin plugin;
    private HikariDataSource dataSource;
    
    public DatabaseManager(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        setupDataSource();
        createTables();
        migrateDatabase();
        plugin.getLogger().info("Database initialized successfully!");
    }
    
    private void setupDataSource() {
        HikariConfig config = new HikariConfig();
        
        String databaseFile = plugin.getConfigManager().getDatabaseFile();
        File dbFile = new File(plugin.getDataFolder(), databaseFile);
        
        // Configure SQLite-specific connection string with proper settings for concurrent access
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath() + 
            "?journal_mode=WAL" +           // Write-Ahead Logging for better concurrent access
            "&busy_timeout=30000" +          // Wait up to 30 seconds if database is locked
            "&synchronous=NORMAL");          // Balance between safety and performance
        
        // Limit pool size to 1 for SQLite to avoid locking issues
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setMaxLifetime(300000);
        config.setConnectionTimeout(30000);  // Increased timeout
        config.setLeakDetectionThreshold(60000);
        
        // Add connection test query
        config.setConnectionTestQuery("SELECT 1");
        
        this.dataSource = new HikariDataSource(config);
    }
    
    private void createTables() {
        String[] createTableQueries = {
            """
            CREATE TABLE IF NOT EXISTS elections (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                region_id TEXT NOT NULL,
                phase TEXT NOT NULL,
                start_time BIGINT NOT NULL,
                end_time BIGINT,
                created_at BIGINT DEFAULT (strftime('%s', 'now'))
            )
            """,
            
            """
            CREATE TABLE IF NOT EXISTS candidates (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                election_id INTEGER NOT NULL,
                player_uuid TEXT NOT NULL,
                player_name TEXT NOT NULL,
                role TEXT NOT NULL,
                slogan TEXT,
                votes INTEGER DEFAULT 0,
                created_at BIGINT DEFAULT (strftime('%s', 'now')),
                FOREIGN KEY (election_id) REFERENCES elections(id),
                UNIQUE(election_id, player_uuid)
            )
            """,
            
            """
            CREATE TABLE IF NOT EXISTS votes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                election_id INTEGER NOT NULL,
                voter_uuid TEXT NOT NULL,
                candidate_id INTEGER NOT NULL,
                voted_at BIGINT DEFAULT (strftime('%s', 'now')),
                FOREIGN KEY (election_id) REFERENCES elections(id),
                FOREIGN KEY (candidate_id) REFERENCES candidates(id),
                UNIQUE(election_id, voter_uuid)
            )
            """,
            
            """
            CREATE TABLE IF NOT EXISTS role_holders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                player_name TEXT NOT NULL,
                region_id TEXT NOT NULL,
                role TEXT NOT NULL,
                start_time BIGINT NOT NULL,
                end_time BIGINT NOT NULL,
                active BOOLEAN DEFAULT TRUE,
                notified BOOLEAN DEFAULT FALSE
            )
            """,
            
            """
            CREATE TABLE IF NOT EXISTS reputation (
                player_uuid TEXT PRIMARY KEY,
                player_name TEXT NOT NULL,
                reputation INTEGER DEFAULT 0,
                last_updated BIGINT DEFAULT (strftime('%s', 'now'))
            )
            """,
            
            """
            CREATE TABLE IF NOT EXISTS npc_locations (
                region_id TEXT PRIMARY KEY,
                world TEXT NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL,
                npc_id INTEGER,
                created_at BIGINT DEFAULT (strftime('%s', 'now'))
            )
            """
        };
        
        try (Connection conn = getConnection()) {
            for (String query : createTableQueries) {
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables", e);
        }
    }
    
    private void migrateDatabase() {
        try (Connection conn = getConnection()) {
            // Add notified column to role_holders if it doesn't exist
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT notified FROM role_holders LIMIT 1")) {
                stmt.executeQuery();
                // Column exists, no migration needed
            } catch (SQLException e) {
                // Column doesn't exist, add it
                try (PreparedStatement stmt = conn.prepareStatement(
                    "ALTER TABLE role_holders ADD COLUMN notified BOOLEAN DEFAULT FALSE")) {
                    stmt.executeUpdate();
                    plugin.getLogger().info("Added 'notified' column to role_holders table");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to migrate database", e);
        }
    }
    
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed");
        }
    }
}
