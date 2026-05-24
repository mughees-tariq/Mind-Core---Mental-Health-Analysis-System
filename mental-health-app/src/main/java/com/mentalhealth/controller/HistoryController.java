package com.mentalhealth.controller;

import com.mentalhealth.Main;
import com.mentalhealth.model.HistoryEntry;
import com.mentalhealth.model.HistoryFilter;
import com.mentalhealth.model.User;
import com.mentalhealth.service.HistoryService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import com.mentalhealth.util.CardAnimations;

import java.util.logging.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

// Controller for HistoryScreen.fxml. Pulls the user's saved analyses from
// HistoryService (which queries predictions + history_meta in the SQLite DB),
// then supports filtering by date/mood/severity/confidence/text/starred,
// timeline + list views, tagging, notes, comparing entries side by side,
// bulk star/delete, and CSV/JSON export.
public class HistoryController extends BaseController {

    private static final Logger LOG = Logger.getLogger(HistoryController.class.getName());

    @FXML private BorderPane root;

    @Override
    protected BorderPane getRoot() { return root; }

    @FXML private Label totalEntriesLabel, activeDaysLabel, avgConfLabel;

    @FXML private Button toggleFilterBtn;
    @FXML private VBox filterPanel;
    @FXML private DatePicker startDatePicker, endDatePicker;
    @FXML private Button clearDatesBtn;
    @FXML private HBox moodFilterContainer;
    @FXML private CheckBox lowSeverityCheck, moderateSeverityCheck, highSeverityCheck, criticalSeverityCheck;
    @FXML private Slider minConfidenceSlider, maxConfidenceSlider;
    @FXML private Label minConfValue, maxConfValue;
    @FXML private TextField searchField;
    @FXML private CheckBox starredOnlyCheck;
    @FXML private ComboBox<String> sortCombo;
    @FXML private Button applyFilterBtn, resetFilterBtn;
    @FXML private FlowPane activeTagsPane;

    @FXML private HBox bulkActionBar;
    @FXML private Label selectedCountLabel;

    @FXML private ToggleButton listViewBtn, timelineViewBtn;
    @FXML private VBox listView, timelineView, compareView;
    @FXML private VBox entriesContainer, timelineContainer;
    @FXML private HBox compareContainer;
    @FXML private Label compareCountLabel;

    @FXML private VBox loadingIndicator, emptyState;
    @FXML private Button loadMoreBtn;
    @FXML private Button exportBtn;

    @FXML private VBox tagDialog, notesDialog, exportDialog;
    @FXML private FlowPane currentTagsPane;
    @FXML private TextField newTagField;
    @FXML private TextArea notesTextArea;
    @FXML private ComboBox<String> exportFormatCombo;
    @FXML private CheckBox exportWithNotesCheck, exportCurrentFilterCheck;

    private HistoryService historyService;
    private User currentUser;

    private List<HistoryEntry> allEntries = new ArrayList<>();
    private List<HistoryEntry> currentEntries = new ArrayList<>();
    private Set<Integer> selectedEntryIds = new HashSet<>();
    private List<HistoryEntry> compareEntries = new ArrayList<>();
    private HistoryEntry currentEditingEntry;
    private int currentOffset = 0;
    private static final int PAGE_SIZE = 20;
    private boolean hasMore = true;
    private boolean filterVisible = true;
    private HistoryFilter currentFilter = new HistoryFilter();

    private static final ZoneId PAKISTAN_ZONE = ZoneId.of("Asia/Karachi");

    private static final Map<String, String> MOOD_COLORS = Map.of(
        "Normal", "#10b981",
        "Stress", "#f59e0b",
        "Anxiety", "#f97316",
        "Bipolar", "#e879f9",
        "Depression", "#ef4444",
        "Personality Disorder", "#dc2626",
        "Suicidal", "#991b1b"
    );

    private static final Map<String, String> MOOD_ICONS = Map.of(
        "Normal", "😊",
        "Stress", "😰",
        "Anxiety", "😨",
        "Bipolar", "🎭",
        "Depression", "😢",
        "Personality Disorder", "🔄",
        "Suicidal", "🆘"
    );

    // FXML init. Bounces guests back to login, then wires the filter panel,
    // view toggle, listeners and triggers the first data load.
    @FXML
    public void initialize() {
        LOG.info("HistoryController initialized with Pakistan timezone");

        currentUser = Main.getCurrentUser();
        if (currentUser == null) {
            handleLogout();
            return;
        }

        historyService = new HistoryService();

        setupFilterControls();
        setupViewToggle();
        loadHeaderStats();
        loadHistory(false);

        setupListeners();

        CardAnimations.animateAll(root, ".card");
    }

