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

import javafx.animation.*;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;

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
    private ListView<Train> adminListView;
    private ListView<Booking> bookingsListView;
    private ListView<String> liveStatusListView;
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

    // Phase 0+ fields for new features
    private Node liveView;
    private String currentTheme = "dark";
    private boolean highContrast = false;
    private ProgressIndicator searchProgress;
    private Popup stationPopup;
    private ListView<String> suggestionListView;
    private ComboBox<String> coachCombo;
    private ListView<Passenger> passengerListView;
    private Map<String, String> popularRoutes = Map.of(
        "Delhi → Howrah", "New Delhi|Howrah",
        "Mumbai → Chennai", "Mumbai Central|Chennai Central",
        "NDLS → PNBE", "New Delhi|Patna",
        "Agra → Varanasi", "Agra Cantt.|Varanasi"
    );
    private List<Button> navTabButtons = new ArrayList<>();
    private Circle userAvatar;
    private ContextMenu avatarContextMenu;
    private Label avatarInitialLabel;
    private StackPane avatarStack;

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

        // Phase 0 init
        applyTheme(currentTheme);
        addKeyboardShortcuts(scene);

        // Initial load
        refreshStations();
        updateStatus();

        // NEW: Show the first login window (prominent but non-blocking)
        // Main search UI is already visible underneath
        Platform.runLater(this::createAndShowInitialLoginWindow);
    }

    private Node buildIRCTCHeader() {
        HBox header = new HBox(12);
        header.setPadding(new Insets(8, 20, 8, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("irctc-header");

        // Left: Logo
        Label logo = new Label("IRCTC");
        logo.getStyleClass().add("irctc-logo");
        Label tag = new Label("Rail Connect");
        tag.getStyleClass().add("irctc-tag");
        HBox leftBox = new HBox(logo, tag);
        leftBox.setAlignment(Pos.CENTER_LEFT);

        // Center: Proper top nav tabs (actions resolve views lazily)
        HBox navTabs = new HBox(4);
        navTabs.setAlignment(Pos.CENTER);
        String[] tabLabels = {"🔍 Search", "🎫 My Bookings", "🚄 Live Trains", "🛠 Admin"};
        String[] tabIds = {"search", "bookings", "live", "admin"};
        navTabButtons.clear();
        for (int i = 0; i < tabLabels.length; i++) {
            Button tab = new Button(tabLabels[i]);
            tab.getStyleClass().add("nav-tab");
            if (i == 0) tab.getStyleClass().add("nav-tab-active");
            final String sid = tabIds[i];
            tab.setOnAction(e -> {
                if ("bookings".equals(sid)) {
                    requireLoginForAction(() -> switchToSection(sid));
                } else {
                    switchToSection(sid);
                }
            });
            navTabButtons.add(tab);
            navTabs.getChildren().add(tab);
        }

        Region centerGrow = new Region();
        HBox.setHgrow(centerGrow, Priority.ALWAYS);

        // Right: Lang, Theme, Avatar + dropdown
        Label lang = new Label("EN | HI");
        lang.getStyleClass().add("lang-label");

        Button themeToggle = new Button("🌓");
        themeToggle.getStyleClass().add("nav-link");
        themeToggle.setOnAction(e -> toggleTheme());
        addTooltip(themeToggle, "Cycle themes (Dark / Light / Classic)");

        // Avatar circle
        userAvatar = new Circle(14);
        userAvatar.setFill(Color.web("#ff9933"));
        userAvatar.setStroke(Color.web("#003366"));
        userAvatar.setStrokeWidth(1.5);
        avatarInitialLabel = new Label(currentUser.substring(0, 1).toUpperCase());
        avatarInitialLabel.setStyle("-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:12px;");
        avatarStack = new StackPane(userAvatar, avatarInitialLabel);
        avatarStack.setOnMouseClicked(e -> showAvatarContextMenu(avatarStack, e));
        addTooltip(avatarStack, "Account • " + currentUser);

        // Keep legacy profileMenu for refresh etc (hidden, actions moved to avatar menu)
        profileMenu = new MenuButton("");
        profileMenu.setVisible(false);
        profileMenu.setManaged(false);

        HBox rightBox = new HBox(10, lang, themeToggle, avatarStack);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        header.getChildren().addAll(leftBox, navTabs, centerGrow, rightBox);
        updateAvatarDisplay();
        return header;
    }

    private void updateUserDisplay() {
        if (profileMenu != null) {
            profileMenu.setText("👤 " + currentUser + ("admin".equals(currentUserRole) ? " (admin)" : ""));
        }
    }

    private void updateAvatarDisplay() {
        if (avatarInitialLabel != null) {
            avatarInitialLabel.setText(currentUser.substring(0, 1).toUpperCase());
        }
        if (avatarStack != null) {
            addTooltip(avatarStack, "Account • " + currentUser +
                ("admin".equals(currentUserRole) ? " (admin)" : ""));
        }
    }

    private void setCurrentUser(String name, String role) {
        currentUser = name;
        currentUserRole = role;
        updateUserDisplay();
        updateAvatarDisplay();
        userRepository.updateLastLogin(name);

        boolean wasShowingBookings = (bookingsView != null && mainContentArea != null &&
            mainContentArea.getChildren().contains(bookingsView));
        bookingsView = buildBookingsView();
        if (wasShowingBookings && mainContentArea != null) {
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(bookingsView);
        }
        Platform.runLater(this::refreshBookingsView);
    }

    private void showAvatarContextMenu(StackPane anchor, javafx.scene.input.MouseEvent e) {
        if (avatarContextMenu == null) {
            avatarContextMenu = new ContextMenu();
            MenuItem loginItem = new MenuItem("Login / Register");
            loginItem.setOnAction(ev -> showUserSelectionDialog());
            MenuItem profileItem = new MenuItem("Profile");
            profileItem.setOnAction(ev -> {
                Dialog<Void> pdlg = new Dialog<>();
                pdlg.setTitle("Profile");
                VBox pv = new VBox(8, new Label("User: " + currentUser), new Label("Role: " + currentUserRole), new Label("Last login tracked in DB"));
                pv.setPadding(new Insets(16));
                pdlg.getDialogPane().setContent(pv);
                pdlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                pdlg.showAndWait();
            });
            MenuItem settingsItem = new MenuItem("Settings");
            settingsItem.setOnAction(ev -> showSettingsDialog());
            MenuItem bookingsItem = new MenuItem("My Bookings");
            bookingsItem.setOnAction(ev -> {
                requireLoginForAction(() -> {
                    switchToView(bookingsView);
                    updateActiveNav("bookings");
                });
            });
            MenuItem liveItem = new MenuItem("Live Trains");
            liveItem.setOnAction(ev -> { switchToView(liveView); updateActiveNav("live"); });
            MenuItem adminItem = new MenuItem("Admin Panel");
            adminItem.setOnAction(ev -> { switchToView(adminView); updateActiveNav("admin"); });
            MenuItem refreshItem = new MenuItem("⟳ Refresh Data");
            refreshItem.setOnAction(ev -> {
                dataService.load();
                refreshStations();
                updateStatus();
                if (resultsListView != null) resultsListView.getItems().clear();
                if (bookingsListView != null) refreshBookingsView();
            });
            MenuItem logoutItem = new MenuItem("Logout");
            logoutItem.setOnAction(ev -> {
                setCurrentUser("Guest", "user");
                applyTheme(currentTheme);
            });
            avatarContextMenu.getItems().addAll(loginItem, profileItem, settingsItem, new SeparatorMenuItem(),
                    bookingsItem, liveItem, adminItem, new SeparatorMenuItem(), refreshItem, logoutItem);
        }
        avatarContextMenu.show(anchor, e.getScreenX(), e.getScreenY());
    }

    private void showSettingsDialog() {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Settings");
        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setPrefWidth(320);

        Label t = new Label("Theme");
        ComboBox<String> themeBox = new ComboBox<>();
        themeBox.getItems().addAll("dark", "light", "classic");
        themeBox.setValue(currentTheme);
        themeBox.setOnAction(e -> applyTheme(themeBox.getValue()));

        CheckBox hc = new CheckBox("High Contrast Mode");
        hc.setSelected(highContrast);
        hc.setOnAction(e -> {
            highContrast = hc.isSelected();
            applyTheme(currentTheme);
        });

        content.getChildren().addAll(t, themeBox, hc, new Label("Keyboard: Ctrl+K focuses search • Esc closes dialogs"));
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.showAndWait();
    }

    private void promptChangeUser() {
        showUserSelectionDialog();
    }

    private void showUserSelectionDialog() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Login or Register");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);

        Node authForm = buildAuthForm(name -> {
            dialog.setResult(true);
            dialog.close();
        }, () -> dialog.close());

        dialog.getDialogPane().setContent(authForm);
        dialog.showAndWait();
    }

    /**
     * Reusable authentication form (Login + Register).
     * Used by both the legacy dialog and the new first-login window.
     */
    private Node buildAuthForm(java.util.function.Consumer<String> onLoginSuccess, Runnable onCancel) {
        VBox content = new VBox(14);
        content.setPadding(new Insets(18));
        content.setPrefWidth(380);

        // --- Login section ---
        Label loginTitle = new Label("Login");
        loginTitle.getStyleClass().add("irctc-section-title");
        loginTitle.setStyle("-fx-font-size:14px; -fx-font-weight:bold;");

        ComboBox<String> userCombo = new ComboBox<>();
        List<String> users = dataService.getAllUsernames();
        if (users.isEmpty()) users.add("admin");
        userCombo.getItems().addAll(users);
        userCombo.setEditable(true);
        userCombo.setValue(currentUser.equals("Guest") ? "admin" : currentUser);
        userCombo.setPrefWidth(220);

        PasswordField loginPw = new PasswordField();
        loginPw.setPromptText("Password");
        loginPw.setPrefWidth(220);

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().add("primary-button");
        Label loginStatus = new Label();
        loginStatus.setStyle("-fx-text-fill:#c00; -fx-font-size:11px;");

        HBox loginRow = new HBox(8, userCombo, loginPw, loginBtn);
        loginRow.setAlignment(Pos.CENTER_LEFT);

        VBox loginBox = new VBox(6, loginTitle, loginRow, loginStatus);

        // --- Register section ---
        Label regTitle = new Label("Create New Account");
        regTitle.getStyleClass().add("irctc-section-title");
        regTitle.setStyle("-fx-font-size:14px; -fx-font-weight:bold;");

        TextField regUser = new TextField();
        regUser.setPromptText("Choose username");
        regUser.setPrefWidth(220);

        PasswordField regPw = new PasswordField();
        regPw.setPromptText("Password (min 4 chars)");
        regPw.setPrefWidth(220);

        PasswordField regPw2 = new PasswordField();
        regPw2.setPromptText("Confirm password");
        regPw2.setPrefWidth(220);

        Button regBtn = new Button("Register & Login");
        regBtn.getStyleClass().add("primary-button");
        Label regStatus = new Label();
        regStatus.setStyle("-fx-text-fill:#c00; -fx-font-size:11px;");

        VBox regBox = new VBox(6, regTitle,
                new HBox(8, regUser, regPw, regPw2, regBtn),
                regStatus);

        content.getChildren().addAll(loginBox, new Separator(), regBox);

        // Login action
        loginBtn.setOnAction(e -> {
            String u = userCombo.getValue();
            String p = loginPw.getText();
            if (u == null || u.isBlank() || p == null || p.isBlank()) {
                loginStatus.setText("Enter username and password");
                return;
            }
            boolean ok = userRepository.authenticate(u.trim(), p);
            if (ok) {
                String name = u.trim();
                setCurrentUser(name, userRepository.getRole(name));
                if (onLoginSuccess != null) onLoginSuccess.accept(name);
            } else {
                loginStatus.setText("Invalid credentials");
            }
        });

        // Register action
        regBtn.setOnAction(e -> {
            String u = regUser.getText();
            String p1 = regPw.getText();
            String p2 = regPw2.getText();
            if (u == null || u.trim().isEmpty() || p1 == null || p1.length() < 4) {
                regStatus.setText("Username + password (≥4 chars) required");
                return;
            }
            if (!p1.equals(p2)) {
                regStatus.setText("Passwords do not match");
                return;
            }
            String name = u.trim();
            if (userRepository.userExists(name)) {
                regStatus.setText("Username already taken");
                return;
            }
            userRepository.createUser(name, p1, "user");
            setCurrentUser(name, "user");
            if (onLoginSuccess != null) onLoginSuccess.accept(name);
        });

        loginPw.setOnAction(e -> loginBtn.fire());
        regPw2.setOnAction(e -> regBtn.fire());

        return content;
    }

    private Node buildMainContent() {
        searchView = buildSearchView();
        bookingsView = buildBookingsView();
        adminView = buildAdminView();
        liveView = buildLiveView();

        mainContentArea = new StackPane(searchView);

        VBox sidebar = buildSidebar();

        ScrollPane contentScroll = new ScrollPane(mainContentArea);
        contentScroll.setFitToWidth(true);
        contentScroll.setFitToHeight(true);
        contentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        contentScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        HBox mainLayout = new HBox();
        mainLayout.getChildren().addAll(sidebar, contentScroll);
        HBox.setHgrow(contentScroll, Priority.ALWAYS);

        return mainLayout;
    }

    private Node buildLiveView() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.getStyleClass().add("live-container");
        Label title = new Label("🚄 Live Train Status");
        title.getStyleClass().add("section-title");
        Label sub = new Label("Simulated real-time status using all trains from JSON. Refresh updates simulations.");
        sub.setStyle("-fx-text-fill:#64748b;");
        Button refresh = new Button("⟳ Refresh Live Status");
        refresh.getStyleClass().add("primary-button");
        liveStatusListView = new ListView<>();
        refresh.setOnAction(e -> refreshLiveStatuses());
        liveStatusListView.setPrefHeight(420);
        box.getChildren().addAll(title, sub, refresh, liveStatusListView);
        refreshLiveStatuses();
        return box;
    }

    private void refreshLiveStatuses() {
        if (liveStatusListView == null || dataService == null) return;
        java.util.List<Train> allTrains = dataService.getAllTrains();
        java.util.List<String> statuses = new java.util.ArrayList<>();
        String[] statusOpts = {"On Time", "Delayed 5m", "Delayed 12m", "Arrived", "Boarding", "En Route"};
        java.util.Random rnd = new java.util.Random();
        for (Train t : allTrains) {
            String occ = (50 + rnd.nextInt(50)) + "%";
            String st = statusOpts[rnd.nextInt(statusOpts.length)];
            String src = (t.getSource() != null && t.getSource().length() > 10) ? t.getSource().substring(0, 8) + ".." : (t.getSource() != null ? t.getSource() : "?");
            String dst = (t.getDestination() != null && t.getDestination().length() > 10) ? t.getDestination().substring(0, 8) + ".." : (t.getDestination() != null ? t.getDestination() : "?");
            String line = t.getTrainNo() + " " + t.getName() + " • " + src + " → " + dst + " • " + st + " • " + occ + " occupancy";
            statuses.add(line);
        }
        liveStatusListView.setItems(FXCollections.observableArrayList(statuses));
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.setPrefWidth(200);
        sidebar.getStyleClass().add("sidebar");

        Button btnSearch = createNavButton("🚂  Search Trains", true);
        btnSearch.setOnAction(e -> { switchToView(searchView); updateActiveNav("search"); });

        Button btnBookings = createNavButton("🎫  My Bookings", false);
        btnBookings.setOnAction(e -> {
            requireLoginForAction(() -> {
                switchToView(bookingsView);
                updateActiveNav("bookings");
            });
        });

        Button btnLive = createNavButton("🚄  Live Trains", false);
        btnLive.setOnAction(e -> { switchToView(liveView); updateActiveNav("live"); });

        Button btnAdmin = createNavButton("🛠️  Manage Trains", false);
        btnAdmin.setOnAction(e -> { switchToView(adminView); updateActiveNav("admin"); });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(btnSearch, btnBookings, btnLive, btnAdmin, spacer);
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

    private void switchToSection(String section) {
        switch (section) {
            case "search" -> switchToView(searchView);
            case "bookings" -> switchToView(bookingsView);
            case "live" -> switchToView(liveView);
            case "admin" -> switchToView(adminView);
            default -> {}
        }
        updateActiveNav(section);
    }

    private void updateActiveNav(String section) {
        // Update sidebar buttons (simple re-style via lookup or recreate - basic impl)
        // For header tabs: set active class
        for (Button b : navTabButtons) {
            b.getStyleClass().remove("nav-tab-active");
            String t = b.getText().toLowerCase();
            if ((section.equals("search") && t.contains("search")) ||
                (section.equals("bookings") && t.contains("booking")) ||
                (section.equals("live") && t.contains("live")) ||
                (section.equals("admin") && t.contains("admin"))) {
                b.getStyleClass().add("nav-tab-active");
            }
        }
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
        fromBox.setAccessibleText("From station - type or select origin");
        toBox = new ComboBox<>();
        toBox.setEditable(true);
        toBox.setPrefWidth(270);
        toBox.setAccessibleText("To station - type or select destination");
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
        addTooltip(flexibleDatesCheck, "Search ±3 days around selected date");
        availableSeatsOnlyCheck = new CheckBox("Search for trains with available seats only");
        CheckBox nearbyCheck = new CheckBox("Search nearby stations");
        addTooltip(nearbyCheck, "Include nearby stations in results (demo)");
        HBox filterBox = new HBox(20, flexibleDatesCheck, availableSeatsOnlyCheck, nearbyCheck);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        gp.add(filterBox, 0, 3, 5, 1);

        // Popular routes chips
        HBox chips = new HBox(6);
        chips.setAlignment(Pos.CENTER_LEFT);
        for (Map.Entry<String, String> entry : popularRoutes.entrySet()) {
            Label chip = new Label(entry.getKey());
            chip.getStyleClass().add("route-chip");
            chip.setOnMouseClicked(ev -> {
                String[] parts = entry.getValue().split("\\|");
                fromBox.setValue(parts[0]);
                toBox.setValue(parts[1]);
                performSearch();
            });
            addTooltip(chip, "Quick search " + entry.getKey());
            chips.getChildren().add(chip);
        }
        card.getChildren().add(chips);

        fromBox.focusedProperty().addListener((obs, was, is) -> { if (is) fromBox.show(); });
        toBox.focusedProperty().addListener((obs, was, is) -> { if (is) toBox.show(); });

        // Basic autocomplete enhancement using popup suggestions
        setupAutocomplete(fromBox);
        setupAutocomplete(toBox);

        card.getChildren().add(gp);
        Button searchBtn = new Button("🔍 SEARCH TRAINS");
        searchBtn.getStyleClass().add("irctc-search-btn");
        searchBtn.setOnAction(e -> performSearch());
        addTooltip(searchBtn, "Search trains (or press Ctrl+K)");
        searchBtn.setAccessibleText("Search for trains between stations on selected date");
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
        searchProgress = new ProgressIndicator();
        searchProgress.setPrefSize(18, 18);
        searchProgress.setVisible(false);
        HBox resultsHeader = new HBox(resultsTitle, resultsCountLabel, searchLoadingLabel, searchProgress, sortCombo);
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
                String dur = "N/A";
                try {
                    if (!"TBD".equals(dep) && !"TBD".equals(arr)) {
                        java.time.LocalTime d = java.time.LocalTime.parse(dep);
                        java.time.LocalTime a = java.time.LocalTime.parse(arr);
                        long mins = java.time.Duration.between(d, a).toMinutes();
                        if (mins < 0) mins += 24*60;
                        dur = (mins/60) + "h " + (mins%60) + "m";
                    }
                } catch (Exception ignore) {}
                String timesText = ("TBD".equals(dep) || "TBD".equals(arr))
                    ? "Timings: Check IRCTC (real data coming soon)"
                    : "⏰ " + dep + " → " + arr + "  (" + dur + ")";
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

                // Running days dots (Mon-Sun)
                HBox dayDots = new HBox(2);
                String freq = train.getFrequency().toUpperCase();
                String[] days = {"M","T","W","T","F","S","S"};
                for (int i=0; i<7; i++) {
                    Label dot = new Label(days[i]);
                    dot.setStyle("-fx-font-size:8px; -fx-padding:1 3; -fx-background-radius:8; -fx-text-fill:#0a192f;");
                    boolean runs = freq.contains(days[i]) || freq.contains("DAILY") || freq.contains("EXCEPT");
                    dot.setStyle(dot.getStyle() + (runs ? " -fx-background-color:#ff9933;" : " -fx-background-color:#475569;"));
                    dayDots.getChildren().add(dot);
                }
                pills.getChildren().add(dayDots);

                info.getChildren().addAll(trainId, route, times, pills);

                Region spacer2 = new Region();
                HBox.setHgrow(spacer2, Priority.ALWAYS);

                Button bookBtn = new Button("Book Now →");
                bookBtn.getStyleClass().add("primary-button");
                bookBtn.setOnAction(ev -> {
                    requireLoginForAction(() -> {
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
        if (searchProgress != null) searchProgress.setVisible(true);
        List<Train> results = dataService.searchTrains(from, to, date);
        // Flexible dates support
        boolean flex = flexibleDatesCheck != null && flexibleDatesCheck.isSelected();
        if (flex && date != null) {
            for (int d = -3; d <= 3; d++) {
                if (d == 0) continue;
                LocalDate alt = date.plusDays(d);
                List<Train> more = dataService.searchTrains(from, to, alt);
                for (Train t : more) {
                    if (results.stream().noneMatch(r -> r.getTrainNo().equals(t.getTrainNo()))) {
                        results.add(t);
                    }
                }
            }
        }
        // Nearby stations (simple hardcoded)
        boolean nearby = false; // placeholder, would need checkbox ref; for demo use flex logic or extend
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
        if (searchProgress != null) searchProgress.setVisible(false);
        if (results.isEmpty()) {
            // Rich empty state instead of alert
            resultsListView.setPlaceholder(new Label("🚂 No trains found. Try flexible dates, nearby stations or popular chips above."));
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

        // Coach selector (Phase 3)
        HBox coachRow = new HBox(8);
        coachRow.setAlignment(Pos.CENTER_LEFT);
        Label coachLbl = new Label("Coach:");
        coachCombo = new ComboBox<>();
        coachCombo.getItems().addAll("A1", "A2", "B1", "B2", "S1", "S2");
        coachCombo.setValue("A1");
        coachCombo.setPrefWidth(80);
        coachRow.getChildren().addAll(coachLbl, coachCombo);

        // Num passengers
        HBox numRow = new HBox(8);
        numRow.setAlignment(Pos.CENTER_LEFT);
        Label numLbl = new Label("Passengers:");
        ComboBox<Integer> numBox = new ComboBox<>();
        numBox.getItems().addAll(1,2,3,4,5,6);
        numBox.setValue(1);

        numRow.getChildren().addAll(numLbl, numBox);

        Label selectionInfo = new Label("Selected: 0 / 1");
        Label fareLabel = new Label("Total Fare: ₹0\n(Base + GST + Resv)");
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

            int available = (train.getAvailableSeats() != null) ? train.getAvailableSeats().getOrDefault(cls, 0) : 0;
            int booked = dataService.getAllBookings().stream()
                    .filter(b -> b.getTrainNo().equals(train.getTrainNo()) && cls.equals(b.getCls()))
                    .mapToInt(b -> b.getPassengers().size()).sum();
            int capacity = booked + available;

            if (capacity <= 0) {
                Label none = new Label("No seats available");
                none.getStyleClass().add("no-seats");
                seatGrid.add(none, 0, 0);
                return;
            }

            int cols = 6;
            for (int i = 0; i < capacity; i++) {
                int r = i / cols;
                int c = i % cols;
                ToggleButton tb = new ToggleButton(cls + (i + 1));
                tb.setPrefSize(30, 18);
                if (i < booked) {
                    tb.getStyleClass().addAll("seat", "seat-booked");
                    tb.setDisable(true);
                } else {
                    tb.getStyleClass().addAll("seat", "seat-available");
                    // Special quota highlights (demo)
                    if ((i + 1) % 5 == 0) {
                        tb.getStyleClass().add("seat-ladies");
                        tb.setTooltip(new Tooltip("Ladies quota"));
                    } else if ((i + 1) % 7 == 0) {
                        tb.getStyleClass().add("seat-senior");
                        tb.setTooltip(new Tooltip("Senior / Divyangjan priority"));
                    }
                    tb.setOnAction(ev -> updateSelectionInfo(selectionInfo, seatToggles, numBox, fareLabel, train, classGroup));
                    seatToggles.add(tb);
                }
                seatGrid.add(tb, c, r);
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
            Button copyBtn = new Button("Copy details from previous passenger");
            copyBtn.getStyleClass().add("secondary-button");
            copyBtn.setOnAction(ev -> {
                if (n > 1 && paxContainer.getChildren().size() > 2) {
                    HBox prev = (HBox) paxContainer.getChildren().get(paxContainer.getChildren().size()-1);
                    HBox last = (HBox) paxContainer.getChildren().get(paxContainer.getChildren().size()-1);
                    // simplistic copy from second last if possible
                    if (paxContainer.getChildren().size() > 2) {
                        HBox secondLast = (HBox) paxContainer.getChildren().get(paxContainer.getChildren().size()-2);
                        Object ud = secondLast.getUserData();
                        if (ud instanceof java.util.Map) {
                            java.util.Map<?,?> m = (java.util.Map<?,?>) ud;
                            TextField pn = (TextField) m.get("name");
                            if (last.getUserData() instanceof java.util.Map) {
                                java.util.Map<?,?> lm = (java.util.Map<?,?>) last.getUserData();
                                ((TextField)lm.get("name")).setText(pn.getText());
                                ((TextField)lm.get("age")).setText(((TextField)m.get("age")).getText());
                                ((ComboBox)lm.get("gender")).setValue(((ComboBox)m.get("gender")).getValue());
                                ((ComboBox)lm.get("berth")).setValue(((ComboBox)m.get("berth")).getValue());
                            }
                        }
                    }
                }
            });
            paxContainer.getChildren().add(copyBtn);
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
                java.util.Map<String, Node> meta = new java.util.HashMap<>();
                meta.put("name", nameF);
                meta.put("age", ageF);
                meta.put("gender", genderC);
                meta.put("berth", berthC);
                row.setUserData(meta);
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

            // Collect passengers from form (use userData to avoid fragile indexing)
            List<Passenger> paxList = new ArrayList<>();
            int idx = 0;
            for (Node node : paxContainer.getChildren()) {
                if (node instanceof HBox) {
                    HBox row = (HBox) node;
                    Object ud = row.getUserData();
                    if (ud instanceof java.util.Map) {
                        java.util.Map<?,?> meta = (java.util.Map<?,?>) ud;
                        TextField nameF = (TextField) meta.get("name");
                        TextField ageF = (TextField) meta.get("age");
                        ComboBox<String> gC = (ComboBox<String>) meta.get("gender");
                        ComboBox<String> bC = (ComboBox<String>) meta.get("berth");
                        int ageVal;
                        try {
                            ageVal = Integer.parseInt(ageF.getText().trim());
                        } catch (NumberFormatException ex) {
                            showAlert(Alert.AlertType.WARNING, "Invalid Age", "Please enter a valid age for passenger " + (idx+1));
                            return;
                        }
                        paxList.add(new Passenger(nameF.getText().trim(), ageVal, gC.getValue(), bC.getValue()));
                    } else {
                        // fallback to older index-based parsing
                        TextField nameF = (TextField) row.getChildren().get(1);
                        TextField ageF = (TextField) row.getChildren().get(3);
                        ComboBox<String> gC = (ComboBox<String>) row.getChildren().get(5);
                        ComboBox<String> bC = (ComboBox<String>) row.getChildren().get(7);
                        int ageVal;
                        try {
                            ageVal = Integer.parseInt(ageF.getText().trim());
                        } catch (NumberFormatException ex) {
                            showAlert(Alert.AlertType.WARNING, "Invalid Age", "Please enter a valid age for passenger " + (idx+1));
                            return;
                        }
                        paxList.add(new Passenger(nameF.getText().trim(), ageVal, gC.getValue(), bC.getValue()));
                    }
                    idx++;
                    if (idx >= num) break;
                }
            }

            String jDate = datePicker.getValue() != null ? datePicker.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE) : LocalDate.now().plusDays(1).toString();

            double fare = dataService.calculateFare(train, selectedClass, num);

            // Collect selected seat numbers
            java.util.List<String> selectedSeats = seatToggles.stream().filter(ToggleButton::isSelected).map(ToggleButton::getText).collect(java.util.stream.Collectors.toList());

            // Close booking details dialog and open payment gateway
            dialog.close();
            Platform.runLater(() -> openPaymentGateway(train, selectedClass, num, paxList, jDate, fare, selectedSeats));
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

        // Berth legend (Phase 3)
        HBox berthLegend = new HBox(10);
        berthLegend.setAlignment(Pos.CENTER_LEFT);
        String[] berths = {"Lower", "Middle", "Upper", "Side Lower", "Side Upper"};
        String[] berthColors = {"#22c55e", "#3b82f6", "#f59e0b", "#a855f7", "#64748b"};
        for (int i = 0; i < berths.length; i++) {
            Label l = new Label("■ " + berths[i]);
            l.setStyle("-fx-text-fill:" + berthColors[i] + "; -fx-font-size:10px;");
            berthLegend.getChildren().add(l);
        }

        root.getChildren().addAll(
            wizardHeader,
            new Label("Booking for: " + currentUser),
            summary,
            classRow,
            coachRow,
            numRow,
            seatTitle, seatGrid, selectionInfo, fareLabel,
            berthLegend,
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
        double base = dataService.calculateFare(train, cls, max);
        double gst = Math.round(base * 0.05 * 100.0) / 100.0;
        double resv = max * 60.0; // reservation charge approx
        double total = base + gst + resv;
        fareLbl.setText(String.format("Base: ₹%.0f + GST: ₹%.0f + Resv: ₹%.0f = Total: ₹%.0f", base, gst, resv, total));
    }

    private void showSuccessPNR(Train train, String cls, List<Passenger> pax, String journeyDate) {
        // Find the latest booking for this user/train (bookings are sorted DESC by booked_at)
        List<Booking> userBookings = dataService.getBookingsForUser(currentUser);
        Booking latest = userBookings.isEmpty() ? null : userBookings.get(0);

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
        return "RAILTXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * Opens a beautiful simulated payment gateway (IRCTC style).
     * On successful payment, books the ticket and shows receipt + PNR.
     */
    private void openPaymentGateway(Train train, String cls, int numPassengers, List<Passenger> paxList,
                                    String journeyDate, double totalFare, java.util.List<String> seatNumbers) {

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

                    Canvas qr = createFakeQRCanvas(80);
                    qr.setOnMouseClicked(e -> showAlert(Alert.AlertType.INFORMATION, "UPI", "Scanned (demo)"));

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
                    boolean booked = dataService.bookTicket(train, cls, numPassengers, paxList, currentUser, journeyDate, method, txnId, seatNumbers);

                    if (booked) {
                        payDialog.close();
                        Platform.runLater(() -> {
                            showPaymentReceipt(train, cls, paxList, journeyDate, totalFare, method, txnId);
                            // PNR / ticket confirmation now reliably pops after the receipt is closed
                            showSuccessPNR(train, cls, paxList, journeyDate);
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
        String gateway = System.getenv("PAYMENT_GATEWAY_URL");
        if (gateway != null && !gateway.isBlank()) {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("method", method);
                if (formData != null && formData.length > 0 && formData[0] instanceof TextField) {
                    String card = ((TextField) formData[0]).getText().replaceAll("\\s", "");
                    payload.put("card_last4", card.length() > 4 ? card.substring(card.length()-4) : card);
                }
                String body = mapper.writeValueAsString(payload);
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(new java.net.URI(gateway))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                        .build();
                java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(resp.body());
                if (node.has("success")) return node.get("success").asBoolean(false);
                if (node.has("status")) {
                    String s = node.get("status").asText();
                    return "ok".equalsIgnoreCase(s) || "success".equalsIgnoreCase(s);
                }
                return false;
            } catch (Exception ex) {
                System.err.println("Payment gateway call failed: " + ex.getMessage());
                return false;
            }
        }
        // Fallback demo behaviour
        if ("Credit Card".equals(method) && formData != null && formData.length > 0) {
            String card = ((TextField) formData[0]).getText().replaceAll("\\s", "");
            if (card.startsWith("4242")) return true;
            if (card.startsWith("4000")) return false;
            return Math.random() > 0.15;
        }
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

        Label note = new Label("Your PNR / ticket will be shown after closing this receipt.");
        note.getStyleClass().add("note");
        note.setStyle("-fx-font-size:11px; -fx-text-fill:#666;");

        Button done = new Button("Done");
        done.getStyleClass().add("primary-button");
        done.setOnAction(e -> receipt.close());

        HBox btns = new HBox(10, note, done);
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
        // If guest, show friendly login-required placeholder instead of empty list
        if ("Guest".equalsIgnoreCase(currentUser)) {
            return buildGuestBookingsPlaceholder();
        }

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
                        if (resultsListView != null) performSearch();
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
                String status = b.getStatus() != null ? b.getStatus() : "ACTIVE";
                Label statusBadge = new Label(status);
                statusBadge.setStyle("-fx-background-color:" + ("CANCELLED".equals(status) ? "#ef4444" : "#22c55e") + "; -fx-text-fill:white; -fx-padding:1 6; -fx-background-radius:4; -fx-font-size:9px;");
                Label main = new Label(b.getTrainName() + " (" + b.getCls() + ") • " + b.getJourneyDate());
                Label pax = new Label(b.getPassengers().size() + " pax • ₹" + String.format("%.0f", b.getTotalFare()) + " • " + b.getUserName());
                info.getChildren().addAll(pnr, statusBadge, main, pax);

                if (b.getPaymentMethod() != null && b.getTransactionId() != null) {
                    Label payInfo = new Label("Paid via " + b.getPaymentMethod() + " • " + b.getTransactionId());
                    payInfo.getStyleClass().add("payment-info-small");
                    info.getChildren().add(payInfo);
                }

                // Small QR per ticket
                Canvas qr = createFakeQRCanvas(28);
                qr.setOnMouseClicked(ev -> {
                    Stage qrDlg = new Stage();
                    qrDlg.initModality(Modality.APPLICATION_MODAL);
                    VBox v = new VBox(new Label("Scan at station (demo)"), createFakeQRCanvas(120));
                    v.setPadding(new Insets(10));
                    qrDlg.setScene(new Scene(v));
                    qrDlg.show();
                });

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
                            if (resultsListView != null) performSearch();
                        }
                    });
                });

                card.getChildren().addAll(info, qr, sp, cancel);
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
            if (!"admin".equalsIgnoreCase(currentUserRole)) {
                showAlert(Alert.AlertType.WARNING, "Access Denied", "Admin only.");
                return;
            }
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

        TextField adminSearch = new TextField();
        adminSearch.setPromptText("Search trains...");
        adminSearch.textProperty().addListener((obs, o, val) -> {
            if (adminListView != null) {
                String v = val.toLowerCase();
                List<Train> filtered = dataService.getAllTrains().stream()
                    .filter(t -> t.getTrainNo().toLowerCase().contains(v) || t.getName().toLowerCase().contains(v))
                    .collect(Collectors.toList());
                adminListView.setItems(FXCollections.observableArrayList(filtered));
            }
        });

        // Current trains table (simple ListView for admin too for consistency)
        adminListView = new ListView<>();
        adminListView.setPrefHeight(280);
        adminListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Train t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setText(null); return; }
                setText(t.getTrainNo() + " | " + t.getName() + " | " + t.getSource() + "→" + t.getDestination() +
                        " | SL:" + t.getAvailableSeats().get("SL"));
            }
        });

        adminListView.setOnMouseClicked(e -> {
            Train sel = adminListView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                // prefill form
                // (omitted full two-way for brevity in this version, user can manually edit)
            }
        });

        // Simple visual "chart" - occupancy bars (Phase 5)
        HBox chartBox = new HBox(4);
        chartBox.setAlignment(Pos.BOTTOM_LEFT);
        for (int i = 0; i < 5; i++) {
            Rectangle bar = new Rectangle(20, 10 + Math.random()*60);
            bar.setFill(Color.web(i % 2 == 0 ? "#ff9933" : "#0066b3"));
            chartBox.getChildren().add(bar);
        }
        Label chartLabel = new Label("Simulated train occupancy");

        container.getChildren().addAll(title, form, adminActions, adminSearch, new Label("Current Trains:"), adminListView, chartLabel, chartBox);
        VBox.setVgrow(adminListView, Priority.ALWAYS);

        // load initial
        Platform.runLater(() -> {
            adminListView.setItems(FXCollections.observableArrayList(dataService.getAllTrains()));
        });

        return container;
    }

    private void refreshAdminTable() {
        if (adminListView != null) {
            adminListView.setItems(FXCollections.observableArrayList(dataService.getAllTrains()));
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

    // ==================== PHASE 0 HELPERS ====================
    private void applyTheme(String theme) {
        currentTheme = theme;
        if (mainRoot == null) return;
        mainRoot.getStyleClass().removeAll("theme-dark", "theme-light", "theme-classic", "high-contrast");
        mainRoot.getStyleClass().add("theme-" + theme);
        if (highContrast) {
            mainRoot.getStyleClass().add("high-contrast");
        }
        // Update bg for classic/light fallbacks
        if ("light".equals(theme)) {
            mainRoot.setStyle("-fx-background-color: #f8f9fa;");
        } else if ("classic".equals(theme)) {
            mainRoot.setStyle("-fx-background-color: #e8f0f8;");
        } else {
            mainRoot.setStyle("-fx-background-color: #0a192f;");
        }
    }

    private void toggleHighContrast() {
        highContrast = !highContrast;
        applyTheme(currentTheme);
    }

    private void addTooltip(Node node, String text) {
        if (node != null) {
            Tooltip.install(node, new Tooltip(text));
        }
    }

    private Canvas createFakeQRCanvas(double size) {
        Canvas c = new Canvas(size, size);
        GraphicsContext g = c.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, size, size);
        g.setFill(Color.BLACK);
        double m = size / 25.0;
        // Finder patterns (3 corners)
        for (int[] corner : new int[][]{{1,1}, {1,19}, {19,1}}) {
            int x = corner[0]; int y = corner[1];
            g.fillRect(x*m, y*m, 7*m, 7*m);
            g.setFill(Color.WHITE); g.fillRect((x+1)*m, (y+1)*m, 5*m, 5*m);
            g.setFill(Color.BLACK); g.fillRect((x+2)*m, (y+2)*m, 3*m, 3*m);
        }
        // Random data modules for demo
        g.setFill(Color.BLACK);
        for (int i = 0; i < 80; i++) {
            int x = 4 + (int)(Math.random()*17);
            int y = 4 + (int)(Math.random()*17);
            if ((x < 9 && y < 9) || (x < 9 && y > 18) || (x > 18 && y < 9)) continue;
            g.fillRect(x*m, y*m, m*0.9, m*0.9);
        }
        return c;
    }

    private void createConfetti(Pane container) {
        if (container == null) return;
        for (int i = 0; i < 35; i++) {
            Circle dot = new Circle(3 + Math.random()*3);
            dot.setFill(Color.rgb((int)(Math.random()*255), (int)(Math.random()*200), 50 + (int)(Math.random()*150)));
            dot.setTranslateX(Math.random() * container.getWidth());
            dot.setTranslateY(-10);
            container.getChildren().add(dot);
            javafx.animation.TranslateTransition tt = new javafx.animation.TranslateTransition(Duration.millis(1200 + Math.random()*800), dot);
            tt.setToY(container.getHeight() + 20);
            tt.setToX(dot.getTranslateX() + (Math.random()-0.5)*80);
            tt.setOnFinished(e -> container.getChildren().remove(dot));
            tt.play();
        }
    }

    private void addKeyboardShortcuts(Scene scene) {
        if (scene == null) return;
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN), () -> {
            if (searchView != null) {
                switchToView(searchView);
                if (fromBox != null) fromBox.requestFocus();
            }
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), () -> {
            // Close top dialog if any (simplified: no-op for now, dialogs handle own)
        });
    }

    private void setupAutocomplete(ComboBox<String> box) {
        if (box == null) return;
        box.setEditable(true);
        // Simple filtering on edit
        box.getEditor().textProperty().addListener((obs, old, val) -> {
            if (val == null || val.isBlank()) {
                box.setItems(FXCollections.observableArrayList(dataService.getAllStations()));
                return;
            }
            String v = val.toLowerCase();
            List<String> filtered = dataService.getAllStations().stream()
                .filter(s -> s.toLowerCase().contains(v))
                .limit(8)
                .collect(Collectors.toList());
            box.setItems(FXCollections.observableArrayList(filtered));
            if (!box.isShowing()) box.show();
        });
    }

    // ============================================================
    // NEW: First Login Window + Login Enforcement (per plan)
    // ============================================================

    private Stage initialLoginStage;

    /**
     * Shows a prominent, non-modal first-login window on startup.
     * Main app (with full search) is already visible underneath.
     * Includes simplified search preview as requested.
     */
    private void createAndShowInitialLoginWindow() {
        if (initialLoginStage != null && initialLoginStage.isShowing()) return;

        initialLoginStage = new Stage();
        initialLoginStage.initOwner(primaryStage);
        initialLoginStage.initModality(Modality.NONE);
        initialLoginStage.setTitle("IRCTC Rail Connect – Welcome");
        initialLoginStage.setMinWidth(620);
        initialLoginStage.setMinHeight(520);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("login-window");

        // Top header
        VBox header = new VBox(4);
        header.setPadding(new Insets(16, 20, 12, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        Label h1 = new Label("Welcome to IRCTC Rail Connect");
        h1.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:#ff9933;");
        Label h2 = new Label("Login or Register to book tickets. Search is available for everyone.");
        h2.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:13px;");
        header.getChildren().addAll(h1, h2);
        root.setTop(header);

        // Center: Auth + Search Preview side-by-side
        HBox center = new HBox(16);
        center.setPadding(new Insets(10, 20, 10, 20));
        center.setAlignment(Pos.TOP_CENTER);

        // Left: Auth form
        Node auth = buildAuthForm(name -> {
            Platform.runLater(() -> {
                if (initialLoginStage != null) initialLoginStage.close();
            });
        }, () -> {});

        VBox authCard = new VBox(auth);
        authCard.getStyleClass().add("login-card");
        authCard.setPrefWidth(380);

        // Right: Simplified search preview
        VBox preview = new VBox(8);
        preview.setPrefWidth(260);
        preview.getStyleClass().add("login-preview-search");

        Label pTitle = new Label("Quick Search (Guest OK)");
        pTitle.setStyle("-fx-font-weight:bold; -fx-font-size:13px;");

        ComboBox<String> pFrom = new ComboBox<>();
        pFrom.getItems().addAll(dataService.getAllStations());
        pFrom.setEditable(true);
        pFrom.setPrefWidth(240);
        pFrom.setPromptText("From station");

        ComboBox<String> pTo = new ComboBox<>();
        pTo.getItems().addAll(dataService.getAllStations());
        pTo.setEditable(true);
        pTo.setPrefWidth(240);
        pTo.setPromptText("To station");

        Button pSearch = new Button("🔍 Search Trains Now");
        pSearch.getStyleClass().add("primary-button");
        pSearch.setMaxWidth(Double.MAX_VALUE);
        pSearch.setOnAction(e -> {
            if (pFrom.getValue() != null && pTo.getValue() != null) {
                fromBox.setValue(pFrom.getValue());
                toBox.setValue(pTo.getValue());
                switchToSection("search");
                performSearch();
                if (initialLoginStage != null) initialLoginStage.close();
            } else {
                showAlert(Alert.AlertType.WARNING, "Search", "Please select From and To stations.");
            }
        });

        preview.getChildren().addAll(pTitle, pFrom, pTo, pSearch);

        center.getChildren().addAll(authCard, preview);
        root.setCenter(center);

        // Bottom: Big Guest CTA
        HBox footer = new HBox();
        footer.setPadding(new Insets(12, 20, 16, 20));
        footer.setAlignment(Pos.CENTER);

        Button guestBtn = new Button("Continue as Guest – Just Search Trains");
        guestBtn.getStyleClass().add("secondary-button");
        guestBtn.setStyle("-fx-font-size:14px; -fx-padding:10 24;");
        guestBtn.setOnAction(e -> {
            if (initialLoginStage != null) initialLoginStage.close();
        });

        footer.getChildren().add(guestBtn);
        root.setBottom(footer);

        Scene scene = new Scene(root, 640, 520);
        var css = getClass().getResource("/styles/railway-reservation.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        initialLoginStage.setScene(scene);
        initialLoginStage.show();
    }

    /**
     * If user is Guest, shows the login dialog and runs the action only after successful login.
     * Used for "Book Now" and "My Bookings".
     */
    private void requireLoginForAction(Runnable actionAfterLogin) {
        if (!"Guest".equalsIgnoreCase(currentUser)) {
            actionAfterLogin.run();
            return;
        }

        // Show the familiar login dialog
        showUserSelectionDialog();

        // After dialog closes, check again
        Platform.runLater(() -> {
            if (!"Guest".equalsIgnoreCase(currentUser)) {
                actionAfterLogin.run();
            }
        });
    }

    /**
     * Returns a friendly "login required" placeholder for the bookings tab when user is Guest.
     */
    private Node buildGuestBookingsPlaceholder() {
        VBox box = new VBox(16);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        box.getStyleClass().add("bookings-container");

        Label icon = new Label("🔐");
        icon.setStyle("-fx-font-size:48px;");

        Label title = new Label("Login Required");
        title.setStyle("-fx-font-size:22px; -fx-font-weight:bold; -fx-text-fill:#ff9933;");

        Label msg = new Label("Please login or register to view and manage your bookings.\nSearch is available without an account.");
        msg.setStyle("-fx-text-alignment:center; -fx-text-fill:#94a3b8;");

        Button loginBtn = new Button("Login / Register");
        loginBtn.getStyleClass().add("primary-button");
        loginBtn.setOnAction(e -> showUserSelectionDialog());

        Button guestSearch = new Button("Back to Search");
        guestSearch.getStyleClass().add("secondary-button");
        guestSearch.setOnAction(e -> switchToSection("search"));

        box.getChildren().addAll(icon, title, msg, loginBtn, guestSearch);
        return box;
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void toggleTheme() {
        // Legacy toggle now cycles through themes
        isDarkTheme = !isDarkTheme;
        String next = "dark".equals(currentTheme) ? "light" : "classic".equals(currentTheme) ? "dark" : "classic";
        applyTheme(next);
    }
}
