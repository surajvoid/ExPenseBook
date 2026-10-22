package com.expensebook.util;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enterprise Production Connection Manager for MongoDB Atlas.
 * Provides thread-safe client lifecycle, Render-optimized connection pooling,
 * health checks, atomic sequences via 'counters', and automated schema indexing.
 */
public class MongoConnectionManager {

    private static final Logger LOGGER = Logger.getLogger(MongoConnectionManager.class.getName());

    private static volatile MongoClient mongoClient;
    private static volatile MongoDatabase database;
    private static volatile boolean initialized = false;
    private static final Object lock = new Object();

    private static String databaseName = "expensebook";

    private MongoConnectionManager() {}

    /**
     * Initializes the MongoDB connection using environment variables or explicit parameters.
     */
    public static void initialize() {
        if (initialized && mongoClient != null) {
            return;
        }

        synchronized (lock) {
            if (initialized && mongoClient != null) {
                return;
            }

            String uri = System.getenv("MONGODB_URI");
            if (uri == null || uri.trim().isEmpty()) {
                uri = System.getProperty("mongodb.uri");
            }

            String dbName = System.getenv("MONGODB_DATABASE");
            if (dbName == null || dbName.trim().isEmpty()) {
                dbName = System.getProperty("mongodb.database", "expensebook");
            }
            databaseName = dbName.trim();

            if (uri == null || uri.trim().isEmpty()) {
                LOGGER.info("No MONGODB_URI configured. MongoDB manager is uninitialized.");
                return;
            }

            initialize(uri.trim(), databaseName);
        }
    }

