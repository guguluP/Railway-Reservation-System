package com.railwayreservation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railwayreservation.model.Booking;
import com.railwayreservation.model.Passenger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BookingRepository {

    private static final String DB_URL = "jdbc:h2:file:" + System.getProperty("user.home") + "/.railway-reservation/railway;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private final ObjectMapper mapper = new ObjectMapper();
    private Connection connection;

    public BookingRepository() {
        try {
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            initSchema();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bookings (
                    pnr VARCHAR(20) PRIMARY KEY,
                    user_name VARCHAR(100),
                    train_no VARCHAR(10),
                    train_name VARCHAR(100),
                    journey_date VARCHAR(20),
                    cls VARCHAR(10),
                    total_fare DOUBLE,
                    booked_at VARCHAR(30),
                    payment_method VARCHAR(30),
                    transaction_id VARCHAR(30),
                    payment_status VARCHAR(20),
                    passengers_json TEXT
                )
            """);
        }
    }

    public void save(Booking booking) {
        String passengersJson;
        try {
            passengersJson = mapper.writeValueAsString(booking.getPassengers());
        } catch (Exception e) {
            passengersJson = "[]";
        }

        String sql = """
            MERGE INTO bookings (pnr, user_name, train_no, train_name, journey_date, cls, total_fare,
                                 booked_at, payment_method, transaction_id, payment_status, passengers_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, booking.getPnr());
            ps.setString(2, booking.getUserName());
            ps.setString(3, booking.getTrainNo());
            ps.setString(4, booking.getTrainName());
            ps.setString(5, booking.getJourneyDate());
            ps.setString(6, booking.getCls());
            ps.setDouble(7, booking.getTotalFare());
            ps.setString(8, booking.getBookedAt());
            ps.setString(9, booking.getPaymentMethod());
            ps.setString(10, booking.getTransactionId());
            ps.setString(11, booking.getPaymentStatus());
            ps.setString(12, passengersJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save booking", e);
        }
    }

    public List<Booking> findAll() {
        List<Booking> bookings = new ArrayList<>();
        String sql = "SELECT * FROM bookings";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Booking b = new Booking();
                b.setPnr(rs.getString("pnr"));
                b.setUserName(rs.getString("user_name"));
                b.setTrainNo(rs.getString("train_no"));
                b.setTrainName(rs.getString("train_name"));
                b.setJourneyDate(rs.getString("journey_date"));
                b.setCls(rs.getString("cls"));
                b.setTotalFare(rs.getDouble("total_fare"));
                b.setBookedAt(rs.getString("booked_at"));
                b.setPaymentMethod(rs.getString("payment_method"));
                b.setTransactionId(rs.getString("transaction_id"));
                b.setPaymentStatus(rs.getString("payment_status"));

                String passengersJson = rs.getString("passengers_json");
                if (passengersJson != null && !passengersJson.isBlank()) {
                    try {
                        List<Passenger> passengers = mapper.readValue(passengersJson, new TypeReference<List<Passenger>>() {});
                        b.setPassengers(passengers);
                    } catch (Exception ignored) {
                        b.setPassengers(new ArrayList<>());
                    }
                } else {
                    b.setPassengers(new ArrayList<>());
                }
                bookings.add(b);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load bookings", e);
        }
        return bookings;
    }

    public void deleteByPnr(String pnr) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM bookings WHERE pnr = ?")) {
            ps.setString(1, pnr);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete booking", e);
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
