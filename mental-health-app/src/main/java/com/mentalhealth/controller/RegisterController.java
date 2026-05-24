package com.mentalhealth.controller;

import com.mentalhealth.Main;
import com.mentalhealth.model.User;
import com.mentalhealth.service.AuthService;
import com.mentalhealth.util.ValidationUtils;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.logging.Logger;

// Controller for RegisterScreen.fxml. Collects new-user details from the form,
// runs them through AuthService.register() (which writes to the users table via
// UserRepository), then logs the user straight in and opens the dashboard.
public class RegisterController {

    private static final Logger LOG = Logger.getLogger(RegisterController.class.getName());

    @FXML private TextField     usernameField;
    @FXML private TextField     nameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField     passwordFieldVisible;
    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField     confirmPasswordFieldVisible;
    @FXML private CheckBox      showPasswordCheck;
    @FXML private CheckBox      showConfirmPasswordCheck;
    @FXML private TextField     ageField;
    @FXML private Label         ageErrorLabel;
    @FXML private TextArea      goalField;
    @FXML private Slider        baselineStressSlider;
    @FXML private Label         stressValueLabel;
    @FXML private Label         messageLabel;
    @FXML private ProgressBar passwordStrengthBar;
    @FXML private Label       passwordStrengthLabel;

    @FXML private ComboBox<String>  genderField;
    @FXML private TextField         locationField;
    @FXML private ComboBox<String>  securityQuestionField;
    @FXML private TextField         securityAnswerField;

    private AuthService authService;

    // FXML init. Binds masked/visible password fields, wires the stress slider
    // label, and attaches the live password-strength listener.
    @FXML
    public void initialize() {
        LOG.info("RegisterController initialized");
        authService = new AuthService();

        passwordFieldVisible.textProperty().bindBidirectional(passwordField.textProperty());
        confirmPasswordFieldVisible.textProperty().bindBidirectional(confirmPasswordField.textProperty());

        baselineStressSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                stressValueLabel.setText(String.valueOf(newVal.intValue())));

