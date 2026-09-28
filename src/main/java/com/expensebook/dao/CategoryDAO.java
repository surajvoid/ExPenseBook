package com.expensebook.dao;

import com.expensebook.model.Category;
import com.expensebook.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {

    public List<Category> getAllCategoriesForUser(int userId) throws SQLException {
        List<Category> list = new ArrayList<>();
        String sql = "SELECT * FROM categories WHERE user_id IS NULL OR user_id = ? ORDER BY is_default DESC, name ASC";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapCategory(rs));
                }
            }
        }
        return list;
    }

    public Category getCategoryById(int id) throws SQLException {
        String sql = "SELECT * FROM categories WHERE id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapCategory(rs);
                }
            }
        }
        return null;
    }

    public Category addCustomCategory(Category category) throws SQLException {
        String sql = "INSERT INTO categories (user_id, name, icon_name, color, is_default) VALUES (?, ?, ?, ?, ?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (category.getUserId() != null && category.getUserId() > 0) {
                ps.setInt(1, category.getUserId());
            } else {
                ps.setNull(1, java.sql.Types.INTEGER);
            }
            ps.setString(2, category.getName());
            ps.setString(3, category.getIconName());
            ps.setString(4, category.getColor());
            ps.setInt(5, category.isDefault() ? 1 : 0);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    category.setId(rs.getInt(1));
                }
            }
        }
        return category;
    }

    public boolean updateCategory(Category category) throws SQLException {
        String sql = "UPDATE categories SET name = ?, icon_name = ?, color = ? WHERE id = ? AND (user_id = ? OR user_id IS NULL)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getIconName());
            ps.setString(3, category.getColor());
            ps.setInt(4, category.getId());
            ps.setInt(5, category.getUserId() != null ? category.getUserId() : 0);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteCategory(int id, int userId) throws SQLException {
        // Can only delete custom categories created by this user
        String sql = "DELETE FROM categories WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Category> getSystemCategories() throws SQLException {
        List<Category> list = new ArrayList<>();
        String sql = "SELECT * FROM categories WHERE user_id IS NULL ORDER BY name ASC";
        Connection conn = DBConnection.getConnection();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapCategory(rs));
            }
        }
        return list;
    }

    private Category mapCategory(ResultSet rs) throws SQLException {
        Category c = new Category();
        c.setId(rs.getInt("id"));
        int uid = rs.getInt("user_id");
        c.setUserId(rs.wasNull() ? null : uid);
        c.setName(rs.getString("name"));
        c.setIconName(rs.getString("icon_name"));
        c.setColor(rs.getString("color"));
        c.setDefault(rs.getInt("is_default") == 1);
        c.setCreatedAt(rs.getTimestamp("created_at"));
        return c;
    }
}
