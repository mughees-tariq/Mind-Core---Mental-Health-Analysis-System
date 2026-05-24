package com.mentalhealth.service;

import com.mentalhealth.model.HistoryEntry;
import com.mentalhealth.model.HistoryFilter;
import com.mentalhealth.repository.PredictionRepository;
import com.mentalhealth.repository.HistoryRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

// Service implementing history retrieval, filtering, and metadata management
public class HistoryService implements IHistoryService {

    private static final Logger LOG = Logger.getLogger(HistoryService.class.getName());
    
    private final PredictionRepository predictionRepo;
    private final HistoryRepository historyRepo;
    
    public HistoryService() {
        this.predictionRepo = new PredictionRepository();
        this.historyRepo = new HistoryRepository();
    }
    
    // Get all history entries for a user with applied filters
    public List<HistoryEntry> getHistory(int userId, HistoryFilter filter, int limit, int offset) {
        return historyRepo.getHistory(userId, filter, limit, offset);
    }
    
    // Get total count for pagination
    public int getHistoryCount(int userId, HistoryFilter filter) {
        return historyRepo.getHistoryCount(userId, filter);
    }
    
    // Toggle starred status
    public boolean toggleStarred(int predictionId, int userId) {
        return historyRepo.toggleStarred(predictionId, userId);
    }
    
    // Update tags for an entry
    public boolean updateTags(int predictionId, int userId, List<String> tags) {
        return historyRepo.updateTags(predictionId, userId, tags);
    }
    
    // Update user notes
    public boolean updateNotes(int predictionId, int userId, String notes) {
        return historyRepo.updateNotes(predictionId, userId, notes);
    }
    
    // Get all available tags for a user
    public List<String> getAllTags(int userId) {
        return historyRepo.getAllTags(userId);
    }
    
    // Delete entries (bulk operation)
    public boolean deleteEntries(int userId, List<Integer> predictionIds) {
        return historyRepo.deleteEntries(userId, predictionIds);
    }
    
    // Export filtered entries to CSV
    public String exportToCSV(int userId, HistoryFilter filter) {
        List<HistoryEntry> entries = getHistory(userId, filter, 10000, 0); // Large limit for export
        
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Time,Prediction,Confidence (%),Severity,Text,Starred,Tags,Notes\n");
        
        for (HistoryEntry e : entries) {
            csv.append(String.format("\"%s\",\"%s\",\"%s\",%.1f,\"%s\",\"%s\",%s,\"%s\",\"%s\"\n",
                e.getFormattedDate(),
                e.getFormattedTime(),
                e.getPrediction(),
                e.getConfidencePercent(),
                e.getSeverity(),
                e.getInputText().replace("\"", "\"\""),
                e.isStarred() ? "Yes" : "No",
                e.getTags() != null ? String.join("; ", e.getTags()) : "",
                e.getUserNotes() != null ? e.getUserNotes().replace("\"", "\"\"") : ""
            ));
        }
        
        return csv.toString();
    }
    
    // Get statistics for header
    public Map<String, Object> getStats(int userId) {
        return historyRepo.getStats(userId);
    }
    
    // Get mood distribution for filter summary
    public Map<String, Integer> getMoodDistribution(int userId, HistoryFilter filter) {
        LocalDate startDate = filter != null ? filter.getStartDate() : null;
        LocalDate endDate = filter != null ? filter.getEndDate() : null;
        return historyRepo.getMoodDistribution(userId, startDate, endDate);
    }
}