    // Builds the filter widgets (date pickers, mood checkboxes, sliders, sort).
    private void setupFilterControls() {
        startDatePicker.setValue(LocalDate.now(ZoneId.of("Asia/Karachi")).minusMonths(1));
        endDatePicker.setValue(LocalDate.now(ZoneId.of("Asia/Karachi")));

        String[] moods = {"Normal", "Stress", "Anxiety", "Bipolar", "Depression", "Personality Disorder", "Suicidal"};
        for (String mood : moods) {
            CheckBox cb = new CheckBox(mood);
            cb.setStyle("-fx-text-fill: " + MOOD_COLORS.getOrDefault(mood, "#64748b") + "; -fx-font-weight: bold;");
            moodFilterContainer.getChildren().add(cb);
        }

        minConfidenceSlider.valueProperty().addListener((obs, old, val) ->
            minConfValue.setText(String.format("%.0f%%", val)));
        maxConfidenceSlider.valueProperty().addListener((obs, old, val) ->
            maxConfValue.setText(String.format("%.0f%%", val)));

        sortCombo.setItems(FXCollections.observableArrayList(
            "Newest first", "Oldest first", "Highest confidence", "Mood (A-Z)"
        ));
        sortCombo.setValue("Newest first");

        toggleFilterBtn.setOnAction(e -> toggleFilterPanel());

        clearDatesBtn.setOnAction(e -> {
            startDatePicker.setValue(null);
            endDatePicker.setValue(null);
        });

        applyFilterBtn.setOnAction(e -> applyFilters());
        resetFilterBtn.setOnAction(e -> resetFilters());
    }

    // Hooks the list/timeline view toggle buttons.
    private void setupViewToggle() {
        ToggleGroup viewGroup = new ToggleGroup();
        listViewBtn.setToggleGroup(viewGroup);
        timelineViewBtn.setToggleGroup(viewGroup);

        listViewBtn.setOnAction(e -> switchToListView());
        timelineViewBtn.setOnAction(e -> switchToTimelineView());
    }

    // Wires live-search and the severity/starred checkboxes to re-apply filters.
    private void setupListeners() {
        searchField.textProperty().addListener((obs, old, val) -> {
            if (val.length() >= 3 || val.isEmpty()) {
                applyFilters();
            }
        });

        starredOnlyCheck.selectedProperty().addListener(e -> applyFilters());

        lowSeverityCheck.selectedProperty().addListener(e -> applyFilters());
        moderateSeverityCheck.selectedProperty().addListener(e -> applyFilters());
        highSeverityCheck.selectedProperty().addListener(e -> applyFilters());
        criticalSeverityCheck.selectedProperty().addListener(e -> applyFilters());
    }

    private void toggleFilterPanel() {
        filterVisible = !filterVisible;
        filterPanel.setVisible(filterVisible);
        filterPanel.setManaged(filterVisible);
        toggleFilterBtn.setText(filterVisible ? "▼ Hide Filters" : "▶ Show Filters");
    }

    // Loads the three header counters (total / active days / avg confidence).
    private void loadHeaderStats() {
        new Thread(() -> {
            Map<String, Object> stats = historyService.getStats(currentUser.getId());
            Platform.runLater(() -> {
                totalEntriesLabel.setText(String.valueOf(stats.getOrDefault("total", 0)));
                activeDaysLabel.setText(String.valueOf(stats.getOrDefault("activeDays", 0)));
                double avgConf = (double) stats.getOrDefault("avgConfidence", 0.0);
                avgConfLabel.setText(String.format("%.1f%%", avgConf));
            });
        }).start();
    }

    // Pulls one page of entries from the service. Pass refresh=true to reset
    // pagination and start over with the current filter.
    private void loadHistory(boolean refresh) {
        if (refresh) {
            currentOffset = 0;
            currentEntries.clear();
            allEntries.clear();
        }

        showLoading(true);
        showEmptyState(false);

        new Thread(() -> {
            List<HistoryEntry> newEntries = historyService.getHistory(
                currentUser.getId(),
                currentFilter,
                PAGE_SIZE,
                currentOffset
            );

            int totalCount = historyService.getHistoryCount(currentUser.getId(), currentFilter);

            Platform.runLater(() -> {
                if (refresh) {
                    currentEntries = newEntries;
                    allEntries = new ArrayList<>(newEntries);
                } else {
                    currentEntries.addAll(newEntries);
                    allEntries.addAll(newEntries);
                }

                hasMore = currentEntries.size() < totalCount;
                loadMoreBtn.setVisible(hasMore);

                showLoading(false);

                if (currentEntries.isEmpty()) {
                    showEmptyState(true);
                    entriesContainer.getChildren().clear();
                } else {
                    showEmptyState(false);
                    renderEntries();

                    updateActiveTags();
                }

                currentOffset += newEntries.size();
            });
        }).start();
    }

    // Re-renders the list view from currentEntries.
    private void renderEntries() {
        entriesContainer.getChildren().clear();

        for (HistoryEntry entry : currentEntries) {
            Node card = createEntryCard(entry);
            entriesContainer.getChildren().add(card);
        }
    }

