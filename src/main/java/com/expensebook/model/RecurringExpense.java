package com.expensebook.model;

import java.sql.Timestamp;
import java.time.LocalDate;

public class RecurringExpense {
    private int id;
    private int userId;
    private String title;
    private int categoryId;
    private double amount;
    private String frequency; // MONTHLY, WEEKLY, YEARLY
    private PaymentMode paymentMode;
    private LocalDate nextDueDate;
    private boolean active;
    private Timestamp createdAt;

    // Transient UI helper fields
    private String categoryName;
    private String categoryIcon;
    private String categoryColor;

    public RecurringExpense() {
        this.frequency = "MONTHLY";
        this.paymentMode = PaymentMode.UPI;
        this.nextDueDate = LocalDate.now().plusMonths(1);
        this.active = true;
    }

    public RecurringExpense(int id, int userId, String title, int categoryId, double amount,
                            String frequency, PaymentMode paymentMode, LocalDate nextDueDate,
                            boolean active, Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.categoryId = categoryId;
        this.amount = amount;
        this.frequency = frequency != null ? frequency : "MONTHLY";
        this.paymentMode = paymentMode != null ? paymentMode : PaymentMode.UPI;
        this.nextDueDate = nextDueDate;
        this.active = active;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public String getFrequency() {
        return frequency != null ? frequency : "MONTHLY";
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public PaymentMode getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(PaymentMode paymentMode) {
        this.paymentMode = paymentMode;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public void setNextDueDate(LocalDate nextDueDate) {
        this.nextDueDate = nextDueDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public boolean isDueOrOverdue() {
        return nextDueDate != null && !nextDueDate.isAfter(LocalDate.now());
    }
}
