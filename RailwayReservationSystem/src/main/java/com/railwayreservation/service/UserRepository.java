package com.railwayreservation.service;

import com.railwayreservation.model.Booking;
import com.railwayreservation.model.User;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.mindrot.jbcrypt.BCrypt;

/**
 * UserRepository handles user accounts + BCrypt password storage for login authentication.
 * Uses HikariCP connection pool and does NOT manage bookings (BookingRepository is exclusive).
 */
public class UserRepository {

    private static final String DB_URL = "jdbc:h2:file:" + System.getProperty("user.home") + "/.railway-reservation/railway;AUTO_SERVER=FALSE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private final HikariDataSource dataSource;

    public UserRepository() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        this.dataSource = new HikariDataSource(config);
        initSchema();
    }

    public UserRepository(HikariDataSource sharedDs) {
        this.dataSource = sharedDs;
        initSchema();
    }

    private void initSchema() throws RuntimeException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    username VARCHAR(100) PRIMARY KEY,
                    password VARCHAR(200),
                    role VARCHAR(50) DEFAULT 'user',
                    created_at VARCHAR(30),
                    last_login_at VARCHAR(30)
                )
            """);

            // Only create admin if it doesn't exist (to avoid regenerating hash on every startup)
            if (!userExists("admin")) {
                String adminHash = BCrypt.hashpw("admin", BCrypt.gensalt(12));
                String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                String sql = "INSERT INTO users (username, password, role, created_at, last_login_at) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, "admin");
                    ps.setString(2, adminHash);
                    ps.setString(3, "admin");
                    ps.setString(4, now);
                    ps.setString(5, now);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize user schema", e);
        }
    }

    public void createUser(String username, String password, String role) {
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String hash = (password == null || password.isEmpty()) ? "" : BCrypt.hashpw(password, BCrypt.gensalt(12));
        
        String sql = "MERGE INTO users (username, password, role, created_at, last_login_at) KEY(username) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, role != null ? role : "user");
            ps.setString(4, now);
            ps.setString(5, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create user", e);
        }
    }

    public boolean authenticate(String username, String password) {
        if (username == null || password == null) return false;
        String sql = "SELECT password FROM users WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String stored = rs.getString("password");
                    if (stored == null) return false;
                    // BCrypt hash check (preferred)
                    if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
                        return BCrypt.checkpw(password, stored);
                    }
                    // Legacy plain-text support (will be replaced on next successful register/login)
                    return stored.equals(password);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to authenticate user", e);
        }
        return false;
    }

    public String getRole(String username) {
        if (username == null) return "user";
        String sql = "SELECT role FROM users WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("role");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch user role", e);
        }
        return "user";
    }

    public void updateLastLogin(String username) {
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String sql = "UPDATE users SET last_login_at = ? WHERE username = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, now);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update last login", e);
        }
    }

    public List<String> getAllUsernames() {
        List<String> names = new ArrayList<>();
        String sql = "SELECT username FROM users ORDER BY last_login_at DESC";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list users", e);
        }
        return names;
    }

    public boolean userExists(String username) {
        if (username == null) return false;
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check user", e);
        }
    }

    public void close() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        } catch (Exception ignored) {}
    }
}