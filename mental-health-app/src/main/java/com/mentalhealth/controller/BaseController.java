package com.mentalhealth.controller;

import com.mentalhealth.Main;
import com.mentalhealth.util.CardAnimations;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;

import java.util.logging.Level;
import java.util.logging.Logger;

// Shared base for every screen controller. Holds the sidebar/topbar nav handlers
// (Dashboard, History, Profile, Logout, etc.) and small UI helpers so each screen
// doesn't reimplement them. Subclasses just override getRoot() for animations.
public abstract class BaseController {

    private static final Logger LOG = Logger.getLogger(BaseController.class.getName());

    // Subclass returns its root BorderPane so card animations can target it.
    protected abstract BorderPane getRoot();

    // Plays the card entrance animation. Call from the subclass initialize().
    protected void animateCards() {
        BorderPane root = getRoot();
        if (root != null) {
            CardAnimations.animateAll(root, ".card");
        }
    }

    @FXML
    public void handleDashboard() {
        navigateTo("MainWindow.fxml", "Dashboard");
    }

    @FXML
    public void handleNewAnalysis() {
        navigateTo("AnalysisScreen.fxml", "New Analysis");
    }

    @FXML
    public void handleHistory() {
        navigateTo("HistoryScreen.fxml", "Analysis History");
    }

    @FXML
    public void handleMoodTracker() {
        navigateTo("MoodTrackerScreen.fxml", "Mood Tracker");
    }

    @FXML
    public void handleProfile() {
        navigateTo("ProfileScreen.fxml", "Profile");
    }

    @FXML
    public void handleAbout() {
        navigateTo("AboutScreen.fxml", "About");
    }

    @FXML
    public void handleRecommendations() {
        navigateTo("RecommendationScreen.fxml", "Recommendations");
    }

    // Clears the session and sends the user back to the login screen.
    @FXML
    public void handleLogout() {
        try {
            Main.setCurrentUser(null);
            Main.switchScene("LoginScreen.fxml");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Logout failed", e);
        }
    }

    // Scene switch with title; logs and swallows failure so nav never crashes UI.
    protected void navigateTo(String fxml, String title) {
        try {
            Main.switchScene(fxml, title);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Navigation to " + title + " failed", e);
        }
    }

    protected void navigateTo(String fxml) {
        try {
            Main.switchScene(fxml);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Navigation failed", e);
        }
    }

    protected void showError(String message) {
        Main.showError("Error", message);
    }

    protected void showSuccess(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
