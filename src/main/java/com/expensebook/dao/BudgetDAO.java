package com.expensebook.dao;

import com.expensebook.model.Budget;
import com.expensebook.model.CategoryBudget;
import com.expensebook.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class BudgetDAO {

    public Budget getBudgetForMonth(int userId, int month, int year) throws SQLException {
        String sql = "SELECT * FROM budgets WHERE user_id = ? AND month = ? AND year = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, month);
            ps.setInt(3, year);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Budget b = new Budget();
                    b.setId(rs.getInt("id"));
                    b.setUserId(rs.getInt("user_id"));
                    b.setMonth(rs.getInt("month"));
                    b.setYear(rs.getInt("year"));
                    b.setTotalBudget(rs.getDouble("total_budget"));
                    b.setCreatedAt(rs.getTimestamp("created_at"));
                    b.setCategoryBudgets(getCategoryBudgets(b.getId()));
                    return b;
                }
            }
        }
        return null;
    }

    public Budget saveOrUpdateBudget(Budget budget) throws SQLException {
        Budget existing = getBudgetForMonth(budget.getUserId(), budget.getMonth(), budget.getYear());
        Connection conn = DBConnection.getConnection();
        if (existing != null) {
            String sql = "UPDATE budgets SET total_budget = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDouble(1, budget.getTotalBudget());
                ps.setInt(2, existing.getId());
                ps.executeUpdate();
                budget.setId(existing.getId());
            }
        } else {
            String sql = "INSERT INTO budgets (user_id, month, year, total_budget) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, budget.getUserId());
                ps.setInt(2, budget.getMonth());
                ps.setInt(3, budget.getYear());
                ps.setDouble(4, budget.getTotalBudget());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        budget.setId(rs.getInt(1));
                    }
                }
            }
        }

        if (budget.getCategoryBudgets() != null && !budget.getCategoryBudgets().isEmpty()) {
            saveCategoryBudgets(budget.getId(), budget.getCategoryBudgets());
        }

        return budget;
    }

    public void saveCategoryBudgets(int budgetId, List<CategoryBudget> categoryBudgets) throws SQLException {
        Connection conn = DBConnection.getConnection();
        // Clear existing for this budget
        String delSql = "DELETE FROM category_budgets WHERE budget_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(delSql)) {
            ps.setInt(1, budgetId);
            ps.executeUpdate();
        }

        String insSql = "INSERT INTO category_budgets (budget_id, category_id, amount) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insSql)) {
            for (CategoryBudget cb : categoryBudgets) {
                if (cb.getAmount() > 0) {
                    ps.setInt(1, budgetId);
                    ps.setInt(2, cb.getCategoryId());
                    ps.setDouble(3, cb.getAmount());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    public List<CategoryBudget> getCategoryBudgets(int budgetId) throws SQLException {
        List<CategoryBudget> list = new ArrayList<>();
        String sql = "SELECT cb.*, c.name as cat_name, c.icon_name as cat_icon, c.color as cat_color " +
                "FROM category_budgets cb LEFT JOIN categories c ON cb.category_id = c.id " +
                "WHERE cb.budget_id = ?";

        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, budgetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CategoryBudget cb = new CategoryBudget();
                    cb.setId(rs.getInt("id"));
                    cb.setBudgetId(rs.getInt("budget_id"));
                    cb.setCategoryId(rs.getInt("category_id"));
                    cb.setAmount(rs.getDouble("amount"));
                    cb.setCategoryName(rs.getString("cat_name") != null ? rs.getString("cat_name") : "Other");
                    cb.setCategoryIcon(rs.getString("cat_icon") != null ? rs.getString("cat_icon") : "OTHERS");
                    cb.setCategoryColor(rs.getString("cat_color") != null ? rs.getString("cat_color") : "#64748B");
                    list.add(cb);
                }
            }
        }
        return list;
    }
}
