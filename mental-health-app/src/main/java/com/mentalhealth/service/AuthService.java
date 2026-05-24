package com.mentalhealth.service;

import com.mentalhealth.model.User;
import com.mentalhealth.repository.IUserRepository;
import com.mentalhealth.repository.UserRepository;
import com.mentalhealth.util.PasswordUtils;
import com.mentalhealth.util.ValidationUtils;
import java.util.Optional;

// Handles login, registration, security-question recovery, and password reset.
// Delegates persistence to IUserRepository (UserRepository in production).
// Called by LoginController, RegisterController, ForgotPasswordController.
public class AuthService {

    private final IUserRepository userRepository;

    public AuthService() {
        this.userRepository = new UserRepository();
    }

    public AuthService(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Verifies credentials; upgrades a legacy password hash on first login if needed.
    public User login(String username, String password) throws Exception {
        Optional<User> userOptional = userRepository.findByUsername(username);

        if (userOptional.isEmpty() || !PasswordUtils.verifyPassword(password, userOptional.get().getPasswordHash())) {
            throw new Exception("Invalid username or password");
        }

        User user = userOptional.get();

        if (PasswordUtils.needsRehash(user.getPasswordHash())) {
            String upgradedHash = PasswordUtils.hashPassword(password);
            if (userRepository.updatePassword(user.getId(), upgradedHash)) {
                user.setPasswordHash(upgradedHash);
            }
        }

        return user;
    }

    // Validates all fields, checks username uniqueness, hashes the password,
    // and writes the new User row via the repository.
    public void register(String username, String password, String name, String ageText, String goal,
                         int baselineStress, String securityQuestion, String securityAnswer) throws Exception {
        String err;
        if ((err = ValidationUtils.validateUsername(username)) != null) throw new Exception(err);
        if ((err = ValidationUtils.validateName(name)) != null) throw new Exception(err);
        if ((err = ValidationUtils.validatePassword(password)) != null) throw new Exception(err);
        if (securityQuestion == null || securityQuestion.trim().isEmpty())
            throw new Exception("Security question is required.");
        if (securityAnswer == null || securityAnswer.trim().isEmpty())
            throw new Exception("Security answer is required.");

        if (userRepository.existsByUsername(username)) {
            throw new Exception("Username already taken. Please choose a different one.");
        }

        User user = new User(username, PasswordUtils.hashPassword(password), name);
        if (ageText != null && !ageText.isBlank()) {
            user.setAge(Integer.parseInt(ageText));
        }
        user.setGoal(goal != null && !goal.isBlank() ? goal : null);
        user.setBaselineStress(baselineStress);
        user.setSecurityQuestion(securityQuestion.trim());
        user.setSecurityAnswer(securityAnswer);

        userRepository.save(user);
    }

    // the provided security answer against the stored hash.
    public boolean verifySecurityAnswer(String username, String providedAnswer) throws Exception {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            throw new Exception("User not found.");
        }
        return userRepository.verifySecurityAnswer(userOptional.get().getId(), providedAnswer);
    }

    //  validates and stores the new password hash.
    public void resetPassword(String username, String newPassword) throws Exception {
        String err;
        if ((err = ValidationUtils.validatePassword(newPassword)) != null) throw new Exception(err);

        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            throw new Exception("User not found.");
        }

        User user = userOptional.get();
        String hashedPassword = PasswordUtils.hashPassword(newPassword);

        if (!userRepository.updatePassword(user.getId(), hashedPassword)) {
            throw new Exception("Failed to update password.");
        }
    }
}
