package com.mentalhealth.repository;

import com.mentalhealth.database.DatabaseManager;
import com.mentalhealth.model.Prediction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

// SQLite-backed implementation of IPredictionRepository
public class PredictionRepository implements IPredictionRepository {

    private static final Logger LOG = Logger.getLogger(PredictionRepository.class.getName());

    private final Connection connection;

    public PredictionRepository() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    // Save a prediction after each BERT analysis
    public boolean save(Prediction prediction) {
        String sql = "INSERT INTO predictions (user_id, input_text, prediction, confidence, severity, color) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, prediction.getUserId());
            stmt.setString(2, prediction.getInputText());
            stmt.setString(3, prediction.getPrediction());
            stmt.setDouble(4, prediction.getConfidence());
            stmt.setString(5, prediction.getSeverity());
            stmt.setString(6, prediction.getColor());
            stmt.executeUpdate();
            LOG.info("Prediction saved: " + prediction.getPrediction());
            return true;
        } catch (SQLException e) {
            LOG.warning("Error saving prediction: " + e.getMessage());
            return false;
        }
    }

    // Get the last 7 days of predictions for a user (for mood tracker)
     
    public List<Prediction> getLast7Days(int userId) {
        String sql = "SELECT * FROM predictions "
                   + "WHERE user_id = ? AND created_at >= datetime('now', '+5 hours', '-7 days') "
                   + "ORDER BY created_at ASC";

        List<Prediction> entries = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                entries.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            LOG.warning("Error fetching predictions: " + e.getMessage());
        }

        return entries;
    }

    // Get all predictions for a user
    
    public List<Prediction> getAllByUser(int userId) {
        String sql = "SELECT * FROM predictions WHERE user_id = ? ORDER BY created_at DESC";
        List<Prediction> entries = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                entries.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            LOG.warning("Error fetching predictions: " + e.getMessage());
        }

        return entries;
    }

    // Get the last 30 days of predictions for a user
    
    public List<Prediction> getLast30Days(int userId) {
        String sql = "SELECT * FROM predictions "
                   + "WHERE user_id = ? AND created_at >= datetime('now', '+5 hours', '-30 days') "
                   + "ORDER BY created_at ASC";

        List<Prediction> entries = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                entries.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            LOG.warning("Error fetching predictions: " + e.getMessage());
        }

        return entries;
    }

    // Get predictions from prior week (7-14 days ago)
     
    public List<Prediction> getPriorWeek(int userId) {
        String sql = "SELECT * FROM predictions " +
                   "WHERE user_id = ? AND created_at >= datetime('now', '+5 hours', '-14 days') " +
                   "AND created_at < datetime('now', '+5 hours', '-7 days') " +
                   "ORDER BY created_at ASC";

        List<Prediction> entries = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                entries.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            LOG.warning("Error fetching prior week: " + e.getMessage());
        }

        return entries;
    }

    // Save a generated consoling report
    
    public boolean saveReport(int userId, String reportText, String periodStart, String periodEnd) {
        String sql = "INSERT INTO consoling_reports (user_id, report_text, period_start, period_end) "
                   + "VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, reportText);
            stmt.setString(3, periodStart);
            stmt.setString(4, periodEnd);
            stmt.executeUpdate();
            LOG.info("Consoling report saved");
            return true;
        } catch (SQLException e) {
            LOG.warning("Error saving report: " + e.getMessage());
            return false;
        }
    }

    private Prediction mapResultSet(ResultSet rs) throws SQLException {
        Prediction p = new Prediction();
        p.setId(rs.getInt("id"));
        p.setUserId(rs.getInt("user_id"));
        p.setInputText(rs.getString("input_text"));
        p.setPrediction(rs.getString("prediction"));
        p.setConfidence(rs.getDouble("confidence"));
        p.setSeverity(rs.getString("severity"));
        p.setColor(rs.getString("color"));
        p.setCreatedAt(rs.getString("created_at"));
        return p;
    }
}