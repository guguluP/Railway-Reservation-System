package com.railwayreservation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railwayreservation.model.Booking;
import com.railwayreservation.model.Passenger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BookingRepository {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final HikariDataSource dataSource;

    public BookingRepository() {
        this.dataSource = DatabaseConfig.createDataSource();
        initSchema();
    }

    public BookingRepository(HikariDataSource sharedDs) {
        this.dataSource = sharedDs;
        initSchema();
    }

    private void initSchema() throws RuntimeException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bookings (
                    pnr VARCHAR(40) PRIMARY KEY,
                    user_name VARCHAR(100),
                    train_no VARCHAR(20),
                    train_name VARCHAR(200),
                    journey_date VARCHAR(30),
                    cls VARCHAR(20),
                    total_fare DOUBLE,
                    booked_at VARCHAR(40),
                    payment_method VARCHAR(50),
                    transaction_id VARCHAR(60),
                    payment_status VARCHAR(40),
                    status VARCHAR(20) DEFAULT 'ACTIVE',
                    cancelled_at VARCHAR(40),
                    refund_amount DOUBLE DEFAULT 0,
                    passengers_json TEXT,
                    seat_numbers_json TEXT
                )
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize booking schema", e);
        }
    }

    public void save(Booking booking) {
        String passengersJson;
        String seatsJson;
        try {
            passengersJson = mapper.writeValueAsString(booking.getPassengers());
        } catch (Exception e) {
            passengersJson = "[]";
        }
        try {
            seatsJson = mapper.writeValueAsString(booking.getSeatNumbers());
        } catch (Exception e) {
            seatsJson = "[]";
        }

        // Portable upsert for both MySQL and H2
        String sql;
        if (DatabaseConfig.isMySqlConfigured()) {
            sql = """
                INSERT INTO bookings (pnr, user_name, train_no, train_name, journey_date, cls, total_fare,
                                      booked_at, payment_method, transaction_id, payment_status, status,
                                      cancelled_at, refund_amount, passengers_json, seat_numbers_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    user_name = VALUES(user_name),
                    train_no = VALUES(train_no),
                    train_name = VALUES(train_name),
                    journey_date = VALUES(journey_date),
                    cls = VALUES(cls),
                    total_fare = VALUES(total_fare),
                    booked_at = VALUES(booked_at),
                    payment_method = VALUES(payment_method),
                    transaction_id = VALUES(transaction_id),
                    payment_status = VALUES(payment_status),
                    status = VALUES(status),
                    cancelled_at = VALUES(cancelled_at),
                    refund_amount = VALUES(refund_amount),
                    passengers_json = VALUES(passengers_json),
                    seat_numbers_json = VALUES(seat_numbers_json)
                """;
        } else {
            // H2 MERGE syntax
            sql = """
                MERGE INTO bookings (pnr, user_name, train_no, train_name, journey_date, cls, total_fare,
                                     booked_at, payment_method, transaction_id, payment_status, status,
                                     cancelled_at, refund_amount, passengers_json, seat_numbers_json)
                KEY(pnr) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
            ps.setString(12, booking.getStatus());
            ps.setString(13, booking.getCancelledAt());
            ps.setDouble(14, booking.getRefundAmount());
            ps.setString(15, passengersJson);
            ps.setString(16, seatsJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save booking", e);
        }
    }

    public List<Booking> findAll() {
        List<Booking> bookings = new ArrayList<>();
        String sql = "SELECT * FROM bookings ORDER BY booked_at DESC";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
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
                b.setStatus(rs.getString("status"));
                b.setCancelledAt(rs.getString("cancelled_at"));
                b.setRefundAmount(rs.getDouble("refund_amount"));

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

                String seatsJson = rs.getString("seat_numbers_json");
                if (seatsJson != null && !seatsJson.isBlank()) {
                    try {
                        List<String> seats = mapper.readValue(seatsJson, new TypeReference<List<String>>() {});
                        b.setSeatNumbers(seats);
                    } catch (Exception ignored) {
                        b.setSeatNumbers(new ArrayList<>());
                    }
                } else {
                    b.setSeatNumbers(new ArrayList<>());
                }

                bookings.add(b);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load bookings", e);
        }
        return bookings;
    }

    public void deleteByPnr(String pnr) {
        String sql = "DELETE FROM bookings WHERE pnr = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pnr);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete booking", e);
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

