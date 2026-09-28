package com.expensebook.service;

import com.expensebook.dao.ExpenseDAO;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DateUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

public class AnalysisService {

    private final ExpenseDAO expenseDAO = new ExpenseDAO();

    public static class MonthComparison {
        public double currentMonthSpent;
        public double previousMonthSpent;
        public double difference; // positive = increased, negative = reduced
        public double percentageChange;
        public boolean hasIncreased;
        public String statusText;

        public String getSummaryText() {
            if (previousMonthSpent == 0 && currentMonthSpent == 0) {
                return "No spending recorded in comparison period.";
            }
            if (previousMonthSpent == 0) {
                return "First month tracking spending.";
            }
            if (difference > 0) {
                return "⚠️ Spending increased by " + CurrencyUtil.formatPercent(percentageChange) +
                        " (" + CurrencyUtil.format(difference) + " more)";
            } else if (difference < 0) {
                return "🟢 Spending reduced by " + CurrencyUtil.formatPercent(Math.abs(percentageChange)) +
                        " (" + CurrencyUtil.format(Math.abs(difference)) + " less)";
            } else {
                return "Spending is identical to previous month.";
            }
        }
    }

    public static class YearlyAnalysisResult {
        public int year;
        public double totalYearlySpent;
        public double averageMonthlySpent;
        public String highestMonthName;
        public double highestMonthAmount;
        public String lowestMonthName;
        public double lowestMonthAmount;
        public Map<Integer, Double> monthlyBreakdown;
    }

    public double calculateDailyAverage(int userId, int month, int year) throws SQLException {
        LocalDate start = DateUtil.getStartOfMonth(month, year);
        LocalDate end = DateUtil.getEndOfMonth(month, year);
        double total = expenseDAO.getTotalSpent(userId, start, end);
        int daysPassed = DateUtil.getDaysPassedInMonth(month, year);
        if (daysPassed <= 0) daysPassed = 1;
        return total / daysPassed;
    }

    public MonthComparison compareWithPreviousMonth(int userId, int month, int year) throws SQLException {
        YearMonth currentYm = YearMonth.of(year, month);
        YearMonth prevYm = currentYm.minusMonths(1);

        double currentSpent = expenseDAO.getTotalSpent(
                userId,
                prevYm.plusMonths(1).atDay(1),
                currentYm.atEndOfMonth()
        );

        double prevSpent = expenseDAO.getTotalSpent(
                userId,
                prevYm.atDay(1),
                prevYm.atEndOfMonth()
        );

        MonthComparison cmp = new MonthComparison();
        cmp.currentMonthSpent = currentSpent;
        cmp.previousMonthSpent = prevSpent;
        cmp.difference = currentSpent - prevSpent;

        if (prevSpent > 0) {
            cmp.percentageChange = ((currentSpent - prevSpent) / prevSpent) * 100.0;
        } else {
            cmp.percentageChange = currentSpent > 0 ? 100.0 : 0.0;
        }
        cmp.hasIncreased = cmp.difference > 0;
        cmp.statusText = cmp.getSummaryText();

        return cmp;
    }

    public YearlyAnalysisResult getYearlyAnalysis(int userId, int year) throws SQLException {
        Map<Integer, Double> monthly = expenseDAO.getMonthlySpendingForYear(userId, year);

        YearlyAnalysisResult res = new YearlyAnalysisResult();
        res.year = year;
        res.monthlyBreakdown = monthly;

        double sum = 0;
        double max = -1;
        int maxM = 1;
        double min = Double.MAX_VALUE;
        int minM = 1;
        int nonZeroCount = 0;

        for (Map.Entry<Integer, Double> entry : monthly.entrySet()) {
            double amt = entry.getValue();
            sum += amt;
            if (amt > max) {
                max = amt;
                maxM = entry.getKey();
            }
            if (amt < min) {
                min = amt;
                minM = entry.getKey();
            }
            if (amt > 0) {
                nonZeroCount++;
            }
        }

        res.totalYearlySpent = sum;
        int activeMonths = LocalDate.now().getYear() == year ? Math.max(1, LocalDate.now().getMonthValue()) : 12;
        res.averageMonthlySpent = sum / activeMonths;

        res.highestMonthName = Month.of(maxM).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        res.highestMonthAmount = Math.max(0, max);

        res.lowestMonthName = Month.of(minM).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        res.lowestMonthAmount = min == Double.MAX_VALUE ? 0 : min;

        return res;
    }
}
