package com.expensebook.service;

import com.expensebook.dao.IncomeDAO;
import com.expensebook.model.Income;
import com.expensebook.util.DateUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class IncomeService {

    private final IncomeDAO incomeDAO = new IncomeDAO();

    public Income addIncome(int userId, String source, double amount, LocalDate date, String desc) throws Exception {
        if (amount <= 0) {
            throw new IllegalArgumentException("Income amount must be greater than ₹0.");
        }
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Income source is required.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date is required.");
        }

        Income inc = new Income();
        inc.setUserId(userId);
        inc.setSource(source.trim());
        inc.setAmount(amount);
        inc.setIncomeDate(date);
        inc.setDescription(desc != null ? desc.trim() : "");
        return incomeDAO.addIncome(inc);
    }

    public boolean updateIncome(Income income) throws Exception {
        if (income.getAmount() <= 0) {
            throw new IllegalArgumentException("Income amount must be greater than ₹0.");
        }
        return incomeDAO.updateIncome(income);
    }

    public boolean deleteIncome(int id, int userId) throws SQLException {
        return incomeDAO.deleteIncome(id, userId);
    }

    public List<Income> getIncomeForMonth(int userId, int month, int year) throws SQLException {
        LocalDate start = DateUtil.getStartOfMonth(month, year);
        LocalDate end = DateUtil.getEndOfMonth(month, year);
        return incomeDAO.getIncomeForUser(userId, start, end);
    }

    public double getTotalIncomeForMonth(int userId, int month, int year) throws SQLException {
        LocalDate start = DateUtil.getStartOfMonth(month, year);
        LocalDate end = DateUtil.getEndOfMonth(month, year);
        return incomeDAO.getTotalIncome(userId, start, end);
    }

    public double calculateNetBalance(double totalIncome, double totalExpenses) {
        return totalIncome - totalExpenses;
    }
}
