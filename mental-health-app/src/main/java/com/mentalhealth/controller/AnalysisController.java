package com.mentalhealth.controller;
import com.mentalhealth.Main;
import com.mentalhealth.repository.PredictionRepository;
import com.mentalhealth.model.Prediction;
import com.mentalhealth.util.CardAnimations;
import com.mentalhealth.util.MoodUtils;
import com.mentalhealth.util.ValidationUtils;
import com.mentalhealth.service.MLAPIService;
import com.mentalhealth.service.MLAPIService.PredictionResult;
import com.mentalhealth.service.MLAPIService.TopPrediction;
import com.mentalhealth.repository.IUserRepository;
import com.mentalhealth.repository.UserRepository;
import com.mentalhealth.model.User;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.List;

import java.util.logging.Logger;

// Controller for AnalysisScreen.fxml. Sends user-entered text to the local
// Flask ML API via MLAPIService, displays the prediction + top-N breakdown,
// and writes the result through PredictionRepository / UserRepository so the
// dashboard, history, and mood tracker pick it up.
public class AnalysisController extends BaseController {

    private static final Logger LOG = Logger.getLogger(AnalysisController.class.getName());

    @FXML private BorderPane root;

    @Override
    protected BorderPane getRoot() { return root; }
    @FXML private TextArea inputTextArea;
    @FXML private Button analyzeButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private HBox apiStatusBox;
    @FXML private Label apiStatusDot;
    @FXML private Label apiStatusLabel;
    @FXML private Label errorLabel;
    @FXML private Label charCountLabel;

    @FXML private VBox resultsBox;
    @FXML private VBox heroCard;
    @FXML private Label resultEmoji;
    @FXML private Label predictionLabel;
    @FXML private Label confidenceLabel;
    @FXML private ProgressBar confidenceBar;
    @FXML private Label severityPill;
    @FXML private VBox predictionsContainer;
    @FXML private Label insightLabel;
    @FXML private Label nextStepLabel;

    private MLAPIService apiService;
    private IUserRepository userRepository;

    // FXML init. Pings the API for status, wires the live char counter, and
    // animates cards in.
    @FXML
    public void initialize() {
        apiService = new MLAPIService();
        userRepository = new UserRepository();
        checkAPIStatus();

        inputTextArea.textProperty().addListener((obs, oldVal, newVal) -> {
            int len = newVal != null ? newVal.length() : 0;
            charCountLabel.setText(len + " / 5000 character" + (len != 1 ? "s" : ""));
            if (len > 5000) {
                charCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #ef4444;");
            } else if (len < 10 && len > 0) {
                charCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #f59e0b;");
            } else {
                charCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
            }
        });

        CardAnimations.animateAll(root, ".card");
    }

    // Background ping to the Flask /health endpoint; updates the status pill.
    private void checkAPIStatus() {
        new Thread(() -> {
            boolean healthy = apiService.isHealthy();
            Platform.runLater(() -> {
                if (healthy) {
                    apiStatusLabel.setText("AI Model Connected");
                    apiStatusDot.setStyle("-fx-text-fill: #10b981; -fx-font-size: 10px;");
                    apiStatusLabel.setStyle("-fx-text-fill: #065f46; -fx-font-size: 12px; -fx-font-weight: bold;");
                    apiStatusBox.setStyle("-fx-background-color: #ecfdf5; -fx-background-radius: 20; -fx-padding: 6 16;");
                } else {
                    apiStatusLabel.setText("AI Not Connected - Start Flask API");
                    apiStatusDot.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 10px;");
                    apiStatusLabel.setStyle("-fx-text-fill: #991b1b; -fx-font-size: 12px; -fx-font-weight: bold;");
                    apiStatusBox.setStyle("-fx-background-color: #fef2f2; -fx-background-radius: 20; -fx-padding: 6 16;");
                }
            });
        }).start();
    }

    // Analyze button. Validates input and calls the ML API on a worker thread.
    @FXML
    public void handleAnalyze() {
        String text = inputTextArea.getText().trim();

        String validationErr = ValidationUtils.validateAnalysisText(text);
        if (validationErr != null) {
            showLocalError(validationErr);
            return;
        }

        if (text.length() < 10) {
            showLocalError("Please enter at least 10 characters for better analysis.");
            return;
        }

        setLoading(true);
        hideError();
        resultsBox.setVisible(false);

        new Thread(() -> {
            PredictionResult result = apiService.predict(text);

            Platform.runLater(() -> {
                setLoading(false);

                if (result.isSuccess()) {
                    displayResults(result);
                } else {
                    showLocalError("Analysis failed: " + result.getError());
                }
            });
        }).start();
    }

