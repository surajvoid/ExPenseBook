package com.expensebook.controller;

import com.expensebook.model.Budget;
import com.expensebook.model.Category;
import com.expensebook.model.Expense;
import com.expensebook.model.User;
import com.expensebook.service.AnalysisService;
import com.expensebook.service.BudgetService;
import com.expensebook.service.ExpenseService;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DateUtil;
import com.expensebook.util.SVGIconUtil;
import com.expensebook.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class DashboardController implements Initializable {

    // Mode A Cards (Always visible)
    @FXML private Label totalSpentLabel;
    @FXML private Label todaySpentLabel;
    @FXML private Label dailyAvgLabel;
    @FXML private Label daysLeftLabel;

    // Mode B Budget Section (Conditionally visible)
    @FXML private VBox budgetSection;
    @FXML private Label monthlyBudgetLabel;
    @FXML private Label budgetSpentLabel;
    @FXML private Label budgetRemainingLabel;
    @FXML private Label budgetPercentLabel;
    @FXML private ProgressBar budgetProgressBar;
    @FXML private Label recommendedDailyLabel;
    @FXML private Label projectionInsightLabel;
    @FXML private VBox alertsContainer;

    // Categories Breakdown Container
    @FXML private VBox categoriesContainer;

    // Daily Spending Trend Chart
    @FXML private LineChart<String, Number> spendingTrendChart;
    @FXML private CategoryAxis chartXAxis;
    @FXML private NumberAxis chartYAxis;

    // Recent Transactions Container
    @FXML private VBox recentTransactionsContainer;

    @FXML private Button btnAddExpenseQuick;

    private final ExpenseService expenseService = new ExpenseService();
    private final AnalysisService analysisService = new AnalysisService();
    private final BudgetService budgetService = new BudgetService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        refreshDashboard();

        // Listen for expense changes
        expenseService.addDataChangeListener(() -> Platform.runLater(this::refreshDashboard));
    }

    @FXML
    private void handleQuickAddExpense() {
        if (MainLayoutController.getInstance() != null) {
            MainLayoutController.getInstance().handleNavAddExpense();
        }
    }

    public void refreshDashboard() {
        User user = SessionManager.getCurrentUser();
        if (user == null) return;

        int userId = user.getId();
        int month = DateUtil.getCurrentMonth();
        int year = DateUtil.getCurrentYear();
        LocalDate now = LocalDate.now();

        try {
            // Mode A Summary Metrics
            double totalSpent = expenseService.getTotalSpentMonth(userId, month, year);
            double todaySpent = expenseService.getTodaySpent(userId);
            double dailyAvg = analysisService.calculateDailyAverage(userId, month, year);
            int daysLeft = DateUtil.getDaysRemainingInMonth(month, year);

            totalSpentLabel.setText(CurrencyUtil.format(totalSpent));
            todaySpentLabel.setText(CurrencyUtil.format(todaySpent));
            dailyAvgLabel.setText(CurrencyUtil.format(dailyAvg));
            daysLeftLabel.setText(String.valueOf(daysLeft));

            // Mode B Budget Dashboard Handling
            boolean isBudgetMode = SessionManager.isBudgetMode();
            budgetSection.setVisible(isBudgetMode);
            budgetSection.setManaged(isBudgetMode);

            if (isBudgetMode) {
                BudgetService.SmartBudgetSummary summary = budgetService.getSmartBudgetSummary(userId, month, year);
                monthlyBudgetLabel.setText(CurrencyUtil.format(summary.monthlyBudget));
                budgetSpentLabel.setText(CurrencyUtil.format(summary.totalSpent));
                budgetRemainingLabel.setText(CurrencyUtil.format(summary.remainingBudget));
                budgetPercentLabel.setText(CurrencyUtil.formatPercent(summary.usedPercentage));

                double progress = summary.monthlyBudget > 0 ? (summary.totalSpent / summary.monthlyBudget) : 0;
                budgetProgressBar.setProgress(Math.min(1.0, progress));

                budgetProgressBar.getStyleClass().removeAll("progress-bar-warning", "progress-bar-danger");
                if (summary.isExceeded) {
                    budgetProgressBar.getStyleClass().add("progress-bar-danger");
                } else if (summary.isNearLimit) {
                    budgetProgressBar.getStyleClass().add("progress-bar-warning");
                }

                recommendedDailyLabel.setText(summary.recommendationInsight);
                projectionInsightLabel.setText(summary.projectionInsight);

                // Alert Banners
                alertsContainer.getChildren().clear();
                if (!summary.activeAlerts.isEmpty()) {
                    for (String alertMsg : summary.activeAlerts) {
                        HBox banner = new HBox(8);
                        banner.setAlignment(Pos.CENTER_LEFT);
                        banner.getStyleClass().add(alertMsg.contains("🔴") ? "alert-banner-danger" : "alert-banner");
                        Label lbl = new Label(alertMsg);
                        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                        banner.getChildren().add(lbl);
                        alertsContainer.getChildren().add(banner);
                    }
                    alertsContainer.setVisible(true);
                    alertsContainer.setManaged(true);
                } else {
                    alertsContainer.setVisible(false);
                    alertsContainer.setManaged(false);
                }
            }

            // Category Breakdown Section ("Where did your money go?")
            loadCategoryBreakdown(userId, month, year, totalSpent);

            // Daily Spending Trend Chart
            loadTrendChart(userId, month, year);

            // Recent Transactions List
            loadRecentTransactions(userId);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadCategoryBreakdown(int userId, int month, int year, double totalSpent) throws Exception {
        categoriesContainer.getChildren().clear();
        LocalDate start = DateUtil.getStartOfMonth(month, year);
        LocalDate end = DateUtil.getEndOfMonth(month, year);

        Map<Category, Double> catMap = expenseService.getCategoryBreakdown(userId, start, end);
        if (catMap.isEmpty()) {
            Label empty = new Label("No expenses recorded for this month yet. Click '+ Add Expense' to get started!");
            empty.getStyleClass().add("text-muted");
            empty.setPadding(new Insets(10, 0, 10, 0));
            categoriesContainer.getChildren().add(empty);
            return;
        }

        for (Map.Entry<Category, Double> entry : catMap.entrySet()) {
            Category cat = entry.getKey();
            double amt = entry.getValue();
            double pct = totalSpent > 0 ? (amt / totalSpent) * 100.0 : 0.0;

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 0, 6, 0));

            // Vector SVG badge
            row.getChildren().add(SVGIconUtil.createCategoryBadge(cat.getIconName(), cat.getColor(), 32));

            // Name & Progress
            VBox center = new VBox(4);
            HBox.setHgrow(center, Priority.ALWAYS);

            HBox nameRow = new HBox(8);
            Label nameLbl = new Label(cat.getName());
            nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #26332F;");
            Label pctLbl = new Label(CurrencyUtil.formatPercent(pct));
            pctLbl.getStyleClass().add("text-muted");
            nameRow.getChildren().addAll(nameLbl, pctLbl);

            ProgressBar pb = new ProgressBar(Math.min(1.0, pct / 100.0));
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.setPrefHeight(6);

            center.getChildren().addAll(nameRow, pb);

            // Amount
            Label amtLbl = new Label(CurrencyUtil.format(amt));
            amtLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #26332F;");

            row.getChildren().addAll(center, amtLbl);
            categoriesContainer.getChildren().add(row);
        }
    }

    private void loadTrendChart(int userId, int month, int year) throws Exception {
        spendingTrendChart.getData().clear();
        Map<Integer, Double> daily = expenseService.getDailySpending(userId, month, year);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Daily Spending");

        for (Map.Entry<Integer, Double> entry : daily.entrySet()) {
            series.getData().add(new XYChart.Data<>(String.valueOf(entry.getKey()), entry.getValue()));
        }

        spendingTrendChart.getData().add(series);
    }

    private void loadRecentTransactions(int userId) throws Exception {
        recentTransactionsContainer.getChildren().clear();
        List<Expense> recent = expenseService.getRecentExpenses(userId, 5);

        if (recent.isEmpty()) {
            Label empty = new Label("No recent transactions.");
            empty.getStyleClass().add("text-muted");
            recentTransactionsContainer.getChildren().add(empty);
            return;
        }

        for (Expense exp : recent) {
            HBox item = new HBox(12);
            item.setAlignment(Pos.CENTER_LEFT);
            item.setPadding(new Insets(8, 12, 8, 12));
            item.setStyle("-fx-background-color: #FAFAF7; -fx-background-radius: 12px; -fx-border-color: #F0EDE4; -fx-border-radius: 12px;");

            item.getChildren().add(SVGIconUtil.createCategoryBadge(exp.getCategoryIcon(), exp.getCategoryColor(), 28));

            VBox info = new VBox(2);
            HBox.setHgrow(info, Priority.ALWAYS);
            Label descLbl = new Label(!exp.getDescription().isEmpty() ? exp.getDescription() : exp.getCategoryName());
            descLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #26332F;");
            Label subLbl = new Label(DateUtil.formatShortDate(exp.getExpenseDate()) + " • " +
                    (exp.getPaymentMode() != null ? exp.getPaymentMode().getDisplayName() : "UPI"));
            subLbl.getStyleClass().add("text-muted");
            info.getChildren().addAll(descLbl, subLbl);

            Label amountLbl = new Label(CurrencyUtil.format(exp.getAmount()));
            amountLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #26332F;");

            item.getChildren().addAll(info, amountLbl);
            recentTransactionsContainer.getChildren().add(item);
        }
    }
}
