package com.railwayreservation.controller;

import com.railwayreservation.model.Train;
import com.railwayreservation.service.DataService;
import com.railwayreservation.util.UIUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles the search functionality and search form UI.
 * Separated from main app for better maintainability.
 */
public class SearchController {

    private final DataService dataService;
    private ComboBox<String> fromBox;
    private ComboBox<String> toBox;
    private DatePicker datePicker;
    private DatePicker returnDatePicker;
    private CheckBox addReturnCheck;
    private ComboBox<String> classCombo;
    private ComboBox<String> quotaCombo;
    private CheckBox flexibleDatesCheck;
    private CheckBox availableSeatsOnlyCheck;
    private ComboBox<String> sortCombo;
    private Label searchLoadingLabel;
    private Label resultsCountLabel;
    private TableView<Train> resultsTableView;

    private ObservableList<Train> trainResults;
    private Runnable onSearchComplete;

    public SearchController(DataService dataService) {
        this.dataService = dataService;
        this.trainResults = FXCollections.observableArrayList();
    }

    /**
     * Build the search form UI.
     */
    public Node buildSearchForm() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("search-form");

        // Trip type: One way / Round trip
        HBox tripTypeBox = new HBox(10);
        tripTypeBox.setAlignment(Pos.CENTER_LEFT);
        Label tripLabel = new Label("Trip Type:");
        RadioButton oneWayRadio = new RadioButton("One Way");
        RadioButton roundTripRadio = new RadioButton("Round Trip");
        ToggleGroup tripGroup = new ToggleGroup();
        oneWayRadio.setToggleGroup(tripGroup);
        roundTripRadio.setToggleGroup(tripGroup);
        oneWayRadio.setSelected(true);

        tripTypeBox.getChildren().addAll(tripLabel, oneWayRadio, roundTripRadio);

        // Stations
        HBox stationsBox = new HBox(8);
        stationsBox.setAlignment(Pos.CENTER_LEFT);

        Label fromLabel = new Label("From:");
        fromBox = new ComboBox<>();
        fromBox.setPrefWidth(150);
        fromBox.setEditable(true);

        Label toLabel = new Label("To:");
        toBox = new ComboBox<>();
        toBox.setPrefWidth(150);
        toBox.setEditable(true);

        Button swapBtn = new Button("⇄");
        swapBtn.getStyleClass().add("secondary-button");
        swapBtn.setOnAction(e -> {
            String temp = fromBox.getValue();
            fromBox.setValue(toBox.getValue());
            toBox.setValue(temp);
        });

        stationsBox.getChildren().addAll(fromLabel, fromBox, toLabel, toBox, swapBtn);

        // Dates
        HBox datesBox = new HBox(8);
        datesBox.setAlignment(Pos.CENTER_LEFT);

        Label departLabel = new Label("Depart:");
        datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(120);

        Label returnLabel = new Label("Return:");
        returnDatePicker = new DatePicker(LocalDate.now().plusDays(1));
        returnDatePicker.setPrefWidth(120);
        returnDatePicker.setDisable(true);

        addReturnCheck = new CheckBox("Add Return");
        addReturnCheck.setOnAction(e -> returnDatePicker.setDisable(!addReturnCheck.isSelected()));

        datesBox.getChildren().addAll(departLabel, datePicker, returnLabel, returnDatePicker, addReturnCheck);

        // Preferences
        HBox prefsBox = new HBox(8);
        prefsBox.setAlignment(Pos.CENTER_LEFT);

        Label classLabel = new Label("Class:");
        classCombo = new ComboBox<>();
        classCombo.getItems().addAll("All Classes", "SL", "3A", "2A", "1A", "2S", "CC", "EC");
        classCombo.setValue("All Classes");
        classCombo.setPrefWidth(100);

        Label quotaLabel = new Label("Quota:");
        quotaCombo = new ComboBox<>();
        quotaCombo.getItems().addAll("General", "Ladies", "Senior/Divyangjan", "Premium");
        quotaCombo.setValue("General");
        quotaCombo.setPrefWidth(100);

