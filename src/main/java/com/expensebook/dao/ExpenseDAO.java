package com.expensebook.dao;

import com.expensebook.model.Category;
import com.expensebook.model.Expense;
import com.expensebook.model.PaymentMode;
import com.expensebook.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExpenseDAO {

    public Expense addExpense(Expense expense) throws SQLException {
        String sql = "INSERT INTO expenses (user_id, category_id, amount, payment_mode, expense_date, description) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, expense.getUserId());
            ps.setInt(2, expense.getCategoryId());
            ps.setDouble(3, expense.getAmount());
            ps.setString(4, expense.getPaymentMode() != null ? expense.getPaymentMode().name() : "UPI");
            ps.setDate(5, Date.valueOf(expense.getExpenseDate()));
            ps.setString(6, expense.getDescription());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    expense.setId(rs.getInt(1));
                }
            }
        }
        return expense;
    }

    public boolean updateExpense(Expense expense) throws SQLException {
        String sql = "UPDATE expenses SET category_id = ?, amount = ?, payment_mode = ?, expense_date = ?, description = ? " +
                "WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, expense.getCategoryId());
            ps.setDouble(2, expense.getAmount());
            ps.setString(3, expense.getPaymentMode() != null ? expense.getPaymentMode().name() : "UPI");
            ps.setDate(4, Date.valueOf(expense.getExpenseDate()));
            ps.setString(5, expense.getDescription());
            ps.setInt(6, expense.getId());
            ps.setInt(7, expense.getUserId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteExpense(int expenseId, int userId) throws SQLException {
        String sql = "DELETE FROM expenses WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, expenseId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public Expense getExpenseById(int id) throws SQLException {
        String sql = "SELECT e.*, c.name AS cat_name, c.icon_name AS cat_icon, c.color AS cat_color " +
                "FROM expenses e LEFT JOIN categories c ON e.category_id = c.id WHERE e.id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapExpense(rs);
                }
            }
        }
        return null;
    }

    public List<Expense> getExpenses(int userId, LocalDate startDate, LocalDate endDate,
                                     Integer categoryId, String paymentMode, String search, String sortBy) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT e.*, c.name AS cat_name, c.icon_name AS cat_icon, c.color AS cat_color " +
                        "FROM expenses e LEFT JOIN categories c ON e.category_id = c.id " +
                        "WHERE e.user_id = ? "
        );

        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (startDate != null) {
            sql.append("AND e.expense_date >= ? ");
            params.add(Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append("AND e.expense_date <= ? ");
            params.add(Date.valueOf(endDate));
        }
        if (categoryId != null && categoryId > 0) {
            sql.append("AND e.category_id = ? ");
            params.add(categoryId);
        }
        if (paymentMode != null && !paymentMode.equalsIgnoreCase("ALL") && !paymentMode.trim().isEmpty()) {
            sql.append("AND e.payment_mode = ? ");
            params.add(paymentMode);
        }
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(e.description) LIKE ? OR LOWER(c.name) LIKE ?) ");
            String term = "%" + search.toLowerCase().trim() + "%";
            params.add(term);
            params.add(term);
        }

        // Sorting
        if ("AMOUNT_DESC".equalsIgnoreCase(sortBy) || "Highest Amount".equalsIgnoreCase(sortBy)) {
            sql.append("ORDER BY e.amount DESC, e.expense_date DESC ");
        } else if ("AMOUNT_ASC".equalsIgnoreCase(sortBy) || "Lowest Amount".equalsIgnoreCase(sortBy)) {
            sql.append("ORDER BY e.amount ASC, e.expense_date DESC ");
        } else if ("DATE_ASC".equalsIgnoreCase(sortBy) || "Oldest".equalsIgnoreCase(sortBy)) {
            sql.append("ORDER BY e.expense_date ASC, e.id ASC ");
        } else {
            // Default newest
            sql.append("ORDER BY e.expense_date DESC, e.id DESC ");
        }

        List<Expense> list = new ArrayList<>();
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapExpense(rs));
                }
            }
        }
        return list;
    }

    public double getTotalSpent(int userId, LocalDate startDate, LocalDate endDate) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE user_id = ? AND expense_date >= ? AND expense_date <= ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDate(2, Date.valueOf(startDate));
            ps.setDate(3, Date.valueOf(endDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    public double getTodaySpent(int userId) throws SQLException {
        LocalDate today = LocalDate.now();
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE user_id = ? AND expense_date = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDate(2, Date.valueOf(today));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    public Map<Category, Double> getCategoryWiseSpending(int userId, LocalDate startDate, LocalDate endDate) throws SQLException {
        String sql = "SELECT c.id, c.name, c.icon_name, c.color, c.is_default, COALESCE(SUM(e.amount), 0) AS total " +
                "FROM expenses e LEFT JOIN categories c ON e.category_id = c.id " +
                "WHERE e.user_id = ? AND e.expense_date >= ? AND e.expense_date <= ? " +
                "GROUP BY c.id, c.name, c.icon_name, c.color, c.is_default " +
                "ORDER BY total DESC";

        Map<Category, Double> map = new LinkedHashMap<>();
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDate(2, Date.valueOf(startDate));
            ps.setDate(3, Date.valueOf(endDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Category c = new Category();
                    c.setId(rs.getInt("id"));
                    String name = rs.getString("name");
                    c.setName(name != null ? name : "Other");
                    String icon = rs.getString("icon_name");
                    c.setIconName(icon != null ? icon : "OTHERS");
                    String color = rs.getString("color");
                    c.setColor(color != null ? color : "#64748B");
                    c.setDefault(rs.getInt("is_default") == 1);
                    map.put(c, rs.getDouble("total"));
                }
            }
        }
        return map;
    }

    public Map<String, Double> getPaymentModeSpending(int userId, LocalDate startDate, LocalDate endDate) throws SQLException {
        String sql = "SELECT payment_mode, COALESCE(SUM(amount), 0) AS total " +
                "FROM expenses " +
                "WHERE user_id = ? AND expense_date >= ? AND expense_date <= ? " +
                "GROUP BY payment_mode " +
                "ORDER BY total DESC";

        Map<String, Double> map = new LinkedHashMap<>();
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDate(2, Date.valueOf(startDate));
            ps.setDate(3, Date.valueOf(endDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pm = rs.getString("payment_mode");
                    map.put(PaymentMode.fromString(pm).getDisplayName(), rs.getDouble("total"));
                }
            }
        }
        return map;
    }

    public Map<Integer, Double> getDailySpendingForMonth(int userId, int month, int year) throws SQLException {
        // Return Map<DayOfMonth, Amount>
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        String sql = "SELECT expense_date, SUM(amount) as total " +
                "FROM expenses WHERE user_id = ? AND expense_date >= ? AND expense_date <= ? " +
                "GROUP BY expense_date ORDER BY expense_date ASC";

        Map<Integer, Double> map = new LinkedHashMap<>();
        for (int i = 1; i <= end.getDayOfMonth(); i++) {
            map.put(i, 0.0);
        }

        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDate(2, Date.valueOf(start));
            ps.setDate(3, Date.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate d = rs.getDate("expense_date").toLocalDate();
                    map.put(d.getDayOfMonth(), rs.getDouble("total"));
                }
            }
        }
        return map;
    }

    public Map<Integer, Double> getMonthlySpendingForYear(int userId, int year) throws SQLException {
        // Return Map<MonthNumber (1-12), Amount>
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        Map<Integer, Double> map = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            map.put(m, 0.0);
        }

        String sql = "SELECT expense_date, amount FROM expenses " +
                "WHERE user_id = ? AND expense_date >= ? AND expense_date <= ?";

        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDate(2, Date.valueOf(start));
            ps.setDate(3, Date.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate d = rs.getDate("expense_date").toLocalDate();
                    int m = d.getMonthValue();
                    map.put(m, map.get(m) + rs.getDouble("amount"));
                }
            }
        }
        return map;
    }

    public List<Expense> getExpensesForDate(int userId, LocalDate date) throws SQLException {
        String sql = "SELECT e.*, c.name AS cat_name, c.icon_name AS cat_icon, c.color AS cat_color " +
                "FROM expenses e LEFT JOIN categories c ON e.category_id = c.id " +
                "WHERE e.user_id = ? AND e.expense_date = ? " +
                "ORDER BY e.id DESC";

        List<Expense> list = new ArrayList<>();
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapExpense(rs));
                }
            }
        }
        return list;
    }

    public int getTransactionCountForDate(int userId, LocalDate date) throws SQLException {
        String sql = "SELECT COUNT(*) FROM expenses WHERE user_id = ? AND expense_date = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public List<Expense> getRecentExpenses(int userId, int limit) throws SQLException {
        String sql = "SELECT e.*, c.name AS cat_name, c.icon_name AS cat_icon, c.color AS cat_color " +
                "FROM expenses e LEFT JOIN categories c ON e.category_id = c.id " +
                "WHERE e.user_id = ? ORDER BY e.expense_date DESC, e.id DESC LIMIT ?";

        List<Expense> list = new ArrayList<>();
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapExpense(rs));
                }
            }
        }
        return list;
    }

    private Expense mapExpense(ResultSet rs) throws SQLException {
        Expense e = new Expense();
        e.setId(rs.getInt("id"));
        e.setUserId(rs.getInt("user_id"));
        e.setCategoryId(rs.getInt("category_id"));
        e.setAmount(rs.getDouble("amount"));
        e.setPaymentMode(PaymentMode.fromString(rs.getString("payment_mode")));
        e.setExpenseDate(rs.getDate("expense_date").toLocalDate());
        e.setDescription(rs.getString("description"));
        e.setCreatedAt(rs.getTimestamp("created_at"));
        try {
            String catName = rs.getString("cat_name");
            String catIcon = rs.getString("cat_icon");
            String catColor = rs.getString("cat_color");
            e.setCategoryName(catName != null ? catName : "Other");
            e.setCategoryIcon(catIcon != null ? catIcon : "OTHERS");
            e.setCategoryColor(catColor != null ? catColor : "#64748B");
            Category cat = new Category(e.getCategoryId(), catName != null ? catName : "Other", catIcon != null ? catIcon : "OTHERS", catColor != null ? catColor : "#64748B", true);
            e.setCategory(cat);
        } catch (SQLException ignored) {
        }
        return e;
    }
}
