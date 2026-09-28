package com.expensebook.model;

import java.sql.Timestamp;

public class User {
    private int id;
    private String fullName;
    private String email;
    private String passwordHash;
    private String role; // 'USER' or 'ADMIN'
    private FinancialMode financialMode;
    private String currency;
    private boolean active;
    private Timestamp createdAt;

    public User() {
        this.role = "USER";
        this.financialMode = FinancialMode.TRACK_ONLY;
        this.currency = "₹";
        this.active = true;
    }

    public User(int id, String fullName, String email, String passwordHash, String role,
                FinancialMode financialMode, String currency, boolean active, Timestamp createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role != null ? role : "USER";
        this.financialMode = financialMode != null ? financialMode : FinancialMode.TRACK_ONLY;
        this.currency = currency != null ? currency : "₹";
        this.active = active;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(this.role);
    }

    public FinancialMode getFinancialMode() {
        return financialMode;
    }

    public void setFinancialMode(FinancialMode financialMode) {
        this.financialMode = financialMode;
    }

    public boolean isBudgetMode() {
        return financialMode == FinancialMode.TRACK_AND_BUDGET;
    }

    public String getCurrency() {
        return currency != null ? currency : "₹";
    }

    public void setCurrency(String currency) {
        this.currency = currency;
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
}
