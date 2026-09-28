package com.expensebook.dao;

import com.expensebook.model.Income;
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

public class IncomeDAO {

    public Income addIncome(Income income) throws SQLException {
        String sql = "INSERT INTO income (user_id, source, amount, income_date, description) VALUES (?, ?, ?, ?, ?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, income.getUserId());
            ps.setString(2, income.getSource());
            ps.setDouble(3, income.getAmount());
            ps.setDate(4, Date.valueOf(income.getIncomeDate()));
            ps.setString(5, income.getDescription());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    income.setId(rs.getInt(1));
                }
            }
        }
        return income;
    }

    public boolean updateIncome(Income income) throws SQLException {
        String sql = "UPDATE income SET source = ?, amount = ?, income_date = ?, description = ? WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, income.getSource());
            ps.setDouble(2, income.getAmount());
            ps.setDate(3, Date.valueOf(income.getIncomeDate()));
            ps.setString(4, income.getDescription());
            ps.setInt(5, income.getId());
            ps.setInt(6, income.getUserId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteIncome(int incomeId, int userId) throws SQLException {
        String sql = "DELETE FROM income WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, incomeId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Income> getIncomeForUser(int userId, LocalDate startDate, LocalDate endDate) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM income WHERE user_id = ? ");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (startDate != null) {
            sql.append("AND income_date >= ? ");
            params.add(Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append("AND income_date <= ? ");
            params.add(Date.valueOf(endDate));
        }
        sql.append("ORDER BY income_date DESC, id DESC");

        List<Income> list = new ArrayList<>();
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Income inc = new Income();
                    inc.setId(rs.getInt("id"));
                    inc.setUserId(rs.getInt("user_id"));
                    inc.setSource(rs.getString("source"));
                    inc.setAmount(rs.getDouble("amount"));
                    inc.setIncomeDate(rs.getDate("income_date").toLocalDate());
                    inc.setDescription(rs.getString("description"));
                    inc.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(inc);
                }
            }
        }
        return list;
    }

    public double getTotalIncome(int userId, LocalDate startDate, LocalDate endDate) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM income WHERE user_id = ? AND income_date >= ? AND income_date <= ?";
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
}
