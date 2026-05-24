package com.railwayreservation.service;

import com.railwayreservation.model.Booking;
import com.railwayreservation.model.Passenger;
import com.railwayreservation.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * UserRepository supports either MySQL (when env MYSQL_URL is present) or falls back to H2 for dev.
 * Stores users and bookings. Passengers are serialized into a simple pipe-separated format.
 */
public class UserRepository {

    private static final String MYSQL_URL = System.getenv("MYSQL_URL");
    private static final String MYSQL_USER = System.getenv("MYSQL_USER");
    private static final String MYSQL_PASSWORD = System.getenv("MYSQL_PASSWORD");

    private static final String H2_URL = "jdbc:h2:file:" + System.getProperty("user.home") + "/.railway-reservation/railway;AUTO_SERVER=TRUE";
    private static final String H2_USER = "sa";
    private static final String H2_PASSWORD = "";

    private Connection connection;

    public UserRepository() {
        try {
            if (MYSQL_URL != null && !MYSQL_URL.trim().isEmpty()) {
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
            } else {
                Class.forName("org.h2.Driver");
                connection = DriverManager.getConnection(H2_URL, H2_USER, H2_PASSWORD);
            }
            initSchema();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize user database", e);
        }
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    username VARCHAR(100) PRIMARY KEY,
                    password VARCHAR(200),
                    role VARCHAR(50) DEFAULT 'user',
                    created_at VARCHAR(30),
                    last_login_at VARCHAR(30)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bookings (
                    pnr VARCHAR(50) PRIMARY KEY,
                    username VARCHAR(100),
                    train_no VARCHAR(50),
                    train_name VARCHAR(200),
                    journey_date VARCHAR(30),
                    class VARCHAR(20),
                    passengers TEXT,
                    total_fare DOUBLE,
                    booked_at VARCHAR(30),
                    payment_method VARCHAR(50),
                    transaction_id VARCHAR(100),
                    payment_status VARCHAR(50),
                    FOREIGN KEY (username) REFERENCES users(username)
                )
            """);
        }

        // Ensure admin exists (demo). In MySQL MERGE isn't available, so use INSERT ... ON DUPLICATE KEY UPDATE
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        if (isUsingMySQL()) {
            String sql = "INSERT INTO users (username, password, role, created_at, last_login_at) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE password = password";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, "admin");
                ps.setString(2, "admin");
                ps.setString(3, "admin");
                ps.setString(4, now);
                ps.setString(5, now);
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = connection.prepareStatement(
                    "MERGE INTO users (username, password, role, created_at, last_login_at) KEY(username) VALUES (?, ?, ?, ?, ?)"
            )) {
                ps.setString(1, "admin");
                ps.setString(2, "admin");
                ps.setString(3, "admin");
                ps.setString(4, now);
                ps.setString(5, now);
                ps.executeUpdate();
            }
        }
    }

    private boolean isUsingMySQL() {
        return MYSQL_URL != null && !MYSQL_URL.trim().isEmpty();
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tableName.toUpperCase(Locale.ROOT));
            ps.setString(2, columnName.toUpperCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public void createUser(String username, String password, String role) {
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        if (isUsingMySQL()) {
            String sql = "INSERT INTO users (username, password, role, created_at, last_login_at) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE password = VALUES(password), role = VALUES(role), last_login_at = VALUES(last_login_at)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password);
                ps.setString(3, role != null ? role : "user");
                ps.setString(4, now);
                ps.setString(5, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create user", e);
            }
        } else {
            String sql = "MERGE INTO users (username, password, role, created_at, last_login_at) KEY(username) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password);
                ps.setString(3, role != null ? role : "user");
                ps.setString(4, now);
                ps.setString(5, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create user", e);
            }
        }
    }

    public boolean authenticate(String username, String password) {
        if (username == null || password == null) return false;
        String sql = "SELECT password FROM users WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String pw = rs.getString("password");
                    return pw != null && pw.equals(password);
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
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("role");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch user role", e);
        }
        return "user";
    }

    public List<Booking> getBookingsForUser(String username) {
        List<Booking> bookings = new ArrayList<>();
        if (username == null) return bookings;
        String sql = "SELECT * FROM bookings WHERE username = ? ORDER BY booked_at DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Booking b = new Booking();
                    b.setPnr(rs.getString("pnr"));
                    b.setUserName(rs.getString("username"));
                    b.setTrainNo(rs.getString("train_no"));
                    b.setTrainName(rs.getString("train_name"));
                    b.setJourneyDate(rs.getString("journey_date"));
                    b.setCls(rs.getString("class"));
                    b.setPassengers(deserializePassengers(rs.getString("passengers")));
                    b.setTotalFare(rs.getDouble("total_fare"));
                    b.setBookedAt(rs.getString("booked_at"));
                    b.setPaymentMethod(rs.getString("payment_method"));
                    b.setTransactionId(rs.getString("transaction_id"));
                    b.setPaymentStatus(rs.getString("payment_status"));
                    bookings.add(b);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load bookings", e);
        }
        return bookings;
    }

    public void saveBooking(Booking b) {
        if (b == null || b.getPnr() == null) return;
        String sql;
        if (isUsingMySQL()) {
            sql = "INSERT INTO bookings (pnr, username, train_no, train_name, journey_date, class, passengers, total_fare, booked_at, payment_method, transaction_id, payment_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE train_no=VALUES(train_no), train_name=VALUES(train_name), journey_date=VALUES(journey_date), class=VALUES(class), passengers=VALUES(passengers), total_fare=VALUES(total_fare), booked_at=VALUES(booked_at), payment_method=VALUES(payment_method), transaction_id=VALUES(transaction_id), payment_status=VALUES(payment_status)";
        } else {
            sql = "MERGE INTO bookings (pnr, username, train_no, train_name, journey_date, class, passengers, total_fare, booked_at, payment_method, transaction_id, payment_status) KEY(pnr) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, b.getPnr());
            ps.setString(2, b.getUserName());
            ps.setString(3, b.getTrainNo());
            ps.setString(4, b.getTrainName());
            ps.setString(5, b.getJourneyDate());
            ps.setString(6, b.getCls());
            ps.setString(7, serializePassengers(b.getPassengers()));
            ps.setDouble(8, b.getTotalFare());
            ps.setString(9, b.getBookedAt());
            ps.setString(10, b.getPaymentMethod());
            ps.setString(11, b.getTransactionId());
            ps.setString(12, b.getPaymentStatus());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save booking", e);
        }
    }

    private String serializePassengers(List<Passenger> passengers) {
        if (passengers == null || passengers.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Passenger p : passengers) {
            if (sb.length() > 0) sb.append(";;");
            sb.append(p.getName() == null ? "" : p.getName().replace(";;", ""))
              .append('|')
              .append(p.getAge() == null ? "" : p.getAge())
              .append('|')
              .append(p.getGender() == null ? "" : p.getGender())
              .append('|')
              .append(p.getBerthPref() == null ? "" : p.getBerthPref());
        }
        return sb.toString();
    }

    private List<Passenger> deserializePassengers(String data) {
        List<Passenger> list = new ArrayList<>();
        if (data == null || data.isEmpty()) return list;
        String[] parts = data.split(";;");
        for (String part : parts) {
            String[] f = part.split("\\|", -1);
            Passenger p = new Passenger();
            if (f.length > 0) p.setName(f[0]);
            if (f.length > 1) p.setAge(f[1]);
            if (f.length > 2) p.setGender(f[2]);
            if (f.length > 3) p.setBerthPref(f[3]);
            list.add(p);
        }
        return list;
    }

    public void updateLastLogin(String username) {
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String sql = "UPDATE users SET last_login_at = ? WHERE username = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
        try (Statement stmt = connection.createStatement();
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
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }
}