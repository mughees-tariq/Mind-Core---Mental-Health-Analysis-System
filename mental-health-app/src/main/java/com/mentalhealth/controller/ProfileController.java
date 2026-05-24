package com.mentalhealth.controller;

import com.mentalhealth.Main;
import com.mentalhealth.model.EmergencyContact;
import com.mentalhealth.model.User;
import com.mentalhealth.service.EmergencyContactService;
import com.mentalhealth.service.UserService;
import com.mentalhealth.model.Prediction;
import com.mentalhealth.service.DashboardService;
import com.mentalhealth.service.DashboardService.*;
import com.mentalhealth.util.PasswordUtils;
import com.mentalhealth.util.ValidationUtils;
import com.google.gson.Gson;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import com.mentalhealth.util.CardAnimations;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

import java.util.logging.Logger;

// Controller for ProfileScreen.fxml. Aggregates data from DashboardService
// (wellness stats, heatmap, analytics, weekly report), UserService (profile
// edits, password change, account deletion), and EmergencyContactService.
// All reads from SQLite happen on a background thread; UI updates marshal
// back to the FX thread.
public class ProfileController extends BaseController {

    private static final Logger LOG = Logger.getLogger(ProfileController.class.getName());

    @FXML private BorderPane root;

    @Override
    protected BorderPane getRoot() { return root; }

    @FXML private Label nameLabel, emailLabel, memberSinceLabel, avatarInitialsLabel;
    @FXML private Label profileCompletenessLabel;
    @FXML private ProgressBar profileCompletenessBar;

    @FXML private Label ageLabel, genderLabel, locationLabel;

    @FXML private Label totalEntriesLabel, avgMoodLabel, lastSessionLabel;
    @FXML private ProgressBar wellnessProgressBar;

    @FXML private GridPane heatmapGrid;
    @FXML private HBox heatmapLegend;
    @FXML private Label heatmapRangeLabel;

    @FXML private Label topMood1Label, avgConfidenceLabel, trendDirection30Label;
    @FXML private HBox topMoodPillsContainer;

    @FXML private Label streakCountLabel, streakFireLabel;
    @FXML private HBox badgesContainer;

    @FXML private Label weeklyAnalysesLabel, weeklyDominantLabel, weeklyChangeLabel, weeklyMoodShiftLabel;

    @FXML private TextArea goalTextArea;
    @FXML private VBox affirmationsContainer;
    @FXML private TextField newAffirmationField;

    @FXML private VBox emergencyContactsContainer;
    @FXML private VBox addContactDialog;
    @FXML private TextField contactNameField, contactPhoneField, contactRelationshipField;

    @FXML private VBox editProfileDialog;
    @FXML private TextField editNameField, editAgeField, editLocationField;
    @FXML private ComboBox<String> editGenderCombo;

    @FXML private VBox changePasswordDialog;
    @FXML private PasswordField currentPasswordField, newPasswordField, confirmNewPasswordField;

    @FXML private VBox deleteAccountDialog;
    @FXML private TextField deleteConfirmField;

    @FXML private Label accountStatusLabel;

    private DashboardService dashboardService;
    private UserService userService;
    private EmergencyContactService emergencyContactService;
    private final Gson gson = new Gson();

    // FXML init. Fills the header synchronously, then fetches everything else
    // on a background thread (heatmap, analytics, contacts, completeness).
    @FXML
    public void initialize() {
        dashboardService = new DashboardService();
        userService = new UserService();
        emergencyContactService = new EmergencyContactService();

        User user = Main.getCurrentUser();
        if (user == null) return;

        populateProfileHeader(user);
        hideAllDialogs();

        if (editGenderCombo != null) {
            editGenderCombo.getItems().addAll("Male", "Female", "Non-binary", "Prefer not to say");
        }

        new Thread(() -> {
            DashboardData dashData = dashboardService.getDashboardData(user.getId());
            AnalyticsSummary analytics = dashboardService.getAnalyticsSummary(user.getId());
            WeeklyReportCard weeklyCard = dashboardService.getWeeklyReportCard(user.getId());
            Map<String, Double> heatmapData = dashboardService.getMoodHeatmapData(user.getId(), 4);
            List<EmergencyContact> contacts = emergencyContactService.findByUserId(user.getId());
            double completeness = userService.getProfileCompleteness(user.getId());

            Platform.runLater(() -> {
                populateWellnessStats(dashData);
                populateHeatmap(heatmapData);
                populateAnalytics(analytics);
                populateStreakAndBadges(dashData.streakDays);
                populateWeeklyReport(weeklyCard);
                populateEmergencyContacts(contacts);
                populateGoalsAndAffirmations(user);
                updateProfileCompleteness(completeness);
            });
        }).start();

        CardAnimations.animateAll(root, ".card");
    }