    // Builds one full entry card: select box, star, date, mood, confidence,
    // severity, action buttons, preview, tags, notes indicator.
    private Node createEntryCard(HistoryEntry entry) {
        VBox card = new VBox(12);
        card.setStyle(createCardStyle(entry));
        card.setUserData(entry.getId());
        card.setOnMouseClicked(e -> handleCardClick(entry, e));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        CheckBox selectBox = new CheckBox();
        selectBox.setSelected(selectedEntryIds.contains(entry.getId()));
        selectBox.setOnAction(e -> toggleSelection(entry.getId(), selectBox.isSelected()));

        Button starBtn = new Button(entry.isStarred() ? "⭐" : "☆");
        starBtn.setStyle("-fx-background-color: transparent; -fx-font-size: 18px; -fx-cursor: hand;");
        starBtn.setOnAction(e -> toggleStar(entry));

        VBox dateBox = new VBox(2);
        Label dateLabel = new Label(getRelativeDate(entry.getCreatedAt()));
        dateLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label timeLabel = new Label(formatTime(entry.getCreatedAt()));
        timeLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        dateBox.getChildren().addAll(dateLabel, timeLabel);

        Label moodLabel = new Label(MOOD_ICONS.getOrDefault(entry.getPrediction(), "😐") + " " + entry.getPrediction());
        moodLabel.setStyle("-fx-background-color: " + entry.getColor() + "20; -fx-text-fill: " + entry.getColor() +
                          "; -fx-padding: 6 12; -fx-background-radius: 20; -fx-font-weight: bold;");

        Label confLabel = new Label(String.format("%.0f%%", entry.getConfidencePercent()));
        confLabel.setStyle("-fx-background-color: #e2e8f0; -fx-padding: 6 12; -fx-background-radius: 20;");

        Label severityLabel = new Label(entry.getSeverity());
        severityLabel.setStyle(getSeverityStyle(entry.getSeverity()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button tagsBtn = new Button("🏷️Tag");
        tagsBtn.setStyle("-fx-background-color: transparent; -fx-font-size: 16px; -fx-cursor: hand;");
        tagsBtn.setOnAction(e -> showTagDialog(entry));

        Button notesBtn = new Button("📝Notes");
        notesBtn.setStyle("-fx-background-color: transparent; -fx-font-size: 16px; -fx-cursor: hand;");
        notesBtn.setOnAction(e -> showNotesDialog(entry));

        Button compareBtn = new Button("🔄Compare");
        compareBtn.setStyle("-fx-background-color: transparent; -fx-font-size: 16px; -fx-cursor: hand;");
        compareBtn.setOnAction(e -> addToCompare(entry));

        header.getChildren().addAll(selectBox, starBtn, dateBox, moodLabel, confLabel, severityLabel,
                                   spacer, tagsBtn, notesBtn, compareBtn);

        Label previewLabel = new Label(getPreviewText(entry));
        previewLabel.setWrapText(true);
        previewLabel.setStyle("-fx-text-fill: #475569; -fx-padding: 5 0 0 25;");

        card.getChildren().add(header);
        card.getChildren().add(previewLabel);

        if (!entry.getTags().isEmpty()) {
            FlowPane tagsFlow = new FlowPane(5, 5);
            tagsFlow.setPadding(new Insets(5, 0, 0, 25));
            for (String tag : entry.getTags()) {
                Label tagLabel = new Label("#" + tag);
                tagLabel.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-padding: 3 10; -fx-background-radius: 12; -fx-font-size: 11px;");
                tagsFlow.getChildren().add(tagLabel);
            }
            card.getChildren().add(tagsFlow);
        }

        if (entry.getUserNotes() != null && !entry.getUserNotes().isEmpty()) {
            HBox noteIndicator = new HBox(5);
            noteIndicator.setPadding(new Insets(5, 0, 0, 25));
            Label noteIcon = new Label("📌");
            Label notePreview = new Label(entry.getUserNotes().length() > 50 ?
                entry.getUserNotes().substring(0, 47) + "..." : entry.getUserNotes());
            notePreview.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic;");
            noteIndicator.getChildren().addAll(noteIcon, notePreview);
            card.getChildren().add(noteIndicator);
        }

        return card;
    }

    private String createCardStyle(HistoryEntry entry) {
        return "-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 12; " +
               "-fx-border-color: " + (entry.isStarred() ? "#fbbf24" : "#e2e8f0") + "; " +
               "-fx-border-width: " + (entry.isStarred() ? "2" : "1") + "; " +
               "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 8, 0, 0, 2);";
    }

    private String getSeverityStyle(String severity) {
        if (severity == null) return "-fx-background-color: #e2e8f0; -fx-padding: 6 12; -fx-background-radius: 20;";

        return switch (severity) {
            case "Low" -> "-fx-background-color: #d1fae5; -fx-text-fill: #065f46; -fx-padding: 6 12; -fx-background-radius: 20;";
            case "Moderate" -> "-fx-background-color: #fed7aa; -fx-text-fill: #92400e; -fx-padding: 6 12; -fx-background-radius: 20;";
            case "High" -> "-fx-background-color: #fecaca; -fx-text-fill: #991b1b; -fx-padding: 6 12; -fx-background-radius: 20;";
            case "Critical" -> "-fx-background-color: #fca5a5; -fx-text-fill: #7f1d1d; -fx-padding: 6 12; -fx-background-radius: 20; -fx-font-weight: bold;";
            default -> "-fx-background-color: #e2e8f0; -fx-padding: 6 12; -fx-background-radius: 20;";
        };
    }

    // Converts a stored UTC timestamp to Asia/Karachi local time.
    private ZonedDateTime toPakistanTime(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return ZonedDateTime.now(PAKISTAN_ZONE);
        ZonedDateTime utcZoned = utcDateTime.atZone(ZoneId.of("UTC"));
        return utcZoned.withZoneSameInstant(PAKISTAN_ZONE);
    }

    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) return "---";
        ZonedDateTime pakistanTime = toPakistanTime(dateTime);
        return pakistanTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }

