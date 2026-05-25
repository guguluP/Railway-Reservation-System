package com.railwayreservation.controller;

import com.railwayreservation.model.Train;
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
 * Handles admin panel UI for managing trains and system operations.
 * Separated from main app for better maintainability.
 */
public class AdminController {

    private final DataService dataService;
    private TableView<Train> adminTableView;
    private ObservableList<Train> adminTrains;
    private Label adminStatsLabel;

    public AdminController(DataService dataService) {
        this.dataService = dataService;
        this.adminTrains = FXCollections.observableArrayList();
    }

    /**
     * Build the admin panel UI.
     */
    public Node buildAdminPanel() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("admin-panel");

        // Admin toolbar
        HBox toolbar = buildAdminToolbar();
        root.getChildren().add(toolbar);

        // Admin table
        adminTableView = buildAdminTable();
        root.getChildren().add(adminTableView);
        VBox.setVgrow(adminTableView, Priority.ALWAYS);

        // Stats footer
        adminStatsLabel = new Label("Total trains: 0");
        adminStatsLabel.getStyleClass().add("admin-stats");
        root.getChildren().add(adminStatsLabel);

        return root;
    }

    /**
     * Build admin toolbar with actions.
     */
    private HBox buildAdminToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8));
        toolbar.getStyleClass().add("admin-toolbar");

        Button addBtn = new Button("+ Add Train");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> showAddTrainDialog());

        Button editBtn = new Button("✎ Edit Selected");
        editBtn.getStyleClass().add("secondary-button");
        editBtn.setOnAction(e -> editSelectedTrain());

        Button deleteBtn = new Button("🗑 Delete Selected");
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setOnAction(e -> deleteSelectedTrain());

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> refreshAdminTable());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label filterLabel = new Label("Filter:");
        TextField filterField = new TextField();
        filterField.setPromptText("Search trains...");
        filterField.setPrefWidth(150);

        toolbar.getChildren().addAll(addBtn, editBtn, deleteBtn, refreshBtn, spacer, filterLabel, filterField);

        return toolbar;
    }

    /**
     * Build admin table view.
     */
    private TableView<Train> buildAdminTable() {
        TableView<Train> table = new TableView<>(adminTrains);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getStyleClass().add("admin-table");

        // Train No column
        TableColumn<Train, String> noCol = new TableColumn<>("Train No");
        noCol.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getTrainNo()));
        noCol.setPrefWidth(80);

        // Name column
        TableColumn<Train, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getName()));
        nameCol.setPrefWidth(150);

        // Source column
        TableColumn<Train, String> sourceCol = new TableColumn<>("From");
        sourceCol.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getSource()));
        sourceCol.setPrefWidth(100);

        // Destination column
        TableColumn<Train, String> destCol = new TableColumn<>("To");
        destCol.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getDestination()));
        destCol.setPrefWidth(100);

        // Departure column
        TableColumn<Train, String> deptCol = new TableColumn<>("Depart");
        deptCol.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getDeparture()));
        deptCol.setPrefWidth(80);

        // Arrival column
        TableColumn<Train, String> arrCol = new TableColumn<>("Arrive");
        arrCol.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getArrival()));
        arrCol.setPrefWidth(80);

        // Base fare column
        TableColumn<Train, String> fareCol = new TableColumn<>("Base Fare");
        fareCol.setCellValueFactory(param -> 
            new javafx.beans.property.SimpleStringProperty(UIUtils.formatRupees(param.getValue().getBaseFare()))
        );
        fareCol.setPrefWidth(100);

        // Total seats column
        TableColumn<Train, String> seatsCol = new TableColumn<>("Available Seats");
        seatsCol.setCellValueFactory(param -> {
            int total = param.getValue().getAvailableSeats().values().stream().mapToInt(Integer::intValue).sum();
            return new javafx.beans.property.SimpleStringProperty(String.valueOf(total));
        });
        seatsCol.setPrefWidth(100);

        table.getColumns().addAll(noCol, nameCol, sourceCol, destCol, deptCol, arrCol, fareCol, seatsCol);

        return table;
    }

    /**
     * Show dialog to add a new train.
     */
    private void showAddTrainDialog() {
        UIUtils.showInfo("Add Train", "Add train functionality coming soon!");
    }

    /**
     * Edit the selected train.
     */
    private void editSelectedTrain() {
        Train selected = adminTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UIUtils.showWarning("No Selection", "Please select a train to edit.");
            return;
        }
        UIUtils.showInfo("Edit Train", "Edit functionality for " + selected.getTrainNo() + " coming soon!");
    }

    /**
     * Delete the selected train.
     */
    private void deleteSelectedTrain() {
        Train selected = adminTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UIUtils.showWarning("No Selection", "Please select a train to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete Train");
        confirm.setContentText("Are you sure you want to delete train " + selected.getTrainNo() + "?");
        
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            UIUtils.showInfo("Deleted", "Train " + selected.getTrainNo() + " deleted!");
            refreshAdminTable();
        }
    }

    /**
     * Refresh the admin table with latest data.
     */
    public void refreshAdminTable() {
        List<Train> trains = dataService.getAllTrains();
        adminTrains.setAll(trains);
        adminStatsLabel.setText("Total trains: " + trains.size());
    }

    /**
     * Get the admin table for integration into main app.
     */
    public TableView<Train> getAdminTable() {
        return adminTableView;
    }
}
