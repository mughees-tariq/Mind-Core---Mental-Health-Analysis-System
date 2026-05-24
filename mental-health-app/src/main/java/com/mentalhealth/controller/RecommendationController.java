package com.mentalhealth.controller;

import com.mentalhealth.Main;
import com.mentalhealth.model.User;
import com.mentalhealth.service.RecommendationService;
import com.mentalhealth.util.CardAnimations;
import com.mentalhealth.service.RecommendationService.*;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

// Controller for RecommendationScreen.fxml. Asks RecommendationService for the
// user's daily action plan (built from their dominant mood + risk level and
// stored progress in recommendation_progress table), renders the per-section
// cards with expand/collapse details, and writes checkbox state back through
// the service.
public class RecommendationController extends BaseController {

    @FXML private BorderPane root;

    @Override
    protected BorderPane getRoot() { return root; }
    @FXML private VBox sectionsContainer;
    @FXML private Label moodPill;
    @FXML private Label riskPill;
    @FXML private Label progressLabel;
    @FXML private ProgressBar progressBar;
    @FXML private VBox weeklyStatsContainer;

    private RecommendationService recService;
    private RecommendationData recData;

    // FXML init. Wires the service and triggers the recommendations load.
    @FXML
    public void initialize() {
        recService = new RecommendationService();
        loadRecommendations();
        CardAnimations.animateAll(root, ".card");
    }

    // Pulls the recommendation data on a worker thread.
    private void loadRecommendations() {
        User currentUser = Main.getCurrentUser();
        if (currentUser == null) return;

        new Thread(() -> {
            RecommendationData data = recService.getRecommendations(currentUser.getId());
            Platform.runLater(() -> populateUI(data));
        }).start();
    }

    // Renders the mood/risk pills, progress bar, sections, and weekly stats.
    private void populateUI(RecommendationData data) {
        this.recData = data;

        moodPill.setText("Mood: " + data.dominantMood);
        moodPill.setStyle("-fx-background-color: " + hexToRgba(data.moodColor, 0.15) + "; -fx-text-fill: " + data.moodColor + "; -fx-background-radius: 20; -fx-padding: 6 16; -fx-font-size: 13px; -fx-font-weight: bold;");

        riskPill.setText("Risk: " + data.riskLevel);
        riskPill.setStyle("-fx-background-color: " + hexToRgba(data.riskColor, 0.15) + "; -fx-text-fill: " + data.riskColor + "; -fx-background-radius: 20; -fx-padding: 6 16; -fx-font-size: 13px; -fx-font-weight: bold;");

        updateProgress(data.completedToday, data.totalItems);

        sectionsContainer.getChildren().clear();
        for (RecSection section : data.sections) {
            sectionsContainer.getChildren().add(buildSectionCard(section));
        }

        buildWeeklyStats(data.weeklyStats, data.totalItems);

        CardAnimations.animateAll(root, ".card");
    }

    // Builds one section card (header + items list).
    private VBox buildSectionCard(RecSection section) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label emoji = new Label(section.emoji);
        emoji.setStyle("-fx-font-size: 22px;");

        Label title = new Label(section.title);
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label badge = new Label(section.conditionBadge);
        badge.setStyle("-fx-background-color: " + hexToRgba(section.badgeColor, 0.15) + "; -fx-text-fill: " + section.badgeColor + "; -fx-background-radius: 12; -fx-padding: 3 10; -fx-font-size: 11px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(emoji, title, spacer, badge);
        card.getChildren().add(header);

        for (RecItem item : section.items) {
            card.getChildren().add(buildItemBlock(item));
        }

        return card;
    }

