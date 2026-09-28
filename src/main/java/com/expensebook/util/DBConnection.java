package com.expensebook.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DBConnection {

    private static Connection connection;
    private static Properties config = new Properties();
    private static boolean isMySQL = true;
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
            if (clean.startsWith("mysql://")) {
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
                return connection;
            }

            String dbType = config.getProperty("db.type", "mysql").toLowerCase();
            boolean fallback = Boolean.parseBoolean(config.getProperty("db.fallback_to_sqlite", "true"));

            if ("mysql".equals(dbType)) {
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    String host = config.getProperty("db.host", "localhost");
                    String port = config.getProperty("db.port", "3306");
                    String database = config.getProperty("db.database", "expensebook");
                    String user = config.getProperty("db.user", "root");
                    String password = config.getProperty("db.password", "");
                    boolean ssl = Boolean.parseBoolean(config.getProperty("db.ssl", "false"));

                    String url = String.format(
                            "jdbc:mysql://%s:%s/%s?createDatabaseIfNotExist=true&useSSL=%s&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                            host, port, database, ssl ? "true" : "false");

                    connection = DriverManager.getConnection(url, user, password);
                    isMySQL = true;
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

    public static boolean isMySQL() {
        return isMySQL;
    }

    public static String getDbType() {
        return config.getProperty("db.type", "sqlite");
    }

    public static String getDatabaseStatusText() {
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
            String autoInc = isMySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT";
            String primaryKey = isMySQL ? "INT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";

            // 1. Users table
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id " + primaryKey + ", " +
                    "full_name VARCHAR(100) NOT NULL, " +
                    "email VARCHAR(150) NOT NULL UNIQUE, " +
                    "password_hash VARCHAR(255) NOT NULL, " +
                    "role VARCHAR(20) DEFAULT 'USER', " +
                    "financial_mode VARCHAR(30) DEFAULT 'TRACK_ONLY', " +
                    "currency VARCHAR(10) DEFAULT '₹', " +
                    "is_active BOOLEAN DEFAULT 1, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // 2. Categories table
            stmt.execute("CREATE TABLE IF NOT EXISTS categories (" +
                    "id " + primaryKey + ", " +
                    "user_id INTEGER NULL, " +
                    "name VARCHAR(50) NOT NULL, " +
                    "icon_name VARCHAR(50) NOT NULL, " +
                    "color VARCHAR(20) DEFAULT '#78BFA0', " +
                    "is_default BOOLEAN DEFAULT 1, " +
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
                    "is_active BOOLEAN DEFAULT 1, " +
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
                    "is_debit BOOLEAN DEFAULT 1, " +
                    "transaction_date DATE NOT NULL, " +
                    "raw_message TEXT, " +
                    "imported_to_expensebook BOOLEAN DEFAULT 0, " +
                    "imported_expense_id INTEGER NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Seed default categories if none exist
            seedDefaultCategories(stmt);

            // Seed default admin if none exist
            seedDefaultAdmin(stmt);

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
                            "INSERT INTO categories (user_id, name, icon_name, color, is_default) VALUES (NULL, '%s', '%s', '%s', 1)",
                            cat[0], cat[1], cat[2]
                    ));
                }
                System.out.println(" Seeded " + defaultCats.length + " default categories.");
            }
        } catch (SQLException e) {
            System.err.println("Notice while seeding categories: " + e.getMessage());
        }
    }

    private static void seedDefaultAdmin(Statement stmt) {
        try {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE email = 'admin@expensebook.com'");
            if (rs.next() && rs.getInt(1) == 0) {
                // admin password: Admin@123
                String adminHash = "$2a$10$7ZzWd6k5oK.vY0vQ8tL5w.rXb4A5uQ9/sYj9mC4.n5L8W3L.c9M9e"; // or generate dynamically
                // We'll insert with PasswordUtil hash
                String hash = PasswordUtil.hashPassword("Admin@123");
                stmt.executeUpdate(String.format(
                        "INSERT INTO users (full_name, email, password_hash, role, financial_mode, currency, is_active) " +
                                "VALUES ('System Admin', 'admin@expensebook.com', '%s', 'ADMIN', 'TRACK_AND_BUDGET', '₹', 1)",
                        hash
                ));
                System.out.println(" Seeded default administrator account: admin@expensebook.com (Pass: Admin@123)");
            }
        } catch (Exception e) {
            System.err.println("Notice while seeding admin: " + e.getMessage());
        }
    }
}