    // Fills avatar initials, name, username, member-since year, and personal info.
    private void populateProfileHeader(User user) {
        if (nameLabel != null) nameLabel.setText(user.getName());
        if (emailLabel != null) emailLabel.setText(user.getUsername());

        if (avatarInitialsLabel != null && user.getName() != null) {
            String[] parts = user.getName().trim().split("\\s+");
            String initials = parts.length >= 2
                    ? ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase()
                    : user.getName().substring(0, Math.min(2, user.getName().length())).toUpperCase();
            avatarInitialsLabel.setText(initials);
        }

        if (memberSinceLabel != null && user.getCreatedAt() != null && user.getCreatedAt().length() >= 4) {
            memberSinceLabel.setText("Member Since: " + user.getCreatedAt().substring(0, 4));
        }

        if (ageLabel != null) ageLabel.setText("Age: " + (user.getAge() != null ? user.getAge() : "--"));
        if (genderLabel != null) genderLabel.setText("Gender: " + (user.getGender() != null ? user.getGender() : "--"));
        if (locationLabel != null) locationLabel.setText("Location: " + (user.getLocation() != null ? user.getLocation() : "--"));
    }

    private void populateWellnessStats(DashboardData data) {
        if (totalEntriesLabel != null) totalEntriesLabel.setText("Total Analyses: " + data.totalAnalyses);
        if (avgMoodLabel != null) avgMoodLabel.setText("Average Mood Score: " + String.format("%.1f", calcAvgMood(data)));
        if (lastSessionLabel != null) lastSessionLabel.setText("Last Session: " + data.lastUpdateTime);
        if (wellnessProgressBar != null) wellnessProgressBar.setProgress(Math.min(1.0, data.streakDays / 7.0));
    }

    // Renders the GitHub-style 4-month activity heatmap. Columns are weeks
    // (Sun..Sat per row). Color encodes average mood score per day.
    private void populateHeatmap(Map<String, Double> data) {
        if (heatmapGrid == null) return;
        heatmapGrid.getChildren().clear();

        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Karachi"));
        LocalDate start = today.minusMonths(4);
        while (start.getDayOfWeek() != DayOfWeek.SUNDAY) {
            start = start.minusDays(1);
        }

        String[] dayLabels = {"", "Mon", "", "Wed", "", "Fri", ""};
        for (int r = 0; r < 7; r++) {
            Label dayLbl = new Label(dayLabels[r]);
            dayLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8; -fx-min-width: 28px;");
            heatmapGrid.add(dayLbl, 0, r);
        }

        int col = 1;
        LocalDate current = start;
        while (!current.isAfter(today)) {
            int row = current.getDayOfWeek().getValue() % 7;

            Region cell = new Region();
            cell.setPrefSize(13, 13);
            cell.setMinSize(13, 13);
            cell.setMaxSize(13, 13);
            cell.getStyleClass().add("heatmap-cell");

            String dateKey = current.toString();
            Double score = data.get(dateKey);
            String color = score == null ? "#ebedf0" : getHeatmapColor(score);
            cell.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");

            String tooltipText = dateKey + (score != null ? " | Score: " + String.format("%.0f", score) : " | No data");
            Tooltip.install(cell, new Tooltip(tooltipText));

            heatmapGrid.add(cell, col, row);

            if (row == 6) col++;
            current = current.plusDays(1);
        }

        if (heatmapLegend != null) {
            heatmapLegend.getChildren().clear();
            heatmapLegend.getChildren().add(new Label("Less"));
            String[] legendColors = {"#ebedf0", "#9be9a8", "#fde68a", "#fdba74", "#fca5a5"};
            for (String c : legendColors) {
                Region swatch = new Region();
                swatch.setPrefSize(13, 13);
                swatch.setMinSize(13, 13);
                swatch.setMaxSize(13, 13);
                swatch.setStyle("-fx-background-color: " + c + "; -fx-background-radius: 3;");
                heatmapLegend.getChildren().add(swatch);
            }
            heatmapLegend.getChildren().add(new Label("More"));
        }
    }

