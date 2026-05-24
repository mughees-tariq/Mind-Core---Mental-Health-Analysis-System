package com.mentalhealth.controller;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import com.mentalhealth.Main;
import com.mentalhealth.model.User;
import com.mentalhealth.repository.UserRepository;
import com.mentalhealth.service.AuthService;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

// Controller for LoginScreen.fxml. Reads username/password from the form and
// hands them to AuthService, which queries UserRepository against the local
// SQLite DB. On success the User is stored in Main and the dashboard loads.
public class LoginController {

    private static final Logger LOG = Logger.getLogger(LoginController.class.getName());

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordTextField;
    @FXML private Button togglePasswordBtn;
    @FXML private Label errorLabel;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private ImageView logoImageView;
    @FXML private ImageView sidebarImage;

    // Swaps the masked PasswordField and the plain TextField so the user can
    // peek at what they typed. Keeps both in sync so the value is preserved.
    @FXML
    private void handleTogglePassword(javafx.event.ActionEvent event) {
        if (passwordField != null && passwordTextField != null && togglePasswordBtn != null) {
            if (passwordField.isVisible()) {
                passwordTextField.setText(passwordField.getText());
                passwordTextField.setVisible(true);
                passwordTextField.setManaged(true);
                passwordField.setVisible(false);
                passwordField.setManaged(false);
                togglePasswordBtn.setText("🙈");
            } else {
                passwordField.setText(passwordTextField.getText());
                passwordField.setVisible(true);
                passwordField.setManaged(true);
                passwordTextField.setVisible(false);
                passwordTextField.setManaged(false);
                togglePasswordBtn.setText("👁️");
            }
        }
    }
    private boolean passwordVisible = false;
    private AuthService authService;

    // FXML lifecycle hook. Wires services and loads the logo + sidebar art.
    @FXML
    public void initialize() {
        if (errorLabel != null) errorLabel.setVisible(false);
        if (loadingIndicator != null) loadingIndicator.setVisible(false);
        authService = new AuthService();
        LOG.info("LoginController initialized");
        LOG.info("Logo resource: " + getClass().getResource("/images/logo.png"));

        var url = getClass().getResource("/images/logo.png");
        LOG.info("Logo URL = " + url);

        if (url != null) {
            logoImageView.setImage(new Image(url.toExternalForm()));
        }

        try {
                sidebarImage.setImage(
                    new Image(
                        getClass().getResourceAsStream("/images/Sidebar-Image.png")
                    )
                );
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to load sidebar image", e);
            }
    }

    // Login button handler. Validates fields, calls AuthService, stores the
    // session in Main, and navigates to the dashboard on success.
    @FXML
    public void handleLogin() {
        hideError();

        String username = usernameField.getText().trim();
        String password = passwordField.isVisible() ? passwordField.getText() : passwordTextField.getText();

        if (username.isEmpty()) {
            showError("Please enter your username");
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter your password");
            return;
        }

        try {
            User user = authService.login(username, password);

            LOG.info("Login successful: " + username);
            Main.setCurrentUser(user);

            Main.switchScene("MainWindow.fxml");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    public void handleRegisterLink() {
        try {
            Main.switchScene("RegisterScreen.fxml");
        } catch (Exception e) {
            showError("Failed to load registration");
        }
    }

    @FXML
    public void handleForgotPassword() {
        try {
            Main.switchScene("ForgotPasswordScreen.fxml");
        } catch (Exception e) {
            showError("Failed to load password recovery: " + e.getMessage());
            LOG.warning("Navigation to ForgotPasswordScreen failed: " + e.getMessage());
        }
    }

    private void showError(String message) {
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setVisible(true);
        }
    }

    private void hideError() {
        if (errorLabel != null) errorLabel.setVisible(false);
    }
}
