package com.expensebook.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class Budget {
    private int id;
    private int userId;
    private int month;
    private int year;
    private double totalBudget;
    private Timestamp createdAt;
    private List<CategoryBudget> categoryBudgets;

    public Budget() {
        this.categoryBudgets = new ArrayList<>();
    }

    public Budget(int id, int userId, int month, int year, double totalBudget, Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.month = month;
        this.year = year;
        this.totalBudget = totalBudget;
        this.createdAt = createdAt;
        this.categoryBudgets = new ArrayList<>();
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

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public double getTotalBudget() {
        return totalBudget;
    }

    public void setTotalBudget(double totalBudget) {
        this.totalBudget = totalBudget;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public List<CategoryBudget> getCategoryBudgets() {
        return categoryBudgets;
    }

    public void setCategoryBudgets(List<CategoryBudget> categoryBudgets) {
        this.categoryBudgets = categoryBudgets;
    }
}