    // Maps a mood score to a heatmap cell color (green = healthy, red = distress).
    private String getHeatmapColor(double score) {
        if (score <= 25) return "#9be9a8";
        if (score <= 45) return "#fde68a";
        if (score <= 70) return "#fdba74";
        return "#fca5a5";
    }

    // Renders top mood pills, average confidence, and 30-day trend direction.
    private void populateAnalytics(AnalyticsSummary analytics) {
        if (analytics.topMoods.isEmpty()) {
            if (topMood1Label != null) topMood1Label.setText("No Data");
            if (avgConfidenceLabel != null) avgConfidenceLabel.setText("--");
            if (trendDirection30Label != null) trendDirection30Label.setText("No Data");
            return;
        }

        if (topMood1Label != null) {
            topMood1Label.setText(analytics.topMoods.get(0).mood);
            topMood1Label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + analytics.topMoods.get(0).color + ";");
        }
        if (avgConfidenceLabel != null) {
            avgConfidenceLabel.setText(String.format("%.1f%%", analytics.avgConfidence));
        }
        if (trendDirection30Label != null) {
            String trendColor = switch (analytics.trendDirection30Day) {
                case "Improving" -> "#10b981";
                case "Declining" -> "#ef4444";
                default -> "#64748b";
            };
            trendDirection30Label.setText(analytics.trendDirection30Day);
            trendDirection30Label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + trendColor + ";");
        }

        if (topMoodPillsContainer != null) {
            topMoodPillsContainer.getChildren().clear();
            for (MoodFrequency mf : analytics.topMoods) {
                Label pill = new Label(mf.mood + " (" + mf.count + " - " + String.format("%.0f%%", mf.percentage) + ")");
                pill.getStyleClass().add("analytics-pill");
                pill.setStyle("-fx-background-color: " + mf.color + "22; -fx-text-fill: " + mf.color + ";");
                topMoodPillsContainer.getChildren().add(pill);
            }
        }
    }

    // Shows streak count and unlocks badge icons at 7/30/100-day milestones.
    private void populateStreakAndBadges(int streakDays) {
        if (streakCountLabel != null) streakCountLabel.setText(streakDays + " days");
        if (streakFireLabel != null) streakFireLabel.setText(streakDays > 0 ? "🔥" : "");

        if (badgesContainer == null) return;
        badgesContainer.getChildren().clear();

        int[][] milestones = {{7, 0}, {30, 1}, {100, 2}};
        String[] names = {"7-Day", "30-Day", "100-Day"};
        String[] icons = {"🏅", "🏆", "💎"};

        for (int i = 0; i < milestones.length; i++) {
            int target = milestones[i][0];
            boolean earned = streakDays >= target;

            VBox badge = new VBox(4);
            badge.setAlignment(Pos.CENTER);
            badge.setPrefWidth(85);
            badge.getStyleClass().add(earned ? "badge-earned" : "badge-locked");

            Label icon = new Label(earned ? icons[i] : "🔒");
            icon.setStyle("-fx-font-size: 24px;");

            Label text = new Label(names[i]);
            text.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + (earned ? "white" : "#94a3b8") + ";");

            badge.getChildren().addAll(icon, text);
            badgesContainer.getChildren().add(badge);
        }
    }

    private void populateWeeklyReport(WeeklyReportCard card) {
        if (weeklyAnalysesLabel != null) weeklyAnalysesLabel.setText(String.valueOf(card.analysesThisWeek));
        if (weeklyDominantLabel != null) weeklyDominantLabel.setText(card.dominantMoodThisWeek);
        if (weeklyChangeLabel != null) weeklyChangeLabel.setText(card.changeDescription);
        if (weeklyMoodShiftLabel != null) {
            String shiftColor = switch (card.moodShift) {
                case "Improved" -> "#10b981";
                case "Declined" -> "#ef4444";
                default -> "#64748b";
            };
            weeklyMoodShiftLabel.setText(card.moodShift);
            weeklyMoodShiftLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + shiftColor + ";");
        }
    }

    // Renders the crisis hotlines card followed by each saved emergency contact.
    private void populateEmergencyContacts(List<EmergencyContact> contacts) {
        if (emergencyContactsContainer == null) return;
        emergencyContactsContainer.getChildren().clear();

        VBox crisisCard = new VBox(8);
        crisisCard.getStyleClass().add("crisis-card");
        Label crisisTitle = new Label("☎  Crisis Hotlines");
        crisisTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #dc2626;");
        Label usLine = new Label("988 Suicide & Crisis Lifeline (US)");
        usLine.setStyle("-fx-text-fill: #7f1d1d;");
        Label pkLine = new Label("0311-7786264 (Pakistan)");
        pkLine.setStyle("-fx-text-fill: #7f1d1d;");
        crisisCard.getChildren().addAll(crisisTitle, usLine, pkLine);
        emergencyContactsContainer.getChildren().add(crisisCard);

        for (EmergencyContact c : contacts) {
            HBox contactRow = new HBox(15);
            contactRow.setAlignment(Pos.CENTER_LEFT);
            contactRow.getStyleClass().add("contact-card");

            VBox info = new VBox(2);
            HBox.setHgrow(info, Priority.ALWAYS);
            Label nameLbl = new Label(c.getName());
            nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            Label phoneLbl = new Label(c.getPhone());
            phoneLbl.setStyle("-fx-text-fill: #64748b;");
            Label relLbl = new Label(c.getRelationship() != null ? c.getRelationship() : "");
            relLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            info.getChildren().addAll(nameLbl, phoneLbl, relLbl);

            Button deleteBtn = new Button("✖");
            deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-cursor: hand; -fx-font-size: 14px;");
            deleteBtn.setOnAction(e -> handleDeleteContact(c.getId()));

            contactRow.getChildren().addAll(info, deleteBtn);
            emergencyContactsContainer.getChildren().add(contactRow);
        }
    }

    // Loads the goal textarea and affirmation list from the JSON blob in User.
    private void populateGoalsAndAffirmations(User user) {
        if (goalTextArea != null) {
            goalTextArea.setText(user.getGoal() != null ? user.getGoal() : "");
        }

        if (affirmationsContainer == null) return;
        affirmationsContainer.getChildren().clear();

        if (user.getAffirmations() != null && !user.getAffirmations().isEmpty()) {
            try {
                String[] affs = gson.fromJson(user.getAffirmations(), String[].class);
                for (int i = 0; i < affs.length; i++) {
                    final int index = i;
                    HBox row = new HBox(10);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("affirmation-row");

                    Label text = new Label("✨ " + affs[i]);
                    text.setStyle("-fx-font-size: 13px;");
                    text.setWrapText(true);
                    HBox.setHgrow(text, Priority.ALWAYS);

                    Button removeBtn = new Button("✖");
                    removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-cursor: hand;");
                    removeBtn.setOnAction(e -> handleRemoveAffirmation(index));

                    row.getChildren().addAll(text, removeBtn);
                    affirmationsContainer.getChildren().add(row);
                }
            } catch (Exception e) {
                LOG.warning("Error parsing affirmations: " + e.getMessage());
            }
        }
    }

    private void updateProfileCompleteness(double completeness) {
        if (profileCompletenessBar != null) profileCompletenessBar.setProgress(completeness);
        if (profileCompletenessLabel != null) {
            profileCompletenessLabel.setText("Profile " + (int)(completeness * 100) + "% complete");
        }
    }

    // Opens the edit-profile dialog pre-filled with current values.
    @FXML
    public void handleEditProfile() {
        User user = Main.getCurrentUser();
        if (user == null) return;

        if (editNameField != null) editNameField.setText(user.getName());
        if (editAgeField != null) editAgeField.setText(user.getAge() != null ? String.valueOf(user.getAge()) : "");
        if (editGenderCombo != null && user.getGender() != null) editGenderCombo.setValue(user.getGender());
        if (editLocationField != null) editLocationField.setText(user.getLocation() != null ? user.getLocation() : "");

        showDialog(editProfileDialog);
    }

    // Persists only the fields that have changed, then refreshes the header.
    @FXML
    public void handleSaveProfile() {
        User user = Main.getCurrentUser();
        if (user == null) return;

        String newName = editNameField != null ? editNameField.getText().trim() : "";
        String ageText = editAgeField != null ? editAgeField.getText().trim() : "";
        String gender = editGenderCombo != null ? editGenderCombo.getValue() : null;
        String location = editLocationField != null ? editLocationField.getText().trim() : "";

        if (!newName.isEmpty()) {
            String nameErr = ValidationUtils.validateName(newName);
            if (nameErr != null) { setAccountStatus(nameErr, "#ef4444"); return; }
        }

        if (!ageText.isEmpty()) {
            String ageErr = ValidationUtils.validateAge(ageText);
            if (ageErr != null) { setAccountStatus(ageErr, "#ef4444"); return; }
        }

        if (location.length() > 200) {
            setAccountStatus("Location must be 200 characters or less.", "#ef4444");
            return;
        }

        new Thread(() -> {
            if (!newName.isEmpty()) {
                userService.updateName(user.getId(), newName);
                user.setName(newName);
            }
            if (!ageText.isEmpty()) {
                try {
                    int age = Integer.parseInt(ageText);
                    userService.updateAge(user.getId(), age);
                    user.setAge(age);
                } catch (NumberFormatException ignored) {}
            }
            if (gender != null) {
                userService.updateGender(user.getId(), gender);
                user.setGender(gender);
            }
            if (!location.isEmpty()) {
                userService.updateLocation(user.getId(), location);
                user.setLocation(location);
            }

            Main.setCurrentUser(user);
            double completeness = userService.getProfileCompleteness(user.getId());

            Platform.runLater(() -> {
                populateProfileHeader(user);
                updateProfileCompleteness(completeness);
                hideDialog(editProfileDialog);
            });
        }).start();
    }

    @FXML
    public void handleCancelEdit() {
        hideDialog(editProfileDialog);
    }

    // Saves the free-text wellness goal to the DB via UserService.
    @FXML
    public void handleSaveGoal() {
        User user = Main.getCurrentUser();
        if (user == null || goalTextArea == null) return;
        String goal = goalTextArea.getText().trim();

        if (goal.length() > 1000) {
            setAccountStatus("Goal must be 1000 characters or less.", "#ef4444");
            return;
        }

        new Thread(() -> {
            userService.updateGoal(user.getId(), goal);
            user.setGoal(goal);
            Main.setCurrentUser(user);
            double completeness = userService.getProfileCompleteness(user.getId());
            Platform.runLater(() -> updateProfileCompleteness(completeness));
        }).start();
    }

    // Appends a new affirmation to the JSON array stored in the users table.
    @FXML
    public void handleAddAffirmation() {
        if (newAffirmationField == null) return;
        String text = newAffirmationField.getText().trim();
        if (text.isEmpty()) return;

        if (text.length() > 500) {
            setAccountStatus("Affirmation must be 500 characters or less.", "#ef4444");
            return;
        }

        User user = Main.getCurrentUser();
        if (user == null) return;

        List<String> affs = new ArrayList<>();
        if (user.getAffirmations() != null && !user.getAffirmations().isEmpty()) {
            try {
                String[] existing = gson.fromJson(user.getAffirmations(), String[].class);
                affs.addAll(Arrays.asList(existing));
            } catch (Exception ignored) {}
        }
        affs.add(text);
        String json = gson.toJson(affs);

        new Thread(() -> {
            userService.updateAffirmations(user.getId(), json);
            user.setAffirmations(json);
            Main.setCurrentUser(user);
            double completeness = userService.getProfileCompleteness(user.getId());
            Platform.runLater(() -> {
                populateGoalsAndAffirmations(user);
                newAffirmationField.clear();
                updateProfileCompleteness(completeness);
            });
        }).start();
    }

    // Removes one affirmation by index from the JSON array.
    private void handleRemoveAffirmation(int index) {
        User user = Main.getCurrentUser();
        if (user == null || user.getAffirmations() == null) return;

        try {
            String[] existing = gson.fromJson(user.getAffirmations(), String[].class);
            List<String> affs = new ArrayList<>(Arrays.asList(existing));
            if (index >= 0 && index < affs.size()) {
                affs.remove(index);
            }
            String json = affs.isEmpty() ? null : gson.toJson(affs);

            new Thread(() -> {
                userService.updateAffirmations(user.getId(), json);
                user.setAffirmations(json);
                Main.setCurrentUser(user);
                Platform.runLater(() -> populateGoalsAndAffirmations(user));
            }).start();
        } catch (Exception e) {
            LOG.warning("Error removing affirmation: " + e.getMessage());
        }
    }

    // Clears the add-contact form and shows the dialog.
    @FXML
    public void handleAddContact() {
        if (contactNameField != null) contactNameField.clear();
        if (contactPhoneField != null) contactPhoneField.clear();
        if (contactRelationshipField != null) contactRelationshipField.clear();
        showDialog(addContactDialog);
    }

    // Saves a new emergency contact and refreshes the contacts list.
    @FXML
    public void handleSaveContact() {
        String name = contactNameField != null ? contactNameField.getText().trim() : "";
        String phone = contactPhoneField != null ? contactPhoneField.getText().trim() : "";
        String relationship = contactRelationshipField != null ? contactRelationshipField.getText().trim() : "";

        if (name.isEmpty()) {
            setAccountStatus("Contact name is required.", "#ef4444");
            return;
        }
        String phoneErr = ValidationUtils.validatePhone(phone);
        if (phoneErr != null) {
            setAccountStatus(phoneErr, "#ef4444");
            return;
        }

        User user = Main.getCurrentUser();
        if (user == null) return;

        EmergencyContact contact = new EmergencyContact(user.getId(), name, phone, relationship);

        new Thread(() -> {
            emergencyContactService.save(contact);
            List<EmergencyContact> contacts = emergencyContactService.findByUserId(user.getId());
            Platform.runLater(() -> {
                populateEmergencyContacts(contacts);
                hideDialog(addContactDialog);
            });
        }).start();
    }

    @FXML
    public void handleCancelContact() {
        hideDialog(addContactDialog);
    }

    private void handleDeleteContact(int contactId) {
        User user = Main.getCurrentUser();
        if (user == null) return;

        new Thread(() -> {
            emergencyContactService.delete(contactId);
            List<EmergencyContact> contacts = emergencyContactService.findByUserId(user.getId());
            Platform.runLater(() -> populateEmergencyContacts(contacts));
        }).start();
    }

    // Opens a FileChooser and writes all predictions to CSV.
    @FXML
    public void handleExportData() {
        User user = Main.getCurrentUser();
        if (user == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Analysis History");
        fileChooser.setInitialFileName("mental_health_export_" + LocalDate.now(java.time.ZoneId.of("Asia/Karachi")) + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = fileChooser.showSaveDialog(Main.getPrimaryStage());
        if (file == null) return;

        new Thread(() -> {
            List<Prediction> all = dashboardService.getAllPredictions(user.getId());
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("Date,Prediction,Confidence,Severity,Input Text");
                for (Prediction p : all) {
                    String escapedText = "\"" + (p.getInputText() != null ? p.getInputText().replace("\"", "\"\"") : "") + "\"";
                    writer.printf("%s,%s,%.4f,%s,%s%n",
                            p.getCreatedAt(), p.getPrediction(), p.getConfidence(),
                            p.getSeverity(), escapedText);
                }
            } catch (Exception e) {
                Platform.runLater(() -> Main.showError("Export Error", e.getMessage()));
                return;
            }
            Platform.runLater(() -> Main.showInfo("Export Complete",
                    "Exported " + all.size() + " entries to " + file.getName()));
        }).start();
    }

    @FXML
    public void handleShowChangePassword() {
        if (currentPasswordField != null) currentPasswordField.clear();
        if (newPasswordField != null) newPasswordField.clear();
        if (confirmNewPasswordField != null) confirmNewPasswordField.clear();
        showDialog(changePasswordDialog);
    }

    // Verifies the current password, validates the new one, then persists hash.
    @FXML
    public void handleSavePassword() {
        User user = Main.getCurrentUser();
        if (user == null) return;

        String currentPw = currentPasswordField != null ? currentPasswordField.getText() : "";
        String newPw = newPasswordField != null ? newPasswordField.getText() : "";
        String confirmPw = confirmNewPasswordField != null ? confirmNewPasswordField.getText() : "";

        if (currentPw.isEmpty()) {
            setAccountStatus("Please enter your current password.", "#ef4444");
            return;
        }
        if (!PasswordUtils.verifyPassword(currentPw, user.getPasswordHash())) {
            setAccountStatus("Current password is incorrect.", "#ef4444");
            return;
        }
        String pwErr = ValidationUtils.validatePassword(newPw);
        if (pwErr != null) {
            setAccountStatus(pwErr, "#ef4444");
            return;
        }
        if (confirmPw.isEmpty()) {
            setAccountStatus("Please confirm your new password.", "#ef4444");
            return;
        }
        if (!newPw.equals(confirmPw)) {
            setAccountStatus("New passwords do not match.", "#ef4444");
            return;
        }

        String hashedPassword = PasswordUtils.hashPassword(newPw);
        boolean updated = userService.updatePassword(user.getId(), hashedPassword);
        if (updated) {
            user.setPasswordHash(hashedPassword);
            Main.setCurrentUser(user);
            setAccountStatus("Password updated successfully!", "#10b981");
            hideDialog(changePasswordDialog);
        }
    }

    @FXML
    public void handleCancelPassword() {
        hideDialog(changePasswordDialog);
    }

    // Confirmation dialog before wiping all prediction/mood history for this user.
    @FXML
    public void handleClearHistory() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear History");
        confirm.setHeaderText("This will delete all your analysis history, mood entries, and reports.");
        confirm.setContentText("This action cannot be undone. Continue?");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            User user = Main.getCurrentUser();
            if (user == null) return;
            boolean cleared = userService.clearHistory(user.getId());
            if (cleared) {
                user.setFeelingHistory(null);
                Main.setCurrentUser(user);
                setAccountStatus("All history has been cleared.", "#10b981");
                initialize();
            }
        }
    }

    @FXML
    public void handleShowDeleteAccount() {
        if (deleteConfirmField != null) deleteConfirmField.clear();
        showDialog(deleteAccountDialog);
    }

    // Requires the user to type "DELETE" as a safety check before hard-deleting
    // the account row and returning to the login screen.
    @FXML
    public void handleDeleteAccount() {
        String confirmText = deleteConfirmField != null ? deleteConfirmField.getText().trim() : "";
        if (!"DELETE".equals(confirmText)) {
            setAccountStatus("Type DELETE to confirm account deletion", "#ef4444");
            return;
        }

        User user = Main.getCurrentUser();
        if (user == null) return;

        boolean deleted = userService.deleteUser(user.getId());
        if (deleted) {
            Main.setCurrentUser(null);
            try {
                Main.switchScene("LoginScreen.fxml");
            } catch (Exception e) {
                LOG.warning("Navigation failed: " + e.getMessage());
            }
        }
    }

    @FXML
    public void handleCancelDelete() {
        hideDialog(deleteAccountDialog);
    }

    // Average mood score across the last 7 chart points.
    private double calcAvgMood(DashboardData data) {
        if (data.last7DaysChart == null || data.last7DaysChart.isEmpty()) return 0;
        double sum = 0;
        for (ChartPoint p : data.last7DaysChart) sum += p.score;
        return sum / data.last7DaysChart.size();
    }

    private void setAccountStatus(String message, String color) {
        if (accountStatusLabel != null) {
            accountStatusLabel.setText(message);
            accountStatusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px;");
        }
    }

    private void showDialog(VBox dialog) {
        if (dialog != null) {
            dialog.setVisible(true);
            dialog.setManaged(true);
        }
    }

    private void hideDialog(VBox dialog) {
        if (dialog != null) {
            dialog.setVisible(false);
            dialog.setManaged(false);
        }
    }

    private void hideAllDialogs() {
        hideDialog(editProfileDialog);
        hideDialog(addContactDialog);
        hideDialog(changePasswordDialog);
        hideDialog(deleteAccountDialog);
    }
}