    // Persists the prediction so it appears in History and feeds the dashboard.
    private void savePredictionToDB(String inputText, PredictionResult result) {
        User currentUser = Main.getCurrentUser();
        if (currentUser == null) {
            LOG.info("No user logged in, skipping prediction DB save");
            return;
        }

        PredictionRepository predRepo = new PredictionRepository();
        Prediction prediction = new Prediction(
            currentUser.getId(),
            inputText,
            result.getPrediction(),
            result.getConfidence(),
            result.getSeverity(),
            result.getColor()
        );

        if (predRepo.save(prediction)) {
            LOG.info("Prediction saved to DB for mood tracking");
        }
    }

    // Renders the prediction hero card, top-N bars, and insight + next step.
    private void displayResults(PredictionResult result) {
        String color = result.getColor() != null ? result.getColor() : "#4f46e5";

        heroCard.setStyle("-fx-border-width: 0 0 0 5; -fx-border-radius: 15; -fx-border-color: " + color + ";");

        resultEmoji.setText(getMoodEmoji(result.getPrediction()));

        predictionLabel.setText(result.getPrediction());
        predictionLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        severityPill.setText(result.getSeverity());
        severityPill.setStyle("-fx-background-color: " + hexToRgba(color, 0.15) + "; -fx-text-fill: " + color + "; -fx-background-radius: 20; -fx-padding: 5 16; -fx-font-size: 12px; -fx-font-weight: bold;");

        double confPct = result.getConfidencePercent();
        confidenceLabel.setText(String.format("%.1f%%", confPct));
        confidenceBar.setProgress(result.getConfidence());

        predictionsContainer.getChildren().clear();
        for (TopPrediction pred : result.getTopPredictions()) {
            predictionsContainer.getChildren().add(createPredictionRow(pred));
        }

        insightLabel.setText(getInsightText(result.getPrediction(), result.getSeverity()));
        nextStepLabel.setText(getNextStepText(result.getPrediction()));

        resultsBox.setVisible(true);
        resultsBox.setManaged(true);

        saveFeelingHistory(inputTextArea.getText().trim(), java.util.Arrays.asList(result.getTopPredictions()));
        savePredictionToDB(inputTextArea.getText().trim(), result);

        CardAnimations.animateAll(root, ".card");
    }

    // Builds one row in the top-predictions list: dot, name, severity, %, bar.
    private VBox createPredictionRow(TopPrediction pred) {
        VBox row = new VBox(6);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10;");

        String predColor = pred.getColor() != null ? pred.getColor() : "#64748b";

        HBox topLine = new HBox(8);
        topLine.setAlignment(Pos.CENTER_LEFT);

        Label colorDot = new Label("●");
        colorDot.setStyle("-fx-text-fill: " + predColor + "; -fx-font-size: 14px;");

        Label nameLabel = new Label(pred.getClassName());
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label sevLabel = new Label(pred.getSeverity());
        sevLabel.setStyle("-fx-background-color: " + hexToRgba(predColor, 0.12) + "; -fx-text-fill: " + predColor + "; -fx-background-radius: 10; -fx-padding: 2 8; -fx-font-size: 10px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label pctLabel = new Label(String.format("%.1f%%", pred.getProbabilityPercent()));
        pctLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + predColor + ";");

        topLine.getChildren().addAll(colorDot, nameLabel, sevLabel, spacer, pctLabel);

        StackPane barContainer = new StackPane();
        barContainer.setMinHeight(8);
        barContainer.setMaxHeight(8);
        barContainer.setStyle("-fx-background-color: #e2e8f0; -fx-background-radius: 4;");
        barContainer.setAlignment(Pos.CENTER_LEFT);

        Region barFill = new Region();
        barFill.setMinHeight(8);
        barFill.setMaxHeight(8);
        barFill.setStyle("-fx-background-color: " + predColor + "; -fx-background-radius: 4;");

        double prob = pred.getProbability();
        barContainer.widthProperty().addListener((obs, oldW, newW) -> {
            double w = newW.doubleValue() * prob;
            barFill.setPrefWidth(w);
            barFill.setMaxWidth(w);
        });

        barContainer.getChildren().add(barFill);
        StackPane.setAlignment(barFill, Pos.CENTER_LEFT);

        row.getChildren().addAll(topLine, barContainer);
        return row;
    }

