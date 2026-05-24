package com.mentalhealth.controller;

import com.mentalhealth.Main;
import com.mentalhealth.model.Prediction;
import com.mentalhealth.model.User;
import com.mentalhealth.repository.PredictionRepository;
import com.mentalhealth.service.MLAPIService;
import com.mentalhealth.service.MLAPIService.ReportResult;
import com.mentalhealth.util.MoodUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.chart.*;
import javafx.scene.text.Text;
import javafx.scene.text.Font;
import javafx.util.StringConverter;
import javafx.geometry.Pos;
import com.mentalhealth.util.CardAnimations;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

// Controller for MoodTrackerScreen.fxml. Reads the user's last 7 days of
// predictions from PredictionRepository, computes summary analytics
// (best/worst day, frequency, average, stability, trend, weather), draws the
// mood line chart, and renders the per-entry history cards. The "Generate
// Report" button calls MLAPIService for an AI-written summary.
public class MoodTrackerController extends BaseController {

    @FXML private BorderPane root;

    @Override
    protected BorderPane getRoot() { return root; }
    @FXML private TextArea reportTextArea;
    @FXML private Button generateReportBtn, showMoreBtn, clearFilterBtn;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private Label statusLabel, weatherLabel, weatherDesc, stabilityLabel, trendLabel, avgScoreLabel, errorLabel;
    @FXML private Label bestDayLabel, bestDayReason, worstDayLabel, worstDayReason, freqMoodLabel, freqMoodDesc;
    @FXML private ProgressBar stabilityBar;
    @FXML private LineChart<String, Number> moodLineChart;
    @FXML private CategoryAxis dateAxis;
    @FXML private NumberAxis moodAxis;
    @FXML private VBox topMoodHistoryContainer, moreMoodHistoryContainer, reportSectionContainer;
    @FXML private DatePicker startDatePicker, endDatePicker;

    private PredictionRepository predRepo;
    private MLAPIService apiService;
    private List<Prediction> allEntries = null;
    private boolean showMore = false;
    private boolean isClearing = false;

    // FXML init. Configures the chart axis, wires the auto-resizing report
    // textarea, sets up the date pickers (locked to the last 7 days), and
    // kicks off the initial history load.
    @FXML
    public void initialize() {
        predRepo = new PredictionRepository();
        apiService = new MLAPIService();
        loadingSpinner.setVisible(false);
        errorLabel.setVisible(false);
        moreMoodHistoryContainer.managedProperty().bind(moreMoodHistoryContainer.visibleProperty());

        if (moodAxis != null) {
            moodAxis.setAutoRanging(false);
            moodAxis.setLowerBound(0);
            moodAxis.setUpperBound(120);
            moodAxis.setTickUnit(15);
            moodAxis.setMinorTickVisible(false);
            moodAxis.setLabel("");
            moodAxis.setTickLabelFormatter(new StringConverter<Number>() {
                @Override
                public String toString(Number number) {
                    int val = number.intValue();
                    return switch (val) {
                        case 15 -> "Normal";
                        case 30 -> "Stress";
                        case 45 -> "Anxiety";
                        case 60 -> "Bipolar";
                        case 75 -> "Depression";
                        case 90 -> "Pers. Disorder";
                        case 105 -> "Suicidal";
                        default -> "";
                    };
                }
                @Override
                public Number fromString(String string) { return 0; }
            });
        }

        if (reportTextArea != null) {
            reportTextArea.setMinHeight(Region.USE_PREF_SIZE);
            reportTextArea.setMaxHeight(Double.MAX_VALUE);

            reportTextArea.textProperty().addListener((obs, oldVal, newVal) -> {
                Platform.runLater(() -> adjustReportHeight());
            });

            reportTextArea.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (reportTextArea.getText() != null && !reportTextArea.getText().isEmpty()) {
                    Platform.runLater(() -> adjustReportHeight());
                }
            });

