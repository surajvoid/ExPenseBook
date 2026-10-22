package com.expensebook.migration;

/**
 * CLI Alias for DatabaseToMongoMigration.
 * Allows running `java com.expensebook.migration.SQLiteToMongoMigration` directly.
 */
public class SQLiteToMongoMigration {
    public static void main(String[] args) {
        DatabaseToMongoMigration.main(args);
    }
}
