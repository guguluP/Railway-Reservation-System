package com.railwayreservation.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class User {
    private String username;
    private String role = "user";
    private boolean active = true;
    private String email;
    private String phone;
    private String createdAt;
    private String lastLoginAt;

    public User() {}

    public User(String username) {
        this.username = username;
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.createdAt = now;
        this.lastLoginAt = now;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(String lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // convenience helpers
    public boolean isAdmin() { return "admin".equals(role); }

    public LocalDateTime getCreatedAtParsed() {
        return createdAt != null ? LocalDateTime.parse(createdAt) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() { return Objects.hash(username); }

    @Override
    public String toString() { return username; }
}