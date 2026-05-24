package com.mentalhealth.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mentalhealth.Main;
import com.mentalhealth.model.User;
import com.mentalhealth.service.DashboardService;
import com.mentalhealth.util.MoodUtils;
import com.mentalhealth.service.DashboardService.DashboardData;
import com.mentalhealth.service.DashboardService.ChartPoint;
import com.mentalhealth.service.DashboardService.MoodFrequency;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import com.mentalhealth.util.CardAnimations;

import java.util.logging.Logger;

// Controller for MainWindow.fxml (the dashboard). Pulls aggregated metrics for
// the logged-in user from DashboardService — which in turn reads predictions,
// mood entries, and self-care state from the SQLite DB — and renders the
// metric cards, mood breakdown, journey strip, daily tip, breathing exercise,
// self-care checklist, resources, and notifications.
public class MainController extends BaseController {

    private static final Logger LOG = Logger.getLogger(MainController.class.getName());

    @FXML private BorderPane root;

    @Override
    protected BorderPane getRoot() { return root; }

    @FXML private VBox signalCard;

    @FXML private Label welcomeLabel;
    @FXML private Label screeningLabel;

    @FXML private Label totalAnalysesLabel;
    @FXML private Label streakLabel;
    @FXML private Label dominantMoodLabel;
    @FXML private Label riskLevelLabel;

    @FXML private Label trendIconLabel;
    @FXML private Label trendTitleLabel;
    @FXML private Label trendDescLabel;
    @FXML private VBox moodDistContainer;
    @FXML private VBox journeyStrip;
    @FXML private VBox journeyOverflow;
    @FXML private Button journeyToggleBtn;

    @FXML private Label latestPredLabel;
    @FXML private Label latestConfLabel;
    @FXML private Label latestSeverityLabel;

    @FXML private VBox breathingCard;
    @FXML private StackPane breathingCircleContainer;
    @FXML private Label breathingPhaseLabel;
    @FXML private Button breathingToggleBtn;

    @FXML private Label dailyTipLabel;
    @FXML private Label tipMoodLabel;

    @FXML private CheckBox checkSleep;
    @FXML private CheckBox checkWater;
    @FXML private CheckBox checkExercise;
    @FXML private CheckBox checkOutside;
    @FXML private CheckBox checkSocial;
    @FXML private Label selfCareCountLabel;

    @FXML private VBox resourceContainer;

    @FXML private Button notificationBtn;
    @FXML private Label notifBadge;
    @FXML private VBox notificationPanel;
    @FXML private VBox notifContainer;

    private DashboardService dashboardService;

    private Circle breathingCircle;
    private Timeline breathingTimeline;
    private boolean breathingActive = false;
    private static final double CIRCLE_MIN_RADIUS = 28.0;
    private static final double CIRCLE_MAX_RADIUS = 52.0;

    private int tipRefreshOffset = 0;
    private String currentTipMood = "Normal";

