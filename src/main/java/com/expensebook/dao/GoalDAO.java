package com.expensebook.dao;

import com.expensebook.model.Goal;
import com.expensebook.util.DBConnection;
import com.expensebook.util.MongoConnectionManager;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Enterprise GoalDAO supporting dual-mode operation:
 * Primary: High-performance MongoDB Atlas when active.
 * Fallback / Dev: Standard JDBC connection for local development.
 */
public class GoalDAO {

    public List<Goal> getGoalsByUserId(int userId) throws SQLException {
        if (MongoConnectionManager.isMongoActive()) {
            List<Goal> list = new ArrayList<>();
            for (Document doc : MongoConnectionManager.getCollection("savings_goals")
                    .find(Filters.eq("userId", userId))
                    .sort(Sorts.descending("createdAt", "id"))) {
                list.add(mapMongoGoal(doc));
            }
            return list;
        }

        List<Goal> list = new ArrayList<>();
        String sql = "SELECT id, user_id, title, target_amount, saved_amount, emoji, color_theme, target_date, status, created_at, updated_at " +
                     "FROM savings_goals WHERE user_id = ? ORDER BY created_at DESC";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public Goal getGoalById(int id, int userId) throws SQLException {
        if (MongoConnectionManager.isMongoActive()) {
            Document doc = MongoConnectionManager.getCollection("savings_goals").find(
                    Filters.and(Filters.eq("id", id), Filters.eq("userId", userId))
            ).first();
            return doc != null ? mapMongoGoal(doc) : null;
        }

        String sql = "SELECT id, user_id, title, target_amount, saved_amount, emoji, color_theme, target_date, status, created_at, updated_at " +
                     "FROM savings_goals WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public Goal createGoal(Goal goal) throws SQLException {
        if (MongoConnectionManager.isMongoActive()) {
            int id = MongoConnectionManager.getNextSequence("goalId");
            goal.setId(id);
            String st = goal.getSavedAmount() >= goal.getTargetAmount() ? "COMPLETED" : "ACTIVE";
            goal.setStatus(st);
            java.util.Date now = new java.util.Date();

            Document doc = new Document("id", id)
                    .append("userId", goal.getUserId())
                    .append("title", goal.getTitle())
                    .append("targetAmount", goal.getTargetAmount())
                    .append("savedAmount", goal.getSavedAmount())
                    .append("emoji", goal.getEmoji() != null ? goal.getEmoji() : "🎯")
                    .append("colorTheme", goal.getColorTheme() != null ? goal.getColorTheme() : "green")
                    .append("targetDate", goal.getTargetDate() != null ? goal.getTargetDate().toString() : null)
                    .append("status", st)
                    .append("createdAt", now)
                    .append("updatedAt", now);

            MongoConnectionManager.getCollection("savings_goals").insertOne(doc);
            return goal;
        }

        String sql = "INSERT INTO savings_goals (user_id, title, target_amount, saved_amount, emoji, color_theme, target_date, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, goal.getUserId());
            ps.setString(2, goal.getTitle());
            ps.setDouble(3, goal.getTargetAmount());
            ps.setDouble(4, goal.getSavedAmount());
            ps.setString(5, goal.getEmoji() != null ? goal.getEmoji() : "🎯");
            ps.setString(6, goal.getColorTheme() != null ? goal.getColorTheme() : "green");
            if (goal.getTargetDate() != null) {
                ps.setDate(7, java.sql.Date.valueOf(goal.getTargetDate()));
            } else {
                ps.setNull(7, Types.DATE);
            }
            String st = goal.getSavedAmount() >= goal.getTargetAmount() ? "COMPLETED" : "ACTIVE";
            ps.setString(8, st);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    goal.setId(keys.getInt(1));
                }
            }
        }
        return goal;
    }

    public boolean updateGoal(Goal goal) throws SQLException {
        if (MongoConnectionManager.isMongoActive()) {
            String st = goal.getSavedAmount() >= goal.getTargetAmount() ? "COMPLETED" : "ACTIVE";
            goal.setStatus(st);
            return MongoConnectionManager.getCollection("savings_goals").updateOne(
                    Filters.and(Filters.eq("id", goal.getId()), Filters.eq("userId", goal.getUserId())),
                    Updates.combine(
                            Updates.set("title", goal.getTitle()),
                            Updates.set("targetAmount", goal.getTargetAmount()),
                            Updates.set("savedAmount", goal.getSavedAmount()),
                            Updates.set("emoji", goal.getEmoji() != null ? goal.getEmoji() : "🎯"),
                            Updates.set("colorTheme", goal.getColorTheme() != null ? goal.getColorTheme() : "green"),
                            Updates.set("targetDate", goal.getTargetDate() != null ? goal.getTargetDate().toString() : null),
                            Updates.set("status", st),
                            Updates.set("updatedAt", new java.util.Date())
                    )
            ).getModifiedCount() >= 0;
        }

        String sql = "UPDATE savings_goals SET title = ?, target_amount = ?, saved_amount = ?, emoji = ?, color_theme = ?, " +
                     "target_date = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, goal.getTitle());
            ps.setDouble(2, goal.getTargetAmount());
            ps.setDouble(3, goal.getSavedAmount());
            ps.setString(4, goal.getEmoji() != null ? goal.getEmoji() : "🎯");
            ps.setString(5, goal.getColorTheme() != null ? goal.getColorTheme() : "green");
            if (goal.getTargetDate() != null) {
                ps.setDate(6, java.sql.Date.valueOf(goal.getTargetDate()));
            } else {
                ps.setNull(6, Types.DATE);
            }
            String st = goal.getSavedAmount() >= goal.getTargetAmount() ? "COMPLETED" : "ACTIVE";
            ps.setString(7, st);
            ps.setInt(8, goal.getId());
            ps.setInt(9, goal.getUserId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean addFunds(int goalId, int userId, double amount) throws SQLException {
        Goal existing = getGoalById(goalId, userId);
        if (existing == null) return false;
        double newTotal = Math.max(0.0, existing.getSavedAmount() + amount);
        existing.setSavedAmount(newTotal);
        if (newTotal >= existing.getTargetAmount()) {
            existing.setStatus("COMPLETED");
        }
        return updateGoal(existing);
    }

    public boolean deleteGoal(int id, int userId) throws SQLException {
        if (MongoConnectionManager.isMongoActive()) {
            return MongoConnectionManager.getCollection("savings_goals").deleteOne(
                    Filters.and(Filters.eq("id", id), Filters.eq("userId", userId))
            ).getDeletedCount() > 0;
        }

        String sql = "DELETE FROM savings_goals WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public Map<String, Object> getSavingsStats(int userId) throws SQLException {
        Map<String, Object> stats = new HashMap<>();

        if (MongoConnectionManager.isMongoActive()) {
            int total = 0;
            int completed = 0;
            double target = 0.0;
            double saved = 0.0;

            for (Document doc : MongoConnectionManager.getCollection("savings_goals").find(Filters.eq("userId", userId))) {
                total++;
                double t = doc.get("targetAmount") != null ? ((Number) doc.get("targetAmount")).doubleValue() : 0.0;
                double s = doc.get("savedAmount") != null ? ((Number) doc.get("savedAmount")).doubleValue() : 0.0;
                String st = doc.getString("status");
                if (s >= t || "COMPLETED".equalsIgnoreCase(st)) {
                    completed++;
                }
                target += t;
                saved += s;
            }

            double pct = target > 0 ? (saved / target) * 100.0 : 0.0;
            stats.put("totalGoals", total);
            stats.put("completedGoals", completed);
            stats.put("activeGoals", total - completed);
            stats.put("totalTarget", target);
            stats.put("totalTargetAmount", target);
            stats.put("totalSaved", saved);
            stats.put("totalSavedAmount", saved);
            stats.put("overallPercentage", Math.min(Math.round(pct * 10.0) / 10.0, 100.0));
            stats.put("totalProgressPercentage", Math.min(Math.round(pct * 10.0) / 10.0, 100.0));
            return stats;
        }

        String sql = "SELECT COUNT(*) AS total_goals, " +
                     "SUM(CASE WHEN saved_amount >= target_amount OR status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_goals, " +
                     "COALESCE(SUM(target_amount), 0.0) AS total_target, " +
                     "COALESCE(SUM(saved_amount), 0.0) AS total_saved " +
                     "FROM savings_goals WHERE user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total_goals");
                    int completed = rs.getInt("completed_goals");
                    double target = rs.getDouble("total_target");
                    double saved = rs.getDouble("total_saved");
                    double pct = target > 0 ? (saved / target) * 100.0 : 0.0;

                    stats.put("totalGoals", total);
                    stats.put("completedGoals", completed);
                    stats.put("activeGoals", total - completed);
                    stats.put("totalTarget", target);
                    stats.put("totalTargetAmount", target);
                    stats.put("totalSaved", saved);
                    stats.put("totalSavedAmount", saved);
                    stats.put("overallPercentage", Math.min(Math.round(pct * 10.0) / 10.0, 100.0));
                    stats.put("totalProgressPercentage", Math.min(Math.round(pct * 10.0) / 10.0, 100.0));
                }
            }
        }
        return stats;
    }

    private Goal mapRow(ResultSet rs) throws SQLException {
        Goal g = new Goal();
        g.setId(rs.getInt("id"));
        g.setUserId(rs.getInt("user_id"));
        g.setTitle(rs.getString("title"));
        g.setTargetAmount(rs.getDouble("target_amount"));
        g.setSavedAmount(rs.getDouble("saved_amount"));
        g.setEmoji(rs.getString("emoji"));
        g.setColorTheme(rs.getString("color_theme"));
        java.sql.Date d = rs.getDate("target_date");
        if (d != null) g.setTargetDate(d.toLocalDate());
        g.setStatus(rs.getString("status"));
        g.setCreatedAt(rs.getTimestamp("created_at"));
        g.setUpdatedAt(rs.getTimestamp("updated_at"));
        return g;
    }

    private Goal mapMongoGoal(Document doc) {
        Goal g = new Goal();
        g.setId(doc.getInteger("id", 0));
        g.setUserId(doc.getInteger("userId", 0));
        g.setTitle(doc.getString("title"));
        g.setTargetAmount(doc.get("targetAmount") != null ? ((Number) doc.get("targetAmount")).doubleValue() : 0.0);
        g.setSavedAmount(doc.get("savedAmount") != null ? ((Number) doc.get("savedAmount")).doubleValue() : 0.0);
        g.setEmoji(doc.getString("emoji"));
        g.setColorTheme(doc.getString("colorTheme"));
        String targetDateStr = doc.getString("targetDate");
        if (targetDateStr != null && !targetDateStr.isEmpty()) {
            g.setTargetDate(LocalDate.parse(targetDateStr));
        }
        g.setStatus(doc.getString("status"));
        java.util.Date cd = doc.getDate("createdAt");
        if (cd != null) g.setCreatedAt(new Timestamp(cd.getTime()));
        java.util.Date ud = doc.getDate("updatedAt");
        if (ud != null) g.setUpdatedAt(new Timestamp(ud.getTime()));
        return g;
    }
}
