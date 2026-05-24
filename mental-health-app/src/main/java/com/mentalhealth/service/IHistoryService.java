package com.mentalhealth.service;

import com.mentalhealth.model.HistoryEntry;
import com.mentalhealth.model.HistoryFilter;
import java.util.List;
import java.util.Map;

// Contract for history-related business logic
public interface IHistoryService {

    // Returns paginated history entries with optional filters applied
    List<HistoryEntry> getHistory(int userId, HistoryFilter filter, int limit, int offset);

    // Returns total count of history entries matching the filter
    int getHistoryCount(int userId, HistoryFilter filter);

    // Toggles starred state of a prediction entry; returns true if succeeded
    boolean toggleStarred(int predictionId, int userId);

    // Updates tags for a prediction entry; returns true if succeeded
    boolean updateTags(int predictionId, int userId, List<String> tags);

    // Updates user notes for a prediction entry; returns true if succeeded
    boolean updateNotes(int predictionId, int userId, String notes);

    // Returns all distinct tags used by the user
    List<String> getAllTags(int userId);

    // Deletes specified prediction entries; returns true if succeeded
    boolean deleteEntries(int userId, List<Integer> predictionIds);

    // Exports filtered history to CSV string
    String exportToCSV(int userId, HistoryFilter filter);

    // Returns aggregate statistics for user's history
    Map<String, Object> getStats(int userId);

    // Returns mood distribution (label → count) for the user
    Map<String, Integer> getMoodDistribution(int userId, HistoryFilter filter);
}
