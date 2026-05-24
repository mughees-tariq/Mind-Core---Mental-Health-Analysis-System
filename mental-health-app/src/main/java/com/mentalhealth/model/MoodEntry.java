package com.mentalhealth.model;

import java.util.Objects;

// A manually recorded mood score for a user on a specific date.
// Stored in the mood_entries table; currently used by DashboardService
// for the self-care and streak calculations.
public class MoodEntry {
    private int id;
    private int userId;
    private int moodScore;
    private String notes;
    private String entryDate;
    private String createdAt;

    public MoodEntry() {}

    public MoodEntry(int userId, int moodScore, String notes, String entryDate) {
        this.userId = userId;
        this.moodScore = moodScore;
        this.notes = notes;
        this.entryDate = entryDate;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getMoodScore() { return moodScore; }
    public void setMoodScore(int moodScore) { this.moodScore = moodScore; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getEntryDate() { return entryDate; }
    public void setEntryDate(String entryDate) { this.entryDate = entryDate; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MoodEntry entry = (MoodEntry) o;
        return id == entry.id && userId == entry.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId);
    }

    @Override
    public String toString() {
        return "MoodEntry{id=" + id + ", moodScore=" + moodScore + ", entryDate='" + entryDate + "'}";
    }
}
