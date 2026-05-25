package com.railwayreservation.controller;

import com.railwayreservation.model.Booking;
import com.railwayreservation.service.DataService;
import com.railwayreservation.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Handles the bookings view for displaying user's bookings.
 * Separated from main app for better maintainability.
 */
public class BookingsViewController {

    private final DataService dataService;
    private final String currentUser;
    private ListView<Booking> bookingsListView;
    private ObservableList<Booking> userBookings;
    private Label bookingStatsLabel;

    public BookingsViewController(DataService dataService, String currentUser) {
        this.dataService = dataService;
        this.currentUser = currentUser;
        this.userBookings = FXCollections.observableArrayList();
    }

    /**
     * Build the bookings view UI.
     */
    public Node buildBookingsView() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("bookings-panel");

        // Bookings toolbar
        HBox toolbar = buildBookingsToolbar();
        root.getChildren().add(toolbar);

        // Bookings list
        bookingsListView = new ListView<>(userBookings);
        bookingsListView.setPrefHeight(400);
        bookingsListView.getStyleClass().add("bookings-list");
        bookingsListView.setCellFactory(param -> new BookingListCell());
        root.getChildren().add(bookingsListView);
        VBox.setVgrow(bookingsListView, Priority.ALWAYS);

        // Stats footer
        bookingStatsLabel = new Label("Total bookings: 0");
        bookingStatsLabel.getStyleClass().add("booking-stats");
        root.getChildren().add(bookingStatsLabel);

        return root;
    }

    /**
     * Build bookings toolbar with actions.
     */
    private HBox buildBookingsToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8));
        toolbar.getStyleClass().add("bookings-toolbar");

        Button viewBtn = new Button("👁 View Details");
        viewBtn.getStyleClass().add("secondary-button");
        viewBtn.setOnAction(e -> viewBookingDetails());

        Button cancelBtn = new Button("✕ Cancel Booking");
        cancelBtn.getStyleClass().add("danger-button");
        cancelBtn.setOnAction(e -> cancelBooking());

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> refreshBookingsView());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusLabel = new Label("Filter by status:");
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("All", "Confirmed", "Cancelled", "Completed");
        statusCombo.setValue("All");
        statusCombo.setPrefWidth(120);

        toolbar.getChildren().addAll(viewBtn, cancelBtn, refreshBtn, spacer, statusLabel, statusCombo);

        return toolbar;
    }

    /**
     * View details of selected booking.
     */
    private void viewBookingDetails() {
        Booking selected = bookingsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UIUtils.showWarning("No Selection", "Please select a booking to view.");
            return;
        }

        Alert details = new Alert(Alert.AlertType.INFORMATION);
        details.setTitle("Booking Details");
        details.setHeaderText("PNR: " + selected.getPnr());
        
        StringBuilder content = new StringBuilder();
        content.append("Train: ").append(selected.getTrainNo()).append("\n");
        content.append("Journey Date: ").append(selected.getJourneyDate()).append("\n");
        content.append("Class: ").append(selected.getCls()).append("\n");
        content.append("Passengers: ").append(selected.getPassengers().size()).append("\n");
        content.append("Total Fare: ").append(UIUtils.formatRupees(selected.getTotalFare())).append("\n");
        content.append("Status: ").append(selected.getStatus()).append("\n");

        details.setContentText(content.toString());
        details.showAndWait();
    }

    /**
     * Cancel the selected booking.
     */
    private void cancelBooking() {
        Booking selected = bookingsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UIUtils.showWarning("No Selection", "Please select a booking to cancel.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Cancel Booking");
        confirm.setHeaderText("Confirm Cancellation");
        confirm.setContentText("Are you sure you want to cancel booking " + selected.getPnr() + "?");
        
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            UIUtils.showInfo("Cancelled", "Booking " + selected.getPnr() + " has been cancelled.");
            refreshBookingsView();
        }
    }

    /**
     * Refresh the bookings list.
     */
    public void refreshBookingsView() {
        List<Booking> bookings = dataService.getBookingsForUser(currentUser);
        userBookings.setAll(bookings);
        bookingStatsLabel.setText("Total bookings: " + bookings.size());
    }

    /**
     * Get the bookings list view for integration into main app.
     */
    public ListView<Booking> getBookingsListView() {
        return bookingsListView;
    }

    /**
     * Custom cell renderer for bookings.
     */
    private static class BookingListCell extends ListCell<Booking> {
        @Override
        protected void updateItem(Booking item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox cell = new VBox(4);
                cell.setPadding(new Insets(8));
                cell.getStyleClass().add("booking-cell");

                Label pnrLabel = new Label("PNR: " + item.getPnr());
                pnrLabel.getStyleClass().add("booking-pnr");

                Label trainLabel = new Label("Train " + item.getTrainNo() + " • " + item.getJourneyDate());
                trainLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #cbd5e1;");

                Label detailsLabel = new Label("Class: " + item.getCls() + " | Passengers: " + 
                    item.getPassengers().size() + " | " + UIUtils.formatRupees(item.getTotalFare()));
                detailsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");

                Label statusLabel = new Label("Status: " + item.getStatus());
                statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #10b981; -fx-font-weight: bold;");

                cell.getChildren().addAll(pnrLabel, trainLabel, detailsLabel, statusLabel);
                setGraphic(cell);
            }
        }
    }
}
