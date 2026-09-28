package com.expensebook.service;

import com.expensebook.dao.CategoryDAO;
import com.expensebook.model.Category;

import java.sql.SQLException;
import java.util.List;

public class CategoryService {

    private final CategoryDAO categoryDAO = new CategoryDAO();

    public List<Category> getCategoriesForUser(int userId) throws SQLException {
        return categoryDAO.getAllCategoriesForUser(userId);
    }

    public Category addCustomCategory(int userId, String name, String iconName, String color) throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required.");
        }

        Category category = new Category();
        category.setUserId(userId);
        category.setName(name.trim());
        category.setIconName(iconName != null ? iconName : "OTHERS");
        category.setColor(color != null ? color : "#78BFA0");
        category.setDefault(false);

        return categoryDAO.addCustomCategory(category);
    }

    public boolean updateCategory(Category category) throws SQLException {
        return categoryDAO.updateCategory(category);
    }

    public boolean deleteCategory(int categoryId, int userId) throws SQLException {
        return categoryDAO.deleteCategory(categoryId, userId);
    }

    public Category getCategoryById(int id) throws SQLException {
        return categoryDAO.getCategoryById(id);
    }

    public List<Category> getSystemDefaultCategories() throws SQLException {
        return categoryDAO.getSystemCategories();
    }
}
