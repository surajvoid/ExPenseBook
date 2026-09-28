package com.expensebook.service;

import com.expensebook.dao.CategoryDAO;
import com.expensebook.dao.ExpenseDAO;
import com.expensebook.model.Category;
import com.expensebook.model.Expense;
import com.expensebook.model.PaymentMode;
import com.expensebook.util.DateUtil;
import com.expensebook.util.ValidationUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExpenseService {

    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final List<Runnable> dataChangeListeners = new ArrayList<>();

    public void addDataChangeListener(Runnable listener) {
        if (!dataChangeListeners.contains(listener)) {
            dataChangeListeners.add(listener);
        }
    }

    public void removeDataChangeListener(Runnable listener) {
        dataChangeListeners.remove(listener);
    }

    public void notifyDataChanged() {
        for (Runnable r : new ArrayList<>(dataChangeListeners)) {
            try {
                r.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public Expense createExpense(int userId, int categoryId, double amount, PaymentMode paymentMode,
                                 LocalDate expenseDate, String description) throws Exception {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than ₹0.");
        }
        if (categoryId <= 0) {
            throw new IllegalArgumentException("Please select a valid category.");
        }
        if (paymentMode == null) {
            throw new IllegalArgumentException("Please select a payment method.");
        }
        if (expenseDate == null) {
            throw new IllegalArgumentException("Please select an expense date.");
        }

        // If description is empty or 'Other', default to the category name
        if (description == null || description.trim().isEmpty() || "Other".equalsIgnoreCase(description.trim())) {
            try {
                Category cat = categoryDAO.getCategoryById(categoryId);
                if (cat != null && cat.getName() != null && !cat.getName().trim().isEmpty()) {
                    description = cat.getName().trim();
                } else {
                    description = "Expense";
                }
            } catch (Exception ignored) {
                description = "Expense";
            }
        }

        Expense expense = new Expense();
        expense.setUserId(userId);
        expense.setCategoryId(categoryId);
        expense.setAmount(amount);
        expense.setPaymentMode(paymentMode);
        expense.setExpenseDate(expenseDate);
        expense.setDescription(description);

        Expense saved = expenseDAO.addExpense(expense);
        notifyDataChanged();
        return saved;
    }

    public boolean updateExpense(Expense expense) throws Exception {
        if (expense.getAmount() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than ₹0.");
        }
        if (expense.getCategoryId() <= 0) {
            throw new IllegalArgumentException("Please select a valid category.");
        }
        if (expense.getPaymentMode() == null) {
            throw new IllegalArgumentException("Please select a payment method.");
        }
        if (expense.getExpenseDate() == null) {
            throw new IllegalArgumentException("Please select an expense date.");
        }

        // If description is empty or 'Other', default to the category name
        if (expense.getDescription() == null || expense.getDescription().trim().isEmpty() || "Other".equalsIgnoreCase(expense.getDescription().trim())) {
            try {
                Category cat = categoryDAO.getCategoryById(expense.getCategoryId());
                if (cat != null && cat.getName() != null && !cat.getName().trim().isEmpty()) {
                    expense.setDescription(cat.getName().trim());
                }
            } catch (Exception ignored) {}
        }

        boolean updated = expenseDAO.updateExpense(expense);
        if (updated) {
            notifyDataChanged();
        }
        return updated;
    }

    public boolean deleteExpense(int expenseId, int userId) throws SQLException {
        boolean deleted = expenseDAO.deleteExpense(expenseId, userId);
        if (deleted) {
            notifyDataChanged();
        }
        return deleted;
    }

    public Expense getExpenseById(int id) throws SQLException {
        return expenseDAO.getExpenseById(id);
    }

    public List<Expense> filterExpenses(int userId, LocalDate startDate, LocalDate endDate,
                                       Integer categoryId, String paymentMode, String search, String sortBy) throws SQLException {
        return expenseDAO.getExpenses(userId, startDate, endDate, categoryId, paymentMode, search, sortBy);
    }

    public double getTotalSpentMonth(int userId, int month, int year) throws SQLException {
        LocalDate start = DateUtil.getStartOfMonth(month, year);
        LocalDate end = DateUtil.getEndOfMonth(month, year);
        return expenseDAO.getTotalSpent(userId, start, end);
    }

    public double getTodaySpent(int userId) throws SQLException {
        return expenseDAO.getTodaySpent(userId);
    }

    public Map<Category, Double> getCategoryBreakdown(int userId, LocalDate start, LocalDate end) throws SQLException {
        return expenseDAO.getCategoryWiseSpending(userId, start, end);
    }

    public Map<String, Double> getPaymentBreakdown(int userId, LocalDate start, LocalDate end) throws SQLException {
        return expenseDAO.getPaymentModeSpending(userId, start, end);
    }

    public Map<Integer, Double> getDailySpending(int userId, int month, int year) throws SQLException {
        return expenseDAO.getDailySpendingForMonth(userId, month, year);
    }

    public Map<Integer, Double> getMonthlySpending(int userId, int year) throws SQLException {
        return expenseDAO.getMonthlySpendingForYear(userId, year);
    }

    public List<Expense> getRecentExpenses(int userId, int limit) throws SQLException {
        return expenseDAO.getRecentExpenses(userId, limit);
    }

    public List<Expense> getExpensesForDate(int userId, LocalDate date) throws SQLException {
        return expenseDAO.getExpensesForDate(userId, date);
    }

    public int getTransactionCountForDate(int userId, LocalDate date) throws SQLException {
        return expenseDAO.getTransactionCountForDate(userId, date);
    }
}
