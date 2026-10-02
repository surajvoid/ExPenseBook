package com.expensebook;

import com.expensebook.model.User;
import com.expensebook.util.DBConnection;
import com.expensebook.util.SessionManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("ExPense Book — Track • Analyse • Control");
        primaryStage.setMinWidth(1080);
        primaryStage.setMinHeight(720);

        // Pre-initialize database
        try {
            DBConnection.getConnection();
        } catch (Exception ignored) {}

        // Auto-restore previous login session if available
        User restoredUser = SessionManager.restorePersistedSession();
        if (restoredUser != null) {
            showMainApp();
        } else {
            showOnboardingScreen();
        }
        primaryStage.show();
    }

    public static void showOnboardingScreen() {
        loadScene("/fxml/onboarding.fxml", 1080, 720);
    }

    public static void showLoginScreen() {
        loadScene("/fxml/login.fxml", 1080, 720);
    }

    public static void showMainApp() {
        loadScene("/fxml/main_layout.fxml", 1200, 780);
    }

    private static void loadScene(String fxmlPath, double width, double height) {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(Main.class.getResource("/css/theme.css").toExternalForm());

            primaryStage.setScene(scene);
            primaryStage.centerOnScreen();
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR loading scene " + fxmlPath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        DBConnection.closeConnection();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
