package com.expensebook.service;

import com.expensebook.dao.GoalDAO;
import com.expensebook.model.Goal;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class GoalService {

    private final GoalDAO goalDAO;

    public GoalService() {
        this.goalDAO = new GoalDAO();
    }

    public GoalService(GoalDAO goalDAO) {
        this.goalDAO = goalDAO;
    }

    public List<Goal> getGoals(int userId) throws SQLException {
        return goalDAO.getGoalsByUserId(userId);
    }

    public Goal getGoal(int id, int userId) throws SQLException {
        return goalDAO.getGoalById(id, userId);
    }

    public Goal createGoal(int userId, String title, double targetAmount, double savedAmount,
                           String emoji, String colorTheme, LocalDate targetDate) throws SQLException {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Goal title cannot be empty.");
        }
        if (targetAmount <= 0) {
            throw new IllegalArgumentException("Target amount must be greater than zero.");
        }
        if (savedAmount < 0) {
            savedAmount = 0.0;
        }

        Goal goal = new Goal();
        goal.setUserId(userId);
        goal.setTitle(title.trim());
        goal.setTargetAmount(targetAmount);
        goal.setSavedAmount(savedAmount);
        goal.setEmoji(emoji != null && !emoji.trim().isEmpty() ? emoji.trim() : "🎯");
        goal.setColorTheme(colorTheme != null && !colorTheme.trim().isEmpty() ? colorTheme.trim().toLowerCase() : "green");
        goal.setTargetDate(targetDate);
        goal.setStatus(savedAmount >= targetAmount ? "COMPLETED" : "ACTIVE");

        return goalDAO.createGoal(goal);
    }

    public boolean updateGoal(int id, int userId, String title, double targetAmount, double savedAmount,
                              String emoji, String colorTheme, LocalDate targetDate) throws SQLException {
        Goal existing = goalDAO.getGoalById(id, userId);
        if (existing == null) {
            throw new IllegalArgumentException("Goal not found.");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Goal title cannot be empty.");
        }
        if (targetAmount <= 0) {
            throw new IllegalArgumentException("Target amount must be greater than zero.");
        }

        existing.setTitle(title.trim());
        existing.setTargetAmount(targetAmount);
        existing.setSavedAmount(Math.max(0.0, savedAmount));
        if (emoji != null && !emoji.trim().isEmpty()) existing.setEmoji(emoji.trim());
        if (colorTheme != null && !colorTheme.trim().isEmpty()) existing.setColorTheme(colorTheme.trim().toLowerCase());
        existing.setTargetDate(targetDate);
        existing.setStatus(existing.getSavedAmount() >= targetAmount ? "COMPLETED" : "ACTIVE");

        return goalDAO.updateGoal(existing);
    }

    public boolean addFunds(int goalId, int userId, double amount) throws SQLException {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be greater than zero.");
        }
        return goalDAO.addFunds(goalId, userId, amount);
    }

    public boolean deleteGoal(int id, int userId) throws SQLException {
        return goalDAO.deleteGoal(id, userId);
    }

    public Map<String, Object> getSummary(int userId) throws SQLException {
        return goalDAO.getSavingsStats(userId);
    }
}
