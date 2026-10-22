package com.expensebook.dao;

import com.expensebook.model.PaymentMode;
import com.expensebook.model.RecurringExpense;
import com.expensebook.util.DBConnection;
import com.expensebook.util.MongoConnectionManager;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise RecurringExpenseDAO supporting dual-mode operation:
 * Primary: High-performance MongoDB Atlas when active.
 * Fallback / Dev: Standard JDBC connection for local development.
 */
public class RecurringExpenseDAO {

    public RecurringExpense addRecurring(RecurringExpense re) throws SQLException {
        if (MongoConnectionManager.isMongoActive()) {
            int id = MongoConnectionManager.getNextSequence("recurringId");
            re.setId(id);

            String catName = re.getCategoryName();
            String catIcon = re.getCategoryIcon();
            String catColor = re.getCategoryColor();
            if ((catName == null || catName.isEmpty()) && re.getCategoryId() > 0) {
                Document cDoc = MongoConnectionManager.getCollection("categories").find(Filters.eq("id", re.getCategoryId())).first();
                if (cDoc != null) {
                    catName = cDoc.getString("name");
                    catIcon = cDoc.getString("iconName");
                    catColor = cDoc.getString("color");
                }
            }
            if (catName == null) catName = "Other";
            if (catIcon == null) catIcon = "OTHERS";
            if (catColor == null) catColor = "#64748B";

            Document doc = new Document("id", id)
                    .append("userId", re.getUserId())
                    .append("title", re.getTitle())
                    .append("categoryId", re.getCategoryId())
                    .append("categoryName", catName)
                    .append("categoryIcon", catIcon)
                    .append("categoryColor", catColor)
                    .append("amount", re.getAmount())
                    .append("frequency", re.getFrequency())
                    .append("paymentMode", re.getPaymentMode() != null ? re.getPaymentMode().name() : "UPI")
                    .append("nextDueDate", re.getNextDueDate().toString())
                    .append("active", re.isActive())
                    .append("isActive", re.isActive())
                    .append("createdAt", new java.util.Date());

            MongoConnectionManager.getCollection("recurring_expenses").insertOne(doc);
            return re;
        }

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
        if (MongoConnectionManager.isMongoActive()) {
            String catName = re.getCategoryName();
            String catIcon = re.getCategoryIcon();
            String catColor = re.getCategoryColor();
            if ((catName == null || catName.isEmpty()) && re.getCategoryId() > 0) {
                Document cDoc = MongoConnectionManager.getCollection("categories").find(Filters.eq("id", re.getCategoryId())).first();
                if (cDoc != null) {
                    catName = cDoc.getString("name");
                    catIcon = cDoc.getString("iconName");
                    catColor = cDoc.getString("color");
                }
            }

            return MongoConnectionManager.getCollection("recurring_expenses").updateOne(
                    Filters.and(Filters.eq("id", re.getId()), Filters.eq("userId", re.getUserId())),
                    Updates.combine(
                            Updates.set("title", re.getTitle()),
                            Updates.set("categoryId", re.getCategoryId()),
                            Updates.set("categoryName", catName != null ? catName : "Other"),
                            Updates.set("categoryIcon", catIcon != null ? catIcon : "OTHERS"),
                            Updates.set("categoryColor", catColor != null ? catColor : "#64748B"),
                            Updates.set("amount", re.getAmount()),
                            Updates.set("frequency", re.getFrequency()),
                            Updates.set("paymentMode", re.getPaymentMode() != null ? re.getPaymentMode().name() : "UPI"),
                            Updates.set("nextDueDate", re.getNextDueDate().toString()),
                            Updates.set("active", re.isActive()),
                            Updates.set("isActive", re.isActive())
                    )
            ).getModifiedCount() >= 0;
        }

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
        if (MongoConnectionManager.isMongoActive()) {
            return MongoConnectionManager.getCollection("recurring_expenses").deleteOne(
                    Filters.and(Filters.eq("id", id), Filters.eq("userId", userId))
            ).getDeletedCount() > 0;
        }

        String sql = "DELETE FROM recurring_expenses WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<RecurringExpense> getRecurringForUser(int userId) throws SQLException {
        if (MongoConnectionManager.isMongoActive()) {
            List<RecurringExpense> list = new ArrayList<>();
            for (Document doc : MongoConnectionManager.getCollection("recurring_expenses")
                    .find(Filters.eq("userId", userId))
                    .sort(Sorts.ascending("nextDueDate"))) {
                list.add(mapMongoRecurring(doc));
            }
            return list;
        }

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
        if (MongoConnectionManager.isMongoActive()) {
            return MongoConnectionManager.getCollection("recurring_expenses").updateOne(
                    Filters.eq("id", id),
                    Updates.set("nextDueDate", nextDate.toString())
            ).getModifiedCount() >= 0;
        }

        String sql = "UPDATE recurring_expenses SET next_due_date = ? WHERE id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(nextDate));
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    private RecurringExpense mapMongoRecurring(Document doc) {
        RecurringExpense re = new RecurringExpense();
        re.setId(doc.getInteger("id", 0));
        re.setUserId(doc.getInteger("userId", 0));
        re.setTitle(doc.getString("title"));
        re.setCategoryId(doc.getInteger("categoryId", 0));
        re.setAmount(doc.get("amount") != null ? ((Number) doc.get("amount")).doubleValue() : 0.0);
        re.setFrequency(doc.getString("frequency"));
        re.setPaymentMode(PaymentMode.fromString(doc.getString("paymentMode")));
        String ndStr = doc.getString("nextDueDate");
        if (ndStr != null) re.setNextDueDate(LocalDate.parse(ndStr));
        boolean act = doc.getBoolean("active", doc.getBoolean("isActive", true));
        re.setActive(act);
        java.util.Date cd = doc.getDate("createdAt");
        if (cd != null) re.setCreatedAt(new Timestamp(cd.getTime()));
        String catName = doc.getString("categoryName");
        re.setCategoryName(catName != null ? catName : "Other");
        String catIcon = doc.getString("categoryIcon");
        re.setCategoryIcon(catIcon != null ? catIcon : "OTHERS");
        String catColor = doc.getString("categoryColor");
        re.setCategoryColor(catColor != null ? catColor : "#64748B");
        return re;
    }
}
