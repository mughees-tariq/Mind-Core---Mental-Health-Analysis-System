package com.mentalhealth.controller;

import com.mentalhealth.Main;
import com.mentalhealth.model.User;
import com.mentalhealth.repository.IUserRepository;
import com.mentalhealth.repository.UserRepository;
import com.mentalhealth.service.AuthService;
import com.mentalhealth.util.ValidationUtils;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.Optional;
import java.util.logging.Logger;

// Controller for ForgotPasswordScreen.fxml. Three-stage recovery flow:
//   1) look up the user in UserRepository
//   2) verify their security answer via AuthService
//   3) write a new password hash back through AuthService.resetPassword()
// Returns the user to the login screen when finished.
public class ForgotPasswordController {

    private static final Logger LOG = Logger.getLogger(ForgotPasswordController.class.getName());

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label errorLabel;

    @FXML private VBox stage1Container;
    @FXML private TextField usernameField;

    @FXML private VBox stage2Container;
    @FXML private TextArea securityQuestionDisplay;
    @FXML private TextField securityAnswerField;

    @FXML private VBox stage3Container;
    @FXML private PasswordField newPasswordField;
    @FXML private TextField newPasswordFieldVisible;
    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField confirmPasswordFieldVisible;
    @FXML private CheckBox showPasswordCheck;
    @FXML private CheckBox showConfirmPasswordCheck;

    @FXML private HBox buttonContainer;
    @FXML private Button nextButton;
    @FXML private Button backButton;

    private AuthService authService;
    private IUserRepository userRepository;
    private String currentUsername;
    private int currentStage = 1;

    // FXML init. Binds masked/visible password pairs and hides the error label.
    @FXML
    public void initialize() {
        LOG.info("ForgotPasswordController initialized");
        authService = new AuthService();
        userRepository = new UserRepository();

        newPasswordFieldVisible.textProperty().bindBidirectional(newPasswordField.textProperty());
        confirmPasswordFieldVisible.textProperty().bindBidirectional(confirmPasswordField.textProperty());

        if (errorLabel != null) errorLabel.setVisible(false);
    }

    //  confirm the username exists and has a security question set.
    @FXML
    public void handleStage1() {
        hideError();

        if (currentStage != 1) return;

        String username = usernameField.getText().trim();

        if (username.isEmpty()) {
            showError("Please enter your username");
            return;
        }

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            showError("Username not found. Please check and try again.");
            return;
        }

        User user = userOpt.get();
        if (user.getSecurityQuestion() == null || user.getSecurityQuestion().isEmpty()) {
            showError("No security question set for this account. Please contact support.");
            return;
        }

        currentUsername = username;
        transitionToStage2(user);
    }

    //  check the user's answer against the stored hash.
    @FXML
    public void handleStage2() {
        hideError();

        if (currentStage != 2) return;

        String answer = securityAnswerField.getText().trim();

        if (answer.isEmpty()) {
            showError("Please enter your security answer");
            return;
        }

        try {
            boolean isCorrect = authService.verifySecurityAnswer(currentUsername, answer);

            if (!isCorrect) {
                showError("Incorrect security answer. Please try again.");
                return;
            }

            transitionToStage3();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    // write the new password and bounce back to login after 2s.
    @FXML
    public void handleStage3() {
        hideError();

        if (currentStage != 3) return;

        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            showError("Please enter both password fields.");
            return;
        }

        String pwErr = ValidationUtils.validatePassword(newPassword);
        if (pwErr != null) {
            showError(pwErr);
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        try {
            authService.resetPassword(currentUsername, newPassword);

            titleLabel.setText("✓ Password Reset Successfully");
            subtitleLabel.setText("Your password has been updated. Redirecting to login...");

            stage3Container.setVisible(false);
            stage3Container.setManaged(false);
            nextButton.setVisible(false);
            nextButton.setManaged(false);
            backButton.setVisible(false);
            backButton.setManaged(false);

            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    javafx.application.Platform.runLater(this::handleBackToLogin);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    // Show stage-2 panel and rewire the next button to verify the answer.
    private void transitionToStage2(User user) {
        currentStage = 2;

        stage1Container.setVisible(false);
        stage1Container.setManaged(false);
        stage3Container.setVisible(false);
        stage3Container.setManaged(false);
        stage2Container.setVisible(true);
        stage2Container.setManaged(true);

        securityQuestionDisplay.setText(user.getSecurityQuestion());

        titleLabel.setText("Verify Your Identity");
        subtitleLabel.setText("Answer your security question to proceed");

        nextButton.setText("Verify Answer");
        nextButton.setOnAction(e -> handleStage2());

        backButton.setVisible(true);
        backButton.setManaged(true);

        securityAnswerField.requestFocus();
    }

    // Show stage-3 panel and rewire the next button to reset the password.
    private void transitionToStage3() {
        currentStage = 3;

        stage2Container.setVisible(false);
        stage2Container.setManaged(false);
        stage3Container.setVisible(true);
        stage3Container.setManaged(true);

        titleLabel.setText("Create New Password");
        subtitleLabel.setText("Enter and confirm your new password");

        nextButton.setText("Reset Password");
        nextButton.setOnAction(e -> handleStage3());

        newPasswordField.requestFocus();
    }

    // Steps the flow back one stage. From stage 3 it re-fetches the user so the
    // security question is shown again.
    @FXML
    public void handleBack() {
        if (currentStage == 2) {
            currentStage = 1;
            stage2Container.setVisible(false);
            stage2Container.setManaged(false);
            stage1Container.setVisible(true);
            stage1Container.setManaged(true);

            titleLabel.setText("Recover Your Account");
            subtitleLabel.setText("Enter your username to get started");

            nextButton.setText("Continue");
            nextButton.setOnAction(e -> handleStage1());

            backButton.setVisible(false);
            backButton.setManaged(false);

            usernameField.requestFocus();
        } else if (currentStage == 3) {
            newPasswordField.clear();
            confirmPasswordField.clear();
            Optional<User> userOpt = userRepository.findByUsername(currentUsername);
            if (userOpt.isPresent()) {
                transitionToStage2(userOpt.get());
            }
        }
    }

    private void showError(String message) {
        errorLabel.setText("⚠  " + message);
        errorLabel.setVisible(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
    }

    @FXML
    public void togglePasswordVisibility() {
        boolean show = showPasswordCheck.isSelected();
        newPasswordField.setVisible(!show);
        newPasswordField.setManaged(!show);
        newPasswordFieldVisible.setVisible(show);
        newPasswordFieldVisible.setManaged(show);
    }

    @FXML
    public void toggleConfirmPasswordVisibility() {
        boolean show = showConfirmPasswordCheck.isSelected();
        confirmPasswordField.setVisible(!show);
        confirmPasswordField.setManaged(!show);
        confirmPasswordFieldVisible.setVisible(show);
        confirmPasswordFieldVisible.setManaged(show);
    }

    @FXML
    public void handleBackToLogin() {
        try {
            Main.switchScene("LoginScreen.fxml");
        } catch (Exception e) {
            LOG.warning("Navigation to login failed: " + e.getMessage());
        }
    }
}