    private String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        ZonedDateTime pakistanTime = toPakistanTime(dateTime);
        int hour = pakistanTime.getHour();
        int minute = pakistanTime.getMinute();
        String ampm = hour >= 12 ? "PM" : "AM";
        hour = hour % 12;
        if (hour == 0) hour = 12;
        return String.format("%d:%02d %s", hour, minute, ampm);
    }

    // "Today" / "Yesterday" / "N days ago" / "N weeks ago" / "MMM dd, yyyy".
    private String getRelativeDate(LocalDateTime dateTime) {
        if (dateTime == null) return "Unknown";

        ZonedDateTime pakistanTime = toPakistanTime(dateTime);
        LocalDate entryDate = pakistanTime.toLocalDate();
        LocalDate now = LocalDate.now(PAKISTAN_ZONE);

        long daysBetween = ChronoUnit.DAYS.between(entryDate, now);

        if (daysBetween == 0) return "Today";
        if (daysBetween == 1) return "Yesterday";
        if (daysBetween < 7) return daysBetween + " days ago";
        if (daysBetween < 30) return (daysBetween / 7) + " weeks ago";

        return pakistanTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }

    private String formatChartDate(LocalDateTime dateTime) {
        if (dateTime == null) return "---";
        ZonedDateTime pakistanTime = toPakistanTime(dateTime);
        String dayName = pakistanTime.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        int hour = pakistanTime.getHour();
        String ampm = hour >= 12 ? "PM" : "AM";
        hour = hour % 12;
        if (hour == 0) hour = 12;
        return dayName + " " + pakistanTime.getMonthValue() + "/" +
               pakistanTime.getDayOfMonth() + " " + hour + ampm;
    }

    private String getPreviewText(HistoryEntry entry) {
        if (entry.getInputText() == null || entry.getInputText().isEmpty()) {
            return "No text provided";
        }
        if (entry.getInputText().length() <= 120) {
            return entry.getInputText();
        }
        return entry.getInputText().substring(0, 117) + "...";
    }

    // Reads every filter widget into a HistoryFilter and reloads the list.
    private void applyFilters() {
        currentFilter = new HistoryFilter();

        currentFilter.setStartDate(startDatePicker.getValue());
        currentFilter.setEndDate(endDatePicker.getValue());

        List<String> selectedMoods = new ArrayList<>();
        for (Node node : moodFilterContainer.getChildren()) {
            if (node instanceof CheckBox cb && cb.isSelected()) {
                selectedMoods.add(cb.getText());
            }
        }
        if (!selectedMoods.isEmpty()) {
            currentFilter.setMoods(selectedMoods);
        }

        List<String> selectedSeverities = new ArrayList<>();
        if (lowSeverityCheck.isSelected()) selectedSeverities.add("Low");
        if (moderateSeverityCheck.isSelected()) selectedSeverities.add("Moderate");
        if (highSeverityCheck.isSelected()) selectedSeverities.add("High");
        if (criticalSeverityCheck.isSelected()) selectedSeverities.add("Critical");
        if (!selectedSeverities.isEmpty()) {
            currentFilter.setSeverities(selectedSeverities);
        }

        currentFilter.setMinConfidence(minConfidenceSlider.getValue() / 100.0);
        currentFilter.setMaxConfidence(maxConfidenceSlider.getValue() / 100.0);

        if (!searchField.getText().trim().isEmpty()) {
            currentFilter.setSearchText(searchField.getText().trim());
        }

        currentFilter.setShowStarredOnly(starredOnlyCheck.isSelected());

        String sortVal = sortCombo.getValue();
        if (sortVal != null) {
            switch (sortVal) {
                case "Newest first": currentFilter.setSortBy("date_desc"); break;
                case "Oldest first": currentFilter.setSortBy("date_asc"); break;
                case "Highest confidence": currentFilter.setSortBy("confidence_desc"); break;
                case "Mood (A-Z)": currentFilter.setSortBy("mood"); break;
            }
        }

        loadHistory(true);
    }

    // Restores all filter widgets to their defaults and reloads.
    private void resetFilters() {
        startDatePicker.setValue(LocalDate.now(ZoneId.of("Asia/Karachi")).minusMonths(1));
        endDatePicker.setValue(LocalDate.now(ZoneId.of("Asia/Karachi")));

        for (Node node : moodFilterContainer.getChildren()) {
            if (node instanceof CheckBox cb) {
                cb.setSelected(false);
            }
        }

        lowSeverityCheck.setSelected(false);
        moderateSeverityCheck.setSelected(false);
        highSeverityCheck.setSelected(false);
        criticalSeverityCheck.setSelected(false);

        minConfidenceSlider.setValue(0);
        maxConfidenceSlider.setValue(100);
        searchField.clear();
        starredOnlyCheck.setSelected(false);
        sortCombo.setValue("Newest first");

        currentFilter = new HistoryFilter();

        loadHistory(true);
    }

    // Builds the row of "active filter" pills above the list.
    private void updateActiveTags() {
        List<String> selectedMoods = new ArrayList<>();
        for (Node node : moodFilterContainer.getChildren()) {
            if (node instanceof CheckBox cb && cb.isSelected()) {
                selectedMoods.add(cb.getText());
            }
        }

        if (!selectedMoods.isEmpty()) {
            activeTagsPane.setVisible(true);
            activeTagsPane.setManaged(true);
            activeTagsPane.getChildren().clear();

            for (String mood : selectedMoods) {
                HBox tag = new HBox(5);
                tag.setAlignment(Pos.CENTER_LEFT);
                tag.setStyle("-fx-background-color: " + MOOD_COLORS.getOrDefault(mood, "#64748b") +
                           "20; -fx-padding: 4 8 4 12; -fx-background-radius: 16;");

                Label moodLabel = new Label(mood);
                moodLabel.setStyle("-fx-text-fill: " + MOOD_COLORS.getOrDefault(mood, "#64748b") +
                                 "; -fx-font-size: 12px; -fx-font-weight: bold;");

                Button removeBtn = new Button("✕");
                removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " +
                                 MOOD_COLORS.getOrDefault(mood, "#64748b") + "; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 0 0 5;");
                removeBtn.setOnAction(e -> {
                    for (Node node : moodFilterContainer.getChildren()) {
                        if (node instanceof CheckBox cb && cb.getText().equals(mood)) {
                            cb.setSelected(false);
                            break;
                        }
                    }
                    applyFilters();
                });

                tag.getChildren().addAll(moodLabel, removeBtn);
                activeTagsPane.getChildren().add(tag);
            }

            Label countLabel = new Label(selectedMoods.size() + " filter" + (selectedMoods.size() > 1 ? "s" : "") + " applied");
            countLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-padding: 0 0 0 10;");
            activeTagsPane.getChildren().add(countLabel);

        } else {
            activeTagsPane.setVisible(false);
            activeTagsPane.setManaged(false);
        }
    }

    private void switchToListView() {
        listView.setVisible(true);
        listView.setManaged(true);
        timelineView.setVisible(false);
        timelineView.setManaged(false);
        compareView.setVisible(false);
        compareView.setManaged(false);
    }

    private void switchToTimelineView() {
        listView.setVisible(false);
        listView.setManaged(false);
        timelineView.setVisible(true);
        timelineView.setManaged(true);
        compareView.setVisible(false);
        compareView.setManaged(false);

        renderTimelineView();
    }

    // Groups currentEntries by date and renders a per-day strip of mood dots.
    private void renderTimelineView() {
        timelineContainer.getChildren().clear();

        if (currentEntries.isEmpty()) {
            Label emptyLabel = new Label("No entries to display in timeline");
            emptyLabel.setStyle("-fx-text-fill: #64748b; -fx-padding: 40;");
            timelineContainer.getChildren().add(emptyLabel);
            return;
        }

        Map<LocalDate, List<HistoryEntry>> byDate = new LinkedHashMap<>();
        for (HistoryEntry entry : currentEntries) {
            ZonedDateTime pakistanTime = toPakistanTime(entry.getCreatedAt());
            LocalDate date = pakistanTime.toLocalDate();
            byDate.computeIfAbsent(date, k -> new ArrayList<>()).add(entry);
        }

        List<LocalDate> sortedDates = new ArrayList<>(byDate.keySet());
        sortedDates.sort(Collections.reverseOrder());

        for (LocalDate date : sortedDates) {
            VBox dateGroup = new VBox(8);
            dateGroup.setStyle("-fx-padding: 10; -fx-background-color: #f8fafc; -fx-background-radius: 8; -fx-margin: 5 0;");

            String dayName = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            Label dateHeader = new Label(dayName + ", " + date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
            dateHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1e293b;");
            dateGroup.getChildren().add(dateHeader);

            HBox entriesRow = new HBox(15);
            entriesRow.setPadding(new Insets(5, 0, 10, 0));

            List<HistoryEntry> dayEntries = byDate.get(date);
            dayEntries.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

            for (HistoryEntry entry : dayEntries) {
                VBox entryDot = createTimelineDot(entry);
                entriesRow.getChildren().add(entryDot);
            }

            dateGroup.getChildren().add(entriesRow);
            timelineContainer.getChildren().add(dateGroup);
        }
    }

    // One mood dot in the timeline view. Click opens the full detail dialog.
    private VBox createTimelineDot(HistoryEntry entry) {
        VBox container = new VBox(5);
        container.setAlignment(Pos.CENTER);
        container.setPrefWidth(70);
        container.setStyle("-fx-cursor: hand;");
        container.setOnMouseClicked(e -> showEntryDetail(entry));

        String tooltipText = MOOD_ICONS.getOrDefault(entry.getPrediction(), "😐") + " " + entry.getPrediction() +
                            "\n" + formatTime(entry.getCreatedAt()) +
                            "\nConfidence: " + String.format("%.0f%%", entry.getConfidencePercent()) +
                            "\n" + (entry.getInputText() != null && entry.getInputText().length() > 50 ?
                                   entry.getInputText().substring(0, 47) + "..." : entry.getInputText() != null ? entry.getInputText() : "No text");
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setStyle("-fx-font-size: 12px;");
        Tooltip.install(container, tooltip);

        Circle dot = new Circle(10);
        dot.setFill(Color.web(entry.getColor()));
        dot.setStroke(Color.web(entry.isStarred() ? "#fbbf24" : "#ffffff"));
        dot.setStrokeWidth(2);

        Label timeLabel = new Label(formatTime(entry.getCreatedAt()));
        timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");

        container.getChildren().addAll(dot, timeLabel);
        return container;
    }

    // Double-click on a card opens the detail dialog.
    private void handleCardClick(HistoryEntry entry, javafx.scene.input.MouseEvent event) {
        if (event.getClickCount() == 2) {
            showEntryDetail(entry);
        }
    }

    // Modal dialog showing the full text, prediction, severity, tags, notes.
    private void showEntryDetail(HistoryEntry entry) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Entry Details");

        ZonedDateTime pakistanTime = toPakistanTime(entry.getCreatedAt());
        String headerDate = pakistanTime.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a"));
        dialog.setHeaderText(headerDate);

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.CLOSE);
        pane.setPrefWidth(600);
        pane.setPrefHeight(500);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label textLabel = new Label("Your entry:");
        textLabel.setStyle("-fx-font-weight: bold;");
        TextArea fullText = new TextArea(entry.getInputText());
        fullText.setWrapText(true);
        fullText.setEditable(false);
        fullText.setPrefRowCount(6);

        GridPane details = new GridPane();
        details.setHgap(15);
        details.setVgap(10);

        details.addRow(0, new Label("Prediction:"),
            new Label(MOOD_ICONS.getOrDefault(entry.getPrediction(), "😐") + " " + entry.getPrediction()));
        details.addRow(1, new Label("Confidence:"),
            new Label(String.format("%.1f%%", entry.getConfidencePercent())));
        details.addRow(2, new Label("Severity:"),
            new Label(entry.getSeverity()));

        if (!entry.getTags().isEmpty()) {
            details.addRow(3, new Label("Tags:"),
                new Label(String.join(", ", entry.getTags())));
        }

        if (entry.getUserNotes() != null && !entry.getUserNotes().isEmpty()) {
            details.addRow(4, new Label("Notes:"),
                new Label(entry.getUserNotes()));
        }

        content.getChildren().addAll(textLabel, fullText, new Separator(), details);
        pane.setContent(content);

        dialog.showAndWait();
    }

    // Tracks which entries the user has selected for bulk actions.
    private void toggleSelection(int entryId, boolean selected) {
        if (selected) {
            selectedEntryIds.add(entryId);
        } else {
            selectedEntryIds.remove(entryId);
        }

        updateBulkActionBar();
    }

    private void updateBulkActionBar() {
        int count = selectedEntryIds.size();
        if (count > 0) {
            selectedCountLabel.setText(count + " item" + (count > 1 ? "s" : "") + " selected");
            bulkActionBar.setVisible(true);
            bulkActionBar.setManaged(true);
        } else {
            bulkActionBar.setVisible(false);
            bulkActionBar.setManaged(false);
        }
    }

    // Flips the starred flag for one entry through the service and re-renders.
    private void toggleStar(HistoryEntry entry) {
        new Thread(() -> {
            boolean newStarred = historyService.toggleStarred(entry.getId(), currentUser.getId());
            Platform.runLater(() -> {
                entry.setStarred(newStarred);
                renderEntries();
                if (timelineView.isVisible()) {
                    renderTimelineView();
                }
            });
        }).start();
    }

    // Adds an entry to the comparison panel (max 4 at a time).
    private void addToCompare(HistoryEntry entry) {
        if (compareEntries.size() >= 4) {
            Main.showInfo("Compare Limit", "You can compare up to 4 entries at once.");
            return;
        }

        if (!compareEntries.contains(entry)) {
            compareEntries.add(entry);
            updateCompareView();
        }
    }

    // Renders the side-by-side compare panel.
    private void updateCompareView() {
        if (compareEntries.isEmpty()) {
            compareView.setVisible(false);
            compareView.setManaged(false);
            return;
        }

        compareView.setVisible(true);
        compareView.setManaged(true);
        compareCountLabel.setText(compareEntries.size() + " entries");

        compareContainer.getChildren().clear();

        for (HistoryEntry entry : compareEntries) {
            VBox compareCard = new VBox(10);
            compareCard.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-min-width: 200; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 8, 0, 0, 2);");

            ZonedDateTime pakistanTime = toPakistanTime(entry.getCreatedAt());
            String dateStr = pakistanTime.format(DateTimeFormatter.ofPattern("MMM dd, h:mm a"));

            Label dateLabel = new Label(dateStr);
            dateLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

            Label moodLabel = new Label(MOOD_ICONS.getOrDefault(entry.getPrediction(), "😐") + " " + entry.getPrediction());
            moodLabel.setStyle("-fx-text-fill: " + entry.getColor() + "; -fx-font-weight: bold; -fx-font-size: 14px;");

            Label confLabel = new Label(String.format("%.0f%% confidence", entry.getConfidencePercent()));
            confLabel.setStyle("-fx-font-size: 12px;");

            Label severityLabel = new Label(entry.getSeverity());
            severityLabel.setStyle(getSeverityStyle(entry.getSeverity()) + "; -fx-font-size: 11px; -fx-padding: 2 8;");

            Button removeBtn = new Button("✕ Remove");
            removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-cursor: hand; -fx-font-size: 11px; -fx-border-color: #ef4444; -fx-border-radius: 4; -fx-padding: 3 8;");
            removeBtn.setOnAction(e -> {
                compareEntries.remove(entry);
                updateCompareView();
            });

            compareCard.getChildren().addAll(dateLabel, moodLabel, confLabel, severityLabel, removeBtn);
            compareContainer.getChildren().add(compareCard);
        }
    }

    @FXML
    private void handleClearCompare() {
        compareEntries.clear();
        compareView.setVisible(false);
        compareView.setManaged(false);
    }

    // Opens the inline tag-edit panel for a given entry.
    private void showTagDialog(HistoryEntry entry) {
        currentEditingEntry = entry;
        tagDialog.setVisible(true);
        tagDialog.setManaged(true);

        currentTagsPane.getChildren().clear();
        if (entry.getTags().isEmpty()) {
            Label noTagsLabel = new Label("No tags yet");
            noTagsLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic;");
            currentTagsPane.getChildren().add(noTagsLabel);
        } else {
            for (String tag : entry.getTags()) {
                HBox tagRow = new HBox(10);
                tagRow.setAlignment(Pos.CENTER_LEFT);

                Label tagLabel = new Label("#" + tag);
                tagLabel.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-padding: 4 12; -fx-background-radius: 16; -fx-font-size: 12px;");

                Button removeBtn = new Button("✕");
                removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 0 0 5;");
                removeBtn.setOnAction(e -> removeTag(entry, tag));

                tagRow.getChildren().addAll(tagLabel, removeBtn);
                currentTagsPane.getChildren().add(tagRow);
            }
        }
    }

    // Adds a normalized tag to the editing entry and persists via the service.
    @FXML
    private void handleAddTag() {
        if (currentEditingEntry == null) return;

        String newTag = newTagField.getText().trim().toLowerCase().replaceAll("\\s+", "_");
        if (newTag.isEmpty()) return;

        if (!currentEditingEntry.getTags().contains(newTag)) {
            currentEditingEntry.addTag(newTag);

            new Thread(() -> {
                historyService.updateTags(currentEditingEntry.getId(), currentUser.getId(),
                                         currentEditingEntry.getTags());
                Platform.runLater(() -> {
                    showTagDialog(currentEditingEntry);
                    newTagField.clear();
                });
            }).start();
        } else {
            Main.showInfo("Duplicate Tag", "Tag already exists");
        }
    }

    private void removeTag(HistoryEntry entry, String tag) {
        entry.getTags().remove(tag);

        new Thread(() -> {
            historyService.updateTags(entry.getId(), currentUser.getId(), entry.getTags());
            Platform.runLater(() -> showTagDialog(entry));
        }).start();
    }

    @FXML
    private void handleCloseTagDialog() {
        tagDialog.setVisible(false);
        tagDialog.setManaged(false);
        currentEditingEntry = null;
        renderEntries();
    }

    // Opens the per-entry notes editor.
    private void showNotesDialog(HistoryEntry entry) {
        currentEditingEntry = entry;
        notesTextArea.setText(entry.getUserNotes());
        notesDialog.setVisible(true);
        notesDialog.setManaged(true);
    }

    // Persists the note text via HistoryService.
    @FXML
    private void handleSaveNotes() {
        if (currentEditingEntry == null) return;

        String notes = notesTextArea.getText().trim();
        currentEditingEntry.setUserNotes(notes);

        new Thread(() -> {
            historyService.updateNotes(currentEditingEntry.getId(), currentUser.getId(), notes);
            Platform.runLater(() -> {
                handleCloseNotesDialog();
                renderEntries();
            });
        }).start();
    }

    @FXML
    private void handleCloseNotesDialog() {
        notesDialog.setVisible(false);
        notesDialog.setManaged(false);
        currentEditingEntry = null;
    }

    // Stars every selected entry, then refreshes the list.
    @FXML
    private void handleBulkStar() {
        new Thread(() -> {
            for (int id : selectedEntryIds) {
                historyService.toggleStarred(id, currentUser.getId());
            }
            Platform.runLater(() -> {
                loadHistory(true);
                selectedEntryIds.clear();
                updateBulkActionBar();
            });
        }).start();
    }

    @FXML
    private void handleBulkExport() {
        showExportDialog(true);
    }

    // Confirms then deletes the selected entries via the service.
    @FXML
    private void handleBulkDelete() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Entries");
        confirm.setHeaderText("Delete " + selectedEntryIds.size() + " selected entries?");
        confirm.setContentText("This action cannot be undone.");
        confirm.initOwner(Main.getPrimaryStage());

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            new Thread(() -> {
                historyService.deleteEntries(currentUser.getId(),
                    new ArrayList<>(selectedEntryIds));
                Platform.runLater(() -> {
                    loadHistory(true);
                    selectedEntryIds.clear();
                    updateBulkActionBar();
                });
            }).start();
        }
    }

    @FXML
    private void handleBulkCancel() {
        selectedEntryIds.clear();
        updateBulkActionBar();
        renderEntries();
    }

    @FXML
    private void handleExport() {
        showExportDialog(false);
    }

    // Opens the export dialog. Bulk mode only allows exporting the selection.
    private void showExportDialog(boolean bulk) {
        exportDialog.setVisible(true);
        exportDialog.setManaged(true);

        if (bulk) {
            exportCurrentFilterCheck.setSelected(false);
            exportCurrentFilterCheck.setDisable(true);
            exportCurrentFilterCheck.setText("Export selected " + selectedEntryIds.size() + " items");
        } else {
            exportCurrentFilterCheck.setDisable(false);
            exportCurrentFilterCheck.setText("Only current filtered view");
        }
    }

    // Writes the chosen format to the user-picked file via the helpers below.
    @FXML
    private void handleConfirmExport() {
        String format = exportFormatCombo.getValue();
        boolean includeNotes = exportWithNotesCheck.isSelected();
        boolean useFilter = exportCurrentFilterCheck.isSelected();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export History");
        fileChooser.setInitialFileName("mental_health_export_" + LocalDate.now(ZoneId.of("Asia/Karachi")) +
            (format.equals("CSV") ? ".csv" : format.equals("JSON") ? ".json" : ".pdf"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            format + " Files", "*." + (format.equals("CSV") ? "csv" : format.equals("JSON") ? "json" : "pdf")));

        File file = fileChooser.showSaveDialog(Main.getPrimaryStage());
        if (file == null) {
            handleCloseExportDialog();
            return;
        }

        new Thread(() -> {
            try {
                if (format.equals("CSV")) {
                    exportToCSV(file, includeNotes, useFilter);
                } else if (format.equals("JSON")) {
                    exportToJSON(file, includeNotes, useFilter);
                } else {
                    Main.showInfo("PDF Export", "PDF export will be implemented with iText library");
                }

                Platform.runLater(() -> {
                    Main.showInfo("Export Complete", "Exported to " + file.getName());
                    handleCloseExportDialog();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Main.showError("Export Failed", e.getMessage());
                });
            }
        }).start();
    }

    // Pulls the CSV body from the service and writes it to disk.
    private void exportToCSV(File file, boolean includeNotes, boolean useFilter) throws Exception {
        HistoryFilter exportFilter = useFilter ? currentFilter : null;
        String csv = historyService.exportToCSV(currentUser.getId(), exportFilter);
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.print(csv);
        }
    }

    // Serializes entries to pretty-printed JSON via Gson.
    private void exportToJSON(File file, boolean includeNotes, boolean useFilter) throws Exception {
        List<HistoryEntry> entries = useFilter ? currentEntries :
            historyService.getHistory(currentUser.getId(), null, 10000, 0);

        List<Map<String, Object>> jsonList = new ArrayList<>();
        for (HistoryEntry e : entries) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", e.getId());
            ZonedDateTime pakistanTime = toPakistanTime(e.getCreatedAt());
            map.put("date", pakistanTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            map.put("time", pakistanTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            map.put("prediction", e.getPrediction());
            map.put("confidence", e.getConfidencePercent());
            map.put("severity", e.getSeverity());
            map.put("text", e.getInputText());
            map.put("starred", e.isStarred());
            if (includeNotes) {
                map.put("notes", e.getUserNotes());
            }
            map.put("tags", e.getTags());
            jsonList.add(map);
        }

        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(jsonList, writer);
        }
    }

    @FXML
    private void handleCloseExportDialog() {
        exportDialog.setVisible(false);
        exportDialog.setManaged(false);
    }

    // Pagination: fetches the next page using the same filter.
    @FXML
    private void handleLoadMore() {
        loadHistory(false);
    }

    private void showLoading(boolean show) {
        loadingIndicator.setVisible(show);
        loadingIndicator.setManaged(show);
    }

    private void showEmptyState(boolean show) {
        emptyState.setVisible(show);
        emptyState.setManaged(show);
    }
}