    // Appends the analyzed text + top moods to the user's feeling_history blob.
    private void saveFeelingHistory(String inputText, List<TopPrediction> topPredictions) {
        User currentUser = Main.getCurrentUser();

        if (currentUser == null) {
            LOG.info("No user logged in, skipping feeling history save");
            return;
        }

        StringBuilder historyEntry = new StringBuilder();
        historyEntry.append(inputText).append(" (");

        for (int i = 0; i < Math.min(3, topPredictions.size()); i++) {
            if (i > 0) {
                historyEntry.append(", ");
            }
            historyEntry.append(topPredictions.get(i).getClassName());
        }

        historyEntry.append(")");

        String newEntry = historyEntry.toString();
        boolean success = userRepository.updateFeelingHistory(currentUser.getId(), newEntry);

        if (success) {
            String updatedHistory = currentUser.getFeelingHistory() == null ||
                                currentUser.getFeelingHistory().isEmpty()
                                ? newEntry
                                : currentUser.getFeelingHistory() + "; " + newEntry;

            currentUser.setFeelingHistory(updatedHistory);
            LOG.info("History synced to Session and DB: " + newEntry);
        }
        else {
            LOG.warning("Database failed to save history");
            showLocalError("History saved locally, but database sync failed.");
        }
    }

    private String getMoodEmoji(String prediction) { return MoodUtils.getMoodEmoji(prediction); }

    // Picks a hardcoded explanation paragraph based on the top prediction.
    private String getInsightText(String prediction, String severity) {
        if (prediction == null) return "";
        switch (prediction.toLowerCase()) {
            case "anxiety":
                return "Your text suggests signs of anxiety. This is a common experience that many people face. The key is recognizing these patterns and developing coping strategies.";
            case "depression":
                return "Your text indicates possible depressive patterns. Remember that seeking help is a sign of strength. Consider talking to a professional for personalized support.";
            case "stress":
                return "Your text shows indicators of stress. While some stress is normal, persistent stress can impact your well-being. Mindfulness and boundary-setting can help.";
            case "normal":
                return "Your mental health indicators appear healthy. Continue maintaining your current habits and stay mindful of any changes in your emotional well-being.";
            case "suicidal":
                return "Your text contains concerning indicators. Please reach out to a crisis helpline immediately. You are not alone, and help is available 24/7.";
            default:
                return "Based on the analysis, we recommend monitoring your mental health regularly and consulting with a professional if you have concerns.";
        }
    }

    // Picks the suggested next-step blurb shown under the insight.
    private String getNextStepText(String prediction) {
        if (prediction == null) return "";
        switch (prediction.toLowerCase()) {
            case "anxiety":
                return "Try the breathing exercises on the Dashboard, or visit Recommendations for personalized calming techniques.";
            case "depression":
                return "Check out the Recommendations page for mood-boosting activities tailored to your current state.";
            case "stress":
                return "Visit the Mood Tracker to log your feelings, and explore stress-relief exercises in Recommendations.";
            case "normal":
                return "Keep tracking your mood regularly! Check the Dashboard for your progress trends and streaks.";
            case "suicidal":
                return "Please contact a crisis helpline: 988 Suicide & Crisis Lifeline (call or text 988). You matter.";
            default:
                return "Visit the Recommendations page for personalized activities based on your analysis results.";
        }
    }

    // Converts a hex color string to a CSS rgba() with the given alpha.
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

    @FXML
    public void handleClear() {
        inputTextArea.clear();
        resultsBox.setVisible(false);
        hideError();
    }

    // Toggles the analyze-button busy state.
    private void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        analyzeButton.setDisable(loading);
        if (loading) {
            analyzeButton.setText("Analyzing...");
            analyzeButton.setStyle("-fx-background-color: #94a3b8; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: wait; -fx-background-radius: 10; -fx-padding: 0 28;");
        } else {
            analyzeButton.setText("🔍 Analyze My Feelings");
            analyzeButton.setStyle("-fx-background-color: linear-gradient(to right, #4f46e5, #7c3aed); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10; -fx-padding: 0 28;");
        }
    }

    private void showLocalError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
    }

}
