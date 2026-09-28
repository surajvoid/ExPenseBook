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
import java.util.List;

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
        String sql = "SELECT * FROM users WHERE email = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.toLowerCase().trim());
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
        String sql = "DELETE FROM users WHERE id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
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
        return u;
    }
}
