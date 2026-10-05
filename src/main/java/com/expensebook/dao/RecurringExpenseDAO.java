package com.expensebook.dao;

import com.expensebook.model.PaymentMode;
import com.expensebook.model.RecurringExpense;
import com.expensebook.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RecurringExpenseDAO {

    public RecurringExpense addRecurring(RecurringExpense re) throws SQLException {
        String sql = "INSERT INTO recurring_expenses (user_id, title, category_id, amount, frequency, payment_mode, next_due_date, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, re.getUserId());
            ps.setString(2, re.getTitle());
            ps.setInt(3, re.getCategoryId());
            ps.setDouble(4, re.getAmount());
            ps.setString(5, re.getFrequency());
            ps.setString(6, re.getPaymentMode() != null ? re.getPaymentMode().name() : "UPI");
            ps.setDate(7, Date.valueOf(re.getNextDueDate()));
            ps.setInt(8, re.isActive() ? 1 : 0);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    re.setId(rs.getInt(1));
                }
            }
        }
        return re;
    }

    public boolean updateRecurring(RecurringExpense re) throws SQLException {
        String sql = "UPDATE recurring_expenses SET title = ?, category_id = ?, amount = ?, frequency = ?, payment_mode = ?, next_due_date = ?, is_active = ? " +
                "WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, re.getTitle());
            ps.setInt(2, re.getCategoryId());
            ps.setDouble(3, re.getAmount());
            ps.setString(4, re.getFrequency());
            ps.setString(5, re.getPaymentMode() != null ? re.getPaymentMode().name() : "UPI");
            ps.setDate(6, Date.valueOf(re.getNextDueDate()));
            ps.setInt(7, re.isActive() ? 1 : 0);
            ps.setInt(8, re.getId());
            ps.setInt(9, re.getUserId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteRecurring(int id, int userId) throws SQLException {
        String sql = "DELETE FROM recurring_expenses WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<RecurringExpense> getRecurringForUser(int userId) throws SQLException {
        String sql = "SELECT r.*, c.name as cat_name, c.icon_name as cat_icon, c.color as cat_color " +
                "FROM recurring_expenses r LEFT JOIN categories c ON r.category_id = c.id " +
                "WHERE r.user_id = ? ORDER BY r.next_due_date ASC";

        List<RecurringExpense> list = new ArrayList<>();
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RecurringExpense re = new RecurringExpense();
                    re.setId(rs.getInt("id"));
                    re.setUserId(rs.getInt("user_id"));
                    re.setTitle(rs.getString("title"));
                    re.setCategoryId(rs.getInt("category_id"));
                    re.setAmount(rs.getDouble("amount"));
                    re.setFrequency(rs.getString("frequency"));
                    re.setPaymentMode(PaymentMode.fromString(rs.getString("payment_mode")));
                    re.setNextDueDate(rs.getDate("next_due_date").toLocalDate());
                    re.setActive(rs.getInt("is_active") == 1);
                    re.setCreatedAt(rs.getTimestamp("created_at"));
                    re.setCategoryName(rs.getString("cat_name") != null ? rs.getString("cat_name") : "Other");
                    re.setCategoryIcon(rs.getString("cat_icon") != null ? rs.getString("cat_icon") : "OTHERS");
                    re.setCategoryColor(rs.getString("cat_color") != null ? rs.getString("cat_color") : "#64748B");
                    list.add(re);
                }
            }
        }
        return list;
    }

    public boolean updateNextDueDate(int id, LocalDate nextDate) throws SQLException {
        String sql = "UPDATE recurring_expenses SET next_due_date = ? WHERE id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(nextDate));
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }
}
