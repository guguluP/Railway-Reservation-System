package com.railwayreservation.controller;

import com.railwayreservation.model.Booking;
import com.railwayreservation.model.Passenger;
import com.railwayreservation.model.Train;
import com.railwayreservation.service.DataService;
import com.railwayreservation.util.UIUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.*;

/**
 * Handles booking dialog and seat selection.
 * Separated from main app for better maintainability and reusability.
 */
public class BookingController {

    private final DataService dataService;
    private final Stage ownerStage;
    private static final List<String> CLASS_OPTIONS = List.of("SL", "3A", "2A", "1A", "2S", "CC", "EC", "P");

    public BookingController(DataService dataService, Stage ownerStage) {
        this.dataService = dataService;
        this.ownerStage = ownerStage;
    }

    /**
     * Open a booking dialog for a selected train.
     * Returns the booking if successful, null otherwise.
     */
    public Booking showBookingDialog(Train train, String preselectClass) {
        LocalDate journeyDate = LocalDate.now();

        // Validate date is not in the past
        if (journeyDate.isBefore(LocalDate.now())) {
            UIUtils.showWarning("Invalid Date", "Cannot book for a past date.");
            return null;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(ownerStage);
        dialog.setTitle("Book Ticket • " + train.getTrainNo());

        VBox root = new VBox(10);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("booking-dialog");

        // Train summary
        HBox summary = buildTrainSummary(train);
        root.getChildren().add(summary);

        // Class selection
        HBox classRow = buildClassSelection(train, preselectClass);
        root.getChildren().add(classRow);

        // Coach selection
        HBox coachRow = buildCoachSelection();
        root.getChildren().add(coachRow);

        // Passenger count
        HBox numRow = buildPassengerCount();
        ComboBox<Integer> numBox = (ComboBox<Integer>) numRow.getChildren().stream()
            .filter(n -> n instanceof ComboBox).findFirst().orElse(null);
        root.getChildren().add(numRow);

        // Seat selection
        Label seatTitle = new Label("Select Seats");
        seatTitle.getStyleClass().add("section-title-small");
        GridPane seatGrid = new GridPane();
        seatGrid.setHgap(3);
        seatGrid.setVgap(3);
        seatGrid.getStyleClass().add("seat-grid");

        root.getChildren().add(seatTitle);
        root.getChildren().add(seatGrid);

        // Fare display
        Label fareLabel = new Label("Total Fare: ₹0");
        fareLabel.getStyleClass().add("fare-label");
        root.getChildren().add(fareLabel);

        // Action buttons
        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        Button bookBtn = new Button("✓ Book Now");
        bookBtn.getStyleClass().add("primary-button");
        bookBtn.setOnAction(e -> {
            // TODO: Process booking
            UIUtils.showInfo("Booking", "Booking confirmed!");
            dialog.close();
        });

        Button cancelBtn = new Button("✗ Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> dialog.close());

        actionBox.getChildren().addAll(bookBtn, cancelBtn);
        root.getChildren().add(actionBox);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        Scene dialogScene = new Scene(scroll, 700, 600);
        dialog.setScene(dialogScene);
        dialog.showAndWait();

        return null; // TODO: Return actual booking
    }

    /**
     * Build train summary section.
     */
    private HBox buildTrainSummary(Train train) {
        HBox summary = new HBox(10);
        summary.getStyleClass().add("train-summary");

        Label sum = new Label(train.getTrainNo() + " • " + train.getName() + "\n" +
                train.getSource() + " → " + train.getDestination() + "  |  " +
                train.getDeparture() + " - " + train.getArrival());
        sum.setStyle("-fx-wrap-text: true;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button routeBtn = new Button("🚉 View All Stops");
        routeBtn.getStyleClass().add("secondary-button");
        routeBtn.setOnAction(e -> showTrainRoute(train));

        summary.getChildren().addAll(sum, spacer, routeBtn);
        return summary;
    }

    /**
     * Build class selection section.
     */
    private HBox buildClassSelection(Train train, String preselectClass) {
        HBox classRow = new HBox(8);
        classRow.setAlignment(Pos.CENTER_LEFT);

        Label clsLbl = new Label("Class:");
        ToggleGroup classGroup = new ToggleGroup();
        List<ToggleButton> classBtns = new ArrayList<>();

        List<String> trainClassOptions = new ArrayList<>();
        if (train.getAvailableSeats() != null && !train.getAvailableSeats().isEmpty()) {
            for (String k : train.getAvailableSeats().keySet()) {
                if (CLASS_OPTIONS.contains(k)) {
                    Integer cnt = train.getAvailableSeats().get(k);
                    if (cnt != null && cnt > 0) {
                        trainClassOptions.add(k);
                    }
                }
            }
        }
        if (trainClassOptions.isEmpty()) {
            trainClassOptions.add(CLASS_OPTIONS.get(0));
        }

        for (String c : trainClassOptions) {
            ToggleButton tb = new ToggleButton(c);
            tb.setToggleGroup(classGroup);
            tb.getStyleClass().add("class-toggle");
            classBtns.add(tb);
        }

        boolean foundPreselect = false;
        if (preselectClass != null) {
            for (ToggleButton tb : classBtns) {
                if (tb.getText().equals(preselectClass)) {
                    tb.setSelected(true);
                    foundPreselect = true;
                    break;
                }
            }
        }
        if (!foundPreselect && !classBtns.isEmpty()) {
            classBtns.get(0).setSelected(true);
        }

        classRow.getChildren().add(clsLbl);
        classRow.getChildren().addAll(classBtns);

        return classRow;
    }

    /**
     * Build coach selection section.
     */
    private HBox buildCoachSelection() {
        HBox coachRow = new HBox(8);
        coachRow.setAlignment(Pos.CENTER_LEFT);

        Label coachLbl = new Label("Coach:");
        ComboBox<String> coachCombo = new ComboBox<>();
        coachCombo.getItems().addAll("A1", "A2", "B1", "B2", "S1", "S2");
        coachCombo.setValue("A1");
        coachCombo.setPrefWidth(80);

        coachRow.getChildren().addAll(coachLbl, coachCombo);
        return coachRow;
    }

    /**
     * Build passenger count section.
     */
    private HBox buildPassengerCount() {
        HBox numRow = new HBox(8);
        numRow.setAlignment(Pos.CENTER_LEFT);

        Label numLbl = new Label("Passengers:");
        ComboBox<Integer> numBox = new ComboBox<>();
        numBox.getItems().addAll(1, 2, 3, 4, 5, 6);
        numBox.setValue(1);

        numRow.getChildren().addAll(numLbl, numBox);
        return numRow;
    }

    /**
     * Show train route in a dialog.
     */
    private void showTrainRoute(Train train) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Train Route • " + train.getTrainNo());
        alert.setHeaderText(train.getName());
        
        StringBuilder route = new StringBuilder();
        route.append("Route: ").append(train.getSource()).append(" → ").append(train.getDestination()).append("\n");
        route.append("Departure: ").append(train.getDeparture()).append("\n");
        route.append("Arrival: ").append(train.getArrival()).append("\n");
        route.append("\nScheduled Stops:\n");
        route.append("(Full route schedule not yet implemented)");
        
        alert.setContentText(route.toString());
        alert.showAndWait();
    }
}
