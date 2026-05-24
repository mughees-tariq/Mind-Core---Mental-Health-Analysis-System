package com.mentalhealth.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Enriched view of a Prediction row. HistoryService layers on starred state,
// user-written tags (JSON array in history_meta), and personal notes so the
// HistoryController can render them without extra DB calls.
public class HistoryEntry {
    private int id;
    private int userId;
    private String inputText;
    private String prediction;
    private double confidence;
    private String severity;
    private String color;
    private LocalDateTime createdAt;
    private boolean starred;
    private List<String> tags;
    private String userNotes;
    private String originalPrediction;

    public HistoryEntry() {
        this.tags = new ArrayList<>();
        this.starred = false;
    }

    // Convenience constructor for wrapping a raw Prediction from the DB.
    public HistoryEntry(Prediction p) {
        this.id = p.getId();
        this.userId = p.getUserId();
        this.inputText = p.getInputText();
        this.prediction = p.getPrediction();
        this.confidence = p.getConfidence();
        this.severity = p.getSeverity();
        this.color = p.getColor();
        this.createdAt = parseDateTime(p.getCreatedAt());
        this.tags = new ArrayList<>();
        this.starred = false;
        this.originalPrediction = p.getPrediction();
    }

    // Normalises the SQLite timestamp string to LocalDateTime.
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null) return LocalDateTime.now(java.time.ZoneId.of("Asia/Karachi"));
        try {
            if (dateTimeStr.contains(" ")) {
                dateTimeStr = dateTimeStr.replace(" ", "T");
            }
            if (dateTimeStr.length() > 19) {
                dateTimeStr = dateTimeStr.substring(0, 19);
            }
            LocalDateTime utc = LocalDateTime.parse(dateTimeStr);
            return utc.atZone(java.time.ZoneOffset.UTC)
                    .withZoneSameInstant(java.time.ZoneId.of("Asia/Karachi"))
                    .toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now(java.time.ZoneId.of("Asia/Karachi"));
        }
    }

    public String getFormattedDate() {
        return createdAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }

    public String getFormattedTime() {
        return createdAt.format(DateTimeFormatter.ofPattern("h:mm a"));
    }

    // Returns "Today" / "Yesterday" / formatted date for display in card headers.
    public String getRelativeDate() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Karachi"));
        if (createdAt.toLocalDate().equals(now.toLocalDate())) {
            return "Today";
        } else if (createdAt.toLocalDate().equals(now.minusDays(1).toLocalDate())) {
            return "Yesterday";
        } else {
            return getFormattedDate();
        }
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
    public double getConfidencePercent() { return confidence * 100; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isStarred() { return starred; }
    public void setStarred(boolean starred) { this.starred = starred; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public void addTag(String tag) { this.tags.add(tag); }

    public String getUserNotes() { return userNotes; }
    public void setUserNotes(String userNotes) { this.userNotes = userNotes; }

    public String getOriginalPrediction() { return originalPrediction; }
    public void setOriginalPrediction(String originalPrediction) { this.originalPrediction = originalPrediction; }

    // First 120 chars of the input text; used for card preview labels.
    public String getPreviewText() {
        if (inputText == null || inputText.isEmpty()) return "No text provided";
        if (inputText.length() <= 120) return inputText;
        return inputText.substring(0, 117) + "...";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoryEntry that = (HistoryEntry) o;
        return id == that.id && userId == that.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId);
    }

    @Override
    public String toString() {
        return "HistoryEntry{id=" + id + ", prediction='" + prediction + "', createdAt=" + createdAt + "}";
    }
}