    // Builds one recommendation row plus its expandable steps/why-it-helps
    // panel. The checkbox persists completion state through the service.
    private VBox buildItemBlock(RecItem item) {
        VBox block = new VBox(0);

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 14, 12, 14));
        String defaultRowStyle = "-fx-background-color: #f8fafc; -fx-background-radius: 10 10 0 0; -fx-border-color: #e2e8f0; -fx-border-radius: 10 10 0 0; -fx-border-width: 0 0 0 3;";
        String completedRowStyle = "-fx-background-color: #f0fdf4; -fx-background-radius: 10 10 0 0; -fx-border-color: #a7f3d0; -fx-border-radius: 10 10 0 0; -fx-border-width: 0 0 0 3;";
        String defaultRowStyleClosed = "-fx-background-color: #f8fafc; -fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-border-width: 0 0 0 3;";
        String completedRowStyleClosed = "-fx-background-color: #f0fdf4; -fx-background-radius: 10; -fx-border-color: #a7f3d0; -fx-border-radius: 10; -fx-border-width: 0 0 0 3;";
        row.setStyle(item.completed ? completedRowStyleClosed : defaultRowStyleClosed);

        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(item.completed);

        Label itemEmoji = new Label(item.emoji);
        itemEmoji.setStyle("-fx-font-size: 18px;");
        itemEmoji.setMinWidth(28);

        VBox textBox = new VBox(3);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label titleLabel = new Label(item.title);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label descLabel = new Label(item.description);
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        textBox.getChildren().addAll(titleLabel, descLabel);

        VBox pillBox = new VBox(4);
        pillBox.setAlignment(Pos.CENTER);
        pillBox.setMinWidth(80);

        Label durationPill = new Label(item.duration);
        durationPill.setStyle("-fx-background-color: #e0e7ff; -fx-text-fill: #4338ca; -fx-background-radius: 12; -fx-padding: 3 10; -fx-font-size: 10px; -fx-font-weight: bold;");
        durationPill.setAlignment(Pos.CENTER);

        Label difficultyPill = new Label(item.difficulty);
        String diffColor = "Easy".equals(item.difficulty) ? "#10b981" : "#f59e0b";
        difficultyPill.setStyle("-fx-background-color: " + hexToRgba(diffColor, 0.15) + "; -fx-text-fill: " + diffColor + "; -fx-background-radius: 12; -fx-padding: 3 10; -fx-font-size: 10px; -fx-font-weight: bold;");
        difficultyPill.setAlignment(Pos.CENTER);

        pillBox.getChildren().addAll(durationPill, difficultyPill);

        Label toggleArrow = new Label("▼");
        toggleArrow.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8; -fx-cursor: hand;");
        toggleArrow.setMinWidth(18);
        toggleArrow.setAlignment(Pos.CENTER);

        row.getChildren().addAll(checkBox, itemEmoji, textBox, pillBox, toggleArrow);

        VBox detailsPanel = new VBox(10);
        detailsPanel.setPadding(new Insets(0, 16, 14, 54));
        detailsPanel.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 0 0 10 10;");
        detailsPanel.setManaged(false);
        detailsPanel.setVisible(false);
        detailsPanel.setMaxHeight(0);

        if (item.steps != null && !item.steps.isEmpty()) {
            Label stepsHeader = new Label("Step-by-Step Guide");
            stepsHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #334155; -fx-padding: 10 0 4 0;");
            detailsPanel.getChildren().add(stepsHeader);

            for (int i = 0; i < item.steps.size(); i++) {
                HBox stepRow = new HBox(8);
                stepRow.setAlignment(Pos.TOP_LEFT);

                Label stepNum = new Label(String.valueOf(i + 1));
                stepNum.setStyle("-fx-background-color: #4338ca; -fx-text-fill: white; -fx-background-radius: 10; -fx-min-width: 20; -fx-min-height: 20; -fx-max-width: 20; -fx-max-height: 20; -fx-alignment: center; -fx-font-size: 10px; -fx-font-weight: bold;");
                stepNum.setAlignment(Pos.CENTER);

                Label stepText = new Label(item.steps.get(i));
                stepText.setWrapText(true);
                stepText.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
                HBox.setHgrow(stepText, Priority.ALWAYS);

                stepRow.getChildren().addAll(stepNum, stepText);
                detailsPanel.getChildren().add(stepRow);
            }
        }

        if (item.whyItHelps != null && !item.whyItHelps.isEmpty()) {
            Label whyHeader = new Label("Why It Helps");
            whyHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #334155; -fx-padding: 8 0 2 0;");

            Label whyText = new Label(item.whyItHelps);
            whyText.setWrapText(true);
            whyText.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-background-color: #e0e7ff; -fx-background-radius: 8; -fx-padding: 10;");

            detailsPanel.getChildren().addAll(whyHeader, whyText);
        }

        final boolean[] expanded = {false};

        Runnable toggleDetails = () -> {
            expanded[0] = !expanded[0];
            if (expanded[0]) {
                toggleArrow.setText("▲");
                detailsPanel.setManaged(true);
                detailsPanel.setVisible(true);
                detailsPanel.setMaxHeight(Region.USE_COMPUTED_SIZE);
                row.setStyle(item.completed ? completedRowStyle : defaultRowStyle);
            } else {
                toggleArrow.setText("▼");
                detailsPanel.setManaged(false);
                detailsPanel.setVisible(false);
                detailsPanel.setMaxHeight(0);
                row.setStyle(item.completed ? completedRowStyleClosed : defaultRowStyleClosed);
            }
        };

        // Consume on each child so the row click handler doesn't fire twice.
        toggleArrow.setOnMouseClicked(e -> { toggleDetails.run(); e.consume(); });
        titleLabel.setOnMouseClicked(e -> { toggleDetails.run(); e.consume(); });
        descLabel.setOnMouseClicked(e -> { toggleDetails.run(); e.consume(); });
        itemEmoji.setOnMouseClicked(e -> { toggleDetails.run(); e.consume(); });
        titleLabel.setStyle(titleLabel.getStyle() + " -fx-cursor: hand;");
        row.setStyle(row.getStyle() + " -fx-cursor: hand;");
        row.setOnMouseClicked(e -> {
            if (e.getTarget() == checkBox) return;
            toggleDetails.run();
            e.consume();
        });

        checkBox.setOnAction(e -> {
            boolean checked = checkBox.isSelected();
            item.completed = checked;
            User user = Main.getCurrentUser();
            if (user != null) {
                new Thread(() -> {
                    recService.setRecItemCompleted(user.getId(), item.key, checked);
                    Platform.runLater(() -> {
                        int done = 0;
                        for (RecSection sec : recData.sections) {
                            for (RecItem ri : sec.items) {
                                if (ri.completed) done++;
                            }
                        }
                        recData.completedToday = done;
                        updateProgress(done, recData.totalItems);

                        if (expanded[0]) {
                            row.setStyle(checked ? completedRowStyle : defaultRowStyle);
                        } else {
                            row.setStyle(checked ? completedRowStyleClosed : defaultRowStyleClosed);
                        }
                    });
                }).start();
            }
        });

        block.getChildren().addAll(row, detailsPanel);
        return block;
    }

    // Updates the day's progress bar + label, color-coded by completion %.
    private void updateProgress(int completed, int total) {
        if (total == 0) {
            progressLabel.setText("No recommendations available");
            progressBar.setProgress(0);
            return;
        }
        double pct = (double) completed / total;
        progressBar.setProgress(pct);
        progressLabel.setText(completed + " / " + total + " completed today");

        String color;
        if (pct >= 0.8) color = "#10b981";
        else if (pct >= 0.4) color = "#f59e0b";
        else color = "#64748b";
        progressLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
    }

    // Renders one bar per day for the past week showing completion counts.
    private void buildWeeklyStats(Map<String, Integer> weeklyStats, int totalItemsPerDay) {
        weeklyStatsContainer.getChildren().clear();

        if (weeklyStats.isEmpty()) {
            Label noData = new Label("No data yet. Start completing recommendations to see your progress!");
            noData.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
            weeklyStatsContainer.getChildren().add(noData);
            return;
        }

        int maxCompleted = weeklyStats.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        if (maxCompleted == 0) maxCompleted = 1;

        for (Map.Entry<String, Integer> entry : weeklyStats.entrySet()) {
            String dateStr = entry.getKey();
            int count = entry.getValue();

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);

            String dayName;
            try {
                LocalDate date = LocalDate.parse(dateStr);
                dayName = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                if (date.equals(LocalDate.now(java.time.ZoneId.of("Asia/Karachi")))) dayName = "Today";
            } catch (Exception e) {
                dayName = dateStr;
            }
            Label dayLabel = new Label(dayName);
            dayLabel.setMinWidth(50);
            dayLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-font-weight: bold;");

            StackPane barContainer = new StackPane();
            HBox.setHgrow(barContainer, Priority.ALWAYS);
            barContainer.setMinHeight(24);
            barContainer.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 6;");
            barContainer.setAlignment(Pos.CENTER_LEFT);

            double fillPct = (double) count / Math.max(totalItemsPerDay, maxCompleted);
            if (fillPct > 1) fillPct = 1;

            Region fill = new Region();
            fill.setMaxHeight(24);
            fill.setMinHeight(24);
            String barColor = count > 0 ? (fillPct >= 0.6 ? "#10b981" : "#60a5fa") : "transparent";
            fill.setStyle("-fx-background-color: " + barColor + "; -fx-background-radius: 6;");

            double finalFillPct = fillPct;
            barContainer.widthProperty().addListener((obs, oldW, newW) -> {
                fill.setPrefWidth(newW.doubleValue() * finalFillPct);
                fill.setMaxWidth(newW.doubleValue() * finalFillPct);
            });

            barContainer.getChildren().add(fill);
            StackPane.setAlignment(fill, Pos.CENTER_LEFT);

            Label countLabel = new Label(String.valueOf(count));
            countLabel.setMinWidth(30);
            countLabel.setAlignment(Pos.CENTER_RIGHT);
            countLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #1e293b; -fx-font-weight: bold;");

            row.getChildren().addAll(dayLabel, barContainer, countLabel);
            weeklyStatsContainer.getChildren().add(row);
        }
    }

    // Hex color string -> CSS rgba() with the supplied alpha.
    private String hexToRgba(String hex, double alpha) {
        if (hex == null || hex.length() < 7) return "rgba(148,163,184," + alpha + ")";
        try {
            Color c = Color.web(hex);
            return String.format("rgba(%d,%d,%d,%.2f)",
                    (int)(c.getRed() * 255), (int)(c.getGreen() * 255), (int)(c.getBlue() * 255), alpha);
        } catch (Exception e) {
            return "rgba(148,163,184," + alpha + ")";
        }
    }

}
