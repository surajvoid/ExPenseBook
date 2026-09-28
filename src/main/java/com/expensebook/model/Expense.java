package com.expensebook.model;

import java.sql.Timestamp;
import java.time.LocalDate;

public class Expense {
    private int id;
    private int userId;
    private int categoryId;
    private double amount;
    private PaymentMode paymentMode;
    private LocalDate expenseDate;
    private String description;
    private Timestamp createdAt;

    // Transient UI helper fields
    private Category category;
    private String categoryName;
    private String categoryIcon;
    private String categoryColor;

    public Expense() {
        this.expenseDate = LocalDate.now();
        this.paymentMode = PaymentMode.UPI;
    }

    public Expense(int id, int userId, int categoryId, double amount, PaymentMode paymentMode,
                   LocalDate expenseDate, String description, Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.categoryId = categoryId;
        this.amount = amount;
        this.paymentMode = paymentMode;
        this.expenseDate = expenseDate;
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

    public PaymentMode getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(PaymentMode paymentMode) {
        this.paymentMode = paymentMode;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate;
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

    public String getCategoryName() {
        return categoryName != null ? categoryName : "Other";
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

    public Category getCategory() {
        if (category == null && (categoryName != null || categoryId > 0)) {
            category = new Category(categoryId, categoryName, categoryIcon, categoryColor, true);
        }
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
        if (category != null) {
            this.categoryId = category.getId();
            this.categoryName = category.getName();
            this.categoryIcon = category.getIconName();
            this.categoryColor = category.getColor();
        }
    }
}
