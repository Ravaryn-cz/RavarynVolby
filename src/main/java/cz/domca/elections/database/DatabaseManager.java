package cz.domca.elections.database;

import cz.domca.elections.WeeklyElectionsPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;

public class DatabaseManager {
    
    private final WeeklyElectionsPlugin plugin;
    private HikariDataSource dataSource;
    
    public DatabaseManager(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        setupDataSource();
        createTables();
        plugin.getLogger().info("Database initialized successfully!");
    }
    
    private void setupDataSource() {
        HikariConfig config = new HikariConfig();
        
        String databaseFile = plugin.getConfigManager().getDatabaseFile();
        File dbFile = new File(plugin.getDataFolder(), databaseFile);
        
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setMaxLifetime(300000);
        config.setConnectionTimeout(10000);
        config.setLeakDetectionThreshold(60000);
        
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
                active BOOLEAN DEFAULT TRUE
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
