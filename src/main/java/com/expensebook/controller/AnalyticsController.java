package com.expensebook.controller;

import com.expensebook.model.Category;
import com.expensebook.service.AnalysisService;
import com.expensebook.service.ExpenseService;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DateUtil;
import com.expensebook.util.SVGIconUtil;
import com.expensebook.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class AnalyticsController implements Initializable {

    @FXML private ComboBox<String> monthPickerCombo;
    @FXML private ComboBox<Integer> yearPickerCombo;

    // Monthly Overview & Category Analysis
    @FXML private Label monthlyTotalLabel;
    @FXML private Label monthlyDailyAvgLabel;
    @FXML private PieChart categoryPieChart;
    @FXML private VBox categoryAnalysisList;

    // Payment Analysis
    @FXML private BarChart<String, Number> paymentBarChart;
    @FXML private VBox paymentModeList;

    // Daily Spending Trend
    @FXML private LineChart<String, Number> dailyTrendChart;

    // Monthly Comparison
    @FXML private Label cmpCurrentMonthName;
    @FXML private Label cmpCurrentMonthAmount;
    @FXML private Label cmpPrevMonthName;
    @FXML private Label cmpPrevMonthAmount;
    @FXML private Label cmpDifferenceLabel;
    @FXML private Label cmpStatusBanner;

    // Yearly Analysis
    @FXML private BarChart<String, Number> yearlyBarChart;
    @FXML private Label yearlyTotalLabel;
    @FXML private Label yearlyMonthlyAvgLabel;
    @FXML private Label yearlyHighestMonthLabel;
    @FXML private Label yearlyLowestMonthLabel;

    private final ExpenseService expenseService = new ExpenseService();
    private final AnalysisService analysisService = new AnalysisService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupDatePickers();
        loadAnalytics();
    }

    private void setupDatePickers() {
        // Populate months
        for (int m = 1; m <= 12; m++) {
            monthPickerCombo.getItems().add(Month.of(m).getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }
        int currentMonth = LocalDate.now().getMonthValue();
        monthPickerCombo.getSelectionModel().select(currentMonth - 1);

        // Populate years
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 3; y <= currentYear + 1; y++) {
            yearPickerCombo.getItems().add(y);
        }
        yearPickerCombo.getSelectionModel().select(Integer.valueOf(currentYear));

        monthPickerCombo.setOnAction(e -> loadAnalytics());
        yearPickerCombo.setOnAction(e -> loadAnalytics());
    }

    public void loadAnalytics() {
        int userId = SessionManager.getCurrentUserId();
        if (userId <= 0) return;

        int month = monthPickerCombo.getSelectionModel().getSelectedIndex() + 1;
        Integer year = yearPickerCombo.getValue();
        if (year == null) year = LocalDate.now().getYear();

        LocalDate start = DateUtil.getStartOfMonth(month, year);
        LocalDate end = DateUtil.getEndOfMonth(month, year);

        try {
            // 1. Monthly Totals
            double totalSpent = expenseService.getTotalSpentMonth(userId, month, year);
            double dailyAvg = analysisService.calculateDailyAverage(userId, month, year);
            monthlyTotalLabel.setText(CurrencyUtil.format(totalSpent));
            monthlyDailyAvgLabel.setText(CurrencyUtil.format(dailyAvg) + " / day");

            // 2. Category Pie Chart & Breakdown
            loadCategoryAnalytics(userId, start, end, totalSpent);

            // 3. Payment Method Analysis
            loadPaymentAnalytics(userId, start, end, totalSpent);

            // 4. Daily Spending Trend
            loadDailyTrend(userId, month, year);

            // 5. Month Comparison
            loadMonthComparison(userId, month, year);

            // 6. Yearly Analysis
            loadYearlyAnalysis(userId, year);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadCategoryAnalytics(int userId, LocalDate start, LocalDate end, double totalSpent) throws Exception {
        categoryPieChart.getData().clear();
        categoryAnalysisList.getChildren().clear();

        Map<Category, Double> catMap = expenseService.getCategoryBreakdown(userId, start, end);
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();

        for (Map.Entry<Category, Double> entry : catMap.entrySet()) {
            Category cat = entry.getKey();
            double amt = entry.getValue();
            double pct = totalSpent > 0 ? (amt / totalSpent) * 100.0 : 0.0;

            pieData.add(new PieChart.Data(cat.getName() + " (" + CurrencyUtil.formatPercent(pct) + ")", amt));

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 0, 4, 0));

            row.getChildren().add(SVGIconUtil.createCategoryBadge(cat.getIconName(), cat.getColor(), 24));

            Label name = new Label(cat.getName());
            name.setStyle("-fx-font-weight: bold; -fx-text-fill: #26332F;");
            HBox.setHgrow(name, Priority.ALWAYS);

            Label pctLbl = new Label(CurrencyUtil.formatPercent(pct));
            pctLbl.getStyleClass().add("text-muted");

            Label amtLbl = new Label(CurrencyUtil.format(amt));
            amtLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #26332F;");

            row.getChildren().addAll(name, pctLbl, amtLbl);
            categoryAnalysisList.getChildren().add(row);
        }

        categoryPieChart.setData(pieData);
    }

    private void loadPaymentAnalytics(int userId, LocalDate start, LocalDate end, double totalSpent) throws Exception {
        paymentBarChart.getData().clear();
        paymentModeList.getChildren().clear();

        Map<String, Double> payMap = expenseService.getPaymentBreakdown(userId, start, end);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Payment Mode");

        for (Map.Entry<String, Double> entry : payMap.entrySet()) {
            String mode = entry.getKey();
            double amt = entry.getValue();
            double pct = totalSpent > 0 ? (amt / totalSpent) * 100.0 : 0.0;

            series.getData().add(new XYChart.Data<>(mode, amt));

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(3, 0, 3, 0));

            Label modeLbl = new Label(mode);
            modeLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #26332F;");
            HBox.setHgrow(modeLbl, Priority.ALWAYS);

            Label pctBadge = new Label(CurrencyUtil.formatPercent(pct));
            pctBadge.getStyleClass().addAll("badge", "badge-blue");

            Label amtLbl = new Label(CurrencyUtil.format(amt));
            amtLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #26332F;");

            row.getChildren().addAll(modeLbl, pctBadge, amtLbl);
            paymentModeList.getChildren().add(row);
        }

        paymentBarChart.getData().add(series);
    }

    private void loadDailyTrend(int userId, int month, int year) throws Exception {
        dailyTrendChart.getData().clear();
        Map<Integer, Double> daily = expenseService.getDailySpending(userId, month, year);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Daily Spending");

        for (Map.Entry<Integer, Double> entry : daily.entrySet()) {
            series.getData().add(new XYChart.Data<>(String.valueOf(entry.getKey()), entry.getValue()));
        }

        dailyTrendChart.getData().add(series);
    }

    private void loadMonthComparison(int userId, int month, int year) throws Exception {
        AnalysisService.MonthComparison cmp = analysisService.compareWithPreviousMonth(userId, month, year);

        YearMonth curYm = YearMonth.of(year, month);
        YearMonth prevYm = curYm.minusMonths(1);

        cmpCurrentMonthName.setText(curYm.format(DateUtil.MONTH_YEAR_FORMAT));
        cmpCurrentMonthAmount.setText(CurrencyUtil.format(cmp.currentMonthSpent));

        cmpPrevMonthName.setText(prevYm.format(DateUtil.MONTH_YEAR_FORMAT));
        cmpPrevMonthAmount.setText(CurrencyUtil.format(cmp.previousMonthSpent));

        cmpStatusBanner.setText(cmp.statusText);
        cmpStatusBanner.getStyleClass().removeAll("alert-banner", "alert-banner-danger", "badge-sage");

        if (cmp.hasIncreased) {
            cmpStatusBanner.getStyleClass().add("alert-banner-danger");
        } else {
            cmpStatusBanner.getStyleClass().add("alert-banner");
        }

        String diffText = (cmp.difference >= 0 ? "+ " : "- ") + CurrencyUtil.format(Math.abs(cmp.difference));
        cmpDifferenceLabel.setText(diffText + " (" + CurrencyUtil.formatPercent(Math.abs(cmp.percentageChange)) + ")");
    }

    private void loadYearlyAnalysis(int userId, int year) throws Exception {
        yearlyBarChart.getData().clear();
        AnalysisService.YearlyAnalysisResult res = analysisService.getYearlyAnalysis(userId, year);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Monthly Spending " + year);

        for (Map.Entry<Integer, Double> entry : res.monthlyBreakdown.entrySet()) {
            String monthName = Month.of(entry.getKey()).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            series.getData().add(new XYChart.Data<>(monthName, entry.getValue()));
        }

        yearlyBarChart.getData().add(series);

        yearlyTotalLabel.setText(CurrencyUtil.format(res.totalYearlySpent));
        yearlyMonthlyAvgLabel.setText(CurrencyUtil.format(res.averageMonthlySpent));
        yearlyHighestMonthLabel.setText(res.highestMonthName + " (" + CurrencyUtil.format(res.highestMonthAmount) + ")");
        yearlyLowestMonthLabel.setText(res.lowestMonthName + " (" + CurrencyUtil.format(res.lowestMonthAmount) + ")");
    }
}
