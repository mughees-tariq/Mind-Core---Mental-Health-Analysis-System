package com.mentalhealth.repository;

import com.mentalhealth.database.DatabaseManager;
import com.mentalhealth.model.EmergencyContact;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

// SQLite-backed implementation of IEmergencyContactRepository
public class EmergencyContactRepository implements IEmergencyContactRepository {

    private static final Logger LOG = Logger.getLogger(EmergencyContactRepository.class.getName());

    private final Connection connection;

    public EmergencyContactRepository() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    public boolean save(EmergencyContact contact) {
        String sql = "INSERT INTO emergency_contacts (user_id, name, phone, relationship) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, contact.getUserId());
            stmt.setString(2, contact.getName());
            stmt.setString(3, contact.getPhone());
            stmt.setString(4, contact.getRelationship());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.warning("Error saving emergency contact: " + e.getMessage());
            return false;
        }
    }

    public List<EmergencyContact> findByUserId(int userId) {
        List<EmergencyContact> contacts = new ArrayList<>();
        String sql = "SELECT * FROM emergency_contacts WHERE user_id = ? ORDER BY created_at ASC";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                EmergencyContact c = new EmergencyContact();
                c.setId(rs.getInt("id"));
                c.setUserId(rs.getInt("user_id"));
                c.setName(rs.getString("name"));
                c.setPhone(rs.getString("phone"));
                c.setRelationship(rs.getString("relationship"));
                c.setCreatedAt(rs.getString("created_at"));
                contacts.add(c);
            }
        } catch (SQLException e) {
            LOG.warning("Error fetching emergency contacts: " + e.getMessage());
        }
        return contacts;
    }

    public boolean delete(int contactId) {
        String sql = "DELETE FROM emergency_contacts WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, contactId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.warning("Error deleting emergency contact: " + e.getMessage());
            return false;
        }
    }
}
