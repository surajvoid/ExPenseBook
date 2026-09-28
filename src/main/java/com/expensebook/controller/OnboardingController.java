package com.expensebook.controller;

import com.expensebook.Main;
import com.expensebook.model.FinancialMode;
import com.expensebook.model.User;
import com.expensebook.service.UserService;
import com.expensebook.util.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

public class OnboardingController implements Initializable {

    // Step containers
    @FXML private VBox step1Welcome;
    @FXML private VBox step2Account;
    @FXML private VBox step3Personalize;

    // Step 2 Account Inputs
    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label accountErrorLabel;

    // Step 3 Personalization
    @FXML private RadioButton radioTrackOnly;
    @FXML private RadioButton radioTrackBudget;
    @FXML private ToggleGroup modeGroup;
    @FXML private VBox cardTrackOnly;
    @FXML private VBox cardTrackBudget;

    private final UserService userService = new UserService();
    private User newlyCreatedUser;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        showStep(1);
        if (accountErrorLabel != null) accountErrorLabel.setVisible(false);

        // Clickable cards in step 3
        if (cardTrackOnly != null && radioTrackOnly != null) {
            cardTrackOnly.setOnMouseClicked(e -> radioTrackOnly.setSelected(true));
        }
        if (cardTrackBudget != null && radioTrackBudget != null) {
            cardTrackBudget.setOnMouseClicked(e -> radioTrackBudget.setSelected(true));
        }
    }

    private void showStep(int step) {
        step1Welcome.setVisible(step == 1);
        step1Welcome.setManaged(step == 1);

        step2Account.setVisible(step == 2);
        step2Account.setManaged(step == 2);

        step3Personalize.setVisible(step == 3);
        step3Personalize.setManaged(step == 3);
    }

    @FXML
    private void handleGetStarted() {
        showStep(2);
    }

    @FXML
    private void handleCreateAccount() {
        accountErrorLabel.setVisible(false);

        String name = fullNameField.getText();
        String email = emailField.getText();
        String pass = passwordField.getText();
        String confPass = confirmPasswordField.getText();

        try {
            // Default track only during initial registration, will finalize in step 3
            newlyCreatedUser = userService.register(name, email, pass, confPass, FinancialMode.TRACK_ONLY);
            SessionManager.setCurrentUser(newlyCreatedUser);
            showStep(3);
        } catch (Exception e) {
            accountErrorLabel.setText(e.getMessage());
            accountErrorLabel.setVisible(true);
        }
    }

    @FXML
    private void handleGoToLogin() {
        Main.showLoginScreen();
    }

    @FXML
    private void handleFinalizePersonalization() {
        FinancialMode selectedMode = radioTrackBudget.isSelected() ?
                FinancialMode.TRACK_AND_BUDGET : FinancialMode.TRACK_ONLY;

        if (newlyCreatedUser != null) {
            try {
                userService.switchFinancialMode(newlyCreatedUser.getId(), selectedMode);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Main.showMainApp();
    }
}
