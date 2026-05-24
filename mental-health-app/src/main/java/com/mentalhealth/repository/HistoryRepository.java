package com.mentalhealth.repository;

import com.mentalhealth.database.DatabaseManager;
import com.mentalhealth.model.HistoryEntry;
import com.mentalhealth.model.HistoryFilter;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// Repository for history metadata operations (starred, tags, notes)
public class HistoryRepository {

    private static final Logger LOG = Logger.getLogger(HistoryRepository.class.getName());
    
    private final Connection connection;
    
    // Table definition for history metadata
    private static final String META_TABLE = """
        CREATE TABLE IF NOT EXISTS history_metadata (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            prediction_id INTEGER NOT NULL,
            user_id INTEGER NOT NULL,
            starred BOOLEAN DEFAULT 0,
            tags TEXT,
            user_notes TEXT,
            UNIQUE(prediction_id, user_id)
        )
    """;
    
    public HistoryRepository() {
        this.connection = DatabaseManager.getInstance().getConnection();
        createMetadataTable();
    }
    
    private void createMetadataTable() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(META_TABLE);
        } catch (SQLException e) {
            LOG.warning("Error creating history_metadata table: " + e.getMessage());
        }
    }
    
    //Toggle starred status for a prediction entry
     
    public boolean toggleStarred(int predictionId, int userId) {
        String sql = """
            INSERT INTO history_metadata (prediction_id, user_id, starred) 
            VALUES (?, ?, 1)
            ON CONFLICT(prediction_id, user_id) 
            DO UPDATE SET starred = NOT COALESCE(
                (SELECT starred FROM history_metadata WHERE prediction_id = ? AND user_id = ?), 0
            )
            RETURNING starred
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, predictionId);
            stmt.setInt(2, userId);
            stmt.setInt(3, predictionId);
            stmt.setInt(4, userId);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("starred");
            }
        } catch (SQLException e) {
            LOG.warning("Error toggling star: " + e.getMessage());
        }
        return false;
    }
    
    //Update tags for an entry
    
    public boolean updateTags(int predictionId, int userId, List<String> tags) {
        String tagsJson = tags != null ? String.join(",", tags) : null;
        String sql = """
            INSERT INTO history_metadata (prediction_id, user_id, tags) 
            VALUES (?, ?, ?)
            ON CONFLICT(prediction_id, user_id) 
            DO UPDATE SET tags = ?
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, predictionId);
            stmt.setInt(2, userId);
            stmt.setString(3, tagsJson);
            stmt.setString(4, tagsJson);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.warning("Error updating tags: " + e.getMessage());
            return false;
        }
    }
    
    // Update user notes for an entry
    public boolean updateNotes(int predictionId, int userId, String notes) {
        String sql = """
            INSERT INTO history_metadata (prediction_id, user_id, user_notes) 
            VALUES (?, ?, ?)
            ON CONFLICT(prediction_id, user_id) 
            DO UPDATE SET user_notes = ?
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, predictionId);
            stmt.setInt(2, userId);
            stmt.setString(3, notes);
            stmt.setString(4, notes);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.warning("Error updating notes: " + e.getMessage());
            return false;
        }
    }
    
    // Get all available tags for a user
    public List<String> getAllTags(int userId) {
        List<String> tags = new ArrayList<>();
        String sql = "SELECT tags FROM history_metadata WHERE user_id = ? AND tags IS NOT NULL";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String tagsStr = rs.getString("tags");
                if (tagsStr != null) {
                    String[] split = tagsStr.split(",");
                    for (String tag : split) {
                        if (!tag.trim().isEmpty() && !tags.contains(tag.trim())) {
                            tags.add(tag.trim());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOG.warning("Error fetching tags: " + e.getMessage());
        }
        Collections.sort(tags);
        return tags;
    }
    
    // Get statistics for a user's history
     
    public Map<String, Object> getStats(int userId) {
        Map<String, Object> stats = new HashMap<>();
        
        String sql = """
            SELECT
                COUNT(*) as total,
                COUNT(DISTINCT DATE(datetime(created_at, '+5 hours'))) as active_days,
                MAX(created_at) as last_entry,
                AVG(confidence) as avg_confidence,
                (SELECT COUNT(*) FROM predictions WHERE user_id = ? AND DATE(datetime(created_at, '+5 hours')) = DATE(datetime('now', '+5 hours'))) as today_count
            FROM predictions WHERE user_id = ?
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                stats.put("total", rs.getInt("total"));
                stats.put("activeDays", rs.getInt("active_days"));
                stats.put("lastEntry", rs.getString("last_entry"));
                stats.put("avgConfidence", rs.getDouble("avg_confidence") * 100);
                stats.put("todayCount", rs.getInt("today_count"));
            }
        } catch (SQLException e) {
            LOG.warning("Error getting stats: " + e.getMessage());
        }
        
        return stats;
    }
    
    //Get mood distribution for a user with optional filter
     
    public Map<String, Integer> getMoodDistribution(int userId, LocalDate startDate, LocalDate endDate) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        
        StringBuilder sql = new StringBuilder("""
            SELECT p.prediction, COUNT(*) as count
            FROM predictions p
            LEFT JOIN history_metadata hm ON p.id = hm.prediction_id AND hm.user_id = p.user_id
            WHERE p.user_id = ?
        """);
        
        List<Object> params = new ArrayList<>();
        params.add(userId);
        
        if (startDate != null) {
            sql.append(" AND DATE(datetime(p.created_at, '+5 hours')) >= ?");
            params.add(startDate.toString());
        }
        if (endDate != null) {
            sql.append(" AND DATE(datetime(p.created_at, '+5 hours')) <= ?");
            params.add(endDate.toString());
        }

        sql.append(" GROUP BY p.prediction ORDER BY count DESC");
        
        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                distribution.put(rs.getString("prediction"), rs.getInt("count"));
            }
        } catch (SQLException e) {
            LOG.warning("Error getting mood distribution: " + e.getMessage());
        }
        
        return distribution;
    }
    
    // Get metadata (starred, tags, notes) for a prediction
     
    public Map<String, Object> getMetadata(int predictionId, int userId) {
        Map<String, Object> metadata = new HashMap<>();
        String sql = "SELECT starred, tags, user_notes FROM history_metadata WHERE prediction_id = ? AND user_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, predictionId);
            stmt.setInt(2, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                metadata.put("starred", rs.getBoolean("starred"));
                metadata.put("tags", rs.getString("tags"));
                metadata.put("notes", rs.getString("user_notes"));
            }
        } catch (SQLException e) {
            LOG.warning("Error getting metadata: " + e.getMessage());
        }
        
        return metadata;
    }
    
    // Get all history entries for a user with applied filters
     
    public List<HistoryEntry> getHistory(int userId, HistoryFilter filter, int limit, int offset) {
        List<HistoryEntry> entries = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT p.*, hm.starred, hm.tags, hm.user_notes 
            FROM predictions p
            LEFT JOIN history_metadata hm ON p.id = hm.prediction_id AND hm.user_id = p.user_id
            WHERE p.user_id = ?
        """);
        
        List<Object> params = new ArrayList<>();
        params.add(userId);
        
        // Apply filters
        if (filter != null) {
            // Date range
            if (filter.getStartDate() != null) {
                sql.append(" AND DATE(datetime(p.created_at, '+5 hours')) >= ?");
                params.add(filter.getStartDate().toString());
            }
            if (filter.getEndDate() != null) {
                sql.append(" AND DATE(datetime(p.created_at, '+5 hours')) <= ?");
                params.add(filter.getEndDate().toString());
            }

            // Mood filter
            if (filter.getMoods() != null && !filter.getMoods().isEmpty()) {
                sql.append(" AND p.prediction IN (");
                sql.append(filter.getMoods().stream().map(m -> "?").collect(Collectors.joining(",")));
                sql.append(")");
                params.addAll(filter.getMoods());
            }
            
            // Confidence range
            if (filter.getMinConfidence() != null) {
                sql.append(" AND p.confidence >= ?");
                params.add(filter.getMinConfidence());
            }
            if (filter.getMaxConfidence() != null) {
                sql.append(" AND p.confidence <= ?");
                params.add(filter.getMaxConfidence());
            }
            
            // Severity filter
            if (filter.getSeverities() != null && !filter.getSeverities().isEmpty()) {
                sql.append(" AND p.severity IN (");
                sql.append(filter.getSeverities().stream().map(s -> "?").collect(Collectors.joining(",")));
                sql.append(")");
                params.addAll(filter.getSeverities());
            }
            
            // Search text
            if (filter.getSearchText() != null && !filter.getSearchText().trim().isEmpty()) {
                sql.append(" AND p.input_text LIKE ?");
                params.add("%" + filter.getSearchText().trim() + "%");
            }
            
            // Starred only
            if (filter.isShowStarredOnly()) {
                sql.append(" AND hm.starred = 1");
            }
        }
        
        // Sorting
        if (filter != null && filter.getSortBy() != null) {
            switch (filter.getSortBy()) {
                case "date_desc": sql.append(" ORDER BY p.created_at DESC"); break;
                case "date_asc": sql.append(" ORDER BY p.created_at ASC"); break;
                case "confidence_desc": sql.append(" ORDER BY p.confidence DESC"); break;
                case "mood": sql.append(" ORDER BY p.prediction, p.created_at DESC"); break;
                default: sql.append(" ORDER BY p.created_at DESC");
            }
        } else {
            sql.append(" ORDER BY p.created_at DESC");
        }
        
        // Pagination
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        
        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                entries.add(mapToHistoryEntry(rs));
            }
        } catch (SQLException e) {
            LOG.warning("Error fetching history: " + e.getMessage());
        }
        
        return entries;
    }
    
    // Get total count for pagination
    public int getHistoryCount(int userId, HistoryFilter filter) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*) as count 
            FROM predictions p
            LEFT JOIN history_metadata hm ON p.id = hm.prediction_id AND hm.user_id = p.user_id
            WHERE p.user_id = ?
        """);
        
        List<Object> params = new ArrayList<>();
        params.add(userId);
        
        if (filter != null) {
            if (filter.getStartDate() != null) {
                sql.append(" AND DATE(datetime(p.created_at, '+5 hours')) >= ?");
                params.add(filter.getStartDate().toString());
            }
            if (filter.getEndDate() != null) {
                sql.append(" AND DATE(datetime(p.created_at, '+5 hours')) <= ?");
                params.add(filter.getEndDate().toString());
            }
            if (filter.getMoods() != null && !filter.getMoods().isEmpty()) {
                sql.append(" AND p.prediction IN (");
                sql.append(filter.getMoods().stream().map(m -> "?").collect(Collectors.joining(",")));
                sql.append(")");
                params.addAll(filter.getMoods());
            }
            if (filter.getSearchText() != null && !filter.getSearchText().trim().isEmpty()) {
                sql.append(" AND p.input_text LIKE ?");
                params.add("%" + filter.getSearchText().trim() + "%");
            }
            if (filter.isShowStarredOnly()) {
                sql.append(" AND hm.starred = 1");
            }
        }
        
        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            LOG.warning("Error counting history: " + e.getMessage());
        }
        return 0;
    }
    
    // Map ResultSet to HistoryEntry object
     
    private HistoryEntry mapToHistoryEntry(ResultSet rs) throws SQLException {
        HistoryEntry entry = new HistoryEntry();
        entry.setId(rs.getInt("id"));
        entry.setUserId(rs.getInt("user_id"));
        entry.setInputText(rs.getString("input_text"));
        entry.setPrediction(rs.getString("prediction"));
        entry.setConfidence(rs.getDouble("confidence"));
        entry.setSeverity(rs.getString("severity"));
        entry.setColor(rs.getString("color"));
        
        String createdAt = rs.getString("created_at");
        if (createdAt != null) {
            try {
                if (createdAt.contains(" ")) {
                    createdAt = createdAt.replace(" ", "T");
                }
                if (createdAt.length() > 19) {
                    createdAt = createdAt.substring(0, 19);
                }
                entry.setCreatedAt(LocalDateTime.parse(createdAt));
            } catch (Exception e) {
                entry.setCreatedAt(LocalDateTime.now());
            }
        }
        
        // Metadata
        entry.setStarred(rs.getBoolean("starred"));
        
        String tagsStr = rs.getString("tags");
        if (tagsStr != null && !tagsStr.isEmpty()) {
            String[] tags = tagsStr.split(",");
            for (String tag : tags) {
                if (!tag.trim().isEmpty()) {
                    entry.addTag(tag.trim());
                }
            }
        }
        
        entry.setUserNotes(rs.getString("user_notes"));
        
        return entry;
    }
    
    // Delete entries (bulk operation)
    public boolean deleteEntries(int userId, List<Integer> predictionIds) {
        if (predictionIds == null || predictionIds.isEmpty()) {
            return false;
        }
        
        String placeholders = predictionIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "DELETE FROM predictions WHERE user_id = ? AND id IN (" + placeholders + ")";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            for (int i = 0; i < predictionIds.size(); i++) {
                stmt.setInt(i + 2, predictionIds.get(i));
            }
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.warning("Error deleting entries: " + e.getMessage());
            return false;
        }
    }
}
