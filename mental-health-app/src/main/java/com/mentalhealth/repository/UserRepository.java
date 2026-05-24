package com.mentalhealth.repository;

import com.mentalhealth.database.DatabaseManager;
import com.mentalhealth.model.User;

import java.sql.*;
import java.util.Optional;
import java.util.logging.Logger;

// SQLite-backed implementation of IUserRepository
public class UserRepository implements IUserRepository {

    private static final Logger LOG = Logger.getLogger(UserRepository.class.getName());

    private final Connection connection;

    public UserRepository() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    public boolean updateName(int userId, String newName) {
        String sql = "UPDATE users SET name = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setInt(2, userId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            LOG.warning("Error updating name: " + e.getMessage());
            return false;
        }
    }

    public User save(User user) {
        String sql = "INSERT INTO users (username, password_hash, name, age, goal, baseline_stress, security_question, security_answer) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPasswordHash());
            stmt.setString(3, user.getName());
            if (user.getAge() != null) stmt.setInt(4, user.getAge()); else stmt.setNull(4, Types.INTEGER);
            stmt.setString(5, user.getGoal());
            stmt.setInt(6, user.getBaselineStress());
            stmt.setString(7, user.getSecurityQuestion());
            stmt.setString(8, user.getSecurityAnswer());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) return null;

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    user.setId(generatedKeys.getInt(1));
                }
            }
            return user;
        } catch (SQLException e) {
            LOG.warning("Error saving user: " + e.getMessage());
            return null;
        }
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            LOG.warning("Error finding user: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<User> findById(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            LOG.warning("Error finding user: " + e.getMessage());
        }
        return Optional.empty();
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            LOG.warning("Error checking user: " + e.getMessage());
        }
        return false;
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setName(rs.getString("name"));
        user.setAge(rs.getObject("age") != null ? rs.getInt("age") : null);
        user.setGoal(rs.getString("goal"));
        user.setBaselineStress(rs.getInt("baseline_stress"));
        user.setCreatedAt(rs.getString("created_at"));
        user.setFeelingHistory(rs.getString("feeling_history"));
        try { user.setGender(rs.getString("gender")); } catch (SQLException ignored) {}
        try { user.setLocation(rs.getString("location")); } catch (SQLException ignored) {}
        try { user.setAffirmations(rs.getString("affirmations")); } catch (SQLException ignored) {}
        try { user.setSecurityQuestion(rs.getString("security_question")); } catch (SQLException ignored) {}
        try { user.setSecurityAnswer(rs.getString("security_answer")); } catch (SQLException ignored) {}
        return user;
    }

    public boolean updateAge(int userId, Integer age) {
        String sql = "UPDATE users SET age = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (age != null) stmt.setInt(1, age); else stmt.setNull(1, Types.INTEGER);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.warning("Error updating age: " + e.getMessage());
            return false;
        }
    }

    public boolean updateGender(int userId, String gender) {
        String sql = "UPDATE users SET gender = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, gender);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.warning("Error updating gender: " + e.getMessage());
            return false;
        }
    }

    public boolean updateLocation(int userId, String location) {
        String sql = "UPDATE users SET location = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, location);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.warning("Error updating location: " + e.getMessage());
            return false;
        }
    }

    public boolean updateGoal(int userId, String goal) {
        String sql = "UPDATE users SET goal = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, goal);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.warning("Error updating goal: " + e.getMessage());
            return false;
        }
    }

    public boolean updateAffirmations(int userId, String affirmationsJson) {
        String sql = "UPDATE users SET affirmations = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, affirmationsJson);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.warning("Error updating affirmations: " + e.getMessage());
            return false;
        }
    }

    public boolean updatePassword(int userId, String newPassword) {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, newPassword);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.warning("Error updating password: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteUser(int userId) {
        String[] deleteSqls = {
            "DELETE FROM predictions WHERE user_id = ?",
            "DELETE FROM mood_entries WHERE user_id = ?",
            "DELETE FROM consoling_reports WHERE user_id = ?",
            "DELETE FROM emergency_contacts WHERE user_id = ?",
            "DELETE FROM users WHERE id = ?"
        };
        try {
            for (String sql : deleteSqls) {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }
            }
            return true;
        } catch (SQLException e) {
            LOG.warning("Error deleting user: " + e.getMessage());
            return false;
        }
    }

    public boolean clearHistory(int userId) {
        String[] deleteSqls = {
            "DELETE FROM predictions WHERE user_id = ?",
            "DELETE FROM mood_entries WHERE user_id = ?",
            "DELETE FROM consoling_reports WHERE user_id = ?"
        };
        try {
            for (String sql : deleteSqls) {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }
            }
            // Clear feeling history too
            try (PreparedStatement stmt = connection.prepareStatement("UPDATE users SET feeling_history = NULL WHERE id = ?")) {
                stmt.setInt(1, userId);
                stmt.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            LOG.warning("Error clearing history: " + e.getMessage());
            return false;
        }
    }

    public double getProfileCompleteness(int userId) {
        Optional<User> userOpt = findById(userId);
        if (userOpt.isEmpty()) return 0.0;
        User user = userOpt.get();

        int filled = 0;
        int total = 6;
        if (user.getName() != null && !user.getName().isEmpty()) filled++;
        if (user.getAge() != null) filled++;
        if (user.getGender() != null && !user.getGender().isEmpty()) filled++;
        if (user.getLocation() != null && !user.getLocation().isEmpty()) filled++;
        if (user.getGoal() != null && !user.getGoal().isEmpty()) filled++;
        if (user.getAffirmations() != null && !user.getAffirmations().isEmpty()) filled++;
        return (double) filled / total;
    }

    public boolean updateFeelingHistory(int userId, String newEntry) {
        Optional<User> userOpt = findById(userId);
        if (userOpt.isEmpty()) return false;

        User user = userOpt.get();
        String currentHistory = user.getFeelingHistory();
        String updatedHistory = (currentHistory == null || currentHistory.isEmpty()) 
                                ? newEntry 
                                : currentHistory + "\n" + newEntry;

        String sql = "UPDATE users SET feeling_history = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, updatedHistory);
            stmt.setInt(2, userId);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                user.setFeelingHistory(updatedHistory);
                return true;
            }
        } catch (SQLException e) {
            LOG.warning("Error updating feeling history: " + e.getMessage());
        }
        return false;
    }


    public boolean updateSecurityQuestion(int userId, String securityQuestion, String securityAnswer) {
        String sql = "UPDATE users SET security_question = ?, security_answer = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, securityQuestion);
            stmt.setString(2, securityAnswer != null ? securityAnswer.trim() : null);
            stmt.setInt(3, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.warning("Error updating security question: " + e.getMessage());
            return false;
        }
    }

    public boolean verifySecurityAnswer(int userId, String providedAnswer) {
        Optional<User> userOpt = findById(userId);
        if (userOpt.isEmpty()) return false;
        
        String storedAnswer = userOpt.get().getSecurityAnswer();
        if (storedAnswer == null) return false;
        
        //trimming all spaces and case insensitive
        String normalizedStored  = storedAnswer.trim().replaceAll("\\s+", "");
        String normalizedProvided = providedAnswer != null ? providedAnswer.trim().replaceAll("\\s+", "") : "";
        return normalizedStored.equalsIgnoreCase(normalizedProvided);
    }
}