package com.mentalhealth.repository;

import com.mentalhealth.database.DatabaseManager;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

// Repository for recommendation progress tracking
public class RecommendationProgressRepository {

    private static final Logger LOG = Logger.getLogger(RecommendationProgressRepository.class.getName());
    
    private final Connection connection;
    
    public RecommendationProgressRepository() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }
    
    // Get all completed recommendation items for today
    public Set<String> getTodayProgress(int userId) {
        Set<String> completed = new HashSet<>();
        String today = LocalDate.now(java.time.ZoneId.of("Asia/Karachi")).toString();
        String sql = "SELECT item_key FROM recommendation_progress WHERE user_id = ? AND rec_date = ? AND completed = 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, today);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                completed.add(rs.getString("item_key"));
            }
        } catch (SQLException e) {
            LOG.warning("getTodayProgress error: " + e.getMessage());
        }
        return completed;
    }
    
    // Mark a recommendation item as completed or uncompleted
    public void setRecItemCompleted(int userId, String itemKey, boolean completed) {
        String today = LocalDate.now(java.time.ZoneId.of("Asia/Karachi")).toString();
        String sql = "INSERT OR REPLACE INTO recommendation_progress (user_id, rec_date, item_key, completed) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, today);
            stmt.setString(3, itemKey);
            stmt.setInt(4, completed ? 1 : 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("setRecItemCompleted error: " + e.getMessage());
        }
    }
    
    // Get weekly statistics for recommendation progress
    public Map<String, Integer> getWeeklyStats(int userId) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Karachi"));
        
        // Initialize all 7 days with 0
        for (int i = 6; i >= 0; i--) {
            stats.put(today.minusDays(i).toString(), 0);
        }
        
        String sql = "SELECT rec_date, COUNT(*) as cnt FROM recommendation_progress WHERE user_id = ? AND rec_date >= ? AND completed = 1 GROUP BY rec_date";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, today.minusDays(6).toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String date = rs.getString("rec_date");
                int count = rs.getInt("cnt");
                if (stats.containsKey(date)) {
                    stats.put(date, count);
                }
            }
        } catch (SQLException e) {
            LOG.warning("getWeeklyStats error: " + e.getMessage());
        }
        
        return stats;
    }
}
