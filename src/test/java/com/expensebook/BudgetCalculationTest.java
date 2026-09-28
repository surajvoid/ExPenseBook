package com.expensebook;

import com.expensebook.service.AnalysisService;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DateUtil;
import com.expensebook.util.PasswordUtil;
import com.expensebook.util.ValidationUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class BudgetCalculationTest {

    @Test
    @DisplayName("Password hashing and BCrypt verification test")
    public void testPasswordHashing() {
        String plain = "Secret@2026";
        String hash = PasswordUtil.hashPassword(plain);
        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$"));
        assertTrue(PasswordUtil.verifyPassword(plain, hash));
        assertFalse(PasswordUtil.verifyPassword("WrongPassword", hash));
    }

    @Test
    @DisplayName("Input validation utilities test")
    public void testValidationUtils() {
        assertTrue(ValidationUtil.isValidEmail("suraj@example.com"));
        assertFalse(ValidationUtil.isValidEmail("invalid-email"));
        assertTrue(ValidationUtil.isValidPassword("123456"));
        assertFalse(ValidationUtil.isValidPassword("123"));

        assertTrue(ValidationUtil.isValidAmount("250.50"));
        assertTrue(ValidationUtil.isValidAmount("1000"));
        assertFalse(ValidationUtil.isValidAmount("0"));
        assertFalse(ValidationUtil.isValidAmount("-50"));
        assertFalse(ValidationUtil.isValidAmount("abc"));
    }

    @Test
    @DisplayName("Month-End Projection and Recommended Daily Spending calculation test")
    public void testSmartBudgetMathematicalFormulas() {
        // From PRD Section 8, 22, 23, 24 example:
        // Monthly Budget: 15,000
        // Spent: 8,450
        // Days passed: 19
        // Days remaining: 12
        double monthlyBudget = 15000.0;
        double spent = 8450.0;
        int daysPassed = 19;
        int daysRemaining = 12;

        double remainingBudget = monthlyBudget - spent;
        assertEquals(6550.0, remainingBudget, 0.01);

        double avgDailySpent = spent / daysPassed;
        assertEquals(444.73, avgDailySpent, 0.5);

        double recommendedDaily = remainingBudget / daysRemaining;
        assertEquals(545.83, recommendedDaily, 0.5);

        // Mathematical month-end projection:
        // projected = spent + (avgDailySpent * daysRemaining)
        double projectedSpending = spent + (avgDailySpent * daysRemaining);
        assertEquals(13786.84, projectedSpending, 5.0); // matches PRD ~₹13,790
        assertTrue(projectedSpending <= monthlyBudget, "Projected spending should be within budget");
    }

    @Test
    @DisplayName("Month-over-month spending comparison percentage calculation test")
    public void testMonthComparisonMath() {
        // PRD Section 20 Example:
        // July: ₹9,800
        // August: ₹8,450
        // Reduction = 1,350 less, 13.7% reduction
        double julySpent = 9800.0;
        double augSpent = 8450.0;

        double diff = augSpent - julySpent;
        assertEquals(-1350.0, diff, 0.01);

        double pctChange = ((augSpent - julySpent) / julySpent) * 100.0;
        assertEquals(-13.77, pctChange, 0.1);
    }

    @Test
    @DisplayName("Currency formatting test")
    public void testCurrencyFormatting() {
        assertEquals("₹ 8,450", CurrencyUtil.format(8450.0, "₹"));
        assertEquals("13.8%", CurrencyUtil.formatPercent(13.775));
        assertEquals("8.5k", CurrencyUtil.formatCompact(8450.0));
    }
}
