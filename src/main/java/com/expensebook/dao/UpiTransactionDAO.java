package com.expensebook.dao;

import com.expensebook.model.UpiTransaction;
import com.expensebook.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for dedicated upi_transactions table.
 * Strictly separated from ExpenseDAO.
 */
public class UpiTransactionDAO {

    public UpiTransaction save(UpiTransaction txn) throws SQLException {
        String sql = "INSERT INTO upi_transactions (user_id, amount, merchant, category_name, source_app, " +
                "upi_ref, is_debit, transaction_date, raw_message, imported_to_expensebook, imported_expense_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, txn.getUserId());
            ps.setDouble(2, txn.getAmount());
            ps.setString(3, txn.getMerchant());
            ps.setString(4, txn.getCategoryName());
            ps.setString(5, txn.getSourceApp());
            ps.setString(6, txn.getUpiRef());
            ps.setInt(7, txn.isDebit() ? 1 : 0);
            ps.setString(8, txn.getTransactionDate().toString());
            ps.setString(9, txn.getRawMessage() != null ? txn.getRawMessage() : "");
            ps.setInt(10, txn.isImportedToExpenseBook() ? 1 : 0);
            if (txn.getImportedExpenseId() != null) {
                ps.setInt(11, txn.getImportedExpenseId());
            } else {
                ps.setNull(11, Types.INTEGER);
            }

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    txn.setId(rs.getInt(1));
                }
            }
        }
        return txn;
    }

    public List<UpiTransaction> findByUserId(int userId, int limit) throws SQLException {
        List<UpiTransaction> list = new ArrayList<>();
        String sql = "SELECT * FROM upi_transactions WHERE user_id = ? ORDER BY transaction_date DESC, id DESC LIMIT ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit > 0 ? limit : 100);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public UpiTransaction findById(int id) throws SQLException {
        String sql = "SELECT * FROM upi_transactions WHERE id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public boolean isDuplicate(int userId, String upiRef) throws SQLException {
        if (upiRef == null || upiRef.trim().isEmpty()) return false;
        String sql = "SELECT id FROM upi_transactions WHERE user_id = ? AND upi_ref = ? LIMIT 1";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, upiRef.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean markImported(int upiId, int expenseId) throws SQLException {
        String sql = "UPDATE upi_transactions SET imported_to_expensebook = 1, imported_expense_id = ? WHERE id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, expenseId);
            ps.setInt(2, upiId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int upiId, int userId) throws SQLException {
        String sql = "DELETE FROM upi_transactions WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, upiId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public Map<String, Object> getUpiDashboardStats(int userId, LocalDate start, LocalDate end) throws SQLException {
        Map<String, Object> stats = new HashMap<>();
        Connection conn = DBConnection.getConnection();

        // 1. Total UPI Spent in period
        String totalSql = "SELECT COALESCE(SUM(amount), 0), COUNT(*) FROM upi_transactions " +
                "WHERE user_id = ? AND is_debit = 1 AND transaction_date BETWEEN ? AND ?";
        try (PreparedStatement ps = conn.prepareStatement(totalSql)) {
            ps.setInt(1, userId);
            ps.setString(2, start.toString());
            ps.setString(3, end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalUpiSpent", rs.getDouble(1));
                    stats.put("totalUpiCount", rs.getInt(2));
                }
            }
        }

        // 2. Spent Today via UPI
        String todaySql = "SELECT COALESCE(SUM(amount), 0) FROM upi_transactions " +
                "WHERE user_id = ? AND is_debit = 1 AND transaction_date = ?";
        try (PreparedStatement ps = conn.prepareStatement(todaySql)) {
            ps.setInt(1, userId);
            ps.setString(2, LocalDate.now().toString());
            try (ResultSet rs = ps.executeQuery()) {
                stats.put("todayUpiSpent", rs.next() ? rs.getDouble(1) : 0.0);
            }
        }

        // 3. App Breakdown
        Map<String, Double> appTotals = new HashMap<>();
        String appSql = "SELECT source_app, SUM(amount) FROM upi_transactions " +
                "WHERE user_id = ? AND is_debit = 1 AND transaction_date BETWEEN ? AND ? GROUP BY source_app";
        try (PreparedStatement ps = conn.prepareStatement(appSql)) {
            ps.setInt(1, userId);
            ps.setString(2, start.toString());
            ps.setString(3, end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    appTotals.put(rs.getString(1), rs.getDouble(2));
                }
            }
        }
        stats.put("appBreakdown", appTotals);

        // 4. Imported Count
        String importedSql = "SELECT COUNT(*) FROM upi_transactions WHERE user_id = ? AND imported_to_expensebook = 1";
        try (PreparedStatement ps = conn.prepareStatement(importedSql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                stats.put("importedCount", rs.next() ? rs.getInt(1) : 0);
            }
        }

        return stats;
    }

    private UpiTransaction mapRow(ResultSet rs) throws SQLException {
        UpiTransaction t = new UpiTransaction();
        t.setId(rs.getInt("id"));
        t.setUserId(rs.getInt("user_id"));
        t.setAmount(rs.getDouble("amount"));
        t.setMerchant(rs.getString("merchant"));
        t.setCategoryName(rs.getString("category_name"));
        t.setSourceApp(rs.getString("source_app"));
        t.setUpiRef(rs.getString("upi_ref"));
        t.setDebit(rs.getInt("is_debit") == 1);
        try {
            t.setTransactionDate(LocalDate.parse(rs.getString("transaction_date")));
        } catch (Exception e) {
            t.setTransactionDate(LocalDate.now());
        }
        t.setRawMessage(rs.getString("raw_message"));
        t.setImportedToExpenseBook(rs.getInt("imported_to_expensebook") == 1);
        int impId = rs.getInt("imported_expense_id");
        if (!rs.wasNull()) {
            t.setImportedExpenseId(impId);
        }
        return t;
    }
}
