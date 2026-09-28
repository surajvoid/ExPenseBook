package com.expensebook;

import com.expensebook.model.*;
import com.expensebook.service.*;
import com.expensebook.util.SessionManager;
import org.junit.jupiter.api.*;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ServiceIntegrationTest {

    private static UserService userService;
    private static ExpenseService expenseService;
    private static BudgetService budgetService;
    private static IncomeService incomeService;
    private static ReportService reportService;
    private static CategoryService categoryService;

    private static User testUser;

    @BeforeAll
    public static void setUp() throws Exception {
        userService = new UserService();
        expenseService = new ExpenseService();
        budgetService = new BudgetService();
        incomeService = new IncomeService();
        reportService = new ReportService();
        categoryService = new CategoryService();

        // Register a test user
        String email = "testuser_" + System.currentTimeMillis() + "@expensebook.com";
        testUser = userService.register("Suraj Sharma", email, "Password@123", "Password@123", FinancialMode.TRACK_AND_BUDGET);
        assertNotNull(testUser);
        assertTrue(testUser.getId() > 0);

        SessionManager.setCurrentUser(testUser);
    }

    @Test
    @Order(1)
    @DisplayName("Test Categories initialization and custom category creation")
    public void testCategories() throws Exception {
        List<Category> categories = categoryService.getCategoriesForUser(testUser.getId());
        assertFalse(categories.isEmpty(), "Default system categories should be present");

        // Add custom category
        Category custom = categoryService.addCustomCategory(testUser.getId(), "Books & Hobbies", "EDUCATION", "#C9B9E8");
        assertNotNull(custom);
        assertTrue(custom.getId() > 0);
        assertFalse(custom.isDefault());
    }

    @Test
    @Order(2)
    @DisplayName("Test Expense creation and real-time retrieval")
    public void testExpenseCreationAndRetrieval() throws Exception {
        List<Category> categories = categoryService.getCategoriesForUser(testUser.getId());
        int catFood = categories.get(0).getId();
        int catTravel = categories.get(1).getId();

        LocalDate today = LocalDate.now();

        // Create 2 expenses
        Expense e1 = expenseService.createExpense(testUser.getId(), catFood, 450.0, PaymentMode.UPI, today, "Lunch with team");
        Expense e2 = expenseService.createExpense(testUser.getId(), catTravel, 200.0, PaymentMode.CASH, today, "Metro card recharge");

        assertNotNull(e1);
        assertNotNull(e2);

        double totalMonth = expenseService.getTotalSpentMonth(testUser.getId(), today.getMonthValue(), today.getYear());
        assertTrue(totalMonth >= 650.0);

        double todaySpent = expenseService.getTodaySpent(testUser.getId());
        assertTrue(todaySpent >= 650.0);

        // Filter search
        List<Expense> filtered = expenseService.filterExpenses(testUser.getId(), null, null, null, null, "Lunch", "NEWEST");
        assertEquals(1, filtered.size());
        assertEquals("Lunch with team", filtered.get(0).getDescription());
    }

    @Test
    @Order(3)
    @DisplayName("Test Budget and Smart Projections")
    public void testBudgetAndSmartProjections() throws Exception {
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        int year = today.getYear();

        Budget budget = new Budget();
        budget.setUserId(testUser.getId());
        budget.setMonth(month);
        budget.setYear(year);
        budget.setTotalBudget(10000.0);

        Budget saved = budgetService.saveBudget(budget);
        assertNotNull(saved);

        BudgetService.SmartBudgetSummary summary = budgetService.getSmartBudgetSummary(testUser.getId(), month, year);
        assertEquals(10000.0, summary.monthlyBudget, 0.01);
        assertTrue(summary.totalSpent >= 650.0);
        assertTrue(summary.remainingBudget <= 9350.0);
        assertTrue(summary.recommendedDailySpending > 0);
        assertTrue(summary.projectedMonthEndSpending > 0);
        assertNotNull(summary.projectionInsight);
    }

    @Test
    @Order(4)
    @DisplayName("Test Income and Net Balance calculations")
    public void testIncomeAndNetBalance() throws Exception {
        LocalDate today = LocalDate.now();
        Income inc = incomeService.addIncome(testUser.getId(), "Salary", 25000.0, today, "Monthly stipend");
        assertNotNull(inc);

        double totalIncome = incomeService.getTotalIncomeForMonth(testUser.getId(), today.getMonthValue(), today.getYear());
        assertEquals(25000.0, totalIncome, 0.01);

        double netBalance = incomeService.calculateNetBalance(totalIncome, 650.0);
        assertEquals(24350.0, netBalance, 0.01);
    }

    @Test
    @Order(5)
    @DisplayName("Test PDF Report Generation")
    public void testPDFReportGeneration() throws Exception {
        LocalDate today = LocalDate.now();
        File tempPdf = File.createTempFile("ExpenseReportTest", ".pdf");
        tempPdf.deleteOnExit();

        reportService.generateMonthlyReportPDF(tempPdf, testUser.getId(), today.getMonthValue(), today.getYear());
        assertTrue(tempPdf.exists(), "PDF report file should be generated");
        assertTrue(tempPdf.length() > 1000, "PDF report should have content");
    }
}
