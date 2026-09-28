package com.expensebook.model;

import java.sql.Timestamp;

public class Category {
    private int id;
    private Integer userId; // null if default system category
    private String name;
    private String iconName;
    private String color;
    private boolean isDefault;
    private Timestamp createdAt;

    public Category() {
        this.color = "#78BFA0";
        this.isDefault = true;
    }

    public Category(int id, String name, String iconName, String color, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.iconName = iconName;
        this.color = color != null ? color : "#78BFA0";
        this.isDefault = isDefault;
    }

    public Category(int id, Integer userId, String name, String iconName, String color, boolean isDefault, Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.iconName = iconName;
        this.color = color != null ? color : "#78BFA0";
        this.isDefault = isDefault;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIconName() {
        return iconName != null ? iconName : "OTHERS";
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }

    public String getColor() {
        return color != null ? color : "#78BFA0";
    }

    public void setColor(String color) {
        this.color = color;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category category = (Category) o;
        return id == category.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
