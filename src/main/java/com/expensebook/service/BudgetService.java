package com.expensebook.service;

import com.expensebook.dao.BudgetDAO;
import com.expensebook.dao.ExpenseDAO;
import com.expensebook.model.Budget;
import com.expensebook.model.Category;
import com.expensebook.model.CategoryBudget;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DateUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BudgetService {

    private final BudgetDAO budgetDAO = new BudgetDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();

    public static class SmartBudgetSummary {
        public double monthlyBudget;
        public double totalBudget; // alias
        public double totalSpent;
        public double remainingBudget;
        public double usedPercentage;
        public double spentPercentage; // alias
        public int daysPassed;
        public int daysRemaining;
        public double averageDailySpent;
        public double recommendedDailySpending;
        public double recommendedDaily; // alias
        public double projectedMonthEndSpending;
        public double projectedMonthEnd; // alias
        public boolean isExceeded;
        public boolean isNearLimit; // >= 80%
        public String projectionInsight;
        public String recommendationInsight;
        public List<String> activeAlerts = new ArrayList<>();
        public List<String> alerts = new ArrayList<>(); // alias
        public List<CategoryBudget> categoryBudgetsWithSpending = new ArrayList<>();
    }

    public Budget getBudgetForMonth(int userId, int month, int year) throws SQLException {
        return budgetDAO.getBudgetForMonth(userId, month, year);
    }

    public Budget saveBudget(Budget budget) throws SQLException {
        if (budget.getTotalBudget() < 0) {
            throw new IllegalArgumentException("Budget amount cannot be negative.");
        }
        return budgetDAO.saveOrUpdateBudget(budget);
    }

    public SmartBudgetSummary getSmartBudgetSummary(int userId, int month, int year) throws SQLException {
        SmartBudgetSummary summary = new SmartBudgetSummary();

        Budget budget = budgetDAO.getBudgetForMonth(userId, month, year);
        double allocated = (budget != null) ? budget.getTotalBudget() : 0.0;
        summary.monthlyBudget = allocated;
        summary.totalBudget = allocated;

        LocalDate start = DateUtil.getStartOfMonth(month, year);
        LocalDate end = DateUtil.getEndOfMonth(month, year);
        double spent = expenseDAO.getTotalSpent(userId, start, end);
        summary.totalSpent = spent;

        summary.remainingBudget = allocated - spent;
        summary.usedPercentage = allocated > 0 ? Math.min(100.0, (spent / allocated) * 100.0) : 0.0;
        summary.spentPercentage = summary.usedPercentage;

        int daysPassed = DateUtil.getDaysPassedInMonth(month, year);
        if (daysPassed <= 0) daysPassed = 1;
        summary.daysPassed = daysPassed;

        int daysRemaining = DateUtil.getDaysRemainingInMonth(month, year);
        summary.daysRemaining = daysRemaining;

        summary.averageDailySpent = spent / daysPassed;

        if (daysRemaining > 0 && summary.remainingBudget > 0) {
            summary.recommendedDailySpending = summary.remainingBudget / daysRemaining;
            summary.recommendedDaily = summary.recommendedDailySpending;
            summary.recommendationInsight = "🟢 You can spend approximately " +
                    CurrencyUtil.format(summary.recommendedDailySpending) + "/day to stay within your budget.";
        } else if (summary.remainingBudget <= 0 && allocated > 0) {
            summary.recommendedDailySpending = 0;
            summary.recommendedDaily = 0;
            summary.recommendationInsight = "🔴 No remaining budget left for this month.";
        } else {
            summary.recommendedDailySpending = 0;
            summary.recommendedDaily = 0;
            summary.recommendationInsight = "Set a monthly budget to get daily spending recommendations.";
        }

        // Month-End Mathematical Projection:
        // Projected = Spent + (Average Daily Spent * Days Remaining)
        if (daysRemaining > 0) {
            summary.projectedMonthEndSpending = spent + (summary.averageDailySpent * daysRemaining);
        } else {
            summary.projectedMonthEndSpending = spent;
        }
        summary.projectedMonthEnd = summary.projectedMonthEndSpending;

        if (allocated > 0) {
            if (summary.projectedMonthEndSpending <= allocated) {
                summary.projectionInsight = "🟢 Likely to stay within budget (Estimated total: " +
                        CurrencyUtil.format(summary.projectedMonthEndSpending) + ").";
            } else {
                double excess = summary.projectedMonthEndSpending - allocated;
                summary.projectionInsight = "🔴 You may exceed your budget by approximately " +
                        CurrencyUtil.format(excess) + " (Estimated total: " + CurrencyUtil.format(summary.projectedMonthEndSpending) + ").";
            }
        } else {
            summary.projectionInsight = "Estimated month-end spending: " + CurrencyUtil.format(summary.projectedMonthEndSpending);
        }

        summary.isExceeded = allocated > 0 && spent > allocated;
        summary.isNearLimit = allocated > 0 && summary.usedPercentage >= 80.0 && !summary.isExceeded;

        // Alerts
        if (summary.isExceeded) {
            summary.activeAlerts.add("🔴 Monthly budget exceeded by " + CurrencyUtil.format(spent - allocated) + "!");
        } else if (summary.isNearLimit) {
            summary.activeAlerts.add("🟡 You've used " + CurrencyUtil.formatPercent(summary.usedPercentage) + " of your monthly budget.");
        }
        summary.alerts = summary.activeAlerts;

        // Category budgets evaluation
        if (budget != null && budget.getCategoryBudgets() != null) {
            Map<Category, Double> catSpending = expenseDAO.getCategoryWiseSpending(userId, start, end);
            for (CategoryBudget cb : budget.getCategoryBudgets()) {
                double catSpent = 0;
                for (Map.Entry<Category, Double> entry : catSpending.entrySet()) {
                    if (entry.getKey().getId() == cb.getCategoryId()) {
                        catSpent = entry.getValue();
                        break;
                    }
                }
                cb.setSpentAmount(catSpent);
                summary.categoryBudgetsWithSpending.add(cb);

                if (cb.isExceeded()) {
                    summary.activeAlerts.add("🔴 " + cb.getCategoryName() + " budget exceeded (" +
                            CurrencyUtil.format(catSpent) + " of " + CurrencyUtil.format(cb.getAmount()) + ")");
                }
            }
        }

        return summary;
    }
}
