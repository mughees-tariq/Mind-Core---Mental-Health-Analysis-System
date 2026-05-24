package com.mentalhealth.repository;

import com.mentalhealth.database.DatabaseManager;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class SelfCareRepository {
    private static final Logger LOG = Logger.getLogger(SelfCareRepository.class.getName());
    private final Connection connection;

    public SelfCareRepository() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    public Map<String, Boolean> getChecklist(int userId, String date) {
        Map<String, Boolean> checklist = new HashMap<>();
        String sql = "SELECT item_key, checked FROM self_care_checklist WHERE user_id = ? AND check_date = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, date);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                checklist.put(rs.getString("item_key"), rs.getBoolean("checked"));
            }
        } catch (SQLException e) {
            LOG.warning("Error fetching self-care checklist: " + e.getMessage());
        }
        return checklist;
    }

    public boolean updateItem(int userId, String date, String itemKey, boolean checked) {
        String sql = "INSERT OR REPLACE INTO self_care_checklist (user_id, check_date, item_key, checked) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, date);
            stmt.setString(3, itemKey);
            stmt.setBoolean(4, checked);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOG.warning("Error updating self-care item: " + e.getMessage());
            return false;
        }
    }
}
