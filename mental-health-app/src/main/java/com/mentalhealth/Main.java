package com.mentalhealth;

import com.mentalhealth.database.DatabaseManager;
import com.mentalhealth.model.User;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.logging.Logger;

// Application entry point. Loads FXML scenes from /resources/fxml, holds the
// current logged-in User session, and exposes shared dialogs/threading helpers
// used across every controller.
public class Main extends Application {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private static Stage primaryStage;
    private static User currentUser;
    private static DatabaseManager dbManager;

    // JavaFX startup hook. Opens the DB, builds the login scene, attaches CSS.
    @Override
    public void start(Stage stage) throws Exception {
        Main.primaryStage = stage;

        dbManager = DatabaseManager.getInstance();

        stage.setTitle("Mental Health Analysis");
        stage.setMinWidth(1000);
        stage.setMinHeight(700);

        Parent root = FXMLLoader.load(getClass().getResource("/fxml/LoginScreen.fxml"));
        Scene scene = new Scene(root);
        stage.setScene(scene);

        try {
            String css = getClass().getResource("/css/styles.css").toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception e) {
            LOG.info("CSS not found, continuing without it");
        }

        stage.centerOnScreen();

        stage.setOnCloseRequest(e -> {
            closeApplication();
        });

        stage.show();
    }

    // Swap the root of the existing scene so window size/position stay intact.
    public static void switchScene(String fxmlFile) throws Exception {
        Parent root = FXMLLoader.load(Main.class.getResource("/fxml/" + fxmlFile));

        Scene currentScene = primaryStage.getScene();
        if (currentScene != null) {
            currentScene.setRoot(root);
        } else {
            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
        }

        try {
            String css = Main.class.getResource("/css/styles.css").toExternalForm();
            primaryStage.getScene().getStylesheets().add(css);
        } catch (Exception e) {
            // CSS already attached on first load
        }
    }

    // Switch scene and update the window title in one call.
    public static void switchScene(String fxmlFile, String title) throws Exception {
        switchScene(fxmlFile);
        primaryStage.setTitle(title);
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    // Stores the active session user. Pass null on logout.
    public static void setCurrentUser(User user) {
        currentUser = user;
        if (user != null) {
            LOG.info("User set: " + user.getUsername() + " (ID: " + user.getId() + ")");
        } else {
            LOG.info("User logged out");
        }
    }

    public static boolean isUserLoggedIn() {
        return currentUser != null;
    }

    // Info popup, marshalled to the FX thread.
    public static void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.initOwner(primaryStage);
            alert.showAndWait();
        });
    }

    // Error popup, marshalled to the FX thread.
    public static void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.initOwner(primaryStage);
            alert.showAndWait();
        });
    }

    // Warning popup, marshalled to the FX thread.
    public static void showWarning(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.initOwner(primaryStage);
            alert.showAndWait();
        });
    }

    // Blocking yes/no dialog. Returns true only if the user clicks OK.
    public static boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(primaryStage);
        return alert.showAndWait().map(response -> response == javafx.scene.control.ButtonType.OK).orElse(false);
    }

    // Clears the session, closes the DB, and shuts the JVM down.
    public static void closeApplication() {
        LOG.info("Closing application...");

        currentUser = null;

        if (dbManager != null) {
            dbManager.closeConnection();
        }

        Platform.exit();
        System.exit(0);
    }

    // Fire-and-forget worker thread for blocking work (DB, HTTP).
    public static void runBackground(Runnable task) {
        new Thread(task).start();
    }

    // Run on the FX thread. Executes inline if already on it.
    public static void runOnUI(Runnable task) {
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
