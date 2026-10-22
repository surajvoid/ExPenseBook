package com.expensebook.dao;

import com.expensebook.model.Budget;
import com.expensebook.model.CategoryBudget;
import com.expensebook.util.DBConnection;
import com.expensebook.util.MongoConnectionManager;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise BudgetDAO supporting dual-mode operation:
 * Primary: High-performance MongoDB Atlas when active.
 * Fallback / Dev: Standard JDBC connection for local development.
 */
public class BudgetDAO {

    public Budget getBudgetForMonth(int userId, int month, int year) throws SQLException {
        if (MongoConnectionManager.isMongoActive()) {
            Document doc = MongoConnectionManager.getCollection("budgets").find(
                    Filters.and(Filters.eq("userId", userId), Filters.eq("month", month), Filters.eq("year", year))
            ).first();
            return doc != null ? mapMongoBudget(doc) : null;
        }

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
        if (MongoConnectionManager.isMongoActive()) {
            Budget existing = getBudgetForMonth(budget.getUserId(), budget.getMonth(), budget.getYear());
            int budgetId = (existing != null) ? existing.getId() : MongoConnectionManager.getNextSequence("budgetId");
            budget.setId(budgetId);

            List<Document> catBudgetDocs = new ArrayList<>();
            if (budget.getCategoryBudgets() != null) {
                for (CategoryBudget cb : budget.getCategoryBudgets()) {
                    if (cb.getAmount() > 0) {
                        catBudgetDocs.add(new Document("categoryId", cb.getCategoryId())
                                .append("amount", cb.getAmount())
                                .append("categoryName", cb.getCategoryName() != null ? cb.getCategoryName() : "Other")
                                .append("categoryIcon", cb.getCategoryIcon() != null ? cb.getCategoryIcon() : "OTHERS")
                                .append("categoryColor", cb.getCategoryColor() != null ? cb.getCategoryColor() : "#64748B"));
                    }
                }
            }

            if (existing != null) {
                MongoConnectionManager.getCollection("budgets").updateOne(
                        Filters.eq("id", budgetId),
                        Updates.combine(
                                Updates.set("totalBudget", budget.getTotalBudget()),
                                Updates.set("categoryBudgets", catBudgetDocs)
                        )
                );
            } else {
                Document doc = new Document("id", budgetId)
                        .append("userId", budget.getUserId())
                        .append("month", budget.getMonth())
                        .append("year", budget.getYear())
                        .append("totalBudget", budget.getTotalBudget())
                        .append("categoryBudgets", catBudgetDocs)
                        .append("createdAt", new java.util.Date());
                MongoConnectionManager.getCollection("budgets").insertOne(doc);
            }
            return budget;
        }

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
        if (MongoConnectionManager.isMongoActive()) {
            List<Document> catBudgetDocs = new ArrayList<>();
            if (categoryBudgets != null) {
                for (CategoryBudget cb : categoryBudgets) {
                    if (cb.getAmount() > 0) {
                        catBudgetDocs.add(new Document("categoryId", cb.getCategoryId())
                                .append("amount", cb.getAmount())
                                .append("categoryName", cb.getCategoryName() != null ? cb.getCategoryName() : "Other")
                                .append("categoryIcon", cb.getCategoryIcon() != null ? cb.getCategoryIcon() : "OTHERS")
                                .append("categoryColor", cb.getCategoryColor() != null ? cb.getCategoryColor() : "#64748B"));
                    }
                }
            }
            MongoConnectionManager.getCollection("budgets").updateOne(
                    Filters.eq("id", budgetId),
                    Updates.set("categoryBudgets", catBudgetDocs)
            );
            return;
        }

        Connection conn = DBConnection.getConnection();
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
        if (MongoConnectionManager.isMongoActive()) {
            Document doc = MongoConnectionManager.getCollection("budgets").find(Filters.eq("id", budgetId)).first();
            if (doc != null && doc.containsKey("categoryBudgets")) {
                List<?> rawList = doc.get("categoryBudgets", List.class);
                List<CategoryBudget> result = new ArrayList<>();
                if (rawList != null) {
                    for (Object obj : rawList) {
                        if (obj instanceof Document d) {
                            CategoryBudget cb = new CategoryBudget();
                            cb.setBudgetId(budgetId);
                            cb.setCategoryId(d.getInteger("categoryId", 0));
                            cb.setAmount(d.get("amount") != null ? ((Number) d.get("amount")).doubleValue() : 0.0);
                            cb.setCategoryName(d.getString("categoryName") != null ? d.getString("categoryName") : "Other");
                            cb.setCategoryIcon(d.getString("categoryIcon") != null ? d.getString("categoryIcon") : "OTHERS");
                            cb.setCategoryColor(d.getString("categoryColor") != null ? d.getString("categoryColor") : "#64748B");
                            result.add(cb);
                        }
                    }
                }
                return result;
            }
            return new ArrayList<>();
        }

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

    private Budget mapMongoBudget(Document doc) {
        Budget b = new Budget();
        b.setId(doc.getInteger("id", 0));
        b.setUserId(doc.getInteger("userId", 0));
        b.setMonth(doc.getInteger("month", 1));
        b.setYear(doc.getInteger("year", 2026));
        b.setTotalBudget(doc.get("totalBudget") != null ? ((Number) doc.get("totalBudget")).doubleValue() : 0.0);
        java.util.Date cd = doc.getDate("createdAt");
        if (cd != null) {
            b.setCreatedAt(new Timestamp(cd.getTime()));
        }

        List<CategoryBudget> catBudgets = new ArrayList<>();
        if (doc.containsKey("categoryBudgets")) {
            List<?> rawList = doc.get("categoryBudgets", List.class);
            if (rawList != null) {
                for (Object item : rawList) {
                    if (item instanceof Document d) {
                        CategoryBudget cb = new CategoryBudget();
                        cb.setBudgetId(b.getId());
                        cb.setCategoryId(d.getInteger("categoryId", 0));
                        cb.setAmount(d.get("amount") != null ? ((Number) d.get("amount")).doubleValue() : 0.0);
                        cb.setCategoryName(d.getString("categoryName") != null ? d.getString("categoryName") : "Other");
                        cb.setCategoryIcon(d.getString("categoryIcon") != null ? d.getString("categoryIcon") : "OTHERS");
                        cb.setCategoryColor(d.getString("categoryColor") != null ? d.getString("categoryColor") : "#64748B");
                        catBudgets.add(cb);
                    }
                }
            }
        }
        b.setCategoryBudgets(catBudgets);
        return b;
    }
}