    /**
     * Initializes the MongoDB connection with an explicit URI and database name.
     */
    public static void initialize(String connectionUri, String dbName) {
        synchronized (lock) {
            close();
            databaseName = (dbName != null && !dbName.trim().isEmpty()) ? dbName.trim() : "expensebook";

            try {
                ConnectionString connectionString = new ConnectionString(connectionUri);

                MongoClientSettings settings = MongoClientSettings.builder()
                        .applyConnectionString(connectionString)
                        .applyToConnectionPoolSettings(builder -> builder
                                .minSize(2)
                                .maxSize(15) // Optimized for 512MB RAM Render Web Services
                                .maxWaitTime(5000, TimeUnit.MILLISECONDS)
                                .maxConnectionIdleTime(60000, TimeUnit.MILLISECONDS)
                        )
                        .applyToSocketSettings(builder -> builder
                                .connectTimeout(5000, TimeUnit.MILLISECONDS)
                                .readTimeout(10000, TimeUnit.MILLISECONDS)
                        )
                        .applyToClusterSettings(builder -> builder
                                .serverSelectionTimeout(5000, TimeUnit.MILLISECONDS)
                        )
                        .build();

                mongoClient = MongoClients.create(settings);
                database = mongoClient.getDatabase(databaseName);

                // Ping check
                database.runCommand(new Document("ping", 1));
                LOGGER.info(" Connected to MongoDB Atlas database [" + databaseName + "] successfully.");

                // Create idempotent indexes
                ensureIndexes();

                initialized = true;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to connect to MongoDB Atlas: " + e.getMessage(), e);
                close();
                throw new RuntimeException("MongoDB connection failure: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Creates all necessary indexes safely and idempotently.
     */
    public static void ensureIndexes() {
        if (database == null) return;

        try {
            // 1. users: unique email, currentSessionId
            MongoCollection<Document> usersCol = database.getCollection("users");
            usersCol.createIndex(Indexes.ascending("email"), new IndexOptions().unique(true));
            usersCol.createIndex(Indexes.ascending("currentSessionId"));

            // 2. expenses: userId + date descending, categoryId
            MongoCollection<Document> expensesCol = database.getCollection("expenses");
            expensesCol.createIndex(Indexes.compoundIndex(Indexes.ascending("userId"), Indexes.descending("expenseDate")));
            expensesCol.createIndex(Indexes.ascending("categoryId"));

            // 3. income: userId + date descending
            MongoCollection<Document> incomeCol = database.getCollection("income");
            incomeCol.createIndex(Indexes.compoundIndex(Indexes.ascending("userId"), Indexes.descending("incomeDate")));

            // 4. budgets: compound unique userId + year + month
            MongoCollection<Document> budgetsCol = database.getCollection("budgets");
            budgetsCol.createIndex(
                    Indexes.compoundIndex(Indexes.ascending("userId"), Indexes.ascending("year"), Indexes.ascending("month")),
                    new IndexOptions().unique(true)
            );

            // 5. categories: userId, isDefault
            MongoCollection<Document> catCol = database.getCollection("categories");
            catCol.createIndex(Indexes.ascending("userId"));
            catCol.createIndex(Indexes.ascending("isDefault"));

            // 6. savings_goals: userId
            MongoCollection<Document> goalsCol = database.getCollection("savings_goals");
            goalsCol.createIndex(Indexes.ascending("userId"));

            // 7. recurring_expenses: userId
            MongoCollection<Document> recurringCol = database.getCollection("recurring_expenses");
            recurringCol.createIndex(Indexes.ascending("userId"));

            // 8. upi_transactions: partial unique index on non-empty upiRef
            MongoCollection<Document> upiCol = database.getCollection("upi_transactions");
            upiCol.createIndex(Indexes.ascending("userId"));
            Document partialFilter = new Document("upiRef", new Document("$type", "string").append("$gt", ""));
            upiCol.createIndex(Indexes.ascending("upiRef"), new IndexOptions().unique(true).partialFilterExpression(partialFilter));

            // 9. audit_logs: timestamp descending, action
            MongoCollection<Document> auditCol = database.getCollection("audit_logs");
            auditCol.createIndex(Indexes.descending("timestamp"));
            auditCol.createIndex(Indexes.ascending("action"));

            LOGGER.info(" MongoDB Atlas collection indexes verified successfully.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Notice during index verification: " + e.getMessage());
        }
    }

    /**
     * Checks if MongoDB is currently configured and actively responding.
     */
    public static boolean isMongoActive() {
        if (!initialized || mongoClient == null || database == null) {
            // Attempt lazy initialization if env is present
            String uri = System.getenv("MONGODB_URI");
            if (uri != null && !uri.trim().isEmpty()) {
                try {
                    initialize();
                } catch (Exception ignored) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return ping();
    }

    /**
     * Issues a lightweight ping command to verify cluster health.
     */
    public static boolean ping() {
        try {
            if (database != null) {
                database.runCommand(new Document("ping", 1));
                return true;
            }
        } catch (Exception e) {
            LOGGER.warning("MongoDB ping failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Returns the active MongoDatabase instance.
     */
    public static MongoDatabase getDatabase() {
        if (!isMongoActive()) {
            initialize();
        }
        return database;
    }

    public static MongoCollection<Document> getCollection(String name) {
        MongoDatabase db = getDatabase();
        if (db == null) {
            throw new IllegalStateException("MongoDB database is not available.");
        }
        return db.getCollection(name);
    }

    /**
     * Atomically generates the next sequence integer for a given collection key.
     * Prevents race conditions and guarantees unique consecutive IDs.
     */
    public static int getNextSequence(String sequenceName) {
        MongoCollection<Document> counters = getCollection("counters");
        Document result = counters.findOneAndUpdate(
                Filters.eq("_id", sequenceName),
                Updates.inc("seq", 1),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
        );
        if (result != null && result.containsKey("seq")) {
            return result.getInteger("seq");
        }
        return 1;
    }

    /**
     * Sets or bumps sequence counter minimum value.
     * Used by migration to ensure future auto-increment IDs start above all migrated records.
     */
    public static void setSequenceMinimum(String sequenceName, int minVal) {
        MongoCollection<Document> counters = getCollection("counters");
        Document doc = counters.find(Filters.eq("_id", sequenceName)).first();
        if (doc == null) {
            counters.insertOne(new Document("_id", sequenceName).append("seq", minVal));
        } else {
            int current = doc.getInteger("seq", 0);
            if (minVal > current) {
                counters.updateOne(Filters.eq("_id", sequenceName), Updates.set("seq", minVal));
            }
        }
    }

    /**
     * Safely closes the MongoClient connection pool.
     */
    public static void close() {
        synchronized (lock) {
            if (mongoClient != null) {
                try {
                    mongoClient.close();
                    LOGGER.info("MongoDB connection pool closed.");
                } catch (Exception ignored) {}
                mongoClient = null;
                database = null;
                initialized = false;
            }
        }
    }
}
