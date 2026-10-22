package com.expensebook.dao;

import com.expensebook.model.Category;
import com.expensebook.util.DBConnection;
import com.expensebook.util.MongoConnectionManager;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise CategoryDAO supporting dual-mode operation:
 * Primary: High-performance MongoDB Atlas when active.
 * Fallback / Dev: Standard JDBC connection for local development.
 */
public class CategoryDAO {

    public List<Category> getAllCategoriesForUser(int userId) throws SQLException {
        if (MongoConnectionManager.isMongoActive()) {
            Bson filter = Filters.or(
                    Filters.eq("userId", null),
                    Filters.eq("userId", 0),
                    Filters.exists("userId", false),
                    Filters.eq("userId", userId)
            );
            List<Category> list = new ArrayList<>();
            for (Document doc : MongoConnectionManager.getCollection("categories")
                    .find(filter)
                    .sort(Sorts.orderBy(Sorts.descending("isDefault"), Sorts.ascending("name")))) {
                list.add(mapMongoCategory(doc));
            }
            return list;
        }

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
        if (MongoConnectionManager.isMongoActive()) {
            Document doc = MongoConnectionManager.getCollection("categories").find(Filters.eq("id", id)).first();
            return doc != null ? mapMongoCategory(doc) : null;
        }

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
        if (MongoConnectionManager.isMongoActive()) {
            int id = MongoConnectionManager.getNextSequence("categoryId");
            category.setId(id);
            Document doc = new Document("id", id)
                    .append("userId", (category.getUserId() != null && category.getUserId() > 0) ? category.getUserId() : null)
                    .append("name", category.getName())
                    .append("iconName", category.getIconName())
                    .append("color", category.getColor())
                    .append("isDefault", category.isDefault())
                    .append("createdAt", new java.util.Date());
            MongoConnectionManager.getCollection("categories").insertOne(doc);
            return category;
        }

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
        if (MongoConnectionManager.isMongoActive()) {
            Integer uid = category.getUserId();
            Bson userFilter = (uid != null && uid > 0) ?
                    Filters.or(Filters.eq("userId", uid), Filters.eq("userId", null)) :
                    Filters.eq("userId", null);

            return MongoConnectionManager.getCollection("categories").updateOne(
                    Filters.and(Filters.eq("id", category.getId()), userFilter),
                    Updates.combine(
                            Updates.set("name", category.getName()),
                            Updates.set("iconName", category.getIconName()),
                            Updates.set("color", category.getColor())
                    )
            ).getModifiedCount() >= 0;
        }

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
        if (MongoConnectionManager.isMongoActive()) {
            return MongoConnectionManager.getCollection("categories").deleteOne(
                    Filters.and(Filters.eq("id", id), Filters.eq("userId", userId))
            ).getDeletedCount() > 0;
        }

        String sql = "DELETE FROM categories WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Category> getSystemCategories() throws SQLException {
        if (MongoConnectionManager.isMongoActive()) {
            Bson filter = Filters.or(
                    Filters.eq("userId", null),
                    Filters.eq("userId", 0),
                    Filters.exists("userId", false)
            );
            List<Category> list = new ArrayList<>();
            for (Document doc : MongoConnectionManager.getCollection("categories").find(filter).sort(Sorts.ascending("name"))) {
                list.add(mapMongoCategory(doc));
            }
            return list;
        }

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

    private Category mapMongoCategory(Document doc) {
        Category c = new Category();
        c.setId(doc.getInteger("id", 0));
        Integer uid = doc.getInteger("userId");
        c.setUserId((uid != null && uid > 0) ? uid : null);
        c.setName(doc.getString("name"));
        c.setIconName(doc.getString("iconName"));
        c.setColor(doc.getString("color"));
        c.setDefault(doc.getBoolean("isDefault", false));
        java.util.Date cd = doc.getDate("createdAt");
        if (cd != null) {
            c.setCreatedAt(new Timestamp(cd.getTime()));
        }
        return c;
    }
}
