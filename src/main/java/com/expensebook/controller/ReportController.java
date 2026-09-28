package com.expensebook.controller;

import com.expensebook.model.Budget;
import com.expensebook.model.Category;
import com.expensebook.model.Expense;
import com.expensebook.service.AnalysisService;
import com.expensebook.service.BudgetService;
import com.expensebook.service.ExpenseService;
import com.expensebook.service.ReportService;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DateUtil;
import com.expensebook.util.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class ReportController implements Initializable {

    @FXML private ComboBox<String> reportTypeCombo;
    @FXML private ComboBox<String> monthCombo;
    @FXML private ComboBox<Integer> yearCombo;

    // Statement Preview
    @FXML private Label previewPeriodTitle;
    @FXML private Label previewTotalExpenses;
    @FXML private Label previewTopCategory;
    @FXML private Label previewTopPayment;
    @FXML private Label previewDailyAverage;
    @FXML private Label previewPreviousMonth;
    @FXML private Label previewCurrentMonth;
    @FXML private Label previewChangePercent;

    @FXML private VBox budgetPreviewBox;
    @FXML private Label previewBudgetAllocated;
    @FXML private Label previewBudgetRemaining;

    @FXML private Button btnGeneratePDF;
    @FXML private Button btnExportCSV;

    private final ReportService reportService = new ReportService();
    private final ExpenseService expenseService = new ExpenseService();
    private final AnalysisService analysisService = new AnalysisService();
    private final BudgetService budgetService = new BudgetService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSelectors();
        loadPreview();
    }

    private void setupSelectors() {
        reportTypeCombo.getItems().addAll("Monthly Expense Report", "Yearly Overview Report");
        reportTypeCombo.getSelectionModel().selectFirst();

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

        reportTypeCombo.setOnAction(e -> loadPreview());
        monthCombo.setOnAction(e -> loadPreview());
        yearCombo.setOnAction(e -> loadPreview());
    }

    public void loadPreview() {
        int userId = SessionManager.getCurrentUserId();
        if (userId <= 0) return;

        int month = monthCombo.getSelectionModel().getSelectedIndex() + 1;
        Integer year = yearCombo.getValue();
        if (year == null) year = LocalDate.now().getYear();

        LocalDate start = DateUtil.getStartOfMonth(month, year);
        LocalDate end = DateUtil.getEndOfMonth(month, year);

        try {
            previewPeriodTitle.setText(DateUtil.formatMonthYear(month, year));

            double totalSpent = expenseService.getTotalSpentMonth(userId, month, year);
            double dailyAvg = analysisService.calculateDailyAverage(userId, month, year);
            AnalysisService.MonthComparison cmp = analysisService.compareWithPreviousMonth(userId, month, year);

            previewTotalExpenses.setText(CurrencyUtil.format(totalSpent));
            previewDailyAverage.setText(CurrencyUtil.format(dailyAvg) + " / day");

            previewPreviousMonth.setText(CurrencyUtil.format(cmp.previousMonthSpent));
            previewCurrentMonth.setText(CurrencyUtil.format(totalSpent));

            String changeSign = cmp.difference >= 0 ? "↑ +" : "↓ ";
            previewChangePercent.setText(changeSign + CurrencyUtil.formatPercent(Math.abs(cmp.percentageChange)));
            previewChangePercent.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (cmp.hasIncreased ? "#C94A4A" : "#397A5E") + ";");

            // Top Category
            Map<Category, Double> catMap = expenseService.getCategoryBreakdown(userId, start, end);
            if (!catMap.isEmpty()) {
                Map.Entry<Category, Double> first = catMap.entrySet().iterator().next();
                previewTopCategory.setText(first.getKey().getName() + " (" + CurrencyUtil.format(first.getValue()) + ")");
            } else {
                previewTopCategory.setText("-");
            }

            // Top Payment Mode
            Map<String, Double> payMap = expenseService.getPaymentBreakdown(userId, start, end);
            if (!payMap.isEmpty()) {
                Map.Entry<String, Double> first = payMap.entrySet().iterator().next();
                previewTopPayment.setText(first.getKey() + " (" + CurrencyUtil.format(first.getValue()) + ")");
            } else {
                previewTopPayment.setText("-");
            }

            // Budget Info
            boolean isBudget = SessionManager.isBudgetMode();
            budgetPreviewBox.setVisible(isBudget);
            budgetPreviewBox.setManaged(isBudget);
            if (isBudget) {
                Budget b = budgetService.getBudgetForMonth(userId, month, year);
                double bAmt = b != null ? b.getTotalBudget() : 0;
                previewBudgetAllocated.setText(CurrencyUtil.format(bAmt));
                previewBudgetRemaining.setText(CurrencyUtil.format(bAmt - totalSpent));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleGeneratePDF() {
        int userId = SessionManager.getCurrentUserId();
        int month = monthCombo.getSelectionModel().getSelectedIndex() + 1;
        Integer year = yearCombo.getValue();
        if (year == null) year = LocalDate.now().getYear();

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Financial Report PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf"));
        chooser.setInitialFileName("ExpenseBook_Report_" + Month.of(month).name() + "_" + year + ".pdf");

        Stage stage = (Stage) btnGeneratePDF.getScene().getWindow();
        File file = chooser.showSaveDialog(stage);

        if (file != null) {
            try {
                reportService.generateMonthlyReportPDF(file, userId, month, year);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Report Generated");
                alert.setHeaderText("PDF Successfully Created!");
                alert.setContentText("Report saved to:\n" + file.getAbsolutePath());
                alert.showAndWait();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export Failed");
                alert.setHeaderText("Could not create PDF report");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }

    @FXML
    private void handleExportCSV() {
        int userId = SessionManager.getCurrentUserId();
        int month = monthCombo.getSelectionModel().getSelectedIndex() + 1;
        Integer year = yearCombo.getValue();
        if (year == null) year = LocalDate.now().getYear();

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Transactions to CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Spreadsheet (*.csv)", "*.csv"));
        chooser.setInitialFileName("ExpenseBook_Transactions_" + Month.of(month).name() + "_" + year + ".csv");

        Stage stage = (Stage) btnExportCSV.getScene().getWindow();
        File file = chooser.showSaveDialog(stage);

        if (file != null) {
            try {
                LocalDate start = DateUtil.getStartOfMonth(month, year);
                LocalDate end = DateUtil.getEndOfMonth(month, year);
                List<Expense> expenses = expenseService.filterExpenses(userId, start, end, null, null, null, "NEWEST");
                reportService.exportExpensesToCSV(file, expenses);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText("CSV Export Complete");
                alert.setContentText("Exported " + expenses.size() + " transactions to:\n" + file.getAbsolutePath());
                alert.showAndWait();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export Failed");
                alert.setHeaderText("Could not export CSV");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }
}
