package com.expensebook.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DBConnection {

    private static Connection connection;
    private static Properties config = new Properties();
    private static boolean isMySQL = true;
    private static boolean isPostgres = false;
    private static final String PROPERTIES_FILE = "db.properties";

    static {
        loadConfig();
    }

    public static void loadConfig() {
        try {
            File propFile = new File(PROPERTIES_FILE);
            if (propFile.exists()) {
                try (InputStream fis = new FileInputStream(propFile)) {
                    config.load(fis);
                }
            } else {
                try (InputStream is = DBConnection.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
                    if (is != null) {
                        config.load(is);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Notice: Using default database properties. " + e.getMessage());
        }

        // Set defaults if missing
        if (!config.containsKey("db.type")) config.setProperty("db.type", "mysql");
        if (!config.containsKey("db.host")) config.setProperty("db.host", "localhost");
        if (!config.containsKey("db.port")) config.setProperty("db.port", "3306");
        if (!config.containsKey("db.database")) config.setProperty("db.database", "expensebook");
        if (!config.containsKey("db.user")) config.setProperty("db.user", "root");
        if (!config.containsKey("db.password")) config.setProperty("db.password", "");
        if (!config.containsKey("db.fallback_to_sqlite")) config.setProperty("db.fallback_to_sqlite", "true");
        if (!config.containsKey("db.ssl")) config.setProperty("db.ssl", "false");

        // Environment variable overrides for production cloud deployments (Railway, Render, Heroku, AWS, Docker)
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isEmpty()) {
            databaseUrl = System.getenv("MYSQL_URL");
        }

        if (databaseUrl != null && !databaseUrl.trim().isEmpty()) {
            parseAndApplyDatabaseUrl(databaseUrl.trim());
        } else {
            if (System.getenv("DB_TYPE") != null) config.setProperty("db.type", System.getenv("DB_TYPE"));
            if (System.getenv("DB_HOST") != null) config.setProperty("db.host", System.getenv("DB_HOST"));
            if (System.getenv("DB_PORT") != null) config.setProperty("db.port", System.getenv("DB_PORT"));
            if (System.getenv("DB_NAME") != null) config.setProperty("db.database", System.getenv("DB_NAME"));
            else if (System.getenv("DB_DATABASE") != null) config.setProperty("db.database", System.getenv("DB_DATABASE"));
            if (System.getenv("DB_USER") != null) config.setProperty("db.user", System.getenv("DB_USER"));
            else if (System.getenv("DB_USERNAME") != null) config.setProperty("db.user", System.getenv("DB_USERNAME"));
            if (System.getenv("DB_PASSWORD") != null) config.setProperty("db.password", System.getenv("DB_PASSWORD"));
            if (System.getenv("DB_SSL") != null) config.setProperty("db.ssl", System.getenv("DB_SSL"));
        }
    }

    private static void parseAndApplyDatabaseUrl(String rawUrl) {
        try {
            // Handles mysql://user:password@host:port/database
            String clean = rawUrl;
            if (clean.startsWith("jdbc:")) {
                clean = clean.substring(5);
            }
            if (clean.startsWith("postgres://") || clean.startsWith("postgresql://")) {
                int schemeEnd = clean.indexOf("://") + 3;
                clean = clean.substring(schemeEnd);
                String userInfo = clean.substring(0, clean.indexOf('@'));
                String hostPortDb = clean.substring(clean.indexOf('@') + 1);

                String[] userParts = userInfo.split(":", 2);
                config.setProperty("db.user", userParts[0]);
                if (userParts.length > 1) config.setProperty("db.password", userParts[1]);

                String hostPort = hostPortDb.substring(0, hostPortDb.indexOf('/'));
                String dbAndParams = hostPortDb.substring(hostPortDb.indexOf('/') + 1);

                String[] hp = hostPort.split(":", 2);
                config.setProperty("db.host", hp[0]);
                if (hp.length > 1) config.setProperty("db.port", hp[1]);
                else config.setProperty("db.port", "5432");

                String dbName = dbAndParams.contains("?") ? dbAndParams.substring(0, dbAndParams.indexOf('?')) : dbAndParams;
                config.setProperty("db.database", dbName);
                config.setProperty("db.type", "postgres");
                config.setProperty("db.ssl", "true");
            } else if (clean.startsWith("mysql://")) {
                clean = clean.substring(8);
                String userInfo = clean.substring(0, clean.indexOf('@'));
                String hostPortDb = clean.substring(clean.indexOf('@') + 1);

                String[] userParts = userInfo.split(":", 2);
                config.setProperty("db.user", userParts[0]);
                if (userParts.length > 1) config.setProperty("db.password", userParts[1]);

                String hostPort = hostPortDb.substring(0, hostPortDb.indexOf('/'));
                String dbAndParams = hostPortDb.substring(hostPortDb.indexOf('/') + 1);

                String[] hp = hostPort.split(":", 2);
                config.setProperty("db.host", hp[0]);
                if (hp.length > 1) config.setProperty("db.port", hp[1]);

                String dbName = dbAndParams.contains("?") ? dbAndParams.substring(0, dbAndParams.indexOf('?')) : dbAndParams;
                config.setProperty("db.database", dbName);
                config.setProperty("db.type", "mysql");
                config.setProperty("db.ssl", "true");
            }
        } catch (Exception e) {
            System.err.println("Notice: Could not parse DATABASE_URL directly, falling back to standard config: " + e.getMessage());
        }
    }

    public static synchronized Connection getConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                try {
                    if (connection.isValid(2)) {
                        return connection;
                    }
                } catch (Exception ignored) {
                    // Stale or dropped socket; close and re-establish connection
                }
                try {
                    connection.close();
                } catch (Exception ignored) {}
                connection = null;
            }

            String dbType = config.getProperty("db.type", "mysql").toLowerCase();
            boolean fallback = Boolean.parseBoolean(config.getProperty("db.fallback_to_sqlite", "true"));
            if (isProduction()) {
                // In production, fallback to ephemeral SQLite is strictly forbidden
                fallback = false;
            }

            if ("postgres".equals(dbType) || "postgresql".equals(dbType)) {
                try {
                    Class.forName("org.postgresql.Driver");
                    String host = config.getProperty("db.host", "localhost");
                    String port = config.getProperty("db.port", "5432");
                    String database = config.getProperty("db.database", "expensebook");
                    String user = config.getProperty("db.user", "postgres");
                    String password = config.getProperty("db.password", "");
                    boolean ssl = Boolean.parseBoolean(config.getProperty("db.ssl", "true"));

                    String url = String.format("jdbc:postgresql://%s:%s/%s%s",
                            host, port, database, ssl ? "?sslmode=require&tcpKeepAlive=true" : "?tcpKeepAlive=true");

                    connection = DriverManager.getConnection(url, user, password);
                    isMySQL = false;
                    isPostgres = true;
                    System.out.println(" Connected to PostgreSQL database [" + database + "] successfully.");
                    initializeDatabase();
                    return connection;
                } catch (Exception pgEx) {
                    System.err.println(" PostgreSQL connection failed: " + pgEx.getMessage());
                    if (!fallback) {
                        throw new RuntimeException("Could not connect to PostgreSQL: " + pgEx.getMessage(), pgEx);
                    }
                    System.out.println(" Falling back to local embedded SQLite database for zero-downtime execution...");
                }
            } else if ("mysql".equals(dbType)) {
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    String host = config.getProperty("db.host", "localhost");
                    String port = config.getProperty("db.port", "3306");
                    String database = config.getProperty("db.database", "expensebook");
                    String user = config.getProperty("db.user", "root");
                    String password = config.getProperty("db.password", "");
                    boolean ssl = Boolean.parseBoolean(config.getProperty("db.ssl", "false"));

                    String url = String.format(
                            "jdbc:mysql://%s:%s/%s?createDatabaseIfNotExist=true&useSSL=%s&allowPublicKeyRetrieval=true&serverTimezone=UTC&autoReconnect=true&tcpKeepAlive=true",
                            host, port, database, ssl ? "true" : "false");

                    connection = DriverManager.getConnection(url, user, password);
                    isMySQL = true;
                    isPostgres = false;
                    System.out.println(" Connected to MySQL database [" + database + "] successfully.");
                    initializeDatabase();
                    return connection;
                } catch (Exception mysqlEx) {
                    System.err.println(" MySQL connection failed: " + mysqlEx.getMessage());
                    if (!fallback) {
                        throw new RuntimeException("Could not connect to MySQL: " + mysqlEx.getMessage(), mysqlEx);
                    }
                    System.out.println(" Falling back to local embedded SQLite database for zero-downtime execution...");
                }
            }

            if (isProduction()) {
                throw new IllegalStateException("FATAL: Running in production mode (APP_ENV=production) but persistent database connection failed. Silent fallback to ephemeral SQLite is strictly prohibited in production. Please configure MONGODB_URI.");
            }

            // SQLite Fallback or SQLite explicit mode
            Class.forName("org.sqlite.JDBC");
            String sqlitePath = System.getenv("SQLITE_DB_PATH");
            if (sqlitePath == null || sqlitePath.trim().isEmpty()) {
                File targetFile = new File("expensebook.db");
                boolean canWrite = false;
                try {
                    File testFile = new File(".perm_check_" + System.currentTimeMillis());
                    if (testFile.createNewFile()) {
                        testFile.delete();
                        canWrite = true;
                    } else if (targetFile.exists() && targetFile.canWrite()) {
                        canWrite = true;
                    }
                } catch (Exception ignored) {
                    canWrite = false;
                }

                if (canWrite) {
                    sqlitePath = targetFile.getAbsolutePath();
                } else {
                    String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");
                    File tmpDb = new File(tmpDir, "expensebook.db");
                    sqlitePath = tmpDb.getAbsolutePath();
                    System.out.println("⚠️ Working directory is read-only. Redirecting SQLite database to writable path: " + sqlitePath);
                }
            }
            String sqliteUrl = "jdbc:sqlite:" + sqlitePath;
            connection = DriverManager.getConnection(sqliteUrl);
            isMySQL = false;
            System.out.println(" Connected to SQLite database [" + sqliteUrl + "].");
            initializeDatabase();
            return connection;

        } catch (Exception e) {
            throw new RuntimeException("Database initialization error: " + e.getMessage(), e);
        }
    }

    public static boolean isProduction() {
        String appEnv = System.getenv("APP_ENV");
        if (appEnv == null || appEnv.trim().isEmpty()) appEnv = System.getenv("ENV");
        if (appEnv == null || appEnv.trim().isEmpty()) appEnv = System.getProperty("app.env", "development");
        return "production".equalsIgnoreCase(appEnv.trim());
    }

    public static boolean isMySQL() {
        return isMySQL;
    }

    public static String getDbType() {
        return config.getProperty("db.type", "sqlite");
    }

    public static String getDatabaseStatusText() {
        if (MongoConnectionManager.isMongoActive()) {
            return "MongoDB Atlas (Connected)";
        }
        if ("sqlite".equalsIgnoreCase(config.getProperty("db.type"))) {
            return "SQLite (Local File: expensebook.db)";
        }
        return isMySQL ? "MySQL 8.0 (Connected)" : "SQLite (Fallback Mode: expensebook.db)";
    }

    public static boolean testConnection(String host, int port, String database, String user, String password) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = String.format(
                    "jdbc:mysql://%s:%d/%s?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    host, port, database);
            try (Connection testConn = DriverManager.getConnection(url, user, password)) {
                return testConn != null && !testConn.isClosed();
            }
        } catch (Exception e) {
            System.err.println("Test connection failed: " + e.getMessage());
            return false;
        }
    }

    public static synchronized void saveProperties(String dbType, String host, int port, String database, String user, String password) {
        config.setProperty("db.type", dbType != null ? dbType.toLowerCase() : "sqlite");
        if (host != null && !host.trim().isEmpty()) config.setProperty("db.host", host);
        if (port > 0) config.setProperty("db.port", String.valueOf(port));
        if (database != null && !database.trim().isEmpty()) config.setProperty("db.database", database);
        if (user != null && !user.trim().isEmpty()) config.setProperty("db.user", user);
        if (password != null) config.setProperty("db.password", password);

        try (FileOutputStream fos = new FileOutputStream(PROPERTIES_FILE)) {
            config.store(fos, "ExPense Book DB Configuration");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Reset connection to pick up new credentials
        closeConnection();
    }

    public static synchronized void saveProperties(String host, int port, String database, String user, String password) {
        saveProperties("mysql", host, port, database, user, password);
    }

    public static synchronized void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        } finally {
            connection = null;
        }
    }

    private static void initializeDatabase() {
        try (Statement stmt = connection.createStatement()) {
            String primaryKey;
            String boolDefault;
            String boolZero;
            if (isPostgres) {
                primaryKey = "SERIAL PRIMARY KEY";
                boolDefault = "DEFAULT TRUE";
                boolZero = "DEFAULT FALSE";
            } else if (isMySQL) {
                primaryKey = "INT AUTO_INCREMENT PRIMARY KEY";
                boolDefault = "DEFAULT 1";
                boolZero = "DEFAULT 0";
            } else {
                primaryKey = "INTEGER PRIMARY KEY AUTOINCREMENT";
                boolDefault = "DEFAULT 1";
                boolZero = "DEFAULT 0";
            }

            // 1. Users table
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id " + primaryKey + ", " +
                    "full_name VARCHAR(100) NOT NULL, " +
                    "email VARCHAR(150) NOT NULL UNIQUE, " +
                    "password_hash VARCHAR(255) NOT NULL, " +
                    "role VARCHAR(20) DEFAULT 'USER', " +
                    "financial_mode VARCHAR(30) DEFAULT 'TRACK_ONLY', " +
                    "currency VARCHAR(10) DEFAULT '₹', " +
                    "is_active BOOLEAN " + boolDefault + ", " +
                    "current_session_id VARCHAR(100), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Safe column migration for users.current_session_id on existing databases
            try {
                boolean hasCol = false;
                try (ResultSet colRs = connection.getMetaData().getColumns(null, null, "users", "current_session_id")) {
                    if (colRs.next()) hasCol = true;
                }
                if (!hasCol) {
                    try (ResultSet colRs = connection.getMetaData().getColumns(null, null, "USERS", "CURRENT_SESSION_ID")) {
                        if (colRs.next()) hasCol = true;
                    }
                }
                if (!hasCol) {
                    stmt.execute("ALTER TABLE users ADD COLUMN current_session_id VARCHAR(100)");
                }
            } catch (Exception ignored) {}

            // 2. Categories table
            stmt.execute("CREATE TABLE IF NOT EXISTS categories (" +
                    "id " + primaryKey + ", " +
                    "user_id INTEGER NULL, " +
                    "name VARCHAR(50) NOT NULL, " +
                    "icon_name VARCHAR(50) NOT NULL, " +
                    "color VARCHAR(20) DEFAULT '#78BFA0', " +
                    "is_default BOOLEAN " + boolDefault + ", " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // 3. Expenses table
            stmt.execute("CREATE TABLE IF NOT EXISTS expenses (" +
                    "id " + primaryKey + ", " +
                    "user_id INTEGER NOT NULL, " +
                    "category_id INTEGER NOT NULL, " +
                    "amount DECIMAL(12,2) NOT NULL, " +
                    "payment_mode VARCHAR(30) NOT NULL, " +
                    "expense_date DATE NOT NULL, " +
                    "description VARCHAR(255), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // 4. Income table
            stmt.execute("CREATE TABLE IF NOT EXISTS income (" +
                    "id " + primaryKey + ", " +
                    "user_id INTEGER NOT NULL, " +
                    "source VARCHAR(50) NOT NULL, " +
                    "amount DECIMAL(12,2) NOT NULL, " +
                    "income_date DATE NOT NULL, " +
                    "description VARCHAR(255), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // 5. Budgets table
            stmt.execute("CREATE TABLE IF NOT EXISTS budgets (" +
                    "id " + primaryKey + ", " +
                    "user_id INTEGER NOT NULL, " +
                    "month INTEGER NOT NULL, " +
                    "year INTEGER NOT NULL, " +
                    "total_budget DECIMAL(12,2) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // 6. Category Budgets table
            stmt.execute("CREATE TABLE IF NOT EXISTS category_budgets (" +
                    "id " + primaryKey + ", " +
                    "budget_id INTEGER NOT NULL, " +
                    "category_id INTEGER NOT NULL, " +
                    "amount DECIMAL(12,2) NOT NULL)");

            // 7. Recurring Expenses table
            stmt.execute("CREATE TABLE IF NOT EXISTS recurring_expenses (" +
                    "id " + primaryKey + ", " +
                    "user_id INTEGER NOT NULL, " +
                    "title VARCHAR(100) NOT NULL, " +
                    "category_id INTEGER NOT NULL, " +
                    "amount DECIMAL(12,2) NOT NULL, " +
                    "frequency VARCHAR(30) DEFAULT 'MONTHLY', " +
                    "payment_mode VARCHAR(30) NOT NULL, " +
                    "next_due_date DATE NOT NULL, " +
                    "is_active BOOLEAN " + boolDefault + ", " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // 8. Dedicated UPI Transactions table (STRICT SEPARATION: separate from main expenses table)
            stmt.execute("CREATE TABLE IF NOT EXISTS upi_transactions (" +
                    "id " + primaryKey + ", " +
                    "user_id INTEGER NOT NULL, " +
                    "amount DECIMAL(12,2) NOT NULL, " +
                    "merchant VARCHAR(100) NOT NULL, " +
                    "category_name VARCHAR(50) NOT NULL, " +
                    "source_app VARCHAR(50) NOT NULL, " +
                    "upi_ref VARCHAR(100) NOT NULL, " +
                    "is_debit BOOLEAN " + boolDefault + ", " +
                    "transaction_date DATE NOT NULL, " +
                    "raw_message TEXT, " +
                    "imported_to_expensebook BOOLEAN " + boolZero + ", " +
                    "imported_expense_id INTEGER NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // 9. Password Resets table
            stmt.execute("CREATE TABLE IF NOT EXISTS password_resets (" +
                    "id " + primaryKey + ", " +
                    "email VARCHAR(150) NOT NULL, " +
                    "token_hash VARCHAR(255) NOT NULL, " +
                    "expires_at TIMESTAMP NOT NULL, " +
                    "used BOOLEAN " + boolZero + ", " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // 10. Savings Goals table (For Images 3, 4, 5)
            stmt.execute("CREATE TABLE IF NOT EXISTS savings_goals (" +
                    "id " + primaryKey + ", " +
                    "user_id INTEGER NOT NULL, " +
                    "title VARCHAR(100) NOT NULL, " +
                    "target_amount DECIMAL(12,2) NOT NULL, " +
                    "saved_amount DECIMAL(12,2) DEFAULT 0.00, " +
                    "emoji VARCHAR(20) DEFAULT '🎯', " +
                    "color_theme VARCHAR(30) DEFAULT 'green', " +
                    "target_date DATE NULL, " +
                    "status VARCHAR(30) DEFAULT 'ACTIVE', " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Seed default categories if none exist
            seedDefaultCategories(stmt);

            // Seed default users and sample goals
            seedDefaultUsers(stmt);

            // Auto-migrate data from local SQLite if connected to cloud PostgreSQL/MySQL
            DataMigrationUtil.checkAndMigrate(connection, isPostgres, isMySQL);

        } catch (SQLException e) {
            System.err.println("Error initializing schema: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void seedDefaultCategories(Statement stmt) {
        try {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM categories WHERE user_id IS NULL");
            if (rs.next() && rs.getInt(1) == 0) {
                String[][] defaultCats = {
                        {"Food", "FOOD", "#E88C8C"},
                        {"Groceries", "GROCERIES", "#78BFA0"},
                        {"Travel", "TRAVEL", "#A9CFE0"},
                        {"Shopping", "SHOPPING", "#F5C6A5"},
                        {"Education", "EDUCATION", "#C9B9E8"},
                        {"Health", "HEALTH", "#E88C8C"},
                        {"Entertainment", "ENTERTAINMENT", "#F5C6A5"},
                        {"Bills", "BILLS", "#71807A"},
                        {"Recharge", "RECHARGE", "#78BFA0"},
                        {"Rent", "RENT", "#A9CFE0"},
                        {"Fitness", "FITNESS", "#78BFA0"},
                        {"Personal Care", "PERSONAL_CARE", "#F5C6A5"},
                        {"Subscription", "SUBSCRIPTION", "#C9B9E8"},
                        {"Electronics", "ELECTRONICS", "#A9CFE0"},
                        {"Gifts", "GIFTS", "#F5C6A5"},
                        {"Savings", "SAVINGS", "#10B981"},
                        {"Others", "OTHERS", "#71807A"}
                };

                for (String[] cat : defaultCats) {
                    stmt.executeUpdate(String.format(
                            "INSERT INTO categories (user_id, name, icon_name, color, is_default) VALUES (NULL, '%s', '%s', '%s', TRUE)",
                            cat[0], cat[1], cat[2]
                    ));
                }
                System.out.println(" Seeded " + defaultCats.length + " default categories.");
            }
        } catch (SQLException e) {
            System.err.println("Notice while seeding categories: " + e.getMessage());
        }
    }

    private static void seedDefaultUsers(Statement stmt) {
        try {
            // 1. Seed admin@expensebook.com only if missing (never overwrite existing passwords)
            ResultSet rsAdmin = stmt.executeQuery("SELECT id FROM users WHERE LOWER(TRIM(email)) = 'admin@expensebook.com'");
            if (!rsAdmin.next()) {
                String adminHash = PasswordUtil.hashPassword("Admin@123");
                stmt.executeUpdate(String.format(
                        "INSERT INTO users (full_name, email, password_hash, role, financial_mode, currency, is_active) " +
                                "VALUES ('System Admin', 'admin@expensebook.com', '%s', 'ADMIN', 'TRACK_AND_BUDGET', '₹', TRUE)",
                        adminHash
                ));
                System.out.println(" Seeded default administrator account: admin@expensebook.com (Pass: Admin@123)");
            }

            // 2. Seed surajjaiswal566sj@gmail.com only if missing (never overwrite existing passwords)
            ResultSet rsSuraj = stmt.executeQuery("SELECT id FROM users WHERE LOWER(TRIM(email)) = 'surajjaiswal566sj@gmail.com'");
            int surajUserId;
            if (rsSuraj.next()) {
                surajUserId = rsSuraj.getInt("id");
            } else {
                String surajHash = PasswordUtil.hashPassword("suraj123");
                stmt.executeUpdate(String.format(
                        "INSERT INTO users (full_name, email, password_hash, role, financial_mode, currency, is_active) " +
                                "VALUES ('Suraj Jaiswal', 'surajjaiswal566sj@gmail.com', '%s', 'USER', 'TRACK_AND_BUDGET', '₹', TRUE)",
                        surajHash
                ));
                ResultSet rsNew = stmt.executeQuery("SELECT id FROM users WHERE LOWER(TRIM(email)) = 'surajjaiswal566sj@gmail.com'");
                surajUserId = rsNew.next() ? rsNew.getInt("id") : 1;
                System.out.println(" Seeded user account: surajjaiswal566sj@gmail.com (Pass: suraj123)");
            }

            // 3. Seed sky2602@gmail.com only if missing (never overwrite existing passwords)
            ResultSet rsSky = stmt.executeQuery("SELECT id FROM users WHERE LOWER(TRIM(email)) = 'sky2602@gmail.com'");
            if (!rsSky.next()) {
                String surajHash = PasswordUtil.hashPassword("suraj123");
                stmt.executeUpdate(String.format(
                        "INSERT INTO users (full_name, email, password_hash, role, financial_mode, currency, is_active) " +
                                "VALUES ('Suraj Jaiswal', 'sky2602@gmail.com', '%s', 'USER', 'TRACK_AND_BUDGET', '₹', TRUE)",
                        surajHash
                ));
                System.out.println(" Seeded user account: sky2602@gmail.com (Pass: suraj123)");
            }

            // 4. Clean up any previous sample savings goals (user requested removal of all default set goals)
            try {
                int cleanedGoals = stmt.executeUpdate("DELETE FROM savings_goals WHERE title IN ('Own House', 'Emergency fund', 'Dream Car')");
                if (cleanedGoals > 0) {
                    System.out.println(" Cleaned up " + cleanedGoals + " sample default savings goals from SQLite.");
                }
            } catch (Exception ignored) {}

            if (MongoConnectionManager.isMongoActive()) {
                try {
                    MongoConnectionManager.getCollection("savings_goals")
                            .deleteMany(new org.bson.Document("title", new org.bson.Document("$in", java.util.Arrays.asList("Own House", "Emergency fund", "Dream Car"))));
                    System.out.println(" Cleaned up sample default savings goals from MongoDB Atlas.");
                } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            System.err.println("Notice while seeding default users & goals: " + e.getMessage());
        }
    }
}
