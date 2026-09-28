package com.expensebook.model;

public class CategoryBudget {
    private int id;
    private int budgetId;
    private int categoryId;
    private double amount;

    // Transient UI helper fields
    private String categoryName;
    private String categoryIcon;
    private String categoryColor;
    private double spentAmount;

    public CategoryBudget() {
    }

    public CategoryBudget(int id, int budgetId, int categoryId, double amount) {
        this.id = id;
        this.budgetId = budgetId;
        this.categoryId = categoryId;
        this.amount = amount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(int budgetId) {
        this.budgetId = budgetId;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getCategoryName() {
        return categoryName != null ? categoryName : "Category";
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryIcon() {
        return categoryIcon != null ? categoryIcon : "OTHERS";
    }

    public void setCategoryIcon(String categoryIcon) {
        this.categoryIcon = categoryIcon;
    }

    public String getCategoryColor() {
        return categoryColor != null ? categoryColor : "#78BFA0";
    }

    public void setCategoryColor(String categoryColor) {
        this.categoryColor = categoryColor;
    }

    public double getSpentAmount() {
        return spentAmount;
    }

    public void setSpentAmount(double spentAmount) {
        this.spentAmount = spentAmount;
    }

    public double getRemainingAmount() {
        return amount - spentAmount;
    }

    public double getUsedPercentage() {
        if (amount <= 0) return 0;
        return Math.min(100.0, (spentAmount / amount) * 100.0);
    }

    public boolean isExceeded() {
        return spentAmount > amount;
    }
}
