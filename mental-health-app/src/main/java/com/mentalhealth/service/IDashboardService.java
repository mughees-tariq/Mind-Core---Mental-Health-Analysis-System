package com.mentalhealth.service;

import com.mentalhealth.model.Prediction;
import com.mentalhealth.service.DashboardService.*;
import java.util.List;
import java.util.Map;

// Contract for dashboard data retrieval and self-care tracking
public interface IDashboardService {

    // Assembles all dashboard metrics for the user
    DashboardData getDashboardData(int userId);

    // Returns user's predictions from the last 7 days
    List<Prediction> getLast7DaysPredictions(int userId);

    // Returns all predictions for the user
    List<Prediction> getAllPredictions(int userId);

    // Returns user's predictions from the last 30 days
    List<Prediction> getLast30DaysPredictions(int userId);

    // Computes analytics summary (averages, counts) for the user
    AnalyticsSummary getAnalyticsSummary(int userId);

    // Generates weekly report card comparing this week vs prior week
    WeeklyReportCard getWeeklyReportCard(int userId);

    // Returns mood-heatmap data mapping date strings to mood scores
    Map<String, Double> getMoodHeatmapData(int userId, int months);

    // Returns contextual daily self-care tip for the given mood
    String getDailyTip(String dominantMood);

    // Returns daily tip with index offset for variety
    String getDailyTipWithOffset(String dominantMood, int offset);

    // Returns today's self-care checklist state
    SelfCareState getSelfCareState(int userId);

    // Toggles a self-care checklist item for today
    void setSelfCareItem(int userId, String itemKey, boolean checked);

    // Returns help-line strings appropriate for the given risk level
    List<String> getResourceLines(String riskLevel);
}
