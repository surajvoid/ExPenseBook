package com.expensebook.service;

import com.expensebook.dao.CategoryDAO;
import com.expensebook.dao.ExpenseDAO;
import com.expensebook.model.Budget;
import com.expensebook.model.Category;
import com.expensebook.model.Expense;
import com.expensebook.model.User;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DateUtil;
import com.expensebook.util.PDFReportUtil;
import com.expensebook.util.SessionManager;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class ReportService {

    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final AnalysisService analysisService = new AnalysisService();
    private final BudgetService budgetService = new BudgetService();

    public void generateMonthlyReportPDF(File targetFile, int userId, int month, int year) throws Exception {
        User user = SessionManager.getCurrentUser();
        LocalDate start = DateUtil.getStartOfMonth(month, year);
        LocalDate end = DateUtil.getEndOfMonth(month, year);

        double totalSpent = expenseDAO.getTotalSpent(userId, start, end);
        double dailyAvg = analysisService.calculateDailyAverage(userId, month, year);

        AnalysisService.MonthComparison cmp = analysisService.compareWithPreviousMonth(userId, month, year);

        // Top Category
        Map<Category, Double> catMap = expenseDAO.getCategoryWiseSpending(userId, start, end);
        String topCatName = "-";
        double topCatAmt = 0;
        if (!catMap.isEmpty()) {
            Map.Entry<Category, Double> first = catMap.entrySet().iterator().next();
            topCatName = first.getKey().getName();
            topCatAmt = first.getValue();
        }

        // Top Payment Mode
        Map<String, Double> payMap = expenseDAO.getPaymentModeSpending(userId, start, end);
        String topPayName = "-";
        double topPayAmt = 0;
        if (!payMap.isEmpty()) {
            Map.Entry<String, Double> first = payMap.entrySet().iterator().next();
            topPayName = first.getKey();
            topPayAmt = first.getValue();
        }

        // Budget if applicable
        Budget budget = null;
        if (SessionManager.isBudgetMode()) {
            budget = budgetService.getBudgetForMonth(userId, month, year);
        }

        List<Expense> expenses = expenseDAO.getExpenses(userId, start, end, null, null, null, "NEWEST");

        String periodTitle = DateUtil.formatMonthYear(month, year);

        PDFReportUtil.generateReport(
                targetFile,
                user,
                periodTitle,
                totalSpent,
                topCatName,
                topCatAmt,
                topPayName,
                topPayAmt,
                dailyAvg,
                cmp.previousMonthSpent,
                cmp.percentageChange,
                budget,
                expenses
        );
    }

    public void exportExpensesToCSV(File file, List<Expense> expenses) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("Date,Category,Description,Payment Mode,Amount");
            for (Expense exp : expenses) {
                String desc = exp.getDescription().replace("\"", "\"\"");
                writer.println(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%.2f",
                        DateUtil.formatDate(exp.getExpenseDate()),
                        exp.getCategoryName(),
                        desc,
                        exp.getPaymentMode() != null ? exp.getPaymentMode().getDisplayName() : "UPI",
                        exp.getAmount()
                ));
            }
        }
    }
}
