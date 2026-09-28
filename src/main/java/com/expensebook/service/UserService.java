package com.expensebook.service;

import com.expensebook.dao.UserDAO;
import com.expensebook.model.FinancialMode;
import com.expensebook.model.User;
import com.expensebook.util.PasswordUtil;
import com.expensebook.util.SessionManager;
import com.expensebook.util.ValidationUtil;

import java.sql.SQLException;
import java.util.List;

public class UserService {

    private final UserDAO userDAO = new UserDAO();

    public User register(String fullName, String email, String password, String confirmPassword, FinancialMode mode) throws Exception {
        if (!ValidationUtil.isNonEmpty(fullName)) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (!ValidationUtil.isValidEmail(email)) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        if (!ValidationUtil.isValidPassword(password)) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
        if (!ValidationUtil.doPasswordsMatch(password, confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        User existing = userDAO.findByEmail(email);
        if (existing != null) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setPasswordHash(PasswordUtil.hashPassword(password));
        user.setRole("USER");
        user.setFinancialMode(mode != null ? mode : FinancialMode.TRACK_ONLY);
        user.setCurrency("₹");
        user.setActive(true);

        return userDAO.register(user);
    }

    public User login(String email, String password) throws Exception {
        if (!ValidationUtil.isValidEmail(email)) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        if (!ValidationUtil.isNonEmpty(password)) {
            throw new IllegalArgumentException("Password is required.");
        }

        User user = userDAO.findByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("No account found with this email.");
        }

        if (!user.isActive()) {
            throw new IllegalStateException("Your account has been deactivated. Please contact admin.");
        }

        if (!PasswordUtil.verifyPassword(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid password. Please check your credentials.");
        }

        SessionManager.setCurrentUser(user);
        return user;
    }

    public boolean switchFinancialMode(int userId, FinancialMode newMode) throws SQLException {
        boolean updated = userDAO.updateFinancialMode(userId, newMode);
        if (updated && SessionManager.getCurrentUser() != null && SessionManager.getCurrentUserId() == userId) {
            SessionManager.getCurrentUser().setFinancialMode(newMode);
        }
        return updated;
    }

    public boolean updateProfile(int userId, String fullName, String currency) throws SQLException {
        if (!ValidationUtil.isNonEmpty(fullName)) {
            throw new IllegalArgumentException("Full name cannot be empty.");
        }
        User user = userDAO.findById(userId);
        if (user != null) {
            user.setFullName(fullName.trim());
            user.setCurrency(currency);
            boolean updated = userDAO.updateProfile(user);
            if (updated && SessionManager.getCurrentUser() != null && SessionManager.getCurrentUserId() == userId) {
                SessionManager.getCurrentUser().setFullName(fullName.trim());
                SessionManager.getCurrentUser().setCurrency(currency);
            }
            return updated;
        }
        return false;
    }

    public boolean changePassword(int userId, String currentPass, String newPass, String confirmPass) throws Exception {
        if (!ValidationUtil.isValidPassword(newPass)) {
            throw new IllegalArgumentException("New password must be at least 6 characters.");
        }
        if (!ValidationUtil.doPasswordsMatch(newPass, confirmPass)) {
            throw new IllegalArgumentException("New passwords do not match.");
        }
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found.");
        }
        if (!PasswordUtil.verifyPassword(currentPass, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        String newHash = PasswordUtil.hashPassword(newPass);
        return userDAO.updatePassword(userId, newHash);
    }

    public List<User> listAllUsers() throws SQLException {
        return userDAO.getAllUsers();
    }

    public boolean toggleUserStatus(int userId, boolean active) throws SQLException {
        return userDAO.toggleActive(userId, active);
    }

    public boolean deleteUser(int userId) throws SQLException {
        return userDAO.deleteUser(userId);
    }

    public int getTotalUsers() throws SQLException {
        return userDAO.getTotalUsersCount();
    }

    public int getActiveUsers() throws SQLException {
        return userDAO.getActiveUsersCount();
    }

    public User getUserById(int userId) throws SQLException {
        return userDAO.findById(userId);
    }
}
