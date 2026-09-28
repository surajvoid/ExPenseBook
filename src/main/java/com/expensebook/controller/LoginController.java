package com.expensebook.controller;

import com.expensebook.Main;
import com.expensebook.model.User;
import com.expensebook.service.UserService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button btnSignIn;
    @FXML private Button btnRegister;

    private final UserService userService = new UserService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        errorLabel.setVisible(false);
    }

    @FXML
    private void handleSignIn() {
        errorLabel.setVisible(false);
        String email = emailField.getText();
        String pass = passwordField.getText();

        try {
            User user = userService.login(email, pass);
            Main.showMainApp();
        } catch (Exception e) {
            errorLabel.setText(e.getMessage());
            errorLabel.setVisible(true);
        }
    }

    @FXML
    private void handleGoToRegister() {
        Main.showOnboardingScreen();
    }

    @FXML
    private void handleQuickDemoAdmin() {
        emailField.setText("admin@expensebook.com");
        passwordField.setText("Admin@123");
    }
}
