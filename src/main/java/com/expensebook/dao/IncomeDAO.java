package com.expensebook.dao;

import com.expensebook.model.Income;
import com.expensebook.util.DBConnection;
import com.expensebook.util.MongoConnectionManager;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

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
 * Enterprise IncomeDAO supporting dual-mode operation:
 * Primary: High-performance MongoDB Atlas when active.
 * Fallback / Dev: Standard JDBC connection for local development.
 */
public class IncomeDAO {

    public Income addIncome(Income income) throws SQLException {
        if (MongoConnectionManager.isMongoActive()) {
            int id = MongoConnectionManager.getNextSequence("incomeId");
            income.setId(id);
            Document doc = new Document("id", id)
                    .append("userId", income.getUserId())
                    .append("source", income.getSource())
                    .append("amount", income.getAmount())
                    .append("incomeDate", income.getIncomeDate().toString())
                    .append("description", income.getDescription() != null ? income.getDescription() : "")
                    .append("createdAt", new java.util.Date());
            MongoConnectionManager.getCollection("income").insertOne(doc);
            return income;
        }

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
        if (MongoConnectionManager.isMongoActive()) {
            return MongoConnectionManager.getCollection("income").updateOne(
                    Filters.and(Filters.eq("id", income.getId()), Filters.eq("userId", income.getUserId())),
                    Updates.combine(
                            Updates.set("source", income.getSource()),
                            Updates.set("amount", income.getAmount()),
                            Updates.set("incomeDate", income.getIncomeDate().toString()),
                            Updates.set("description", income.getDescription() != null ? income.getDescription() : "")
                    )
            ).getModifiedCount() >= 0;
        }

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
        if (MongoConnectionManager.isMongoActive()) {
            return MongoConnectionManager.getCollection("income").deleteOne(
                    Filters.and(Filters.eq("id", incomeId), Filters.eq("userId", userId))
            ).getDeletedCount() > 0;
        }

        String sql = "DELETE FROM income WHERE id = ? AND user_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, incomeId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Income> getIncomeForUser(int userId, LocalDate startDate, LocalDate endDate) throws SQLException {
        if (MongoConnectionManager.isMongoActive()) {
            List<Bson> filters = new ArrayList<>();
            filters.add(Filters.eq("userId", userId));
            if (startDate != null) filters.add(Filters.gte("incomeDate", startDate.toString()));
            if (endDate != null) filters.add(Filters.lte("incomeDate", endDate.toString()));

            List<Income> list = new ArrayList<>();
            for (Document doc : MongoConnectionManager.getCollection("income")
                    .find(Filters.and(filters))
                    .sort(Sorts.descending("incomeDate", "id"))) {
                list.add(mapMongoIncome(doc));
            }
            return list;
        }

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
        if (MongoConnectionManager.isMongoActive()) {
            List<Bson> filters = new ArrayList<>();
            filters.add(Filters.eq("userId", userId));
            if (startDate != null) filters.add(Filters.gte("incomeDate", startDate.toString()));
            if (endDate != null) filters.add(Filters.lte("incomeDate", endDate.toString()));

            List<Bson> pipeline = List.of(
                    Aggregates.match(Filters.and(filters)),
                    Aggregates.group(null, Accumulators.sum("total", "$amount"))
            );
            Document doc = MongoConnectionManager.getCollection("income").aggregate(pipeline).first();
            return (doc != null && doc.get("total") != null) ? ((Number) doc.get("total")).doubleValue() : 0.0;
        }

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

    private Income mapMongoIncome(Document doc) {
        Income inc = new Income();
        inc.setId(doc.getInteger("id", 0));
        inc.setUserId(doc.getInteger("userId", 0));
        inc.setSource(doc.getString("source"));
        inc.setAmount(doc.get("amount") != null ? ((Number) doc.get("amount")).doubleValue() : 0.0);
        String dateStr = doc.getString("incomeDate");
        if (dateStr != null) {
            inc.setIncomeDate(LocalDate.parse(dateStr));
        }
        inc.setDescription(doc.getString("description"));
        java.util.Date cd = doc.getDate("createdAt");
        if (cd != null) {
            inc.setCreatedAt(new Timestamp(cd.getTime()));
        }
        return inc;
    }
}
