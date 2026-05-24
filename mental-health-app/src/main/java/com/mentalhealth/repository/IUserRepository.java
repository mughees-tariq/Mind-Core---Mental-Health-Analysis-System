package com.mentalhealth.repository;

import com.mentalhealth.model.User;
import java.util.Optional;

// Contract for user persistence operations
public interface IUserRepository {

    // Persists a new user; returns saved entity with generated ID, or null on failure
    User save(User user);

    // Finds a user by username; returns empty Optional if not found
    Optional<User> findByUsername(String username);

    // Finds a user by primary key; returns empty Optional if not found
    Optional<User> findById(int id);

    // Returns true if the username is already taken
    boolean existsByUsername(String username);

    // Updates display name; returns true if succeeded
    boolean updateName(int userId, String newName);

    // Updates age (null clears it); returns true if succeeded
    boolean updateAge(int userId, Integer age);

    // Updates gender; returns true if succeeded
    boolean updateGender(int userId, String gender);

    // Updates location; returns true if succeeded
    boolean updateLocation(int userId, String location);

    // Updates personal goal; returns true if succeeded
    boolean updateGoal(int userId, String goal);

    // Updates affirmations JSON; returns true if succeeded
    boolean updateAffirmations(int userId, String affirmationsJson);

    // Updates password hash; returns true if succeeded
    boolean updatePassword(int userId, String newPassword);

    // Appends new entry to feeling history; returns true if succeeded
    boolean updateFeelingHistory(int userId, String newEntry);

    // Deletes user and all associated data; returns true if succeeded
    boolean deleteUser(int userId);

    // Clears all predictions, mood entries, and reports; returns true if succeeded
    boolean clearHistory(int userId);

    // Returns profile completeness ratio between 0.0 (empty) and 1.0 (full)
    double getProfileCompleteness(int userId);

    // Updates security question and answer; returns true if succeeded
    boolean updateSecurityQuestion(int userId, String question, String answer);

    // Verifies security answer (case-insensitive, trimmed); returns true if matches
    boolean verifySecurityAnswer(int userId, String providedAnswer);
}
