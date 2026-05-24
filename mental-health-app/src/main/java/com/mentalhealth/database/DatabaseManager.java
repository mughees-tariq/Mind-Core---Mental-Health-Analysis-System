package com.mentalhealth.database;

import java.sql.*;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;

// Singleton manager for SQLite database connection, schema creation, and migration
public class DatabaseManager {

    private static final Logger LOG = Logger.getLogger(DatabaseManager.class.getName());
    
    private static DatabaseManager instance;
    private Connection connection;
    private static final String DB_PATH;

    static {
        String appData = System.getenv("APPDATA");
        String base = (appData != null && !appData.isEmpty())
            ? appData + "\\MentalHealthApp"
            : System.getProperty("user.home") + "/.mentalhealth";
        DB_PATH = base + "/mental_health.db";
    }
    
    private DatabaseManager() {
        initializeDatabase();
    }
    
    // Returns singleton instance, creating it if necessary
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }
    
    private void initializeDatabase() {
        try {
            // Create data directory
            Files.createDirectories(Paths.get(DB_PATH).getParent());
            
            // Connect to SQLite
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            LOG.info("Database connected: " + DB_PATH);
            
            // Create tables
            createTables();
            
            // Migrate existing databases
            migrateDatabase();
            
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Database initialization error", e);
        }
    }
    
    private void createTables() throws SQLException {
        String[] tables = {
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                name TEXT NOT NULL,
                age INTEGER,
                goal TEXT,
                baseline_stress INTEGER DEFAULT 5,
                feeling_history TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS predictions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                input_text TEXT NOT NULL,
                prediction TEXT NOT NULL,
                confidence REAL NOT NULL,
                severity TEXT,
                color TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS mood_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                mood_score INTEGER NOT NULL,
                notes TEXT,
                entry_date DATE NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS consoling_reports (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                report_text TEXT NOT NULL,
                period_start DATE NOT NULL,
                period_end DATE NOT NULL,
                generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS emergency_contacts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                phone TEXT NOT NULL,
                relationship TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS history_metadata (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                prediction_id INTEGER NOT NULL,
                user_id INTEGER NOT NULL,
                starred BOOLEAN DEFAULT 0,
                tags TEXT,
                user_notes TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(prediction_id, user_id),
                FOREIGN KEY (prediction_id) REFERENCES predictions(id) ON DELETE CASCADE,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS self_care_checklist (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                check_date DATE NOT NULL,
                item_key TEXT NOT NULL,
                checked BOOLEAN NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(user_id, check_date, item_key),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS recommendation_progress (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                rec_date DATE NOT NULL,
                item_key TEXT NOT NULL,
                completed BOOLEAN NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(user_id, rec_date, item_key),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """
        };
        
        try (Statement stmt = connection.createStatement()) {
            for (String sql : tables) {
                stmt.execute(sql);
            }
            LOG.info("Database tables ready!");
        }
    }
    
    private void migrateDatabase() throws SQLException {
        // Add feeling_history column if it doesn't exist
        addColumnIfMissing("users", "feeling_history", "TEXT");
        // Add gender, location, affirmations columns for profile enhancements
        addColumnIfMissing("users", "gender", "TEXT");
        addColumnIfMissing("users", "location", "TEXT");
        addColumnIfMissing("users", "affirmations", "TEXT");
        addColumnIfMissing("users", "security_question", "TEXT");
        addColumnIfMissing("users", "security_answer", "TEXT");
    }

    private void addColumnIfMissing(String table, String column, String type) {
        try (Statement stmt = connection.createStatement()) {
            // Check if column exists
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")");
            boolean hasColumn = false;
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    hasColumn = true;
                    break;
                }
            }
            rs.close();
            
            // Add column if it doesn't exist
            if (!hasColumn) {
                stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                LOG.info("Added " + column + " column to " + table + " table");
            }
        } catch (SQLException e) {
            LOG.warning("Migration warning (" + column + "): " + e.getMessage());
        }
    }
    
    // Returns active JDBC connection, re-initialising if closed
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                initializeDatabase();
            }
        } catch (SQLException e) {
            LOG.warning("Error checking connection: " + e.getMessage());
        }
        return connection;
    }
    
    // Closes database connection and releases resources
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                LOG.info("Database connection closed.");
            }
        } catch (SQLException e) {
            LOG.warning("Error closing database: " + e.getMessage());
        }
    }
    
    // Utility method to execute a simple query
    public boolean executeUpdate(String sql, Object... params) {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.warning("Error executing update: " + e.getMessage());
            return false;
        }
    }
    
    // Utility method to begin transaction
    public void beginTransaction() throws SQLException {
        connection.setAutoCommit(false);
    }
    
    // Utility method to commit transaction
    public void commitTransaction() throws SQLException {
        connection.commit();
        connection.setAutoCommit(true);
    }
    
    // Utility method to rollback transaction
    public void rollbackTransaction() {
        try {
            connection.rollback();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            LOG.warning("Error rolling back transaction: " + e.getMessage());
        }
    }
}