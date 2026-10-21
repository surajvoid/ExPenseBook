package com.expensebook.model;

import java.sql.Timestamp;
import java.time.LocalDate;

public class Goal {
    private int id;
    private int userId;
    private String title;
    private double targetAmount;
    private double savedAmount;
    private String emoji;
    private String colorTheme; // 'pink', 'green', 'yellow', 'blue', 'purple'
    private LocalDate targetDate;
    private String status; // 'ACTIVE', 'COMPLETED'
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private double percentage;

    public Goal() {
        this.emoji = "🎯";
        this.colorTheme = "green";
        this.status = "ACTIVE";
        this.savedAmount = 0.0;
        this.percentage = 0.0;
    }

    public Goal(int id, int userId, String title, double targetAmount, double savedAmount,
                String emoji, String colorTheme, LocalDate targetDate, String status,
                Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.targetAmount = targetAmount;
        this.savedAmount = savedAmount;
        this.emoji = emoji != null && !emoji.isEmpty() ? emoji : "🎯";
        this.colorTheme = colorTheme != null && !colorTheme.isEmpty() ? colorTheme : "green";
        this.targetDate = targetDate;
        this.status = status != null ? status : "ACTIVE";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.percentage = targetAmount > 0 ? Math.min(Math.round((savedAmount / targetAmount) * 1000.0) / 10.0, 100.0) : 0.0;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(double targetAmount) {
        this.targetAmount = targetAmount;
        updatePercentage();
    }

    public double getSavedAmount() { return savedAmount; }
    public void setSavedAmount(double savedAmount) {
        this.savedAmount = savedAmount;
        updatePercentage();
    }

    public void updatePercentage() {
        if (targetAmount <= 0) {
            this.percentage = 0.0;
        } else {
            double p = (savedAmount / targetAmount) * 100.0;
            this.percentage = Math.min(Math.round(p * 10.0) / 10.0, 100.0);
        }
    }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public String getColorTheme() { return colorTheme; }
    public void setColorTheme(String colorTheme) { this.colorTheme = colorTheme; }

    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public double getPercentage() {
        updatePercentage();
        return this.percentage;
    }

    public boolean isCompleted() {
        return "COMPLETED".equalsIgnoreCase(status) || (targetAmount > 0 && savedAmount >= targetAmount);
    }
}