        flexibleDatesCheck = new CheckBox("Flexible Dates (±3 days)");
        availableSeatsOnlyCheck = new CheckBox("Available Seats Only");
        availableSeatsOnlyCheck.setSelected(true);

        prefsBox.getChildren().addAll(classLabel, classCombo, quotaLabel, quotaCombo, 
                                      flexibleDatesCheck, availableSeatsOnlyCheck);

        // Search button
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);

        Button searchBtn = new Button("🔍 Search Trains");
        searchBtn.getStyleClass().add("primary-button");
        searchBtn.setPrefWidth(150);
        searchBtn.setOnAction(e -> performSearch());

        searchLoadingLabel = new Label("");
        searchLoadingLabel.getStyleClass().add("loading-label");

        searchBox.getChildren().addAll(searchBtn, searchLoadingLabel);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        searchBox.getChildren().add(spacer);

        root.getChildren().addAll(tripTypeBox, stationsBox, datesBox, prefsBox, searchBox);
        return root;
    }

    /**
     * Build the results table view.
     */
    public Node buildResultsView() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(12));

        // Results info bar
        HBox infoBar = new HBox(10);
        infoBar.setAlignment(Pos.CENTER_LEFT);

        resultsCountLabel = new Label("Results: 0 trains");
        resultsCountLabel.getStyleClass().add("results-count");

        Label sortLabel = new Label("Sort by:");
        sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll("Departure Time", "Arrival Time", "Duration", "Fare (Low to High)");
        sortCombo.setValue("Departure Time");
        sortCombo.setOnAction(e -> applySort());

        infoBar.getChildren().addAll(resultsCountLabel, new Separator(Orientation.VERTICAL), sortLabel, sortCombo);

        // Results table
        resultsTableView = new TableView<>(trainResults);
        resultsTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        resultsTableView.setPrefHeight(400);
        resultsTableView.getStyleClass().add("results-table");

        // Columns
        TableColumn<Train, String> noCol = new TableColumn<>("Train No");
        noCol.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getTrainNo()));
        noCol.setPrefWidth(80);

        TableColumn<Train, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getName()));
        nameCol.setPrefWidth(150);

        TableColumn<Train, String> routeCol = new TableColumn<>("Route");
        routeCol.setCellValueFactory(param -> 
            new javafx.beans.property.SimpleStringProperty(param.getValue().getSource() + " → " + param.getValue().getDestination())
        );
        routeCol.setPrefWidth(150);

        TableColumn<Train, String> deptCol = new TableColumn<>("Depart");
        deptCol.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getDeparture()));
        deptCol.setPrefWidth(80);

        TableColumn<Train, String> arrCol = new TableColumn<>("Arrive");
        arrCol.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getArrival()));
        arrCol.setPrefWidth(80);

        TableColumn<Train, String> durationCol = new TableColumn<>("Duration");
        durationCol.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(calculateDuration(param.getValue())));
        durationCol.setPrefWidth(80);

        TableColumn<Train, String> availCol = new TableColumn<>("Availability");
        availCol.setCellValueFactory(param -> {
            int totalSeats = param.getValue().getAvailableSeats().values().stream().mapToInt(Integer::intValue).sum();
            return new javafx.beans.property.SimpleStringProperty(totalSeats + " seats");
        });
        availCol.setPrefWidth(100);

        resultsTableView.getColumns().addAll(noCol, nameCol, routeCol, deptCol, arrCol, durationCol, availCol);

        root.getChildren().addAll(infoBar, resultsTableView);
        VBox.setVgrow(resultsTableView, Priority.ALWAYS);

        return root;
    }

    /**
     * Perform the train search asynchronously.
     */
    private void performSearch() {
        String from = fromBox.getValue();
        String to = toBox.getValue();
        LocalDate date = datePicker.getValue();

        if (from == null || from.isEmpty() || to == null || to.isEmpty() || date == null) {
            UIUtils.showWarning("Incomplete Search", "Please fill in all fields.");
            return;
        }

        if (from.equals(to)) {
            UIUtils.showWarning("Invalid Route", "From and To stations cannot be the same.");
            return;
        }

        showLoadingState(true);

        Task<List<Train>> searchTask = new Task<List<Train>>() {
            @Override
            protected List<Train> call() throws Exception {
                return dataService.searchTrains(from, to, date);
            }
        };

        searchTask.setOnSucceeded(e -> {
            List<Train> results = searchTask.getValue();
            trainResults.setAll(results);
            resultsCountLabel.setText("Results: " + results.size() + " trains");
            showLoadingState(false);
            if (onSearchComplete != null) {
                onSearchComplete.run();
            }
        });

        searchTask.setOnFailed(e -> {
            UIUtils.showError("Search Error", "Failed to search trains: " + searchTask.getException().getMessage());
            showLoadingState(false);
        });

        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }

    /**
     * Apply sorting to results.
     */
    private void applySort() {
        String sortBy = sortCombo.getValue();
        List<Train> trains = new ArrayList<>(trainResults);

        switch (sortBy) {
            case "Departure Time":
                trains.sort((a, b) -> a.getDeparture().compareTo(b.getDeparture()));
                break;
            case "Arrival Time":
                trains.sort((a, b) -> a.getArrival().compareTo(b.getArrival()));
                break;
            case "Duration":
                trains.sort((a, b) -> {
                    int durA = calculateDurationMinutes(a);
                    int durB = calculateDurationMinutes(b);
                    return Integer.compare(durA, durB);
                });
                break;
            case "Fare (Low to High)":
                trains.sort((a, b) -> Double.compare(a.getBaseFare(), b.getBaseFare()));
                break;
        }

        trainResults.setAll(trains);
    }

    /**
     * Refresh station list.
     */
    public void refreshStations() {
        List<String> stations = dataService.getAllStations();
        ObservableList<String> stationList = FXCollections.observableArrayList(stations);
        fromBox.setItems(stationList);
        toBox.setItems(stationList);
    }

    /**
     * Set callback when search completes.
     */
    public void setOnSearchComplete(Runnable callback) {
        this.onSearchComplete = callback;
    }

    /**
     * Get the results table view for integration into main app.
     */
    public TableView<Train> getResultsTable() {
        return resultsTableView;
    }

    /**
     * Get the currently selected train, if any.
     */
    public Train getSelectedTrain() {
        return resultsTableView.getSelectionModel().getSelectedItem();
    }

    private void showLoadingState(boolean loading) {
        Platform.runLater(() -> {
            if (loading) {
                searchLoadingLabel.setText("🔄 Searching...");
                searchLoadingLabel.getStyleClass().add("loading-active");
            } else {
                searchLoadingLabel.setText("");
                searchLoadingLabel.getStyleClass().remove("loading-active");
            }
        });
    }

    private String calculateDuration(Train train) {
        try {
            String[] deptParts = train.getDeparture().split(":");
            String[] arrParts = train.getArrival().split(":");
            int deptMins = Integer.parseInt(deptParts[0]) * 60 + Integer.parseInt(deptParts[1]);
            int arrMins = Integer.parseInt(arrParts[0]) * 60 + Integer.parseInt(arrParts[1]);
            int duration = arrMins - deptMins;
            if (duration < 0) duration += 24 * 60;
            int hours = duration / 60;
            int mins = duration % 60;
            return hours + "h " + mins + "m";
        } catch (Exception e) {
            return "N/A";
        }
    }

    private int calculateDurationMinutes(Train train) {
        try {
            String[] deptParts = train.getDeparture().split(":");
            String[] arrParts = train.getArrival().split(":");
            int deptMins = Integer.parseInt(deptParts[0]) * 60 + Integer.parseInt(deptParts[1]);
            int arrMins = Integer.parseInt(arrParts[0]) * 60 + Integer.parseInt(arrParts[1]);
            int duration = arrMins - deptMins;
            if (duration < 0) duration += 24 * 60;
            return duration;
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }
}
