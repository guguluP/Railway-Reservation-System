package com.railwayreservation.util;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;

/**
 * Shared UI utility methods for consistent UI patterns across the application.
 * This class centralizes common UI operations to reduce code duplication.
 */
public class UIUtils {

    private UIUtils() {
        // Utility class, no instantiation
    }

    /**
     * Show an alert dialog with the given type, title, and message.
     */
    public static void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show a simple information alert.
     */
    public static void showInfo(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    /**
     * Show a simple warning alert.
     */
    public static void showWarning(String title, String message) {
        showAlert(Alert.AlertType.WARNING, title, message);
    }

    /**
     * Show an error alert.
     */
    public static void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    /**
     * Add a tooltip to a node with the given text.
     */
    public static void addTooltip(Node node, String text) {
        if (node != null && text != null && !text.isEmpty()) {
            node.setOnMouseEntered(e -> {
                Tooltip tooltip = new Tooltip(text);
                Tooltip.install(node, tooltip);
            });
        }
    }

    /**
     * Load a CSS stylesheet for a stage.
     * Safely handles resource loading with fallback.
     */
    public static void loadStylesheet(javafx.scene.Scene scene, String resourcePath) {
        try {
            String resource = UIUtils.class.getResource(resourcePath).toExternalForm();
            scene.getStylesheets().add(resource);
        } catch (NullPointerException e) {
            System.err.println("Warning: Could not load stylesheet at: " + resourcePath);
            System.err.println("Using default styling.");
        }
    }

    /**
     * Apply a theme by setting/replacing stylesheets.
     * This allows runtime theme switching without scene reload.
     */
    public static void applyTheme(javafx.scene.Scene scene, String themeName) {
        // Clear existing custom stylesheets (keep base if needed)
        scene.getStylesheets().removeIf(url -> url.contains("theme-") || url.contains("dark") || url.contains("light"));
        
        // Add theme-specific stylesheet
        String themePath = "/styles/" + themeName + ".css";
        loadStylesheet(scene, themePath);
    }

    /**
     * Format currency in Indian Rupees.
     */
    public static String formatRupees(double amount) {
        return String.format("₹%,.2f", amount);
    }

    /**
     * Format train time (HH:mm format).
     */
    public static String formatTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return "N/A";
        }
        return timeStr;
    }

    /**
     * Get initials from a name for avatar display.
     */
    public static String getInitials(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                initials.append(part.charAt(0));
                if (initials.length() >= 2) break;
            }
        }
        return initials.toString().toUpperCase();
    }

    /**
     * Validate email format.
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    /**
     * Validate phone number (10 digits for India).
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        return phone.matches("\\d{10}");
    }
}
