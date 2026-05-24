package com.railwayreservation;

import com.railwayreservation.model.Booking;
import com.railwayreservation.model.Passenger;
import com.railwayreservation.model.Train;
import com.railwayreservation.model.ScheduleStop;
import com.railwayreservation.service.DataService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RailwayApp extends Application {

    private final DataService dataService = new DataService();
    private final com.railwayreservation.service.UserRepository userRepository = new com.railwayreservation.service.UserRepository();
    private String currentUser = "Guest";
    private String currentUserRole = "user";

    private ComboBox<String> fromBox;
    private ComboBox<String> toBox;
    private DatePicker datePicker;
    private DatePicker returnDatePicker;
    private CheckBox addReturnCheck;
    private ComboBox<String> classCombo;
    private ComboBox<String> quotaCombo;
    private CheckBox flexibleDatesCheck;
    private CheckBox availableSeatsOnlyCheck;
    private ListView<Train> resultsListView;
    private ListView<Booking> bookingsListView;
    private Label statusLabel;
    private TabPane tabPane;
    private Label userLabel;
    private Stage primaryStage;
    private MenuButton profileMenu;

    private StackPane mainContentArea;
    private Node searchView;
    private Node bookingsView;
    private Node adminView;
    private Label resultsCountLabel;
    private Label searchLoadingLabel;
    private ComboBox<String> sortCombo;
    private boolean isDarkTheme = true;
    private BorderPane mainRoot;

    private static final List<String> CLASS_OPTIONS = List.of("SL", "3A", "2A", "1A", "2S", "CC", "EC", "P");

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        dataService.load();
        dataService.registerOrUpdateUser(currentUser);

        mainRoot = new BorderPane();
        mainRoot.setTop(buildIRCTCHeader());
        mainRoot.setCenter(buildMainContent());
        mainRoot.setBottom(buildStatusBar());

        Scene scene = new Scene(mainRoot, 1100, 740);
        var cssUrl = getClass().getResource("/styles/railway-reservation.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.out.println("⚠️ CSS not found at /styles/railway-reservation.css — UI will be unstyled");
        }

        primaryStage.setTitle("IRCTC Rail Connect • Book Train Tickets");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> dataService.saveAll());
        primaryStage.show();

        // Initial load
        refreshStations();
        updateStatus();
    }

    private Node buildIRCTCHeader() {
        HBox header = new HBox(10);
        header.setPadding(new Insets(8, 20, 8, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("irctc-header");
        Label logo = new Label("IRCTC");
        logo.getStyleClass().add("irctc-logo");
        Label tag = new Label("Rail Connect");
        tag.getStyleClass().add("irctc-tag");
        HBox leftBox = new HBox(logo, tag);
        leftBox.setAlignment(Pos.CENTER_LEFT);
        HBox nav = new HBox(2);
        nav.setAlignment(Pos.CENTER);
        Label trainsNav = new Label("Trains");
        trainsNav.getStyleClass().addAll("nav-link", "active");
        Label flightsNav = new Label("Flights");
        flightsNav.getStyleClass().add("nav-link");
        flightsNav.setOnMouseClicked(e -> showAlert(Alert.AlertType.INFORMATION, "Info", "Flights demo not implemented in this prototype"));
        Label hotelsNav = new Label("Hotels");
        hotelsNav.getStyleClass().add("nav-link");
        hotelsNav.setOnMouseClicked(e -> showAlert(Alert.AlertType.INFORMATION, "Info", "Hotels demo not implemented in this prototype"));
        Label pnrNav = new Label("PNR Status");
        pnrNav.getStyleClass().add("nav-link");
        pnrNav.setOnMouseClicked(e -> showAlert(Alert.AlertType.INFORMATION, "Info", "PNR demo not implemented in this prototype"));
        nav.getChildren().addAll(trainsNav, flightsNav, hotelsNav, pnrNav);
        Region centerGrow = new Region();
        HBox.setHgrow(centerGrow, Priority.ALWAYS);
        Label lang = new Label("EN | HI");
        lang.getStyleClass().add("lang-label");
        profileMenu = new MenuButton("👤 " + currentUser);
        profileMenu.getStyleClass().add("profile-menu");
        MenuItem changeUserItem = new MenuItem("Login");
        changeUserItem.setOnAction(e -> showUserSelectionDialog());
        MenuItem myBookingsItem = new MenuItem("My Bookings");
        myBookingsItem.setOnAction(e -> { if (tabPane != null) tabPane.getSelectionModel().select(1); });
        MenuItem adminItem = new MenuItem("Manage Trains");
        adminItem.setOnAction(e -> { if (tabPane != null) tabPane.getSelectionModel().select(2); });
        MenuItem refreshItem = new MenuItem("⟳ Refresh Data");
        refreshItem.setOnAction(e -> {
            dataService.load();
            refreshStations();
            updateStatus();
            if (resultsListView != null) resultsListView.getItems().clear();
            if (bookingsListView != null) refreshBookingsView();
        });
        MenuItem logoutItem = new MenuItem("Logout");
        logoutItem.setOnAction(e -> {
            currentUser = "Guest";
            profileMenu.setText("👤 " + currentUser);
            refreshBookingsView();
        });
        profileMenu.getItems().addAll(changeUserItem, myBookingsItem, adminItem, refreshItem, new SeparatorMenuItem(), logoutItem);
        Button themeToggle = new Button("🌓");
        themeToggle.getStyleClass().add("nav-link");
        themeToggle.setOnAction(e -> toggleTheme());
        HBox rightBox = new HBox(8, lang, themeToggle, profileMenu);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        header.getChildren().addAll(leftBox, nav, centerGrow, rightBox);
        return header;
    }

    private void promptChangeUser() {
        showUserSelectionDialog();
    }

    private void showUserSelectionDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Select or Register User");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));

        Label existingLbl = new Label("Existing users:");
        ComboBox<String> usersCombo = new ComboBox<>();
        List<String> allUsers = dataService.getAllUsernames();
        if (allUsers.isEmpty()) allUsers.add("Guest");
        usersCombo.getItems().addAll(allUsers);
        usersCombo.setValue(currentUser);

        Label newLbl = new Label("Register new user:");
        TextField newUserField = new TextField();
        newUserField.setPromptText("New username");

        content.getChildren().addAll(existingLbl, usersCombo, newLbl, newUserField);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String newName = newUserField.getText();
                if (newName != null && !newName.trim().isEmpty()) {
                    return newName.trim();
                }
                return usersCombo.getValue();
            }
            return null;
        });

        java.util.Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (name == null || name.trim().isEmpty()) return;
            String finalName = name.trim();
            dataService.registerOrUpdateUser(finalName);
            currentUser = finalName;
            currentUserRole = "user";
            try {
                if (userRepository.userExists("admin") && finalName.equalsIgnoreCase("admin")) {
                    currentUserRole = "admin";
                }
            } catch (Exception ignored) {}
            if (profileMenu != null) {
                profileMenu.setText("👤 " + currentUser + ("admin".equals(currentUserRole) ? " (admin)" : ""));
            }
            dataService.updateUserLogin(currentUser);
            refreshBookingsView();
        });
    }

    private Node buildMainContent() {
        searchView = buildSearchView();
        bookingsView = buildBookingsView();
        adminView = buildAdminView();

        mainContentArea = new StackPane(searchView);

        VBox sidebar = buildSidebar();

        HBox mainLayout = new HBox();
        mainLayout.getChildren().addAll(sidebar, mainContentArea);
        HBox.setHgrow(mainContentArea, Priority.ALWAYS);

        return mainLayout;
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.setPrefWidth(200);
        sidebar.getStyleClass().add("sidebar");

        Button btnSearch = createNavButton("🚂  Search Trains", true);
        btnSearch.setOnAction(e -> switchToView(searchView));

        Button btnBookings = createNavButton("🎫  My Bookings", false);
        btnBookings.setOnAction(e -> switchToView(bookingsView));

        Button btnAdmin = createNavButton("🛠️  Manage Trains", false);
        btnAdmin.setOnAction(e -> switchToView(adminView));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(btnSearch, btnBookings, btnAdmin, spacer);
        return sidebar;
    }

    private Button createNavButton(String text, boolean active) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add("nav-button");
        if (active) btn.getStyleClass().add("nav-button-active");
        return btn;
    }

    private void switchToView(Node view) {
        mainContentArea.getChildren().clear();
        mainContentArea.getChildren().add(view);
    }

    private Node buildSearchView() {
        return buildSearchTab();
    }

    private Node buildBookingsView() {
        return buildBookingsTab();
    }

    private Node buildAdminView() {
        return buildAdminTab();
    }

    private Node buildSearchTab() {
        VBox container = new VBox(8);
        container.setPadding(new Insets(8));
        container.getStyleClass().add("irctc-main");
        Label heroTitle = new Label("Book Train Tickets");
        heroTitle.getStyleClass().add("irctc-section-title");
        Label heroSub = new Label("Search trains with confirmed availability across India");
        heroSub.setStyle("-fx-text-fill:#555; -fx-font-size:12px;");
        VBox heroBox = new VBox(2, heroTitle, heroSub);
        heroBox.getStyleClass().add("hero-section");
        VBox card = new VBox(12);
        card.getStyleClass().add("search-card");
        GridPane gp = new GridPane();
        gp.setHgap(10);
        gp.setVgap(8);
        fromBox = new ComboBox<>();
        fromBox.setEditable(true);
        fromBox.setPrefWidth(270);
        toBox = new ComboBox<>();
        toBox.setEditable(true);
        toBox.setPrefWidth(270);
        datePicker = new DatePicker(LocalDate.now().plusDays(1));
        datePicker.setPrefWidth(150);
        returnDatePicker = new DatePicker(LocalDate.now().plusDays(3));
        returnDatePicker.setPrefWidth(150);
        returnDatePicker.setVisible(false);
        returnDatePicker.setManaged(false);
        addReturnCheck = new CheckBox("Return date");
        addReturnCheck.setOnAction(e -> {
            boolean s = addReturnCheck.isSelected();
            returnDatePicker.setVisible(s);
            returnDatePicker.setManaged(s);
        });
        Label fromIconLbl = new Label("📍 From");
        fromIconLbl.getStyleClass().add("irctc-label");
        Label toIconLbl = new Label("📍 To");
        toIconLbl.getStyleClass().add("irctc-label");
        Button swapBtn = new Button("↔");
        swapBtn.getStyleClass().add("swap-btn");
        swapBtn.setOnAction(e -> {
            String t = fromBox.getValue();
            fromBox.setValue(toBox.getValue());
            toBox.setValue(t);
            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(120), swapBtn);
            st.setFromX(1.0); st.setFromY(1.0);
            st.setToX(0.7); st.setToY(0.7);
            st.setAutoReverse(true);
            st.setCycleCount(2);
            st.play();
        });
        Label dateLbl = new Label("📅 Journey Date");
        dateLbl.getStyleClass().add("irctc-label");
        gp.add(fromIconLbl, 0, 0);
        gp.add(fromBox, 1, 0);
        gp.add(swapBtn, 2, 0);
        gp.add(toIconLbl, 3, 0);
        gp.add(toBox, 4, 0);
        gp.add(dateLbl, 0, 1);
        Button todayBtn = new Button("Today");
        todayBtn.getStyleClass().add("quick-date-btn");
        todayBtn.setOnAction(e -> datePicker.setValue(LocalDate.now()));
        Button tomorrowBtn = new Button("Tomorrow");
        tomorrowBtn.getStyleClass().add("quick-date-btn");
        tomorrowBtn.setOnAction(e -> datePicker.setValue(LocalDate.now().plusDays(1)));
        Button plus2Btn = new Button("+2 Days");
        plus2Btn.getStyleClass().add("quick-date-btn");
        plus2Btn.setOnAction(e -> datePicker.setValue(LocalDate.now().plusDays(2)));
        HBox quickDates = new HBox(4, todayBtn, tomorrowBtn, plus2Btn);
        quickDates.setAlignment(Pos.CENTER_LEFT);
        VBox dateControls = new VBox(4, quickDates, new HBox(6, datePicker, addReturnCheck, returnDatePicker));
        dateControls.setAlignment(Pos.CENTER_LEFT);
        gp.add(dateControls, 1, 1, 4, 1);
        classCombo = new ComboBox<>();
        classCombo.getItems().addAll("All Classes", "Sleeper (SL)", "AC 3 Tier (3A)", "AC 2 Tier (2A)", "AC First (1A)", "Chair Car (CC)", "Second Sitting (2S)", "Executive (EC)", "General (P)");
        classCombo.setValue("All Classes");
        classCombo.setPrefWidth(180);
        quotaCombo = new ComboBox<>();
        quotaCombo.getItems().addAll("General (GN)", "Ladies (LD)", "Lower Berth / Sr. Citizen", "Person with Disability (Divyangjan)", "Tatkal (TQ)", "Premium Tatkal (PT)");
        quotaCombo.setValue("General (GN)");
        quotaCombo.setPrefWidth(200);
        Label clsLbl = new Label("Class");
        clsLbl.getStyleClass().add("irctc-label");
        Label qtaLbl = new Label("Quota");
        qtaLbl.getStyleClass().add("irctc-label");
        gp.add(clsLbl, 0, 2);
        gp.add(classCombo, 1, 2);
        gp.add(qtaLbl, 3, 2);
        gp.add(quotaCombo, 4, 2);
        flexibleDatesCheck = new CheckBox("Flexible dates");
        availableSeatsOnlyCheck = new CheckBox("Search for trains with available seats only");
        HBox filterBox = new HBox(20, flexibleDatesCheck, availableSeatsOnlyCheck);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        gp.add(filterBox, 0, 3, 5, 1);
        fromBox.focusedProperty().addListener((obs, was, is) -> { if (is) fromBox.show(); });
        toBox.focusedProperty().addListener((obs, was, is) -> { if (is) toBox.show(); });
        card.getChildren().add(gp);
        Button searchBtn = new Button("🔍 SEARCH TRAINS");
        searchBtn.getStyleClass().add("irctc-search-btn");
        searchBtn.setOnAction(e -> performSearch());
        card.getChildren().add(searchBtn);
        Label resultsTitle = new Label("Available Trains");
        resultsTitle.getStyleClass().add("irctc-section-title");
        resultsCountLabel = new Label("");
        resultsCountLabel.getStyleClass().add("results-count");
        searchLoadingLabel = new Label("");
        searchLoadingLabel.getStyleClass().add("search-loading");
        sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll("Departure Time", "Cheapest", "Most Seats");
        sortCombo.setValue("Departure Time");
        sortCombo.setPrefWidth(130);
        sortCombo.getStyleClass().add("sort-combo");
        sortCombo.setOnAction(e -> applyResultsSort());
        HBox resultsHeader = new HBox(resultsTitle, resultsCountLabel, searchLoadingLabel, sortCombo);
        resultsHeader.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(resultsCountLabel, Priority.ALWAYS);
        resultsListView = new ListView<>();
        resultsListView.setPrefHeight(380);
        resultsListView.setCellFactory(createTrainCellFactory());
        VBox.setVgrow(resultsListView, Priority.ALWAYS);
        container.getChildren().addAll(heroBox, card, resultsHeader, resultsListView);
        return container;
    }

    private Callback<ListView<Train>, ListCell<Train>> createTrainCellFactory() {
        return lv -> new ListCell<>() {
            @Override
            protected void updateItem(Train train, boolean empty) {
                super.updateItem(train, empty);
                if (empty || train == null) {
                    setGraphic(null);
                    return;
                }
                HBox card = new HBox(12);
                card.setAlignment(Pos.CENTER_LEFT);
                card.getStyleClass().add("train-card");

                VBox info = new VBox(4);
                Label trainId = new Label(train.getTrainNo() + " • " + train.getName());
                trainId.getStyleClass().add("train-name");

                Label route = new Label(train.getSource() + " → " + train.getDestination());
                route.getStyleClass().add("train-route");

                String dep = train.getDeparture();
                String arr = train.getArrival();
                String timesText = ("TBD".equals(dep) || "TBD".equals(arr))
                    ? "Timings: Check IRCTC (real data coming soon)"
                    : "⏰ " + dep + "  →  " + arr;
                Label times = new Label(timesText);
                times.getStyleClass().add("train-times");

                HBox pills = new HBox(6);
                for (Map.Entry<String, Integer> e : train.getAvailableSeats().entrySet()) {
                    Label pill = new Label(e.getKey() + ": " + e.getValue());
                    pill.getStyleClass().add("availability-pill");
                    int cnt = e.getValue() != null ? e.getValue() : 0;
                    if (cnt > 50) pill.setStyle("-fx-background-color:#22c55e; -fx-text-fill:white;");
                    else if (cnt > 10) pill.setStyle("-fx-background-color:#f59e0b; -fx-text-fill:white;");
                    else pill.setStyle("-fx-background-color:#ef4444; -fx-text-fill:white;");
                    pills.getChildren().add(pill);
                }

                // Frequency badge from real timetable data (Phase 2)
                Label freqPill = new Label(train.getFrequency());
                freqPill.getStyleClass().add("frequency-pill");
                pills.getChildren().add(freqPill);

                info.getChildren().addAll(trainId, route, times, pills);

                Region spacer2 = new Region();
                HBox.setHgrow(spacer2, Priority.ALWAYS);

                Button bookBtn = new Button("Book Now →");
                bookBtn.getStyleClass().add("primary-button");
                bookBtn.setOnAction(ev -> {
                    String pref = null;
                    if (classCombo != null) {
                        String d = classCombo.getValue();
                        if (d != null && !d.equals("All Classes") && d.contains("(") && d.endsWith(")")) {
                            int o = d.lastIndexOf('(');
                            int c = d.lastIndexOf(')');
                            pref = d.substring(o + 1, c).trim();
                        }
                    }
                    openBookingDialog(train, pref);
                });

                card.getChildren().addAll(info, spacer2, bookBtn);
                setGraphic(card);
            }
        };
    }

    private void performSearch() {
        String from = fromBox.getValue();
        String to = toBox.getValue();
        LocalDate date = datePicker.getValue();
        if (from == null || to == null || from.isBlank() || to.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Missing Info", "Please enter From and To stations.");
            return;
        }
        if (searchLoadingLabel != null) searchLoadingLabel.setText("  ⏳ Searching...");
        List<Train> results = dataService.searchTrains(from, to, date);
        String clsDisplay = (classCombo != null) ? classCombo.getValue() : "All Classes";
        boolean onlyAvail = (availableSeatsOnlyCheck != null) && availableSeatsOnlyCheck.isSelected();
        if (clsDisplay != null && !clsDisplay.equals("All Classes") && onlyAvail) {
            String code = null;
            if (clsDisplay.contains("(") && clsDisplay.endsWith(")")) {
                int o = clsDisplay.lastIndexOf('(');
                int c = clsDisplay.lastIndexOf(')');
                code = clsDisplay.substring(o + 1, c).trim();
            }
            if (code != null) {
                final String fc = code;
                results = results.stream()
                    .filter(t -> t.getAvailableSeats() != null && t.getAvailableSeats().getOrDefault(fc, 0) > 0)
                    .collect(Collectors.toList());
            }
        }
        resultsListView.setItems(FXCollections.observableArrayList(results));
        if (resultsCountLabel != null) {
            resultsCountLabel.setText(results.size() + " trains found");
        }
        if (searchLoadingLabel != null) searchLoadingLabel.setText("");
        if (results.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "No Trains Found", "No matching trains for the selected route.");
        }
        updateStatus();
        applyResultsSort();
    }

    private void applyResultsSort() {
        if (resultsListView == null || sortCombo == null) return;
        ObservableList<Train> items = resultsListView.getItems();
        if (items == null || items.isEmpty()) return;
        String sort = sortCombo.getValue();
        List<Train> sorted = new ArrayList<>(items);
        if ("Cheapest".equals(sort)) {
            sorted.sort(Comparator.comparingDouble(Train::getBaseFare));
        } else if ("Most Seats".equals(sort)) {
            sorted.sort((a, b) -> {
                int sumA = a.getAvailableSeats().values().stream().mapToInt(Integer::intValue).sum();
                int sumB = b.getAvailableSeats().values().stream().mapToInt(Integer::intValue).sum();
                return Integer.compare(sumB, sumA);
            });
        } else {
            sorted.sort(Comparator.comparing(Train::getDeparture, Comparator.nullsLast(Comparator.naturalOrder())));
        }
        resultsListView.setItems(FXCollections.observableArrayList(sorted));
    }

    private void openBookingDialog(Train train) {
        openBookingDialog(train, null);
    }

    private void openBookingDialog(Train train, String preselectClass) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(primaryStage);
        dialog.setTitle("Book Ticket • " + train.getTrainNo());

        VBox root = new VBox(10);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("booking-dialog");

        // Wizard header
        HBox wizardHeader = new HBox(10);
        wizardHeader.setAlignment(Pos.CENTER_LEFT);
        Label stepLabel = new Label("Step 1/4: Class & Passengers");
        stepLabel.getStyleClass().add("wizard-step");
        ProgressBar progressBar = new ProgressBar(0.25);
        progressBar.setPrefWidth(180);
        wizardHeader.getChildren().addAll(stepLabel, progressBar);

        HBox summary = new HBox(10);
        summary.getStyleClass().add("train-summary");
        Label sum = new Label(train.getTrainNo() + " • " + train.getName() + "\n" +
                train.getSource() + " → " + train.getDestination() + "  |  " + train.getDeparture() + " - " + train.getArrival());
        Region sumSpacer = new Region();
        HBox.setHgrow(sumSpacer, Priority.ALWAYS);
        Button routeBtn = new Button("🚉 View All Stops");
        routeBtn.getStyleClass().add("secondary-button");
        routeBtn.setOnAction(e -> showTrainRouteDialog(train));
        summary.getChildren().addAll(sum, sumSpacer, routeBtn);

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
        boolean didPre = false;
        if (preselectClass != null) {
            for (ToggleButton tb : classBtns) {
                if (tb.getText().equals(preselectClass)) {
                    tb.setSelected(true);
                    didPre = true;
                    break;
                }
            }
        }
        if (!didPre) {
            classBtns.get(0).setSelected(true);
        }
        classRow.getChildren().add(clsLbl);
        classRow.getChildren().addAll(classBtns);

        // Num passengers
        HBox numRow = new HBox(8);
        numRow.setAlignment(Pos.CENTER_LEFT);
        Label numLbl = new Label("Passengers:");
        ComboBox<Integer> numBox = new ComboBox<>();
        numBox.getItems().addAll(1,2,3,4,5,6);
        numBox.setValue(1);

        numRow.getChildren().addAll(numLbl, numBox);

        Label selectionInfo = new Label("Selected: 0 / 1");
        Label fareLabel = new Label("Total Fare: ₹0");
        fareLabel.getStyleClass().add("fare-label");

        Label seatTitle = new Label("Select Seats (class coach layout)");
        seatTitle.getStyleClass().add("section-title-small");

        GridPane seatGrid = new GridPane();
        seatGrid.setHgap(3);
        seatGrid.setVgap(3);
        seatGrid.getStyleClass().add("seat-grid");
        for (int i = 0; i < 6; i++) {
            javafx.scene.layout.ColumnConstraints cc = new javafx.scene.layout.ColumnConstraints();
            cc.setPrefWidth(32);
            seatGrid.getColumnConstraints().add(cc);
        }
        List<ToggleButton> seatToggles = new ArrayList<>();
        Runnable rebuildSeats = () -> {
            seatGrid.getChildren().clear();
            seatToggles.clear();
            String cls = (classGroup.getSelectedToggle() != null) ? ((ToggleButton) classGroup.getSelectedToggle()).getText() : "SL";
            seatTitle.setText("IR " + cls + " Coach Map");
            if ("SL".equals(cls) || "3A".equals(cls)) {
                for (int b = 0; b < 4; b++) {
                    int r = b;
                    ToggleButton sl = new ToggleButton("SL" + (b+1)); sl.getStyleClass().addAll("seat","seat-available"); sl.setPrefSize(30,18); seatGrid.add(sl,0,r); seatToggles.add(sl);
                    ToggleButton su = new ToggleButton("SU" + (b+1)); su.getStyleClass().addAll("seat","seat-available"); su.setPrefSize(30,18); seatGrid.add(su,1,r); seatToggles.add(su);
                    ToggleButton lb = new ToggleButton("LB" + (b+1)); lb.getStyleClass().addAll("seat","seat-available"); lb.setPrefSize(30,18); seatGrid.add(lb,3,r); seatToggles.add(lb);
                    ToggleButton mb = new ToggleButton("MB" + (b+1)); mb.getStyleClass().addAll("seat","seat-available"); mb.setPrefSize(30,18); seatGrid.add(mb,4,r); seatToggles.add(mb);
                    ToggleButton ub = new ToggleButton("UB" + (b+1)); ub.getStyleClass().addAll("seat","seat-available"); ub.setPrefSize(30,18); seatGrid.add(ub,5,r); seatToggles.add(ub);
                    Label aisle = new Label("—"); aisle.getStyleClass().add("aisle"); seatGrid.add(aisle,2,r);
                }
            } else if ("2A".equals(cls) || "1A".equals(cls)) {
                for (int b = 0; b < 4; b++) {
                    int r = b;
                    ToggleButton sl = new ToggleButton("SL" + (b+1)); sl.getStyleClass().addAll("seat","seat-available"); sl.setPrefSize(30,18); seatGrid.add(sl,0,r); seatToggles.add(sl);
                    ToggleButton su = new ToggleButton("SU" + (b+1)); su.getStyleClass().addAll("seat","seat-available"); su.setPrefSize(30,18); seatGrid.add(su,1,r); seatToggles.add(su);
                    ToggleButton lb = new ToggleButton("LB" + (b+1)); lb.getStyleClass().addAll("seat","seat-available"); lb.setPrefSize(30,18); seatGrid.add(lb,3,r); seatToggles.add(lb);
                    ToggleButton ub = new ToggleButton("UB" + (b+1)); ub.getStyleClass().addAll("seat","seat-available"); ub.setPrefSize(30,18); seatGrid.add(ub,4,r); seatToggles.add(ub);
                    Label aisle = new Label("—"); aisle.getStyleClass().add("aisle"); seatGrid.add(aisle,2,r);
                }
            } else {
                for (int b = 0; b < 5; b++) {
                    int r = b;
                    ToggleButton w1 = new ToggleButton((b*5+1)+"W"); w1.getStyleClass().addAll("seat","seat-available"); w1.setPrefSize(26,18); seatGrid.add(w1,0,r); seatToggles.add(w1);
                    ToggleButton a1 = new ToggleButton((b*5+2)+"A"); a1.getStyleClass().addAll("seat","seat-available"); a1.setPrefSize(26,18); seatGrid.add(a1,1,r); seatToggles.add(a1);
                    Label aisle = new Label("|"); aisle.getStyleClass().add("aisle"); seatGrid.add(aisle,2,r);
                    ToggleButton a2 = new ToggleButton((b*5+3)+"A"); a2.getStyleClass().addAll("seat","seat-available"); a2.setPrefSize(26,18); seatGrid.add(a2,3,r); seatToggles.add(a2);
                    ToggleButton m  = new ToggleButton((b*5+4)+"M"); m.getStyleClass().addAll("seat","seat-available"); m.setPrefSize(26,18); seatGrid.add(m,4,r); seatToggles.add(m);
                    ToggleButton w2 = new ToggleButton((b*5+5)+"W"); w2.getStyleClass().addAll("seat","seat-available"); w2.setPrefSize(26,18); seatGrid.add(w2,5,r); seatToggles.add(w2);
                }
            }
            for (ToggleButton s : seatToggles) {
                s.setOnAction(ev -> updateSelectionInfo(selectionInfo, seatToggles, numBox, fareLabel, train, classGroup));
            }
        };

        VBox paxContainer = new VBox(6);
        paxContainer.getStyleClass().add("pax-container");
        Label paxTitle = new Label("Passenger Details");
        paxTitle.getStyleClass().add("section-title-small");

        Runnable updatePaxForm = () -> {
            int n = numBox.getValue();
            paxContainer.getChildren().clear();
            paxContainer.getChildren().add(paxTitle);
            for (int i = 0; i < n; i++) {
                HBox row = new HBox(6);
                row.setAlignment(Pos.CENTER_LEFT);
                TextField nameF = new TextField("Passenger " + (i+1));
                nameF.setPrefWidth(160);
                TextField ageF = new TextField("28");
                ageF.setPrefWidth(50);
                ComboBox<String> genderC = new ComboBox<>();
                genderC.getItems().addAll("M", "F", "O");
                genderC.setValue("M");
                ComboBox<String> berthC = new ComboBox<>();
                berthC.getItems().addAll("Lower", "Middle", "Upper", "Side Lower", "Side Upper");
                berthC.setValue("Lower");
                row.getChildren().addAll(new Label("Name:"), nameF, new Label("Age:"), ageF, new Label("Gender:"), genderC, new Label("Berth:"), berthC);
                paxContainer.getChildren().add(row);
            }
        };

        numBox.setOnAction(e -> {
            updatePaxForm.run();
            updateSelectionInfo(selectionInfo, seatToggles, numBox, fareLabel, train, classGroup);
        });

        classGroup.selectedToggleProperty().addListener((obs, old, sel) -> {
            rebuildSeats.run();
            updateSelectionInfo(selectionInfo, seatToggles, numBox, fareLabel, train, classGroup);
            seatToggles.forEach(s -> s.setSelected(false));
            seatToggles.forEach(s -> s.getStyleClass().remove("seat-selected"));
        });

        updatePaxForm.run();
        rebuildSeats.run();
        updateSelectionInfo(selectionInfo, seatToggles, numBox, fareLabel, train, classGroup);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button backBtn = new Button("← Back");
        backBtn.getStyleClass().add("secondary-button");
        Button nextBtn = new Button("Next →");
        nextBtn.getStyleClass().add("primary-button");
        int[] currentStep = {1};
        nextBtn.setOnAction(ev -> {
            currentStep[0]++;
            if (currentStep[0] > 4) currentStep[0] = 4;
            stepLabel.setText("Step " + currentStep[0] + "/4: " + (currentStep[0] == 1 ? "Class & Passengers" : currentStep[0] == 2 ? "Seat Selection" : currentStep[0] == 3 ? "Passenger Details" : "Review & Confirm"));
            progressBar.setProgress(currentStep[0] / 4.0);
            if (currentStep[0] == 4) nextBtn.setText("💳 Proceed to Pay");
        });
        backBtn.setOnAction(ev -> {
            currentStep[0]--;
            if (currentStep[0] < 1) currentStep[0] = 1;
            stepLabel.setText("Step " + currentStep[0] + "/4: " + (currentStep[0] == 1 ? "Class & Passengers" : currentStep[0] == 2 ? "Seat Selection" : currentStep[0] == 3 ? "Passenger Details" : "Review & Confirm"));
            progressBar.setProgress(currentStep[0] / 4.0);
            if (currentStep[0] < 4) nextBtn.setText("Next →");
        });
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        Button confirmBtn = new Button("💳 Proceed to Pay");
        confirmBtn.getStyleClass().add("primary-button");

        confirmBtn.setOnAction(e -> {
            String selectedClass = ((ToggleButton) classGroup.getSelectedToggle()).getText();
            int num = numBox.getValue();
            long selectedCount = seatToggles.stream().filter(ToggleButton::isSelected).count();

            if (selectedCount != num) {
                showAlert(Alert.AlertType.WARNING, "Seat Selection", "Please select exactly " + num + " seat(s) from the map.");
                return;
            }

            // Collect passengers from form
            List<Passenger> paxList = new ArrayList<>();
            int idx = 0;
            for (Node node : paxContainer.getChildren()) {
                if (node instanceof HBox) {
                    HBox row = (HBox) node;
                    TextField nameF = (TextField) row.getChildren().get(1);
                    TextField ageF = (TextField) row.getChildren().get(3);
                    ComboBox<String> gC = (ComboBox<String>) row.getChildren().get(5);
                    ComboBox<String> bC = (ComboBox<String>) row.getChildren().get(7);
                    paxList.add(new Passenger(nameF.getText().trim(), ageF.getText().trim(), gC.getValue(), bC.getValue()));
                    idx++;
                    if (idx >= num) break;
                }
            }

            String jDate = datePicker.getValue() != null ? datePicker.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE) : LocalDate.now().plusDays(1).toString();

            double fare = dataService.calculateFare(train, selectedClass, num);

            // Close booking details dialog and open payment gateway
            dialog.close();
            Platform.runLater(() -> openPaymentGateway(train, selectedClass, num, paxList, jDate, fare));
        });

        cancelBtn.setOnAction(e -> dialog.close());

        // Wizard navigation wiring (basic for now)
        nextBtn.setOnAction(ev -> {
            if (currentStep[0] < 4) {
                currentStep[0]++;
                stepLabel.setText("Step " + currentStep[0] + "/4: " + (currentStep[0] == 1 ? "Class & Passengers" : currentStep[0] == 2 ? "Seat Selection" : currentStep[0] == 3 ? "Passenger Details" : "Review & Confirm"));
                progressBar.setProgress(currentStep[0] / 4.0);
            } else {
                confirmBtn.fire();
            }
        });

        actions.getChildren().addAll(backBtn, nextBtn, cancelBtn, confirmBtn);

        root.getChildren().addAll(
            wizardHeader,
            new Label("Booking for: " + currentUser),
            summary,
            classRow,
            numRow,
            seatTitle, seatGrid, selectionInfo, fareLabel,
            paxContainer,
            actions
        );

        Scene dialogScene = new Scene(root, 720, 620);
        var css = getClass().getResource("/styles/railway-reservation.css");
        if (css != null) dialogScene.getStylesheets().add(css.toExternalForm());
        dialog.setScene(dialogScene);
        dialog.showAndWait();
    }

    private void updateSelectionInfo(Label info, List<ToggleButton> seats, ComboBox<Integer> numBox,
                                     Label fareLbl, Train train, ToggleGroup classGroup) {
        int max = numBox.getValue();
        long sel = seats.stream().filter(ToggleButton::isSelected).count();

        if (sel > max) {
            // auto deselect last ones
            seats.stream().filter(ToggleButton::isSelected).skip(max).forEach(s -> s.setSelected(false));
            sel = max;
        }

        info.setText("Selected: " + sel + " / " + max);

        String cls = classGroup.getSelectedToggle() != null ?
            ((ToggleButton) classGroup.getSelectedToggle()).getText() : "SL";
        double fare = dataService.calculateFare(train, cls, max);
        fareLbl.setText("Total Fare: ₹" + String.format("%.0f", fare));
    }

    private void showSuccessPNR(Train train, String cls, List<Passenger> pax, String journeyDate) {
        // Find the latest booking for this user/train
        List<Booking> userBookings = dataService.getBookingsForUser(currentUser);
        Booking latest = userBookings.isEmpty() ? null : userBookings.get(userBookings.size() - 1);

        Stage success = new Stage();
        success.initModality(Modality.APPLICATION_MODAL);
        success.initOwner(primaryStage);
        success.setTitle("Booking Confirmed");

        VBox box = new VBox(12);
        box.setPadding(new Insets(25));
        box.getStyleClass().add("success-box");

        Label title = new Label("🎉 Booking Confirmed!");
        title.getStyleClass().add("success-title");

        Label pnrLabel = new Label(latest != null ? "PNR: " + latest.getPnr() : "PNR: N/A");
        pnrLabel.getStyleClass().add("pnr-display");

        Label details = new Label(train.getName() + " (" + cls + ")\n" +
                train.getSource() + " → " + train.getDestination() + " on " + journeyDate + "\n" +
                pax.size() + " Passenger(s) • ₹" + (latest != null ? String.format("%.0f", latest.getTotalFare()) : "0"));
        details.getStyleClass().add("success-details");

        Button close = new Button("Done");
        close.getStyleClass().add("primary-button");
        close.setOnAction(e -> success.close());

        box.getChildren().addAll(title, pnrLabel, details, close);
        Scene sc = new Scene(box, 480, 260);
        var css = getClass().getResource("/styles/railway-reservation.css");
        if (css != null) sc.getStylesheets().add(css.toExternalForm());
        success.setScene(sc);
        success.showAndWait();
    }

    // ==================== PHASE 3: PAYMENT GATEWAY ====================

    private String generateTransactionId() {
        return "RAILTXN" + (System.currentTimeMillis() % 1000000000000L);
    }

    /**
     * Opens a beautiful simulated payment gateway (IRCTC style).
     * On successful payment, books the ticket and shows receipt + PNR.
     */
    private void openPaymentGateway(Train train, String cls, int numPassengers, List<Passenger> paxList,
                                    String journeyDate, double totalFare) {

        Stage payDialog = new Stage();
        payDialog.initModality(Modality.APPLICATION_MODAL);
        payDialog.initOwner(primaryStage);
        payDialog.setTitle("RailPay • Secure Payment");

        VBox root = new VBox(14);
        root.setPadding(new Insets(18, 22, 18, 22));
        root.getStyleClass().add("payment-gateway");

        // Header
        Label header = new Label("Pay ₹" + String.format("%.0f", totalFare) +
                " for " + numPassengers + " passenger(s) on " + train.getTrainNo());
        header.getStyleClass().add("payment-header");

        Label sub = new Label(train.getName() + " • " + train.getSource() + " → " + train.getDestination() +
                " • " + journeyDate);
        sub.getStyleClass().add("payment-subheader");

        // Method selection
        HBox methodBar = new HBox(6);
        methodBar.setAlignment(Pos.CENTER);
        methodBar.getStyleClass().add("payment-method-bar");

        ToggleGroup methodGroup = new ToggleGroup();
        String[] labels = {"💳 Card", "📱 UPI", "🏦 Net Banking", "👛 Wallet"};
        String[] keys   = {"Credit Card", "UPI", "Net Banking", "Wallet"};

        final ToggleButton[] methodBtns = new ToggleButton[4];
        for (int i = 0; i < 4; i++) {
            ToggleButton b = new ToggleButton(labels[i]);
            b.setToggleGroup(methodGroup);
            b.setUserData(keys[i]);
            b.getStyleClass().add("payment-method-btn");
            methodBtns[i] = b;
            methodBar.getChildren().add(b);
        }
        methodGroup.selectToggle(methodBtns[0]); // default Card

        // Dynamic form area
        VBox formArea = new VBox(10);
        formArea.getStyleClass().add("payment-form-area");

        // We will rebuild the form when method changes
        Runnable rebuildForm = new Runnable() {
            VBox currentForm = null;

            @Override
            public void run() {
                formArea.getChildren().clear();

                Toggle sel = methodGroup.getSelectedToggle();
                String method = (sel != null) ? (String) sel.getUserData() : "Credit Card";
                if (method == null) method = "Credit Card";

                VBox form = new VBox(8);
                form.getStyleClass().add("payment-form");

                if ("Credit Card".equals(method)) {
                    Label l = new Label("Card Details");
                    l.getStyleClass().add("form-section");

                    TextField cardNo = new TextField("4242 4242 4242 4242");
                    cardNo.setPromptText("Card Number");
                    cardNo.getStyleClass().add("payment-input");

                    TextField name = new TextField("Test User");
                    name.setPromptText("Cardholder Name");

                    HBox row = new HBox(8);
                    TextField exp = new TextField("12/28");
                    exp.setPromptText("MM/YY");
                    exp.setPrefWidth(90);
                    PasswordField cvv = new PasswordField();
                    cvv.setPromptText("CVV");
                    cvv.setPrefWidth(70);
                    row.getChildren().addAll(exp, cvv);

                    form.getChildren().addAll(l, cardNo, name, row);

                    // store references for simulation
                    form.setUserData(new Object[]{cardNo, name, exp, cvv});

                } else if ("UPI".equals(method)) {
                    Label l = new Label("UPI Payment");
                    l.getStyleClass().add("form-section");

                    TextField vpa = new TextField("testuser@oksbi");
                    vpa.setPromptText("yourname@upi");
                    vpa.getStyleClass().add("payment-input");

                    Label qr = new Label("📱 Scan QR or enter VPA above\n(Fake QR for demo)");
                    qr.getStyleClass().add("fake-qr");

                    form.getChildren().addAll(l, vpa, qr);
                    form.setUserData(new Object[]{vpa});

                } else if ("Net Banking".equals(method)) {
                    Label l = new Label("Net Banking");
                    l.getStyleClass().add("form-section");

                    ComboBox<String> bank = new ComboBox<>();
                    bank.getItems().addAll("State Bank of India", "HDFC Bank", "ICICI Bank", "Axis Bank", "Punjab National Bank", "Bank of Baroda");
                    bank.setValue("State Bank of India");

                    Label note = new Label("You will be redirected to the bank's secure page (simulated).");
                    note.getStyleClass().add("note");

                    form.getChildren().addAll(l, bank, note);
                    form.setUserData(new Object[]{bank});

                } else { // Wallet
                    Label l = new Label("Digital Wallet");
                    l.getStyleClass().add("form-section");

                    ComboBox<String> wallet = new ComboBox<>();
                    wallet.getItems().addAll("Paytm", "PhonePe", "Google Pay", "Amazon Pay");
                    wallet.setValue("Paytm");

                    Label bal = new Label("Available Balance: ₹12,450 (demo)");
                    bal.getStyleClass().add("note");

                    form.getChildren().addAll(l, wallet, bal);
                    form.setUserData(new Object[]{wallet});
                }

                formArea.getChildren().add(form);
                currentForm = form;
            }
        };

        // initial form
        rebuildForm.run();

        // listen for method change
        methodGroup.selectedToggleProperty().addListener((obs, old, sel) -> {
            if (sel != null) rebuildForm.run();
        });

        // Amount and secure note
        Label amountLabel = new Label("Total Amount: ₹" + String.format("%.0f", totalFare));
        amountLabel.getStyleClass().add("payment-amount");

        Label secure = new Label("🔒 256-bit SSL Secured  •  PCI DSS Compliant  •  RailPay");
        secure.getStyleClass().add("secure-note");

        // Action buttons
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancelPay = new Button("Cancel");
        cancelPay.getStyleClass().add("secondary-button");
        cancelPay.setOnAction(e -> payDialog.close());

        Button payBtn = new Button("Pay ₹" + String.format("%.0f", totalFare) + " Securely");
        payBtn.getStyleClass().add("primary-button");

        payBtn.setOnAction(e -> {
            ToggleButton selected = (ToggleButton) methodGroup.getSelectedToggle();
            String method = selected != null ? (String) selected.getUserData() : "Credit Card";

            VBox currentForm = null;
            if (!formArea.getChildren().isEmpty() && formArea.getChildren().get(0) instanceof VBox) {
                currentForm = (VBox) formArea.getChildren().get(0);
            }
            Object[] data = (currentForm != null) ? (Object[]) currentForm.getUserData() : null;

            // Simulate payment decision
            boolean success = simulatePayment(method, data);

            payBtn.setDisable(true);
            Label proc = new Label("⏳ Processing payment via " + method + "...");
            proc.getStyleClass().add("processing-label");
            actions.getChildren().add(proc);

            PauseTransition pt = new PauseTransition(Duration.millis(1350));
            pt.setOnFinished(ev -> {
                actions.getChildren().remove(proc);
                payBtn.setDisable(false);

                if (success) {
                    String txnId = generateTransactionId();
                    boolean booked = dataService.bookTicket(train, cls, numPassengers, paxList, currentUser, journeyDate, method, txnId);

                    if (booked) {
                        payDialog.close();
                        Platform.runLater(() -> {
                            showPaymentReceipt(train, cls, paxList, journeyDate, totalFare, method, txnId);
                            resultsListView.refresh();
                            refreshBookingsView();
                            updateStatus();
                        });
                    } else {
                        payDialog.close();
                        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Booking Failed", "Seats no longer available after payment simulation."));
                    }
                } else {
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Payment Failed",
                            "Your transaction was declined by the bank / gateway.\nPlease try another method or card."));
                    // allow retry
                }
            });
            pt.play();
        });

        actions.getChildren().addAll(cancelPay, payBtn);

        root.getChildren().addAll(header, sub, methodBar, formArea, amountLabel, secure, actions);

        Scene scene = new Scene(root, 620, 520);
        var cssUrl = getClass().getResource("/styles/railway-reservation.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        payDialog.setScene(scene);
        payDialog.showAndWait();
    }

    private boolean simulatePayment(String method, Object[] formData) {
        if ("Credit Card".equals(method) && formData != null && formData.length > 0) {
            String card = ((TextField) formData[0]).getText().replaceAll("\\s", "");
            if (card.startsWith("4242")) return true;           // success
            if (card.startsWith("4000")) return false;          // fail
            return Math.random() > 0.15;                        // mostly success
        }
        // For other methods, 85% success rate for demo
        return Math.random() > 0.15;
    }

    private void showPaymentReceipt(Train train, String cls, List<Passenger> pax, String journeyDate,
                                    double totalFare, String method, String txnId) {
        Stage receipt = new Stage();
        receipt.initModality(Modality.APPLICATION_MODAL);
        receipt.initOwner(primaryStage);
        receipt.setTitle("Payment Receipt • RailPay");

        VBox box = new VBox(10);
        box.setPadding(new Insets(22));
        box.getStyleClass().add("receipt-box");

        Label title = new Label("✅ Payment Successful");
        title.getStyleClass().add("receipt-title");

        Label txn = new Label("Transaction ID: " + txnId);
        txn.getStyleClass().add("txn-id");

        Label details = new Label("Method: " + method + "\n" +
                "Amount: ₹" + String.format("%.0f", totalFare) + "\n" +
                train.getName() + " (" + cls + ")\n" +
                train.getSource() + " → " + train.getDestination() + " on " + journeyDate + "\n" +
                pax.size() + " Passenger(s)");
        details.getStyleClass().add("receipt-details");

        Button viewTicket = new Button("View PNR & Ticket");
        viewTicket.getStyleClass().add("primary-button");
        viewTicket.setOnAction(e -> {
            receipt.close();
            showSuccessPNR(train, cls, pax, journeyDate);   // reuse existing PNR dialog
        });

        Button done = new Button("Done");
        done.getStyleClass().add("secondary-button");
        done.setOnAction(e -> receipt.close());

        HBox btns = new HBox(10, viewTicket, done);
        btns.setAlignment(Pos.CENTER_RIGHT);

        box.getChildren().addAll(title, txn, details, btns);

        Scene sc = new Scene(box, 460, 280);
        var css = getClass().getResource("/styles/railway-reservation.css");
        if (css != null) sc.getStylesheets().add(css.toExternalForm());
        receipt.setScene(sc);
        receipt.showAndWait();
    }

    // ==================== END PHASE 3 ====================

    private Node buildBookingsTab() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(20));
        container.getStyleClass().add("bookings-container");

        Label title = new Label("Your Bookings");
        title.getStyleClass().add("section-title");

        ComboBox<String> filterBox = new ComboBox<>();
        filterBox.getItems().addAll("All", "Upcoming", "Past");
        filterBox.setValue("All");
        filterBox.setOnAction(e -> refreshBookingsView());

        HBox filterRow = new HBox(8, new Label("Filter:"), filterBox);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        bookingsListView = new ListView<>();
        bookingsListView.setPrefHeight(480);
        bookingsListView.setCellFactory(createBookingCellFactory());

        Button cancelSelected = new Button("Cancel Selected Booking");
        cancelSelected.getStyleClass().add("danger-button");
        cancelSelected.setOnAction(e -> {
            Booking selected = bookingsListView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert(Alert.AlertType.INFORMATION, "No Selection", "Select a booking to cancel.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Cancel PNR " + selected.getPnr() + "?", ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.YES) {
                    boolean ok = dataService.cancelBooking(selected.getPnr());
                    if (ok) {
                        refreshBookingsView();
                        updateStatus();
                        if (resultsListView != null) resultsListView.refresh();
                    }
                }
            });
        });

        container.getChildren().addAll(title, filterRow, bookingsListView, cancelSelected);
        VBox.setVgrow(bookingsListView, Priority.ALWAYS);

        // Initial load
        Platform.runLater(this::refreshBookingsView);

        return container;
    }

    private Callback<ListView<Booking>, ListCell<Booking>> createBookingCellFactory() {
        return lv -> new ListCell<>() {
            @Override
            protected void updateItem(Booking b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) {
                    setGraphic(null);
                    return;
                }
                HBox card = new HBox(10);
                card.setAlignment(Pos.CENTER_LEFT);
                card.getStyleClass().add("booking-card");

                VBox info = new VBox(3);
                Label pnr = new Label("PNR: " + b.getPnr());
                pnr.getStyleClass().add("pnr-small");
                Label main = new Label(b.getTrainName() + " (" + b.getCls() + ") • " + b.getJourneyDate());
                Label pax = new Label(b.getPassengers().size() + " pax • ₹" + String.format("%.0f", b.getTotalFare()) + " • " + b.getUserName());
                info.getChildren().addAll(pnr, main, pax);

                if (b.getPaymentMethod() != null && b.getTransactionId() != null) {
                    Label payInfo = new Label("Paid via " + b.getPaymentMethod() + " • " + b.getTransactionId());
                    payInfo.getStyleClass().add("payment-info-small");
                    info.getChildren().add(payInfo);
                }

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Button cancel = new Button("Cancel");
                cancel.getStyleClass().add("danger-button");
                cancel.setOnAction(e -> {
                    Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Cancel booking " + b.getPnr() + "?", ButtonType.YES, ButtonType.NO);
                    c.showAndWait().ifPresent(bt -> {
                        if (bt == ButtonType.YES) {
                            dataService.cancelBooking(b.getPnr());
                            refreshBookingsView();
                            updateStatus();
                            if (resultsListView != null) resultsListView.refresh();
                        }
                    });
                });

                card.getChildren().addAll(info, sp, cancel);
                setGraphic(card);
            }
        };
    }

    private void refreshBookingsView() {
        if (bookingsListView == null) return;
        List<Booking> list = dataService.getBookingsForUser(currentUser);
        bookingsListView.setItems(FXCollections.observableArrayList(list));
    }

    private Node buildAdminTab() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(20));
        container.getStyleClass().add("admin-container");

        Label title = new Label("Manage Trains (Admin Demo)");
        title.getStyleClass().add("section-title");

        // Add form
        GridPane form = new GridPane();
        form.setHgap(8); form.setVgap(6);

        TextField noF = new TextField("12999");
        TextField nameF = new TextField("New Express");
        TextField srcF = new TextField("New Delhi");
        TextField dstF = new TextField("Mumbai Central");
        TextField depF = new TextField("20:00");
        TextField arrF = new TextField("07:30");
        TextField slF = new TextField("100");
        TextField a3F = new TextField("40");
        TextField a2F = new TextField("20");
        TextField a1F = new TextField("8");
        TextField fareF = new TextField("2100");

        form.add(new Label("Train No:"), 0, 0); form.add(noF, 1, 0);
        form.add(new Label("Name:"), 2, 0); form.add(nameF, 3, 0);
        form.add(new Label("Source:"), 0, 1); form.add(srcF, 1, 1);
        form.add(new Label("Dest:"), 2, 1); form.add(dstF, 3, 1);
        form.add(new Label("Dep:"), 0, 2); form.add(depF, 1, 2);
        form.add(new Label("Arr:"), 2, 2); form.add(arrF, 3, 2);
        form.add(new Label("SL:"), 0, 3); form.add(slF, 1, 3);
        form.add(new Label("3A:"), 2, 3); form.add(a3F, 3, 3);
        form.add(new Label("2A:"), 0, 4); form.add(a2F, 1, 4);
        form.add(new Label("1A:"), 2, 4); form.add(a1F, 3, 4);
        form.add(new Label("Base Fare:"), 0, 5); form.add(fareF, 1, 5);

        Button addBtn = new Button("➕ Add / Update Train");
        addBtn.getStyleClass().add("primary-button");

        addBtn.setOnAction(e -> {
            try {
                Map<String, Integer> seats = new LinkedHashMap<>();
                seats.put("SL", Integer.parseInt(slF.getText().trim()));
                seats.put("3A", Integer.parseInt(a3F.getText().trim()));
                seats.put("2A", Integer.parseInt(a2F.getText().trim()));
                seats.put("1A", Integer.parseInt(a1F.getText().trim()));

                Train t = new Train(
                    noF.getText().trim(),
                    nameF.getText().trim(),
                    srcF.getText().trim(),
                    dstF.getText().trim(),
                    depF.getText().trim(),
                    arrF.getText().trim(),
                    seats,
                    Double.parseDouble(fareF.getText().trim())
                );
                dataService.addOrUpdateTrain(t);
                refreshAdminTable();
                refreshStations();
                showAlert(Alert.AlertType.INFORMATION, "Saved", "Train " + t.getTrainNo() + " saved.");
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please check all numeric fields.");
            }
        });

        Button reloadBtn = new Button("Reload Trains from JSON");
        reloadBtn.getStyleClass().add("secondary-button");
        reloadBtn.setOnAction(e -> {
            dataService.reloadSamples();
            refreshAdminTable();
            refreshStations();
        });

        HBox adminActions = new HBox(8, addBtn, reloadBtn);

        // Current trains table (simple ListView for admin too for consistency)
        ListView<Train> adminList = new ListView<>();
        adminList.setPrefHeight(280);
        adminList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Train t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setText(null); return; }
                setText(t.getTrainNo() + " | " + t.getName() + " | " + t.getSource() + "→" + t.getDestination() +
                        " | SL:" + t.getAvailableSeats().get("SL"));
            }
        });

        adminList.setOnMouseClicked(e -> {
            Train sel = adminList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                // prefill form
                // (omitted full two-way for brevity in this version, user can manually edit)
            }
        });

        container.getChildren().addAll(title, form, adminActions, new Label("Current Trains:"), adminList);
        VBox.setVgrow(adminList, Priority.ALWAYS);

        // load initial
        Platform.runLater(() -> {
            adminList.setItems(FXCollections.observableArrayList(dataService.getAllTrains()));
        });

        return container;
    }

    private void refreshAdminTable() {
        // Since admin tab uses local ListView, we can find it but for simplicity just reload data
        // In real would use better architecture; here we refresh search results if open
        if (resultsListView != null) {
            resultsListView.refresh();
        }
    }

    private void refreshStations() {
        if (fromBox == null || toBox == null) return;
        List<String> stations = dataService.getAllStations();
        fromBox.setItems(FXCollections.observableArrayList(stations));
        toBox.setItems(FXCollections.observableArrayList(stations));
        if (!stations.isEmpty()) {
            fromBox.setValue(stations.get(0));
            toBox.setValue(stations.size() > 1 ? stations.get(1) : stations.get(0));
        }
    }

    private Node buildStatusBar() {
        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-bar");
        return statusLabel;
    }

    private void updateStatus() {
        if (statusLabel == null) return;
        int tCount = dataService.getAllTrains().size();
        int bCount = dataService.getAllBookings().size();
        statusLabel.setText("📁 Data: ~/.railway-reservation/  •  " + tCount + " trains loaded  •  " + bCount + " total bookings  •  JavaFX + Heavy CSS");
    }

    private void showTrainRouteDialog(Train train) {
        Stage routeDialog = new Stage();
        routeDialog.initModality(Modality.APPLICATION_MODAL);
        routeDialog.initOwner(primaryStage);
        routeDialog.setTitle("Full Route • " + train.getTrainNo() + " " + train.getName());

        VBox box = new VBox(8);
        box.setPadding(new Insets(16));

        Label title = new Label("All Stops (" + train.getSource() + " → " + train.getDestination() + ")");
        title.getStyleClass().add("irctc-section-title");

        ListView<ScheduleStop> stopsList = new ListView<>();
        List<ScheduleStop> sched = train.getSchedule();
        if (sched == null || sched.isEmpty()) {
            ScheduleStop start = new ScheduleStop(train.getSource(), train.getDeparture());
            ScheduleStop end = new ScheduleStop(train.getDestination(), train.getArrival());
            stopsList.getItems().addAll(start, end);
        } else {
            stopsList.getItems().addAll(sched);
        }

        stopsList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ScheduleStop stop, boolean empty) {
                super.updateItem(stop, empty);
                if (empty || stop == null) {
                    setText(null);
                    return;
                }
                String time = (stop.getTime() != null && !stop.getTime().isBlank()) ? stop.getTime() : "--:--";
                setText("• " + stop.getStation() + "   " + time);
            }
        });
        stopsList.setPrefHeight(Math.min(420, 60 + stopsList.getItems().size() * 22));

        Label note = new Label("Times are as per current timetable data.");
        note.setStyle("-fx-text-fill:#666; -fx-font-size:11px;");

        box.getChildren().addAll(title, stopsList, note);

        Scene scene = new Scene(box, 420, 480);
        var cssUrl = getClass().getResource("/styles/railway-reservation.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        routeDialog.setScene(scene);
        routeDialog.showAndWait();
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        if (mainRoot != null) {
            if (isDarkTheme) {
                mainRoot.setStyle("-fx-background-color: #0a192f;");
            } else {
                mainRoot.setStyle("-fx-background-color: #f8f9fa;");
            }
        }
    }
}