        passwordField.textProperty().addListener((obs, oldVal, newVal) ->
                updatePasswordStrength(newVal));
    }

    // Scores the password (length + char classes) and updates the bar/label.
    private void updatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            passwordStrengthBar.setProgress(0);
            passwordStrengthLabel.setText("");
            passwordStrengthBar.getStyleClass().removeAll(
                    "register-strength-weak", "register-strength-fair",
                    "register-strength-good", "register-strength-strong");
            passwordStrengthBar.getStyleClass().add("register-strength-weak");
            return;
        }

        int score = 0;
        if (password.length() >= 8)                          score++;
        if (password.length() >= 12)                         score++;
        if (password.matches(".*[A-Z].*"))                   score++;
        if (password.matches(".*[0-9].*"))                   score++;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]].*")) score++;

        passwordStrengthBar.getStyleClass().removeAll(
                "register-strength-weak", "register-strength-fair",
                "register-strength-good", "register-strength-strong");

        switch (score) {
            case 0:
            case 1:
                passwordStrengthBar.setProgress(0.2);
                passwordStrengthBar.getStyleClass().add("register-strength-weak");
                passwordStrengthLabel.setText("Weak");
                passwordStrengthLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #ef4444;");
                break;
            case 2:
                passwordStrengthBar.setProgress(0.45);
                passwordStrengthBar.getStyleClass().add("register-strength-fair");
                passwordStrengthLabel.setText("Fair");
                passwordStrengthLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #f59e0b;");
                break;
            case 3:
                passwordStrengthBar.setProgress(0.70);
                passwordStrengthBar.getStyleClass().add("register-strength-good");
                passwordStrengthLabel.setText("Good");
                passwordStrengthLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1E88E5;");
                break;
            default:
                passwordStrengthBar.setProgress(1.0);
                passwordStrengthBar.getStyleClass().add("register-strength-strong");
                passwordStrengthLabel.setText("Strong ✓");
                passwordStrengthLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #10b981;");
        }
    }

    // Show/hide the masked password field by swapping it with a plain TextField.
    @FXML
    public void togglePasswordVisibility() {
        boolean show = showPasswordCheck.isSelected();
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        passwordFieldVisible.setVisible(show);
        passwordFieldVisible.setManaged(show);
    }

    // Same toggle for the confirm-password field.
    @FXML
    public void toggleConfirmPasswordVisibility() {
        boolean show = showConfirmPasswordCheck.isSelected();
        confirmPasswordField.setVisible(!show);
        confirmPasswordField.setManaged(!show);
        confirmPasswordFieldVisible.setVisible(show);
        confirmPasswordFieldVisible.setManaged(show);
    }

    // Register button handler. Validates the form, calls AuthService.register(),
    // applies optional profile fields, then signs the user in.
    @FXML
    public void handleRegister() {
        hideMessage();

        String username        = usernameField.getText().trim();
        String name            = nameField.getText().trim();
        String password        = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        String ageText         = ageField.getText().trim();
        String goal            = goalField.getText().trim();
        int    baselineStress  = (int) baselineStressSlider.getValue();

        String gender          = genderField.getValue();
        String location        = locationField.getText().trim();
        String securityQuestion = securityQuestionField.getValue();
        String securityAnswer   = securityAnswerField.getText().trim();

        String usernameErr = ValidationUtils.validateUsername(username);
        if (usernameErr != null) { showError(usernameErr); return; }

        String nameErr = ValidationUtils.validateName(name);
        if (nameErr != null) { showError(nameErr); return; }

        String pwErr = ValidationUtils.validatePassword(password);
        if (pwErr != null) { showError(pwErr); return; }

        if (confirmPassword.isEmpty()) {
            showError("Please confirm your password.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        ageErrorLabel.setVisible(false);
        ageErrorLabel.setManaged(false);
        if (ageText.isEmpty()) {
            ageErrorLabel.setText("Age is required.");
            ageErrorLabel.setVisible(true);
            ageErrorLabel.setManaged(true);
            return;
        }
        try {
            int age = Integer.parseInt(ageText);
            if (age < 13) {
                ageErrorLabel.setText("You must be at least 13 years old to register.");
                ageErrorLabel.setVisible(true);
                ageErrorLabel.setManaged(true);
                return;
            }
            if (age >120) {
                ageErrorLabel.setText("Number Higher Than Allowed Range");
                ageErrorLabel.setVisible(true);
                ageErrorLabel.setManaged(true);
                return;
            }
        } catch (NumberFormatException e) {
            ageErrorLabel.setText("Age must be a valid number.");
            ageErrorLabel.setVisible(true);
            ageErrorLabel.setManaged(true);
            return;
        }

        if (securityQuestion == null || securityQuestion.isEmpty()) {
            showError("Security question is required.");
            return;
        }

        if (securityAnswer.isEmpty()) {
            showError("Security answer is required.");
            return;
        }

        try {
            authService.register(username, password, name, ageText, goal, baselineStress, securityQuestion, securityAnswer);

            User savedUser = authService.login(username, password);
            if (gender != null && !gender.isEmpty()) {
                savedUser.setGender(gender);
            }
            if (!location.isEmpty()) {
                savedUser.setLocation(location);
            }

            LOG.info("User registered successfully: " + savedUser.getUsername());
            Main.setCurrentUser(savedUser);
            try {
                Main.switchScene("MainWindow.fxml");
            } catch (Exception e) {
                showError("Registration successful but failed to navigate: " + e.getMessage());
            }
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void showError(String message) {
        messageLabel.setText("⚠  " + message);
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void hideMessage() {
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);
        ageErrorLabel.setVisible(false);
        ageErrorLabel.setManaged(false);
    }

    @FXML
    public void handleBackToLogin() {
        try {
            Main.switchScene("LoginScreen.fxml");
        } catch (Exception e) {
            LOG.warning("Navigation failed: " + e.getMessage());
        }
    }
}
