package com.expensebook.service;

import com.expensebook.dao.RecurringExpenseDAO;
import com.expensebook.model.Expense;
import com.expensebook.model.PaymentMode;
import com.expensebook.model.RecurringExpense;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class RecurringService {

    private final RecurringExpenseDAO recurringDAO = new RecurringExpenseDAO();

    public RecurringExpense addRecurring(int userId, String title, int categoryId, double amount,
                                         String frequency, PaymentMode mode, LocalDate nextDueDate) throws Exception {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than ₹0.");
        }
        if (categoryId <= 0) {
            throw new IllegalArgumentException("Please select a category.");
        }
        if (nextDueDate == null) {
            throw new IllegalArgumentException("Next due date is required.");
        }

        RecurringExpense re = new RecurringExpense();
        re.setUserId(userId);
        re.setTitle(title.trim());
        re.setCategoryId(categoryId);
        re.setAmount(amount);
        re.setFrequency(frequency != null ? frequency : "MONTHLY");
        re.setPaymentMode(mode != null ? mode : PaymentMode.UPI);
        re.setNextDueDate(nextDueDate);
        re.setActive(true);

        return recurringDAO.addRecurring(re);
    }

    public boolean updateRecurring(RecurringExpense re) throws Exception {
        if (re.getAmount() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than ₹0.");
        }
        return recurringDAO.updateRecurring(re);
    }

    public boolean deleteRecurring(int id, int userId) throws SQLException {
        return recurringDAO.deleteRecurring(id, userId);
    }

    public List<RecurringExpense> getRecurringList(int userId) throws SQLException {
        return recurringDAO.getRecurringForUser(userId);
    }

    public Expense processDueRecurringExpense(RecurringExpense recurring, ExpenseService expenseService) throws Exception {
        // Record as an expense
        Expense expense = expenseService.createExpense(
                recurring.getUserId(),
                recurring.getCategoryId(),
                recurring.getAmount(),
                recurring.getPaymentMode(),
                recurring.getNextDueDate(),
                recurring.getTitle() + " (Recurring)"
        );

        // Advance next due date
        LocalDate nextDate;
        String freq = recurring.getFrequency().toUpperCase();
        if ("DAILY".equals(freq)) {
            nextDate = recurring.getNextDueDate().plusDays(1);
        } else if ("WEEKLY".equals(freq)) {
            nextDate = recurring.getNextDueDate().plusWeeks(1);
        } else if ("YEARLY".equals(freq)) {
            nextDate = recurring.getNextDueDate().plusYears(1);
        } else {
            // Default MONTHLY
            nextDate = recurring.getNextDueDate().plusMonths(1);
        }

        recurring.setNextDueDate(nextDate);
        recurringDAO.updateNextDueDate(recurring.getId(), nextDate);

        return expense;
    }
}
