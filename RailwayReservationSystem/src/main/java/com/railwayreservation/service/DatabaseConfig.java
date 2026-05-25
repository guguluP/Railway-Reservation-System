package com.railwayreservation.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Central place to create the database connection pool.
 * Supports MySQL (when MYSQL_URL env var is set) or falls back to embedded H2.
 *
 * Usage:
 *   export MYSQL_URL="jdbc:mysql://127.0.0.1:3306/RailwayReservation?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
 *   export MYSQL_USER="root"
 *   export MYSQL_PASSWORD="lunapnb1"
 */
public class DatabaseConfig {

    private static final String MYSQL_URL_ENV = "MYSQL_URL";
    private static final String MYSQL_USER_ENV = "MYSQL_USER";
    private static final String MYSQL_PASSWORD_ENV = "MYSQL_PASSWORD";

    private static boolean usingMySQL = false;

    /**
     * Returns true if the application is using a MySQL database (either via env or the
     * temporary hardcoded credentials). Used by repositories to choose the correct
     * upsert syntax (ON DUPLICATE KEY for MySQL, MERGE for H2).
     */
    public static boolean isMySqlConfigured() {
        return usingMySQL;
    }

    public static HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();

        // =====================================================
        // TEMPORARY HARDCODED MySQL (for testing your exact credentials)
        // Using the values you just provided
        // =====================================================
        boolean useHardcoded = true;   // ← Change to false later when env vars work reliably

        if (useHardcoded) {
            String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/RailwayReservation?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            config.setJdbcUrl(jdbcUrl);
            config.setUsername("root");
            config.setPassword("lunapnb1.");   // Note the dot at the end

            usingMySQL = true;
            System.out.println("[DatabaseConfig] Using HARDCODED MySQL (root + lunapnb1.)");

            // Recommended MySQL + Hikari settings
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");

        } else if (System.getenv(MYSQL_URL_ENV) != null && !System.getenv(MYSQL_URL_ENV).isBlank()) {
            // Normal env var path (keep for later)
            usingMySQL = true;
            config.setJdbcUrl(System.getenv(MYSQL_URL_ENV));
            config.setUsername(System.getenv(MYSQL_USER_ENV));
            config.setPassword(System.getenv(MYSQL_PASSWORD_ENV));

            System.out.println("[DatabaseConfig] Using MySQL database: " + maskJdbcUrl(System.getenv(MYSQL_URL_ENV)));
        } else {
            // Default: Embedded H2
            usingMySQL = false;
            String h2Url = "jdbc:h2:file:" + System.getProperty("user.home") +
                    "/.railway-reservation/railway;AUTO_SERVER=FALSE";
            config.setJdbcUrl(h2Url);
            config.setUsername("sa");
            config.setPassword("");
            System.out.println("[DatabaseConfig] Using embedded H2 database (no MYSQL_URL set)");
        }

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        return new HikariDataSource(config);
    }

    private static String maskJdbcUrl(String url) {
        if (url == null) return "null";
        return url.replaceAll("(?<=//)[^@]+@", "***:***@");
    }
}
