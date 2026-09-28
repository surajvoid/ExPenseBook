package com.expensebook.util;

import java.time.LocalDate;
import java.util.regex.Pattern;

public class ValidationUtil {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.trim().length() >= 6;
    }

    public static boolean doPasswordsMatch(String password, String confirmPassword) {
        if (password == null || confirmPassword == null) return false;
        return password.equals(confirmPassword);
    }

    public static boolean isValidAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) return false;
        try {
            double val = Double.parseDouble(amountStr.trim());
            return val > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static double parseAmount(String amountStr) {
        if (!isValidAmount(amountStr)) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        return Double.parseDouble(amountStr.trim());
    }

    public static boolean isNonEmpty(String text) {
        return text != null && !text.trim().isEmpty();
    }

    public static boolean isValidDate(LocalDate date) {
        return date != null;
    }
}
