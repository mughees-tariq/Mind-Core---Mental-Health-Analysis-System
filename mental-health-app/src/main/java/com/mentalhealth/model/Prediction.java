package com.mentalhealth.model;

import java.util.Objects;

// One ML-model result row. Stored in the predictions table via
// PredictionRepository. Read by DashboardService, HistoryService,
// and MoodTrackerController to build charts and analytics.
public class Prediction {
    private int id;
    private int userId;
    private String inputText;
    private String prediction;
    private double confidence;
    private String severity;
    private String color;      // hex colour associated with the predicted mood
    private String createdAt;

    public Prediction() {}

    public Prediction(int userId, String inputText, String prediction,
                      double confidence, String severity, String color) {
        this.userId = userId;
        this.inputText = inputText;
        this.prediction = prediction;
        this.confidence = confidence;
        this.severity = severity;
        this.color = color;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = inputText; }

    public String getPrediction() { return prediction; }
    public void setPrediction(String prediction) { this.prediction = prediction; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Prediction that = (Prediction) o;
        return id == that.id && userId == that.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId);
    }

    @Override
    public String toString() {
        return "Prediction{id=" + id + ", prediction='" + prediction + "', confidence=" + confidence + "}";
    }
}
