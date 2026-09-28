package com.expensebook.controller;

import com.expensebook.model.Budget;
import com.expensebook.model.Category;
import com.expensebook.model.CategoryBudget;
import com.expensebook.service.BudgetService;
import com.expensebook.service.CategoryService;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DateUtil;
import com.expensebook.util.SVGIconUtil;
import com.expensebook.util.SessionManager;
import com.expensebook.util.ValidationUtil;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class BudgetController implements Initializable {

    @FXML private ComboBox<String> monthCombo;
    @FXML private ComboBox<Integer> yearCombo;
    @FXML private TextField overallBudgetInput;
    @FXML private Button btnSaveOverallBudget;

    // Overview Cards
    @FXML private Label monthlyBudgetDisplay;
    @FXML private Label spentDisplay;
    @FXML private Label remainingDisplay;
    @FXML private Label budgetUsedPercentDisplay;
    @FXML private ProgressBar budgetProgressBar;

    // Smart Metrics
    @FXML private Label daysPassedLabel;
    @FXML private Label daysRemainingLabel;
    @FXML private Label avgDailySpentLabel;
    @FXML private Label recommendedDailyLabel;
    @FXML private Label recommendationCard;
    @FXML private Label projectedSpendingLabel;
    @FXML private Label projectionStatusLabel;

    // Alerts
    @FXML private VBox alertsBox;

    // Category Budgets
    @FXML private VBox categoryBudgetsContainer;
    @FXML private Button btnSaveCategoryBudgets;

    private final BudgetService budgetService = new BudgetService();
    private final CategoryService categoryService = new CategoryService();
    private final Map<Integer, TextField> categoryBudgetInputs = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupDatePickers();
        loadBudgetData();
    }

    private void setupDatePickers() {
        for (int m = 1; m <= 12; m++) {
            monthCombo.getItems().add(Month.of(m).getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }
        int curMonth = LocalDate.now().getMonthValue();
        monthCombo.getSelectionModel().select(curMonth - 1);

        int curYear = LocalDate.now().getYear();
        for (int y = curYear - 2; y <= curYear + 1; y++) {
            yearCombo.getItems().add(y);
        }
        yearCombo.getSelectionModel().select(Integer.valueOf(curYear));

        monthCombo.setOnAction(e -> loadBudgetData());
        yearCombo.setOnAction(e -> loadBudgetData());
    }

    public void loadBudgetData() {
        int userId = SessionManager.getCurrentUserId();
        if (userId <= 0) return;

        int month = monthCombo.getSelectionModel().getSelectedIndex() + 1;
        Integer year = yearCombo.getValue();
        if (year == null) year = LocalDate.now().getYear();

        try {
            BudgetService.SmartBudgetSummary summary = budgetService.getSmartBudgetSummary(userId, month, year);

            overallBudgetInput.setText(String.format("%.0f", summary.monthlyBudget));
            monthlyBudgetDisplay.setText(CurrencyUtil.format(summary.monthlyBudget));
            spentDisplay.setText(CurrencyUtil.format(summary.totalSpent));
            remainingDisplay.setText(CurrencyUtil.format(summary.remainingBudget));
            budgetUsedPercentDisplay.setText(CurrencyUtil.formatPercent(summary.usedPercentage));

            double progress = summary.monthlyBudget > 0 ? (summary.totalSpent / summary.monthlyBudget) : 0;
            budgetProgressBar.setProgress(Math.min(1.0, progress));

            budgetProgressBar.getStyleClass().removeAll("progress-bar-warning", "progress-bar-danger");
            if (summary.isExceeded) {
                budgetProgressBar.getStyleClass().add("progress-bar-danger");
            } else if (summary.isNearLimit) {
                budgetProgressBar.getStyleClass().add("progress-bar-warning");
            }

            daysPassedLabel.setText(String.valueOf(summary.daysPassed));
            daysRemainingLabel.setText(String.valueOf(summary.daysRemaining));
            avgDailySpentLabel.setText(CurrencyUtil.format(summary.averageDailySpent));
            recommendedDailyLabel.setText(CurrencyUtil.format(summary.recommendedDailySpending));
            recommendationCard.setText(summary.recommendationInsight);

            projectedSpendingLabel.setText(CurrencyUtil.format(summary.projectedMonthEndSpending));
            projectionStatusLabel.setText(summary.projectionInsight);

            // Active Alerts
            alertsBox.getChildren().clear();
            if (!summary.activeAlerts.isEmpty()) {
                alertsBox.setVisible(true);
                alertsBox.setManaged(true);
                for (String msg : summary.activeAlerts) {
                    HBox banner = new HBox(8);
                    banner.setAlignment(Pos.CENTER_LEFT);
                    banner.getStyleClass().add(msg.contains("🔴") ? "alert-banner-danger" : "alert-banner");
                    Label l = new Label(msg);
                    l.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                    banner.getChildren().add(l);
                    alertsBox.getChildren().add(banner);
                }
            } else {
                alertsBox.setVisible(false);
                alertsBox.setManaged(false);
            }

            // Category Budgets
            loadCategoryBudgetsList(userId, month, year, summary);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadCategoryBudgetsList(int userId, int month, int year, BudgetService.SmartBudgetSummary summary) throws Exception {
        categoryBudgetsContainer.getChildren().clear();
        categoryBudgetInputs.clear();

        List<Category> allCategories = categoryService.getCategoriesForUser(userId);
        Budget budget = budgetService.getBudgetForMonth(userId, month, year);

        Map<Integer, Double> budgetMap = new HashMap<>();
        Map<Integer, Double> spentMap = new HashMap<>();

        if (budget != null && budget.getCategoryBudgets() != null) {
            for (CategoryBudget cb : budget.getCategoryBudgets()) {
                budgetMap.put(cb.getCategoryId(), cb.getAmount());
            }
        }
        for (CategoryBudget cb : summary.categoryBudgetsWithSpending) {
            spentMap.put(cb.getCategoryId(), cb.getSpentAmount());
        }

        for (Category cat : allCategories) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 12, 8, 12));
            row.setStyle("-fx-background-color: #FAFAF7; -fx-background-radius: 12px; -fx-border-color: #EFE8DC; -fx-border-radius: 12px;");

            row.getChildren().add(SVGIconUtil.createCategoryBadge(cat.getIconName(), cat.getColor(), 28));

            VBox nameBox = new VBox(2);
            nameBox.setPrefWidth(140);
            Label nameLbl = new Label(cat.getName());
            nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #26332F;");
            double spent = spentMap.getOrDefault(cat.getId(), 0.0);
            Label spentLbl = new Label("Spent: " + CurrencyUtil.format(spent));
            spentLbl.getStyleClass().add("text-muted");
            nameBox.getChildren().addAll(nameLbl, spentLbl);

            double allocated = budgetMap.getOrDefault(cat.getId(), 0.0);
            double pct = allocated > 0 ? (spent / allocated) * 100.0 : 0.0;

            VBox progressBox = new VBox(4);
            HBox.setHgrow(progressBox, Priority.ALWAYS);
            HBox pctHeader = new HBox();
            Label pctLabel = new Label(allocated > 0 ? CurrencyUtil.formatPercent(pct) + " used" : "No budget set");
            pctLabel.getStyleClass().add("text-muted");
            pctHeader.getChildren().add(pctLabel);

            ProgressBar pb = new ProgressBar(allocated > 0 ? Math.min(1.0, spent / allocated) : 0);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.setPrefHeight(6);

            if (spent > allocated && allocated > 0) {
                pb.getStyleClass().add("progress-bar-danger");
            } else if (pct >= 80.0) {
                pb.getStyleClass().add("progress-bar-warning");
            }

            progressBox.getChildren().addAll(pctHeader, pb);

            // Budget Input Field
            TextField input = new TextField(allocated > 0 ? String.format("%.0f", allocated) : "");
            input.setPromptText("Limit (₹)");
            input.setPrefWidth(110);
            categoryBudgetInputs.put(cat.getId(), input);

            row.getChildren().addAll(nameBox, progressBox, input);
            categoryBudgetsContainer.getChildren().add(row);
        }
    }

    @FXML
    private void handleSaveOverallBudget() {
        String val = overallBudgetInput.getText();
        if (!ValidationUtil.isValidAmount(val)) {
            showAlert("Invalid Budget", "Please enter a valid positive budget amount.");
            return;
        }

        double amount = Double.parseDouble(val.trim());
        int userId = SessionManager.getCurrentUserId();
        int month = monthCombo.getSelectionModel().getSelectedIndex() + 1;
        int year = yearCombo.getValue();

        try {
            Budget budget = budgetService.getBudgetForMonth(userId, month, year);
            if (budget == null) {
                budget = new Budget();
                budget.setUserId(userId);
                budget.setMonth(month);
                budget.setYear(year);
            }
            budget.setTotalBudget(amount);
            budgetService.saveBudget(budget);

            loadBudgetData();
            showAlert("Budget Saved", "Monthly budget updated to " + CurrencyUtil.format(amount));

        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    private void handleSaveCategoryBudgets() {
        int userId = SessionManager.getCurrentUserId();
        int month = monthCombo.getSelectionModel().getSelectedIndex() + 1;
        int year = yearCombo.getValue();

        try {
            Budget budget = budgetService.getBudgetForMonth(userId, month, year);
            if (budget == null) {
                String val = overallBudgetInput.getText();
                double amount = ValidationUtil.isValidAmount(val) ? Double.parseDouble(val.trim()) : 0.0;
                budget = new Budget();
                budget.setUserId(userId);
                budget.setMonth(month);
                budget.setYear(year);
                budget.setTotalBudget(amount);
                budget = budgetService.saveBudget(budget);
            }

            List<CategoryBudget> list = new ArrayList<>();
            for (Map.Entry<Integer, TextField> entry : categoryBudgetInputs.entrySet()) {
                String txt = entry.getValue().getText();
                if (ValidationUtil.isValidAmount(txt)) {
                    CategoryBudget cb = new CategoryBudget();
                    cb.setBudgetId(budget.getId());
                    cb.setCategoryId(entry.getKey());
                    cb.setAmount(Double.parseDouble(txt.trim()));
                    list.add(cb);
                }
            }

            budget.setCategoryBudgets(list);
            budgetService.saveBudget(budget);

            loadBudgetData();
            showAlert("Category Budgets Saved", "Category-specific spending limits have been updated.");

        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