            VBox.setVgrow(reportTextArea, Priority.ALWAYS);
        }

        loadMoodHistory();

        if (showMoreBtn != null) {
            showMoreBtn.setOnAction(e -> toggleShowMore());
        }

        if (startDatePicker != null && endDatePicker != null) {
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate sevenDaysAgo = today.minusDays(6);

            startDatePicker.setDayCellFactory(picker -> new javafx.scene.control.DateCell() {
                @Override
                public void updateItem(java.time.LocalDate date, boolean empty) {
                    super.updateItem(date, empty);
                    setDisable(empty || date.isBefore(sevenDaysAgo) || date.isAfter(today));
                }
            });

            endDatePicker.setDayCellFactory(picker -> new javafx.scene.control.DateCell() {
                @Override
                public void updateItem(java.time.LocalDate date, boolean empty) {
                    super.updateItem(date, empty);
                    setDisable(empty || date.isBefore(sevenDaysAgo) || date.isAfter(today));
                }
            });

            startDatePicker.setOnAction(e -> filterByDateRange());
            endDatePicker.setOnAction(e -> filterByDateRange());
        }

        if (clearFilterBtn != null) {
            clearFilterBtn.setOnAction(e -> clearDateFilter());
        }

        CardAnimations.animateAll(root, ".card", ".card-success", ".card-danger", ".card-info", ".card-shadow");
    }

    // Pulls the last 7 days from the repository and either renders all of them
    // or hands off to the date filter if the user has set one.
    private void loadMoodHistory() {
        User currentUser = Main.getCurrentUser();
        if (currentUser == null) {
            showEmptyState("Please log in to view your mood history.");
            return;
        }

        List<Prediction> entries = predRepo.getLast7Days(currentUser.getId());
        this.allEntries = entries;
        if (entries == null || entries.isEmpty()) {
            showEmptyState("Start tracking your mood to see insights and patterns!");
            return;
        }
        hideEmptyState();

        if (startDatePicker != null && endDatePicker != null &&
            (startDatePicker.getValue() != null || endDatePicker.getValue() != null)) {
            filterByDateRange();
        } else {
            processAnalytics(entries);
            updateChart(entries);
            populateHistory(entries);
        }
    }

    // Re-renders analytics, chart, and history using only entries that fall
    // within the picked date range. Swaps start/end if the user inverted them.
    private void filterByDateRange() {
        if (isClearing) return;
        if (allEntries == null || allEntries.isEmpty()) {
            return;
        }

        java.time.LocalDate startDate = startDatePicker != null ? startDatePicker.getValue() : null;
        java.time.LocalDate endDate = endDatePicker != null ? endDatePicker.getValue() : null;

        if (startDate == null && endDate == null) {
            processAnalytics(allEntries);
            updateChart(allEntries);
            populateHistory(allEntries);
            return;
        }

        if (startDate == null) {
            startDate = endDate;
        }
        if (endDate == null) {
            endDate = startDate;
        }

        if (startDate.isAfter(endDate)) {
            java.time.LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }

        final java.time.LocalDate finalStartDate = startDate;
        final java.time.LocalDate finalEndDate = endDate;

        List<Prediction> filtered = new ArrayList<>();
        for (Prediction p : allEntries) {
            if (p.getCreatedAt() != null && p.getCreatedAt().length() >= 19) {
                try {
                    java.time.LocalDateTime utc = java.time.LocalDateTime.parse(p.getCreatedAt().substring(0, 19).replace(" ", "T"));
                    java.time.LocalDate entryDate = utc.atZone(java.time.ZoneOffset.UTC)
                            .withZoneSameInstant(java.time.ZoneId.of("Asia/Karachi"))
                            .toLocalDate();
                    if (!entryDate.isBefore(finalStartDate) && !entryDate.isAfter(finalEndDate)) {
                        filtered.add(p);
                    }
                } catch (Exception e) {
                    // Skip entries with unparseable dates
                }
            }
        }

        if (filtered.isEmpty()) {
            showEmptyState("No entries found for the selected date range.");
        } else {
            hideEmptyState();
            processAnalytics(filtered);
            updateChart(filtered);
            populateHistory(filtered);
        }
    }

    // Resets the date pickers and re-renders with the full 7-day history.
    private void clearDateFilter() {
        isClearing = true;
        if (startDatePicker != null) {
            startDatePicker.setValue(null);
        }
        if (endDatePicker != null) {
            endDatePicker.setValue(null);
        }
        isClearing = false;

        if (allEntries != null && !allEntries.isEmpty()) {
            hideEmptyState();
            processAnalytics(allEntries);
            updateChart(allEntries);
            populateHistory(allEntries);
        }
    }

    // Blanks every analytics widget and shows the given placeholder message.
    private void showEmptyState(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 16px; -fx-padding: 40px; -fx-alignment: center;");

        if (bestDayLabel != null) bestDayLabel.setText("---");
        if (worstDayLabel != null) worstDayLabel.setText("---");
        if (freqMoodLabel != null) freqMoodLabel.setText("---");
        if (weatherLabel != null) weatherLabel.setText("---");
        if (stabilityLabel != null) stabilityLabel.setText("Stability Index: 0%");
        if (stabilityBar != null) stabilityBar.setProgress(0);
        if (trendLabel != null) trendLabel.setText("No data");
        if (avgScoreLabel != null) avgScoreLabel.setText("Average: --- (0)");
        if (moodLineChart != null) moodLineChart.getData().clear();
        if (topMoodHistoryContainer != null) topMoodHistoryContainer.getChildren().clear();
        if (moreMoodHistoryContainer != null) moreMoodHistoryContainer.getChildren().clear();
        if (showMoreBtn != null) showMoreBtn.setVisible(false);
        if (generateReportBtn != null) generateReportBtn.setDisable(true);
    }

    private void hideEmptyState() {
        errorLabel.setVisible(false);
        if (generateReportBtn != null) generateReportBtn.setDisable(false);
    }

    // Computes best/worst day, frequency, average, stability, weather, and
    // trend over the supplied entries and pushes them into the summary cards.
    // Lower mood score = better state (Normal=15, Suicidal=105).
    private void processAnalytics(List<Prediction> entries) {
        if (entries == null || entries.isEmpty()) return;

        double sum = 0;
        Map<String, Integer> counts = new HashMap<>();

        Prediction best = entries.get(0), worst = entries.get(0);

        double minScore = Double.MAX_VALUE, maxScore = Double.MIN_VALUE;

        for (Prediction p : entries) {
            double score = getMoodScore(p.getPrediction());
            sum += score;
            counts.merge(p.getPrediction(), 1, Integer::sum);

            if (score < getMoodScore(best.getPrediction())) {
                best = p;
            }
            if (score > getMoodScore(worst.getPrediction())) {
                worst = p;
            }

            if (score < minScore) minScore = score;
            if (score > maxScore) maxScore = score;
        }

       if(best.getPrediction().equals("Normal")){
        String bestDate = formatDate(best.getCreatedAt());
        bestDayLabel.setText(best.getPrediction() + " (" + bestDate + ")");
        String bestNote = best.getInputText();
        if (bestNote == null || bestNote.trim().isEmpty()) {
            bestDayReason.setText("No notes added.");
        } else {
            bestDayReason.setText("\"" + truncateText(bestNote, 80) + "\"");
        }
    }else{        bestDayLabel.setText("No bright spots");}
        if(!worst.getPrediction().equals("Normal")){

        String worstDate = formatDate(worst.getCreatedAt());
        worstDayLabel.setText(worst.getPrediction() + " (" + worstDate + ")");
        String worstNote = worst.getInputText();
        if (worstNote == null || worstNote.trim().isEmpty()) {
            worstDayReason.setText("No notes added.");
        } else {
            worstDayReason.setText("\"" + truncateText(worstNote, 80) + "\"");
        }
        }else{
            worstDayLabel.setText("No significant challenges");
            worstDayReason.setText("Great job maintaining a positive mood!");
        }

        String freq = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("-");
        int freqCount = counts.getOrDefault(freq, 0);
        freqMoodLabel.setText(freq);
        freqMoodDesc.setText("This mood appeared " + freqCount + " time" + (freqCount != 1 ? "s" : "") + ". It was your baseline this week.");

        double avg = sum / entries.size();
        avgScoreLabel.setText("Average: " + getMoodLabel(avg) + " (" + String.format("%.0f", avg) + ")");

        // Stability = 100% when range is zero, 0% when range spans Normal..Suicidal.
        double maxPossibleRange = 90.0;
        double actualRange = maxScore - minScore;

        double stability = ((maxPossibleRange - actualRange) / maxPossibleRange) * 100.0;
        stability = Math.max(0, Math.min(100, stability));

        stabilityLabel.setText("Stability Index: " + String.format("%.0f%%", stability));
        stabilityBar.setProgress(stability / 100.0);

        if (stability > 80) {
            stabilityBar.setStyle("-fx-accent: #10b981;");
        } else if (stability > 50) {
            stabilityBar.setStyle("-fx-accent: #f59e0b;");
        } else {
            stabilityBar.setStyle("-fx-accent: #ef4444;");
        }

        if (stability > 75) {
            weatherLabel.setText("Calm Seas ☀️");
            weatherDesc.setText("Highly stable. Your emotional state has been very consistent this week.");
        } else if (stability > 45) {
            weatherLabel.setText("Partly Cloudy ⛅");
            weatherDesc.setText("Moderate changes. You're experiencing a healthy emotional range.");
        } else {
            weatherLabel.setText("Stormy Weather ⛈️");
            weatherDesc.setText("High volatility detected. You've had significant mood shifts this week.");
        }

        // Trend: small datasets compare first vs last entry; larger datasets
        // compare first-half average vs second-half average.
        if (entries.size() >= 2) {
            if (entries.size() < 4) {
                double firstScore = getMoodScore(entries.get(0).getPrediction());
                double lastScore = getMoodScore(entries.get(entries.size() - 1).getPrediction());
                double threshold = 8.0;

                if (lastScore < firstScore - threshold) {
                    trendLabel.setText("📈 Improving");
                    trendLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                    Tooltip tt = new Tooltip(String.format("Your mood improved from %.1f to %.1f over this period.", firstScore, lastScore));
                    trendLabel.setTooltip(tt);
                } else if (lastScore > firstScore + threshold) {
                    trendLabel.setText("📉 Declining");
                    trendLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");

                    String recentNote = entries.get(entries.size() - 1).getInputText();
                    String reason = (recentNote == null || recentNote.trim().isEmpty())
                        ? "Recent entries show increased emotional intensity."
                        : "Recent note: \"" + truncateText(recentNote, 60) + "\"";

                    Tooltip tt = new Tooltip(String.format("Your mood declined from %.1f to %.1f.\n%s", firstScore, lastScore, reason));
                    trendLabel.setTooltip(tt);
                } else {
                    trendLabel.setText("➡️ Stable");
                    trendLabel.setStyle("-fx-text-fill: #475569; -fx-font-weight: bold;");
                    Tooltip tt = new Tooltip("Your mood has remained relatively stable over this period.");
                    trendLabel.setTooltip(tt);
                }
            } else {
                int midPoint = entries.size() / 2;
                double avgFirstHalf = entries.subList(0, midPoint).stream()
                        .mapToDouble(e -> getMoodScore(e.getPrediction()))
                        .average()
                        .orElse(avg);
                double avgSecondHalf = entries.subList(midPoint, entries.size()).stream()
                        .mapToDouble(e -> getMoodScore(e.getPrediction()))
                        .average()
                        .orElse(avg);

                double threshold = 5.0;

                if (avgSecondHalf < avgFirstHalf - threshold) {
                    trendLabel.setText("📈 Improving");
                    trendLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                    Tooltip tt = new Tooltip(String.format("Your mood improved from %.1f to %.1f.\nKeep up the positive momentum!", avgFirstHalf, avgSecondHalf));
                    trendLabel.setTooltip(tt);
                } else if (avgSecondHalf > avgFirstHalf + threshold) {
                    trendLabel.setText("📉 Declining");
                    trendLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");

                    String recentNote = entries.get(entries.size() - 1).getInputText();
                    String reason = (recentNote == null || recentNote.trim().isEmpty())
                        ? "Recent entries show increased emotional intensity."
                        : "Recent note: \"" + truncateText(recentNote, 60) + "\"";

                    Tooltip tt = new Tooltip(String.format("Your mood declined from %.1f to %.1f.\n%s", avgFirstHalf, avgSecondHalf, reason));
                    trendLabel.setTooltip(tt);
                } else {
                    trendLabel.setText("➡️ Stable");
                    trendLabel.setStyle("-fx-text-fill: #475569; -fx-font-weight: bold;");
                    Tooltip tt = new Tooltip("Your mood has remained relatively stable during this period.");
                    trendLabel.setTooltip(tt);
                }
            }
        } else if (entries.size() == 1) {
            String mood = entries.get(0).getPrediction();
            trendLabel.setText("📍 Single Entry");
            trendLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold;");
            Tooltip tt = new Tooltip("Currently showing: " + mood + ". Track more moods to see trends.");
            trendLabel.setTooltip(tt);
        } else {
            trendLabel.setText("➡️ No Data");
            trendLabel.setStyle("-fx-text-fill: #94a3b8;");
            Tooltip tt = new Tooltip("No entries available for this period.");
            trendLabel.setTooltip(tt);
        }
    }

    // Pushes data points into the line chart and adds a flat average reference
    // series when there are at least two entries.
    private void updateChart(List<Prediction> entries) {
        if (moodLineChart == null || entries == null || entries.isEmpty()) return;

        moodLineChart.getData().clear();
        moodLineChart.setLegendVisible(entries.size() >= 2);

        XYChart.Series<String, Number> moodSeries = new XYChart.Series<>();
        moodSeries.setName("Mood Level");

        double sum = 0;
        for (Prediction p : entries) {
            String dateLabel = formatDateForChart(p.getCreatedAt());
            double score = getMoodScore(p.getPrediction());
            sum += score;
            moodSeries.getData().add(new XYChart.Data<>(dateLabel, score));
        }

        moodLineChart.getData().add(moodSeries);

        if (entries.size() >= 2) {
            double avg = sum / entries.size();
            XYChart.Series<String, Number> avgSeries = new XYChart.Series<>();
            avgSeries.setName("Weekly Avg: " + getMoodLabel(avg));

            String firstLabel = formatDateForChart(entries.get(0).getCreatedAt());
            String lastLabel = formatDateForChart(entries.get(entries.size() - 1).getCreatedAt());
            avgSeries.getData().add(new XYChart.Data<>(firstLabel, avg));
            if (!firstLabel.equals(lastLabel)) {
                avgSeries.getData().add(new XYChart.Data<>(lastLabel, avg));
            }

            moodLineChart.getData().add(avgSeries);

            Platform.runLater(() -> {
                for (XYChart.Data<String, Number> d : avgSeries.getData()) {
                    if (d.getNode() != null) {
                        d.getNode().setStyle("-fx-background-color: transparent;");
                    }
                }
            });
        }

        for (int i = 0; i < moodSeries.getData().size(); i++) {
            XYChart.Data<String, Number> dataPoint = moodSeries.getData().get(i);
            final Prediction pred = entries.get(i);

            if (dataPoint.getNode() != null) {
                styleDataPoint(dataPoint.getNode(), pred);
            }
            dataPoint.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    styleDataPoint(newNode, pred);
                }
            });
        }
    }

    // Colors a chart point by mood and attaches a tooltip with the user note.
    private void styleDataPoint(javafx.scene.Node node, Prediction p) {
        String color = getMoodColor(p.getPrediction());
        node.setStyle("-fx-background-color: " + color + ", white; -fx-background-radius: 7px; -fx-padding: 5px;");

        String tooltipText = getMoodIcon(p.getPrediction()) + " " + p.getPrediction() +
                "\nConfidence: " + String.format("%.0f%%", p.getConfidence() * 100) +
                "\n" + formatFullDate(p.getCreatedAt());

        if (p.getInputText() != null && !p.getInputText().trim().isEmpty()) {
            tooltipText += "\n\n\"" + truncateText(p.getInputText(), 80) + "\"";
        }

        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setStyle("-fx-font-size: 12px;");
        tooltip.setShowDelay(javafx.util.Duration.millis(200));
        Tooltip.install(node, tooltip);
    }

    // Renders the per-entry history cards. First three go into the visible
    // strip, the rest into the collapsible "show more" section.
    private void populateHistory(List<Prediction> entries) {
        if (topMoodHistoryContainer == null || moreMoodHistoryContainer == null) return;

        topMoodHistoryContainer.getChildren().clear();
        moreMoodHistoryContainer.getChildren().clear();

        List<Prediction> reversed = new ArrayList<>(entries);
        Collections.reverse(reversed);

        for (int i = 0; i < reversed.size(); i++) {
            HBox card = createMoodCard(reversed.get(i), i == 0);
            if (i < 3) {
                topMoodHistoryContainer.getChildren().add(card);
            } else {
                moreMoodHistoryContainer.getChildren().add(card);
            }
        }

        if (showMoreBtn != null) {
            if (entries.size() > 3) {
                showMoreBtn.setVisible(true);
                int hiddenCount = entries.size() - 3;
                showMoreBtn.setText(showMore ? "Show Less" : "Show " + hiddenCount + " More");
            } else {
                showMoreBtn.setVisible(false);
            }
        }
    }

    // Builds one history card row: relative date, time, mood pill, note.
    private HBox createMoodCard(Prediction p, boolean isLatest) {
        HBox card = new HBox(15);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefHeight(80);

        String baseStyle = "-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 12; " +
                          "-fx-border-color: " + getMoodBorderColor(p.getPrediction()) + "; " +
                          "-fx-border-width: 0 0 0 4; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 8, 0, 0, 2);";

        if (isLatest) {
            baseStyle += " -fx-border-color: #3b82f6; -fx-border-width: 2;";
        }

        card.setStyle(baseStyle);

        VBox dateBox = new VBox(2);
        dateBox.setPrefWidth(100);
        String relativeDate = getRelativeDate(p.getCreatedAt());
        String timeStr = extractTime(p.getCreatedAt());

        Label dateLabel = new Label(relativeDate);
        dateLabel.setStyle("-fx-text-fill: #0f172a; -fx-font-weight: bold; -fx-font-size: 13px;");

        Label timeLabel = new Label(timeStr);
        timeLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        dateBox.getChildren().addAll(dateLabel, timeLabel);

        Label moodLabel = new Label(getMoodIcon(p.getPrediction()) + " " + p.getPrediction());
        moodLabel.setPrefWidth(130);
        moodLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: " + getMoodColor(p.getPrediction()));

        String noteText = p.getInputText();
        if (noteText == null || noteText.trim().isEmpty()) {
            noteText = "No details provided";
        } else {
            noteText = "\"" + truncateText(noteText, 100) + "\"";
        }

        Label noteLabel = new Label(noteText);
        noteLabel.setWrapText(true);
        noteLabel.setMaxWidth(400);
        noteLabel.setStyle("-fx-font-style: italic; -fx-text-fill: " +
                          (p.getInputText() == null || p.getInputText().trim().isEmpty() ? "#cbd5e1" : "#64748b") +
                          "; -fx-font-size: 13px;");
        HBox.setHgrow(noteLabel, Priority.ALWAYS);

        card.getChildren().addAll(dateBox, moodLabel, noteLabel);
        return card;
    }

    // Show/hide the overflow history section.
    private void toggleShowMore() {
        showMore = !showMore;
        moreMoodHistoryContainer.setVisible(showMore);

        if (allEntries != null && allEntries.size() > 3) {
            int hiddenCount = allEntries.size() - 3;
            showMoreBtn.setText(showMore ? "Show Less" : "Show " + hiddenCount + " More");
        }
    }

    private double getMoodScore(String mood) { return MoodUtils.getMoodScore(mood); }
    private String getMoodLabel(double score) { return MoodUtils.getMoodLabel(score); }
    private String getMoodColor(String mood)  { return MoodUtils.getMoodColor(mood); }
    private String getMoodBorderColor(String mood) { return MoodUtils.getMoodBorderColor(mood); }
    private String getMoodIcon(String mood) { return MoodUtils.getMoodEmoji(mood); }

    // UTC timestamp string -> "MM-dd" in Asia/Karachi.
    private String formatDate(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.length() < 10) return "---";
        try {
            LocalDateTime utc = LocalDateTime.parse(dateTimeStr.substring(0, 19).replace(" ", "T"));
            java.time.ZonedDateTime local = utc.atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(java.time.ZoneId.of("Asia/Karachi"));
            return local.format(DateTimeFormatter.ofPattern("MM-dd"));
        } catch (Exception e) {
            return dateTimeStr.substring(0, Math.min(10, dateTimeStr.length()));
        }
    }

    // UTC timestamp -> chart-axis label like "Mon 7/14 3PM" in Asia/Karachi.
    private String formatDateForChart(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.length() < 10) return "---";
        try {
            LocalDateTime utc = LocalDateTime.parse(dateTimeStr.substring(0, 19).replace(" ", "T"));
            java.time.ZonedDateTime local = utc.atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(java.time.ZoneId.of("Asia/Karachi"));
            String dayName = local.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            int hour = local.getHour();
            String ampm = hour >= 12 ? "PM" : "AM";
            hour = hour % 12;
            if (hour == 0) hour = 12;
            return dayName + " " + local.getMonthValue() + "/" + local.getDayOfMonth() + " " + hour + ampm;
        } catch (Exception e) {
            try {
                String[] parts = dateTimeStr.substring(0, 10).split("-");
                if (parts.length >= 3) {
                    return parts[1] + "/" + parts[2];
                }
            } catch (Exception ex) { /* fallback below */ }
            return dateTimeStr.substring(0, Math.min(10, dateTimeStr.length()));
        }
    }

    // UTC timestamp -> long display string for tooltips.
    private String formatFullDate(String dateTimeStr) {
        if (dateTimeStr == null) return "";
        try {
            LocalDateTime utc = LocalDateTime.parse(dateTimeStr.substring(0, 19).replace(" ", "T"));
            java.time.ZonedDateTime local = utc.atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(java.time.ZoneId.of("Asia/Karachi"));
            return local.format(DateTimeFormatter.ofPattern("EEEE, MMM d 'at' h:mm a"));
        } catch (Exception e) {
            return dateTimeStr;
        }
    }

    // Sizes the report textarea to fit its content. Falls back to a line-count
    // estimate if measuring via a Text node fails.
    private void adjustReportHeight() {
        String text = reportTextArea.getText();

        if (text == null || text.trim().isEmpty()) {
            reportTextArea.setPrefHeight(80);
            return;
        }

        try {
            Text measureText = new Text(text);
            measureText.setFont(Font.font("Segoe UI", 15));

            double availableWidth = reportTextArea.getWidth() > 0
                ? reportTextArea.getWidth() - 40
                : 800;

            measureText.setWrappingWidth(availableWidth);

            double textHeight = measureText.getLayoutBounds().getHeight();
            double requiredHeight = textHeight + 60;

            double minHeight = 80;
            double maxHeight = 1200;
            double finalHeight = Math.max(minHeight, Math.min(requiredHeight, maxHeight));

            reportTextArea.setPrefHeight(finalHeight);

        } catch (Exception e) {
            int lineCount = text.split("\n").length;
            double estimatedHeight = Math.max(80, Math.min(lineCount * 22 + 60, 1200));
            reportTextArea.setPrefHeight(estimatedHeight);
        }
    }

    // UTC timestamp -> "Today" / "Yesterday" / "N days ago" / "MMM dd".
    private String getRelativeDate(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.length() < 10) return "Unknown";
        try {
            LocalDateTime utc = LocalDateTime.parse(dateTimeStr.substring(0, 19).replace(" ", "T"));
            java.time.ZonedDateTime local = utc.atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(java.time.ZoneId.of("Asia/Karachi"));
            java.time.LocalDate entryDate = local.toLocalDate();
            java.time.LocalDate now = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Karachi"));
            long daysBetween = ChronoUnit.DAYS.between(entryDate, now);
            if (daysBetween == 0) return "Today";
            if (daysBetween == 1) return "Yesterday";
            if (daysBetween < 7) return daysBetween + " days ago";
            return entryDate.format(DateTimeFormatter.ofPattern("MMM dd"));
        } catch (Exception e) {
            return formatDate(dateTimeStr);
        }
    }

    // UTC timestamp -> 12-hour clock string in Asia/Karachi.
    private String extractTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.length() < 16) return "";
        try {
            LocalDateTime utc = LocalDateTime.parse(dateTimeStr.substring(0, 19).replace(" ", "T"));
            java.time.ZonedDateTime local = utc.atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(java.time.ZoneId.of("Asia/Karachi"));
            int hour = local.getHour();
            int minute = local.getMinute();
            String ampm = hour >= 12 ? "PM" : "AM";
            hour = hour % 12;
            if (hour == 0) hour = 12;
            return String.format("%d:%02d %s", hour, minute, ampm);
        } catch (Exception e) {
            return dateTimeStr.length() >= 16 ? dateTimeStr.substring(11, 16) : "";
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        text = text.trim();
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    // Pops the help dialog explaining the chart axis and reading conventions.
    @FXML
    private void handleShowGraphKey() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Graph Key - Understanding Your Mood Chart");
        alert.setHeaderText("How to Read Your Mood Journey");

        String content =
            "MOOD CATEGORIES (Y-Axis, bottom to top):\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  😊  Normal (15)  -  Healthy emotional state\n" +
            "        You're feeling balanced and stable.\n\n" +
            "  😰  Stress (30)  -  Mild emotional tension\n" +
            "        Everyday pressures are noticeable.\n\n" +
            "  😨  Anxiety (45)  -  Heightened worry or unease\n" +
            "        Persistent concern affecting daily life.\n\n" +
            "  🎭  Bipolar (60)  -  Mood swings detected\n" +
            "        Alternating between emotional highs and lows.\n\n" +
            "  😢  Depression (75)  -  Low mood indicators\n" +
            "        Prolonged sadness or loss of interest.\n\n" +
            "  🔄  Personality Disorder (90)  -  Complex patterns\n" +
            "        Deeply ingrained behavioral patterns detected.\n\n" +
            "  🆘  Suicidal (105)  -  Crisis-level indicators\n" +
            "        Immediate professional support recommended.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "READING THE CHART:\n\n" +
            "  •  Each dot represents one analysis entry\n" +
            "  •  The line connects your entries chronologically\n" +
            "  •  Lower on the chart = healthier emotional state\n" +
            "  •  Higher on the chart = more intense distress\n" +
            "  •  The dashed line shows your weekly average\n" +
            "  •  Sharp spikes indicate high emotional volatility\n" +
            "  •  Hover over any dot to see details & your note\n\n" +
            "  •  Use the date pickers to filter a specific range\n" +
            "  •  Click 'Clear' to reset and show all 7 days";

        alert.setContentText(content);
        alert.getDialogPane().setMinWidth(520);
        alert.getDialogPane().setMinHeight(600);
        alert.getDialogPane().setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 12px;");
        alert.showAndWait();
    }

    // Asks the ML API for a written report covering the loaded entries and
    // drops the result into the textarea.
    @FXML
    private void handleGenerateReport() {
        User user = Main.getCurrentUser();
        if (user == null || allEntries == null || allEntries.isEmpty()) {
            statusLabel.setText("No data available to generate report.");
            statusLabel.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        generateReportBtn.setDisable(true);
        loadingSpinner.setVisible(true);
        statusLabel.setText("Generating personalized insights...");
        statusLabel.setStyle("-fx-text-fill: #3b82f6;");

        reportTextArea.setText("");
        reportTextArea.setPrefHeight(80);

        new Thread(() -> {
            ReportResult res = apiService.generateReport(user.getName(), allEntries);
            Platform.runLater(() -> {
                loadingSpinner.setVisible(false);
                generateReportBtn.setDisable(false);

                if (res.isSuccess()) {
                    reportTextArea.setText(res.getReport());
                    Platform.runLater(() -> {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                        adjustReportHeight();
                    });
                    statusLabel.setText("Report generated successfully!");
                    statusLabel.setStyle("-fx-text-fill: #10b981;");
                } else {
                    statusLabel.setText("Error: Unable to generate report. Please try again.");
                    statusLabel.setStyle("-fx-text-fill: #ef4444;");
                }
            });
        }).start();
    }
}
