package com.mentalhealth.service;

import com.mentalhealth.service.RecommendationService.*;
import java.util.Map;
import java.util.Set;

// Contract for personalised recommendation generation
public interface IRecommendationService {

    // Generates personalised recommendations based on user's mood history
    RecommendationData getRecommendations(int userId);

    // Returns set of recommendation item keys completed today
    Set<String> getTodayProgress(int userId);

    // Marks recommendation item as completed or not for today
    void setRecItemCompleted(int userId, String itemKey, boolean completed);

    // Returns weekly recommendation completion statistics (day → count)
    Map<String, Integer> getWeeklyStats(int userId);
}
