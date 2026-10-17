package com.expensebook.dao;

import com.expensebook.model.FinancialMode;
import com.expensebook.model.User;
import com.expensebook.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserDAO {

    public User register(User user) throws SQLException {
        String sql = "INSERT INTO users (full_name, email, password_hash, role, financial_mode, currency, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail().toLowerCase().trim());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole() != null ? user.getRole() : "USER");
            ps.setString(5, user.getFinancialMode() != null ? user.getFinancialMode().name() : "TRACK_ONLY");
            ps.setString(6, user.getCurrency() != null ? user.getCurrency() : "₹");
            ps.setInt(7, user.isActive() ? 1 : 0);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    user.setId(rs.getInt(1));
                }
            }
        }
        return user;
    }

    public User findByEmail(String email) throws SQLException {
        if (email == null) return null;
        String sql = "SELECT * FROM users WHERE LOWER(TRIM(email)) = LOWER(TRIM(?))";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }
        return null;
    }

    public User findById(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }
        return null;
    }

    public boolean updateProfile(User user) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, currency = ? WHERE id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getCurrency());
            ps.setInt(3, user.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateFinancialMode(int userId, FinancialMode mode) throws SQLException {
        String sql = "UPDATE users SET financial_mode = ? WHERE id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mode.name());
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updatePassword(int userId, String passwordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<User> getAllUsers() throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        Connection conn = DBConnection.getConnection();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapUser(rs));
            }
        }
        return list;
    }

    public List<Map<String, Object>> getAllUsersWithStats() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT u.id, u.full_name, u.email, u.role, u.financial_mode, u.currency, u.is_active, u.created_at, u.current_session_id, " +
                "COALESCE(e.exp_count, 0) AS expense_count, " +
                "COALESCE(e.total_spent, 0.0) AS total_spent " +
                "FROM users u " +
                "LEFT JOIN (" +
                "    SELECT user_id, COUNT(*) AS exp_count, SUM(amount) AS total_spent " +
                "    FROM expenses " +
                "    GROUP BY user_id" +
                ") e ON u.id = e.user_id " +
                "ORDER BY u.created_at DESC";

        Connection conn = DBConnection.getConnection();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rs.getInt("id"));
                map.put("fullName", rs.getString("full_name"));
                map.put("email", rs.getString("email"));
                map.put("role", rs.getString("role"));
                map.put("financialMode", rs.getString("financial_mode"));
                map.put("currency", rs.getString("currency"));
                boolean active = rs.getInt("is_active") == 1;
                map.put("active", active);
                map.put("isActive", active); // Compatible with both conventions
                java.sql.Timestamp ts = rs.getTimestamp("created_at");
                map.put("createdAt", ts != null ? ts.toString() : "");
                String sid = rs.getString("current_session_id");
                map.put("hasActiveSession", sid != null && !sid.trim().isEmpty());
                map.put("expenseCount", rs.getInt("expense_count"));
                map.put("totalSpent", rs.getDouble("total_spent"));
                map.put("totalExpense", rs.getDouble("total_spent"));
                map.put("name", rs.getString("full_name"));
                list.add(map);
            }
        }
        return list;
    }

    public boolean adminUpdateUser(int userId, String fullName, String email, String role, FinancialMode mode, boolean active) throws SQLException {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be empty.");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }

        Connection conn = DBConnection.getConnection();
        // Check duplicate email
        String checkSql = "SELECT id FROM users WHERE LOWER(TRIM(email)) = LOWER(TRIM(?)) AND id != ?";
        try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setString(1, email.trim());
            checkPs.setInt(2, userId);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException("An account with this email address already exists.");
                }
            }
        }

        String sql = "UPDATE users SET full_name = ?, email = ?, role = ?, financial_mode = ?, is_active = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName.trim());
            ps.setString(2, email.trim().toLowerCase());
            ps.setString(3, role != null ? role.trim().toUpperCase() : "USER");
            ps.setString(4, mode != null ? mode.name() : "TRACK_ONLY");
            ps.setInt(5, active ? 1 : 0);
            ps.setInt(6, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean toggleActive(int userId, boolean active) throws SQLException {
        String sql = "UPDATE users SET is_active = ? WHERE id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteUser(int userId) throws SQLException {
        Connection conn = DBConnection.getConnection();
        boolean origAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            // Delete dependent records first to guarantee relational integrity across SQLite & MySQL
            try (PreparedStatement ps1 = conn.prepareStatement("DELETE FROM category_budgets WHERE budget_id IN (SELECT id FROM budgets WHERE user_id = ?)")) {
                ps1.setInt(1, userId);
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = conn.prepareStatement("DELETE FROM budgets WHERE user_id = ?")) {
                ps2.setInt(1, userId);
                ps2.executeUpdate();
            }
            try (PreparedStatement ps3 = conn.prepareStatement("DELETE FROM expenses WHERE user_id = ?")) {
                ps3.setInt(1, userId);
                ps3.executeUpdate();
            }
            try (PreparedStatement ps4 = conn.prepareStatement("DELETE FROM income WHERE user_id = ?")) {
                ps4.setInt(1, userId);
                ps4.executeUpdate();
            }
            try (PreparedStatement ps5 = conn.prepareStatement("DELETE FROM recurring_expenses WHERE user_id = ?")) {
                ps5.setInt(1, userId);
                ps5.executeUpdate();
            }
            try (PreparedStatement ps6 = conn.prepareStatement("DELETE FROM upi_transactions WHERE user_id = ?")) {
                ps6.setInt(1, userId);
                ps6.executeUpdate();
            }
            try (PreparedStatement ps7 = conn.prepareStatement("DELETE FROM categories WHERE user_id = ?")) {
                ps7.setInt(1, userId);
                ps7.executeUpdate();
            }
            try (PreparedStatement ps8 = conn.prepareStatement("DELETE FROM password_resets WHERE email IN (SELECT email FROM users WHERE id = ?)")) {
                ps8.setInt(1, userId);
                ps8.executeUpdate();
            }
            boolean deleted;
            try (PreparedStatement ps9 = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                ps9.setInt(1, userId);
                deleted = ps9.executeUpdate() > 0;
            }
            conn.commit();
            return deleted;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(origAutoCommit);
        }
    }

    public int getTotalUsersCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users";
        Connection conn = DBConnection.getConnection();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public int getActiveUsersCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE is_active = 1";
        Connection conn = DBConnection.getConnection();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public boolean updateSessionId(int userId, String sessionId) throws SQLException {
        String sql = "UPDATE users SET current_session_id = ? WHERE id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean clearSessionId(int userId) throws SQLException {
        return updateSessionId(userId, null);
    }

    public String getCurrentSessionId(int userId) throws SQLException {
        String sql = "SELECT current_session_id FROM users WHERE id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("current_session_id");
                }
            }
        }
        return null;
    }

    public boolean createPasswordReset(String email, String tokenHash, long expiryMs) throws SQLException {
        String sql = "INSERT INTO password_resets (email, token_hash, expires_at, used) VALUES (?, ?, ?, ?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.toLowerCase().trim());
            ps.setString(2, tokenHash);
            ps.setTimestamp(3, new java.sql.Timestamp(System.currentTimeMillis() + expiryMs));
            ps.setInt(4, 0);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean validateAndConsumePasswordReset(String email, String tokenHash) throws SQLException {
        String checkSql = "SELECT id FROM password_resets WHERE email = ? AND token_hash = ? AND used = 0 AND expires_at > ? ORDER BY id DESC LIMIT 1";
        Connection conn = DBConnection.getConnection();
        int resetId = -1;
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, email.toLowerCase().trim());
            ps.setString(2, tokenHash);
            ps.setTimestamp(3, new java.sql.Timestamp(System.currentTimeMillis()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    resetId = rs.getInt("id");
                }
            }
        }
        if (resetId > 0) {
            String updateSql = "UPDATE password_resets SET used = 1 WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, resetId);
                ps.executeUpdate();
            }
            return true;
        }
        return false;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getString("role"));
        u.setFinancialMode(FinancialMode.fromString(rs.getString("financial_mode")));
        u.setCurrency(rs.getString("currency"));
        u.setActive(rs.getInt("is_active") == 1);
        u.setCreatedAt(rs.getTimestamp("created_at"));
        try {
            u.setCurrentSessionId(rs.getString("current_session_id"));
        } catch (SQLException ignored) {
            // Column may not be projected in custom queries or prior to migration
        }
        return u;
    }
}