    // FXML init. Wires the dashboard service, sets up the breathing circle,
    // stops the timeline on scene teardown, and kicks off the data load.
    @FXML
    public void initialize() {
        LOG.info("MainController initialized");
        dashboardService = new DashboardService();

        setupBreathingCircle();

        if (root != null) {
            root.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene == null && breathingTimeline != null) {
                    breathingTimeline.stop();
                }
            });
        }

        CardAnimations.animateAll(root, ".card", ".card-blue");

        loadDashboardData();
    }

    // Fetches dashboard data on a worker thread so the FX thread stays smooth.
    private void loadDashboardData() {
        User currentUser = Main.getCurrentUser();

        if (currentUser != null) {
            welcomeLabel.setText("Hello " + currentUser.getName() + "! 👋");
        } else {
            welcomeLabel.setText("Hello! 👋");
            screeningLabel.setText("Please log in to see your dashboard");
            return;
        }

        new Thread(() -> {
            DashboardData data = dashboardService.getDashboardData(currentUser.getId());

            Platform.runLater(() -> populateDashboard(data));
        }).start();
    }

    // Fills every dashboard widget from the service result.
    private void populateDashboard(DashboardData data) {

        if (data.totalAnalyses == 0) {
            screeningLabel.setText("No analyses yet — go to New Analysis to get started!");
        } else {
            String summary = data.latestPrediction + " detected (Last: " + data.lastUpdateTime + ")";
            screeningLabel.setText(summary);
        }

        totalAnalysesLabel.setText(String.valueOf(data.totalAnalyses));

        if (data.streakDays > 0) {
            streakLabel.setText(data.streakDays + (data.streakDays == 1 ? " day" : " days"));
            if (data.streakDays >= 7) {
                streakLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #10b981;");
            }
        } else {
            streakLabel.setText("0 days");
        }

        dominantMoodLabel.setText(data.dominantMood);
        dominantMoodLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + data.dominantMoodColor + ";");

        riskLevelLabel.setText(data.riskLevel);
        riskLevelLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + data.riskColor + ";");

        trendIconLabel.setText(data.trendIcon);
        trendTitleLabel.setText("Overall Trend");
        trendDescLabel.setText("");

        populateMoodDistribution(data);

        latestPredLabel.setText(data.latestPrediction);
        latestPredLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + data.latestColor + ";");

        if (data.latestConfidence > 0) {
            latestConfLabel.setText(String.format("Confidence: %.1f%%", data.latestConfidence * 100));
        } else {
            latestConfLabel.setText("Confidence: —");
        }

        latestSeverityLabel.setText("Severity: " + data.latestSeverity);

        currentTipMood = data.tipMood != null ? data.tipMood : "Normal";
        if (dailyTipLabel != null) {
            dailyTipLabel.setText(data.dailyTip != null ? data.dailyTip : "No tip available");
        }
        if (tipMoodLabel != null) {
            tipMoodLabel.setText(currentTipMood);
        }

        if (data.selfCare != null) {
            populateSelfCare(data.selfCare);
        }

        populateResources(data.resourceLines, data.riskLevel);

        populateNotifications(data);
    }

    // Renders one horizontal bar per mood, sized by this week's frequency.
    private void populateMoodDistribution(DashboardData data) {
        if (moodDistContainer == null) return;
        moodDistContainer.getChildren().clear();

        if (data.moodBreakdown == null || data.moodBreakdown.isEmpty()) {
            Label empty = new Label("No analyses this week — start tracking to see your mood breakdown");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            moodDistContainer.getChildren().add(empty);
            return;
        }

        for (MoodFrequency mf : data.moodBreakdown) {
            HBox row = new HBox(10);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            String emoji = getMoodEmoji(mf.mood);
            Label nameLabel = new Label(emoji + " " + mf.mood);
            nameLabel.setMinWidth(140);
            nameLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #334155;");

            StackPane barBg = new StackPane();
            barBg.setMinHeight(14);
            barBg.setMaxHeight(14);
            barBg.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 7;");
            HBox.setHgrow(barBg, Priority.ALWAYS);
            barBg.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Region barFill = new Region();
            barFill.setMinHeight(14);
            barFill.setMaxHeight(14);
            barFill.setStyle("-fx-background-color: " + mf.color + "; -fx-background-radius: 7;");

            barBg.widthProperty().addListener((obs, oldW, newW) -> {
                double w = newW.doubleValue() * (mf.percentage / 100.0);
                barFill.setPrefWidth(Math.max(w, 4));
                barFill.setMaxWidth(Math.max(w, 4));
            });

            barBg.getChildren().add(barFill);
            StackPane.setAlignment(barFill, javafx.geometry.Pos.CENTER_LEFT);

            Label pctLabel = new Label(mf.count + " (" + String.format("%.0f%%", mf.percentage) + ")");
            pctLabel.setMinWidth(60);
            pctLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + mf.color + ";");

            row.getChildren().addAll(nameLabel, barBg, pctLabel);
            moodDistContainer.getChildren().add(row);
        }
    }

    private static final int JOURNEY_VISIBLE_COUNT = 3;
    private boolean journeyExpanded = false;

    // Renders the weekly journey timeline. Shows the latest 3 entries; the
    // toggle reveals the rest.
    private void populateJourneyStrip(DashboardData data) {
        if (journeyStrip == null) return;
        journeyStrip.getChildren().clear();
        if (journeyOverflow != null) journeyOverflow.getChildren().clear();

        if (data.last7DaysChart == null || data.last7DaysChart.isEmpty()) {
            Label empty = new Label("No entries yet");
            empty.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 11px;");
            journeyStrip.getChildren().add(empty);
            if (journeyToggleBtn != null) journeyToggleBtn.setVisible(false);
            return;
        }

        int total = data.last7DaysChart.size();
        boolean hasOverflow = total > JOURNEY_VISIBLE_COUNT;

        if (journeyToggleBtn != null) {
            journeyToggleBtn.setVisible(hasOverflow);
            journeyToggleBtn.setText("Show All (" + total + ")");
            journeyExpanded = false;
            journeyToggleBtn.setOnAction(e -> {
                journeyExpanded = !journeyExpanded;
                journeyOverflow.setVisible(journeyExpanded);
                journeyOverflow.setManaged(journeyExpanded);
                journeyToggleBtn.setText(journeyExpanded
                    ? "Show Less"
                    : "Show All (" + total + ")");
            });
        }

        for (int i = 0; i < total; i++) {
            ChartPoint cp = data.last7DaysChart.get(i);
            boolean isLast = (i == total - 1);
            HBox row = buildJourneyRow(cp, isLast);

            if (i < JOURNEY_VISIBLE_COUNT) {
                journeyStrip.getChildren().add(row);
            } else if (journeyOverflow != null) {
                journeyOverflow.getChildren().add(row);
            }
        }
    }

    // Builds a single dot + connector + label row for the journey timeline.
    private HBox buildJourneyRow(ChartPoint cp, boolean isLast) {
        String color = MoodUtils.getMoodColor(cp.mood);
        String emoji = getMoodEmoji(cp.mood);

        HBox row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        VBox dotCol = new VBox(0);
        dotCol.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        dotCol.setMinWidth(20);
        dotCol.setMaxWidth(20);

        Circle circle = new Circle(6);
        circle.setFill(Color.web(color));
        circle.setStroke(Color.web(color, 0.4));
        circle.setStrokeWidth(2);
        dotCol.getChildren().add(circle);

        if (!isLast) {
            Region line = new Region();
            line.setMinWidth(2);
            line.setMaxWidth(2);
            line.setMinHeight(16);
            line.setStyle("-fx-background-color: " + color + "33;");
            dotCol.getChildren().add(line);
            VBox.setMargin(line, new javafx.geometry.Insets(2, 0, 0, 0));
        }

        Label moodLabel = new Label(emoji + " " + cp.mood + "  ·  " + cp.dateLabel);
        moodLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        row.getChildren().addAll(dotCol, moodLabel);
        return row;
    }

    private String getMoodEmoji(String mood) { return MoodUtils.getMoodEmoji(mood); }

    // Fills the notification list and updates the badge count on the bell icon.
    private void populateNotifications(DashboardData data) {
        if (notifContainer == null) return;
        notifContainer.getChildren().clear();
        if (data.notifications.isEmpty()) {
            Label noNotif = new Label("✅ All caught up! No new notifications.");
            noNotif.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px; -fx-padding: 8;");
            notifContainer.getChildren().add(noNotif);

            notifBadge.setVisible(false);
            notifBadge.setManaged(false);
        } else {
            for (String notif : data.notifications) {
                Label notifLabel = new Label(notif);
                notifLabel.setWrapText(true);
                notifLabel.setStyle(
                    "-fx-font-size: 13px; -fx-padding: 8 12; -fx-background-color: #f8fafc; " +
                    "-fx-background-radius: 8; -fx-text-fill: #1e293b;"
                );
                notifContainer.getChildren().add(notifLabel);
            }

            notifBadge.setText(String.valueOf(data.notifications.size()));
            notifBadge.setVisible(true);
            notifBadge.setManaged(true);
        }
    }

    // Builds the breathing circle node and adds it to its container.
    private void setupBreathingCircle() {
        breathingCircle = new Circle(CIRCLE_MIN_RADIUS);
        breathingCircle.setFill(Color.web("#c7d2fe"));
        breathingCircle.setStroke(Color.web("#4f46e5"));
        breathingCircle.setStrokeWidth(2.5);
        breathingCircle.setEffect(new DropShadow(15, 0, 0, Color.rgb(79, 70, 229, 0.3)));

        if (breathingCircleContainer != null) {
            breathingCircleContainer.getChildren().add(breathingCircle);
        }
    }

    // Start/stop button for the breathing exercise. Resets the circle on stop.
    @FXML
    public void handleBreathingToggle() {
        if (breathingActive) {
            breathingActive = false;
            if (breathingTimeline != null) {
                breathingTimeline.stop();
            }
            if (breathingCircle != null) {
                breathingCircle.setRadius(CIRCLE_MIN_RADIUS);
                breathingCircle.setFill(Color.web("#c7d2fe"));
            }
            breathingPhaseLabel.setText("Press Start");
            breathingPhaseLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b; -fx-font-weight: bold;");
            breathingToggleBtn.setText("▶  Start");
            breathingToggleBtn.setStyle(
                "-fx-background-color: #4f46e5; -fx-text-fill: white; " +
                "-fx-font-size: 12px; -fx-background-radius: 8; -fx-cursor: hand;");
        } else {
            breathingActive = true;
            breathingToggleBtn.setText("⏹  Stop");
            breathingToggleBtn.setStyle(
                "-fx-background-color: #ef4444; -fx-text-fill: white; " +
                "-fx-font-size: 12px; -fx-background-radius: 8; -fx-cursor: hand;");
            startBreathingCycle();
        }
    }

    // Box-breathing timeline: 4s inhale, 4s hold, 4s exhale, 4s hold; loops.
    private void startBreathingCycle() {
        if (breathingCircle == null) return;

        breathingTimeline = new Timeline();
        breathingTimeline.setCycleCount(Timeline.INDEFINITE);

        KeyFrame kf0 = new KeyFrame(Duration.ZERO,
            new KeyValue(breathingCircle.radiusProperty(), CIRCLE_MIN_RADIUS, Interpolator.EASE_BOTH));
        KeyFrame kf0label = new KeyFrame(Duration.millis(1), e -> {
            breathingPhaseLabel.setText("Inhale...");
            breathingPhaseLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #4f46e5; -fx-font-weight: bold;");
            breathingCircle.setFill(Color.web("#a5b4fc"));
        });
        KeyFrame kf4 = new KeyFrame(Duration.millis(4000),
            new KeyValue(breathingCircle.radiusProperty(), CIRCLE_MAX_RADIUS, Interpolator.EASE_BOTH));

        KeyFrame kf4label = new KeyFrame(Duration.millis(4001), e -> {
            breathingPhaseLabel.setText("Hold...");
            breathingPhaseLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #10b981; -fx-font-weight: bold;");
            breathingCircle.setFill(Color.web("#86efac"));
        });
        KeyFrame kf8 = new KeyFrame(Duration.millis(8000),
            new KeyValue(breathingCircle.radiusProperty(), CIRCLE_MAX_RADIUS, Interpolator.LINEAR));

        KeyFrame kf8label = new KeyFrame(Duration.millis(8001), e -> {
            breathingPhaseLabel.setText("Exhale...");
            breathingPhaseLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #f59e0b; -fx-font-weight: bold;");
            breathingCircle.setFill(Color.web("#fde68a"));
        });
        KeyFrame kf12 = new KeyFrame(Duration.millis(12000),
            new KeyValue(breathingCircle.radiusProperty(), CIRCLE_MIN_RADIUS, Interpolator.EASE_BOTH));

        KeyFrame kf12label = new KeyFrame(Duration.millis(12001), e -> {
            breathingPhaseLabel.setText("Hold...");
            breathingPhaseLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #10b981; -fx-font-weight: bold;");
            breathingCircle.setFill(Color.web("#c7d2fe"));
        });
        KeyFrame kf16 = new KeyFrame(Duration.millis(16000),
            new KeyValue(breathingCircle.radiusProperty(), CIRCLE_MIN_RADIUS, Interpolator.LINEAR));

        breathingTimeline.getKeyFrames().addAll(
            kf0, kf0label, kf4, kf4label, kf8, kf8label, kf12, kf12label, kf16);
        breathingTimeline.play();
    }

    // Asks the service for the next tip variant. Offset gives a fresh tip
    // even when the mood hasn't changed.
    @FXML
    public void handleRefreshTip() {
        tipRefreshOffset++;
        new Thread(() -> {
            String tip = dashboardService.getDailyTipWithOffset(currentTipMood, tipRefreshOffset);
            Platform.runLater(() -> {
                if (dailyTipLabel != null) {
                    dailyTipLabel.setText(tip);
                }
            });
        }).start();
    }

    // Persists the current checklist state and updates the X/5 counter.
    @FXML
    public void handleChecklistToggle() {
        User user = Main.getCurrentUser();
        if (user == null) return;

        Map<String, Boolean> currentState = new LinkedHashMap<>();
        currentState.put("sleep",    checkSleep    != null && checkSleep.isSelected());
        currentState.put("water",    checkWater    != null && checkWater.isSelected());
        currentState.put("exercise", checkExercise != null && checkExercise.isSelected());
        currentState.put("outside",  checkOutside  != null && checkOutside.isSelected());
        currentState.put("social",   checkSocial   != null && checkSocial.isSelected());

        new Thread(() -> {
            for (Map.Entry<String, Boolean> entry : currentState.entrySet()) {
                dashboardService.setSelfCareItem(user.getId(), entry.getKey(), entry.getValue());
            }
            long checked = currentState.values().stream().filter(v -> v).count();
            Platform.runLater(() -> {
                if (selfCareCountLabel != null) {
                    selfCareCountLabel.setText(checked + " / 5");
                    String color = checked == 5 ? "#10b981" : checked >= 3 ? "#f59e0b" : "#ef4444";
                    selfCareCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
                }
            });
        }).start();
    }

    // Mirrors the persisted self-care state into the checkboxes.
    private void populateSelfCare(DashboardService.SelfCareState state) {
        if (checkSleep    != null) checkSleep.setSelected(state.slept);
        if (checkWater    != null) checkWater.setSelected(state.water);
        if (checkExercise != null) checkExercise.setSelected(state.exercise);
        if (checkOutside  != null) checkOutside.setSelected(state.outside);
        if (checkSocial   != null) checkSocial.setSelected(state.social);

        int count = state.completedCount();
        if (selfCareCountLabel != null) {
            selfCareCountLabel.setText(count + " / 5");
            String color = count == 5 ? "#10b981" : count >= 3 ? "#f59e0b" : "#64748b";
            selfCareCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
        }
    }

    // Renders helpline lines. Lines wrapped in *** are styled as crisis alerts.
    private void populateResources(List<String> lines, String riskLevel) {
        if (resourceContainer == null || lines == null) return;
        resourceContainer.getChildren().clear();

        for (String line : lines) {
            Label lbl = new Label(line);
            lbl.setWrapText(true);

            if (line.startsWith("***")) {
                String cleanLine = line.replace("***", "").trim();
                lbl.setText("⚠ " + cleanLine);
                lbl.setStyle(
                    "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #991b1b; " +
                    "-fx-background-color: #fef2f2; -fx-padding: 4 8; -fx-background-radius: 6;");
            } else {
                lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");
            }

            resourceContainer.getChildren().add(lbl);
        }
    }

    // Notification bell handler. Toggles the dropdown panel with a fade-in.
    @FXML
    public void handleNotifications() {
        boolean isVisible = notificationPanel.isVisible();
        notificationPanel.setVisible(!isVisible);
        notificationPanel.setManaged(!isVisible);

        if (!isVisible) {
            FadeTransition fade = new FadeTransition(Duration.millis(200), notificationPanel);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        }
    }

}
