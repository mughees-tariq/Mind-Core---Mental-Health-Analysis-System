package com.mentalhealth.repository;

import com.mentalhealth.model.Prediction;
import java.util.List;

// Contract for prediction persistence operations
public interface IPredictionRepository {

    // Persists a prediction; returns true if succeeded
    boolean save(Prediction prediction);

    // Returns predictions from last 7 days, ordered chronologically
    List<Prediction> getLast7Days(int userId);

    // Returns all predictions for user, ordered by creation date descending
    List<Prediction> getAllByUser(int userId);

    // Returns predictions from last 30 days, ordered chronologically
    List<Prediction> getLast30Days(int userId);

    // Returns predictions from 7-14 days ago, ordered chronologically
    List<Prediction> getPriorWeek(int userId);

    // Saves a generated consoling report for the specified period; returns true if succeeded
    boolean saveReport(int userId, String reportText, String periodStart, String periodEnd);
}
