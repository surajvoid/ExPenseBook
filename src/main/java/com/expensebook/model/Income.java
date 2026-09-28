package com.expensebook.model;

import java.sql.Timestamp;
import java.time.LocalDate;

public class Income {
    private int id;
    private int userId;
    private String source; // Salary, Freelancing, Allowance, Business, Other
    private double amount;
    private LocalDate incomeDate;
    private String description;
    private Timestamp createdAt;

    public Income() {
        this.incomeDate = LocalDate.now();
        this.source = "Salary";
    }

    public Income(int id, int userId, String source, double amount, LocalDate incomeDate, String description, Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.source = source;
        this.amount = amount;
        this.incomeDate = incomeDate;
        this.description = description;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getSource() {
        return source != null ? source : "Other";
    }

    public void setSource(String source) {
        this.source = source;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public LocalDate getIncomeDate() {
        return incomeDate;
    }

    public void setIncomeDate(LocalDate incomeDate) {
        this.incomeDate = incomeDate;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
