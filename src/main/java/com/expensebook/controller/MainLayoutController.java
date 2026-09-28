package com.expensebook.controller;

import com.expensebook.Main;
import com.expensebook.model.FinancialMode;
import com.expensebook.model.User;
import com.expensebook.service.ExpenseService;
import com.expensebook.util.DBConnection;
import com.expensebook.util.DateUtil;
import com.expensebook.util.SVGIconUtil;
import com.expensebook.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class MainLayoutController implements Initializable {

    @FXML private Label brandTitle;
    @FXML private Label userGreetingLabel;
    @FXML private Label currentPeriodLabel;
    @FXML private Label modeBadgeLabel;
    @FXML private Label dbStatusBadge;
    @FXML private Label sidebarUserName;
    @FXML private Label sidebarUserEmail;
    @FXML private Label sidebarUserMode;
    @FXML private StackPane contentArea;
    @FXML private Button btnAddExpenseHeader;

    // Nav buttons
    @FXML private Button btnNavDashboard;
    @FXML private Button btnNavAddExpense;
    @FXML private Button btnNavTransactions;
    @FXML private Button btnNavCalendar;
    @FXML private Button btnNavAnalytics;
    @FXML private Button btnNavBudget;
    @FXML private Button btnNavIncome;
    @FXML private Button btnNavRecurring;
    @FXML private Button btnNavReports;
    @FXML private Button btnNavSettings;
    @FXML private Button btnNavAdmin;
    @FXML private Button btnNavLogout;

    private final Map<String, Node> viewCache = new HashMap<>();
    private final Map<String, Object> controllerCache = new HashMap<>();
    private Button currentActiveBtn;

    private static MainLayoutController instance;

    public static MainLayoutController getInstance() {
        return instance;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        instance = this;
        updateUserDisplay();
        setupNavIcons();

        // Listen for session/profile/mode changes
        SessionManager.addListener(() -> Platform.runLater(this::updateUserDisplay));

        // Default navigate to Dashboard
        navigateTo("dashboard", btnNavDashboard);
    }

    public void updateUserDisplay() {
        User user = SessionManager.getCurrentUser();
        if (user != null) {
            userGreetingLabel.setText(DateUtil.getGreeting() + ", " + user.getFullName().split(" ")[0] + " 👋");
            sidebarUserName.setText(user.getFullName());
            sidebarUserEmail.setText(user.getEmail());

            boolean isBudget = user.isBudgetMode();
            modeBadgeLabel.setText(isBudget ? "💰 Track + Budget Mode" : "📝 Track Only Mode");
            modeBadgeLabel.getStyleClass().removeAll("badge-sage", "badge-blue");
            modeBadgeLabel.getStyleClass().add(isBudget ? "badge-blue" : "badge-sage");

            sidebarUserMode.setText(isBudget ? "Track + Budget" : "Track Only");

            // Budget nav button styling or visibility
            if (btnNavBudget != null) {
                btnNavBudget.setVisible(true);
                btnNavBudget.setManaged(true);
                if (!isBudget) {
                    btnNavBudget.setText("💰 Budget (Disabled)");
                    btnNavBudget.setOpacity(0.5);
                } else {
                    btnNavBudget.setText("💰 Budget");
                    btnNavBudget.setOpacity(1.0);
                }
            }

            // Admin nav button visibility
            if (btnNavAdmin != null) {
                boolean isAdmin = user.isAdmin();
                btnNavAdmin.setVisible(isAdmin);
                btnNavAdmin.setManaged(isAdmin);
            }
        }

        currentPeriodLabel.setText(DateUtil.getCurrentMonthYearString());
        if (dbStatusBadge != null) {
            dbStatusBadge.setText(DBConnection.isMySQL() ? "MySQL 8.0" : "SQLite");
        }
    }

    private void setupNavIcons() {
        // Set icons on nav buttons if needed
    }

    @FXML
    private void handleNavDashboard() {
        navigateTo("dashboard", btnNavDashboard);
    }

    @FXML
    public void handleNavAddExpense() {
        openExpenseModal(null);
    }

    @FXML
    private void handleNavTransactions() {
        navigateTo("transactions", btnNavTransactions);
    }

    @FXML
    private void handleNavCalendar() {
        navigateTo("calendar", btnNavCalendar);
    }

    @FXML
    private void handleNavAnalytics() {
        navigateTo("analytics", btnNavAnalytics);
    }

    @FXML
    private void handleNavBudget() {
        if (!SessionManager.isBudgetMode()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Enable Budget Mode");
            alert.setHeaderText("Budget Mode is currently inactive");
            alert.setContentText("Would you like to switch to 'Track + Monthly Budget' mode to access Budget features?");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try {
                        new com.expensebook.service.UserService().switchFinancialMode(
                                SessionManager.getCurrentUserId(),
                                FinancialMode.TRACK_AND_BUDGET
                        );
                        updateUserDisplay();
                        navigateTo("budget", btnNavBudget);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            return;
        }
        navigateTo("budget", btnNavBudget);
    }

    @FXML
    private void handleNavIncome() {
        navigateTo("income", btnNavIncome);
    }

    @FXML
    private void handleNavRecurring() {
        navigateTo("recurring", btnNavRecurring);
    }

    @FXML
    private void handleNavReports() {
        navigateTo("reports", btnNavReports);
    }

    @FXML
    private void handleNavSettings() {
        navigateTo("settings", btnNavSettings);
    }

    @FXML
    private void handleNavAdmin() {
        if (SessionManager.isAdmin()) {
            navigateTo("admin", btnNavAdmin);
        }
    }

    @FXML
    private void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Logout");
        alert.setHeaderText("Are you sure you want to log out?");
        alert.setContentText("You will need to sign in again to access your account.");
        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                SessionManager.logout();
                Main.showLoginScreen();
            }
        });
    }

    public void navigateTo(String viewName, Button navBtn) {
        try {
            Node viewNode;
            // Always reload dynamic views or refresh them
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/" + viewName + ".fxml"));
            viewNode = loader.load();
            Object controller = loader.getController();

            contentArea.getChildren().clear();
            contentArea.getChildren().add(viewNode);

            // Update active nav button
            if (currentActiveBtn != null) {
                currentActiveBtn.getStyleClass().remove("nav-button-active");
            }
            if (navBtn != null) {
                navBtn.getStyleClass().add("nav-button-active");
                currentActiveBtn = navBtn;
            }
        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Navigation Error");
            alert.setHeaderText("Failed to load view: " + viewName);
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    public void openExpenseModal(com.expensebook.model.Expense expenseToEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/expense_modal.fxml"));
            Parent root = loader.load();

            ExpenseModalController controller = loader.getController();
            if (expenseToEdit != null) {
                controller.setExpenseToEdit(expenseToEdit);
            }

            Stage stage = new Stage();
            stage.setTitle(expenseToEdit == null ? "Add Expense — ExPense Book" : "Edit Expense — ExPense Book");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
