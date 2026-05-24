package com.mentalhealth.service;

import com.mentalhealth.model.User;
import com.mentalhealth.repository.IUserRepository;
import com.mentalhealth.repository.UserRepository;

// Thin service wrapping profile-management writes in UserRepository.
// Used by ProfileController for edit-profile, password change, and account deletion.
public class UserService {
    private final IUserRepository userRepository;

    public UserService() {
        this.userRepository = new UserRepository();
    }

    public UserService(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean updateName(int userId, String name) {
        return userRepository.updateName(userId, name);
    }

    public boolean updateAge(int userId, int age) {
        return userRepository.updateAge(userId, age);
    }

    public boolean updateGender(int userId, String gender) {
        return userRepository.updateGender(userId, gender);
    }

    public boolean updateLocation(int userId, String location) {
        return userRepository.updateLocation(userId, location);
    }

    public boolean updateGoal(int userId, String goal) {
        return userRepository.updateGoal(userId, goal);
    }

    public boolean updateAffirmations(int userId, String json) {
        return userRepository.updateAffirmations(userId, json);
    }

    public boolean updatePassword(int userId, String newHash) {
        return userRepository.updatePassword(userId, newHash);
    }

    // 0.0–1.0 ratio of filled optional profile fields.
    public double getProfileCompleteness(int userId) {
        return userRepository.getProfileCompleteness(userId);
    }

    // Deletes all predictions and mood entries for the user.
    public boolean clearHistory(int userId) {
        return userRepository.clearHistory(userId);
    }

    public boolean deleteUser(int userId) {
        return userRepository.deleteUser(userId);
    }

    public boolean updateFeelingHistory(int userId, String feelingHistory) {
        return userRepository.updateFeelingHistory(userId, feelingHistory);
    }
}
