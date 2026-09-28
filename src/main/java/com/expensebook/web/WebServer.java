package com.expensebook.web;

import com.expensebook.model.*;
import com.expensebook.service.*;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DBConnection;
import com.expensebook.util.DateUtil;
import com.expensebook.util.SessionManager;
import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.Executors;

public class WebServer {

    private final int port;
    private HttpServer server;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>) (src, typeOfSrc, context) ->
                    new JsonPrimitive(src.toString()))
            .registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>) (json, typeOfT, context) ->
                    LocalDate.parse(json.getAsString()))
            .registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) ->
                    new JsonPrimitive(src.toString()))
            .registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>) (json, typeOfT, context) ->
                    LocalDateTime.parse(json.getAsString()))
            .create();

    private final UserService userService = new UserService();
    private final ExpenseService expenseService = new ExpenseService();
    private final CategoryService categoryService = new CategoryService();
    private final BudgetService budgetService = new BudgetService();
    private final IncomeService incomeService = new IncomeService();
    private final RecurringService recurringService = new RecurringService();
    private final AnalysisService analysisService = new AnalysisService();
    private final ReportService reportService = new ReportService();
    private final GeminiAiService geminiAiService = new GeminiAiService();
    private final UpiService upiService = new UpiService();

    // Map token -> User for web sessions
    private static final Map<String, User> activeSessions = new java.util.concurrent.ConcurrentHashMap<>();

    public WebServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        // Initialize Database
        DBConnection.getConnection();

        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(Executors.newFixedThreadPool(16));

        // Static Web UI Handler
        server.createContext("/", new StaticHandler());

        // API Contexts - Main ExpenseBook
        server.createContext("/api/auth/register", this::handleRegister);
        server.createContext("/api/auth/login", this::handleLogin);
        server.createContext("/api/auth/current", this::handleGetCurrentUser);
        server.createContext("/api/auth/logout", this::handleLogout);

        server.createContext("/api/dashboard", this::handleDashboard);
        server.createContext("/api/expenses", this::handleExpenses);
        server.createContext("/api/categories", this::handleCategories);
        server.createContext("/api/calendar", this::handleCalendar);
        server.createContext("/api/analytics", this::handleAnalytics);
        server.createContext("/api/budget", this::handleBudget);
        server.createContext("/api/income", this::handleIncome);
        server.createContext("/api/recurring", this::handleRecurring);
        server.createContext("/api/reports/pdf", this::handleReportPdf);
        server.createContext("/api/reports/csv", this::handleReportCsv);
        server.createContext("/api/settings", this::handleSettings);
        server.createContext("/api/admin", this::handleAdmin);
        server.createContext("/api/database", this::handleDatabase);
        server.createContext("/api/ai/chat", this::handleAiChat);
        server.createContext("/api/ai/status", this::handleAiStatus);

        // API Contexts - ExpenseBook UPI (STRICTLY SEPARATED)
        server.createContext("/api/upi/dashboard", this::handleUpiDashboard);
        server.createContext("/api/upi/transactions", this::handleUpiTransactions);
        server.createContext("/api/upi/webhook", this::handleUpiWebhook);
        server.createContext("/api/upi/parse-text", this::handleUpiParseText);
        server.createContext("/api/upi/simulate", this::handleUpiSimulate);
        server.createContext("/api/upi/import", this::handleUpiImport);
        server.createContext("/api/upi/status", this::handleUpiStatus);

        server.start();
        System.out.println("=================================================");
        System.out.println("🚀 ExPense Book Web Server started successfully!");
        System.out.println("🌐 Local Access:   http://localhost:" + port);
        System.out.println("📱 Mobile/Network: http://" + getLocalIpAddress() + ":" + port);
        System.out.println("=================================================");
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
        }
    }

    private String getLocalIpAddress() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    // --- Authentication Handlers ---

    private void handleRegister(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendMethodNotAllowed(ex);
            return;
        }
        try {
            JsonObject json = parseJsonBody(ex);
            String name = json.get("fullName").getAsString();
            String email = json.get("email").getAsString();
            String password = json.get("password").getAsString();
            String confirmPassword = json.get("confirmPassword").getAsString();
            String modeStr = json.has("mode") ? json.get("mode").getAsString() : "TRACK_ONLY";
            FinancialMode mode = FinancialMode.fromString(modeStr);

            User user = userService.register(name, email, password, confirmPassword, mode);
            String token = UUID.randomUUID().toString();
            activeSessions.put(token, user);
            SessionManager.setCurrentUser(user);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("token", token);
            resp.put("user", user);
            sendJsonResponse(ex, 200, resp);
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendMethodNotAllowed(ex);
            return;
        }
        try {
            JsonObject json = parseJsonBody(ex);
            String email = json.get("email").getAsString();
            String password = json.get("password").getAsString();

            User user = userService.login(email, password);
            String token = UUID.randomUUID().toString();
            activeSessions.put(token, user);
            SessionManager.setCurrentUser(user);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("token", token);
            resp.put("user", user);
            sendJsonResponse(ex, 200, resp);
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    private void handleGetCurrentUser(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Not logged in");
            return;
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("user", user);
        sendJsonResponse(ex, 200, resp);
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        String token = getToken(ex);
        if (token != null) activeSessions.remove(token);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        sendJsonResponse(ex, 200, resp);
    }

    // --- Dashboard Handler ---

    private void handleDashboard(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        Map<String, String> query = parseQuery(ex.getRequestURI().getQuery());
        int month = query.containsKey("month") ? Integer.parseInt(query.get("month")) : DateUtil.getCurrentMonth();
        int year = query.containsKey("year") ? Integer.parseInt(query.get("year")) : DateUtil.getCurrentYear();

        try {
            Map<String, Object> data = new HashMap<>();
            double totalSpent = expenseService.getTotalSpentMonth(user.getId(), month, year);
            double todaySpent = expenseService.getTodaySpent(user.getId());
            double dailyAvg = analysisService.calculateDailyAverage(user.getId(), month, year);
            int daysLeft = DateUtil.getDaysRemainingInMonth(month, year);

            data.put("totalSpent", totalSpent);
            data.put("todaySpent", todaySpent);
            data.put("dailyAvg", dailyAvg);
            data.put("daysLeft", daysLeft);
            data.put("currency", user.getCurrency());
            data.put("mode", user.getFinancialMode().name());
            data.put("monthYearTitle", DateUtil.formatMonthYear(month, year));

            // Budget Info
            boolean isBudget = user.isBudgetMode();
            data.put("isBudgetMode", isBudget);
            if (isBudget) {
                BudgetService.SmartBudgetSummary summary = budgetService.getSmartBudgetSummary(user.getId(), month, year);
                data.put("budgetSummary", summary);
            }

            // Categories Breakdown
            LocalDate start = DateUtil.getStartOfMonth(month, year);
            LocalDate end = DateUtil.getEndOfMonth(month, year);
            Map<Category, Double> catMap = expenseService.getCategoryBreakdown(user.getId(), start, end);
            List<Map<String, Object>> catList = new ArrayList<>();
            for (Map.Entry<Category, Double> entry : catMap.entrySet()) {
                Map<String, Object> item = new HashMap<>();
                item.put("category", entry.getKey());
                item.put("amount", entry.getValue());
                double pct = totalSpent > 0 ? (entry.getValue() / totalSpent) * 100.0 : 0;
                item.put("percentage", pct);
                catList.add(item);
            }
            data.put("categoriesBreakdown", catList);

            // Daily Spending Trend
            Map<Integer, Double> daily = expenseService.getDailySpending(user.getId(), month, year);
            data.put("dailySpending", daily);

            // Recent Transactions
            List<Expense> recent = expenseService.getRecentExpenses(user.getId(), 5);
            data.put("recentTransactions", recent);

            sendJsonResponse(ex, 200, data);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, e.getMessage());
        }
    }

    // --- Expenses CRUD & Filter ---

    private void handleExpenses(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        String method = ex.getRequestMethod().toUpperCase();
        String path = ex.getRequestURI().getPath();

        try {
            if ("GET".equals(method)) {
                Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                LocalDate start = q.containsKey("startDate") ? LocalDate.parse(q.get("startDate")) : null;
                LocalDate end = q.containsKey("endDate") ? LocalDate.parse(q.get("endDate")) : null;
                Integer catId = q.containsKey("categoryId") && !q.get("categoryId").isEmpty() ? Integer.parseInt(q.get("categoryId")) : null;
                String payment = q.get("paymentMode");
                String search = q.get("search");
                String sort = q.get("sortBy");

                List<Expense> list = expenseService.filterExpenses(user.getId(), start, end, catId, payment, search, sort);
                double total = 0;
                for (Expense e : list) total += e.getAmount();

                Map<String, Object> resp = new HashMap<>();
                resp.put("expenses", list);
                resp.put("total", total);
                resp.put("count", list.size());
                sendJsonResponse(ex, 200, resp);

            } else if ("POST".equals(method)) {
                JsonObject json = parseJsonBody(ex);
                double amount = json.get("amount").getAsDouble();
                int categoryId = json.get("categoryId").getAsInt();
                String pmStr = json.get("paymentMode").getAsString();
                PaymentMode pm = PaymentMode.fromString(pmStr);
                LocalDate date = LocalDate.parse(json.get("expenseDate").getAsString());
                String desc = json.has("description") ? json.get("description").getAsString().trim() : "";
                if (desc.isEmpty() || "Other".equalsIgnoreCase(desc)) {
                    try {
                        Category cat = categoryService.getCategoryById(categoryId);
                        if (cat != null && cat.getName() != null && !cat.getName().trim().isEmpty()) {
                            desc = cat.getName().trim();
                        }
                    } catch (Exception ignored) {}
                }

                Expense created = expenseService.createExpense(user.getId(), categoryId, amount, pm, date, desc);
                sendJsonResponse(ex, 201, created);

            } else if ("PUT".equals(method)) {
                JsonObject json = parseJsonBody(ex);
                int id = json.get("id").getAsInt();
                double amount = json.get("amount").getAsDouble();
                int categoryId = json.get("categoryId").getAsInt();
                String pmStr = json.get("paymentMode").getAsString();
                PaymentMode pm = PaymentMode.fromString(pmStr);
                LocalDate date = LocalDate.parse(json.get("expenseDate").getAsString());
                String desc = json.has("description") ? json.get("description").getAsString().trim() : "";
                if (desc.isEmpty() || "Other".equalsIgnoreCase(desc)) {
                    try {
                        Category cat = categoryService.getCategoryById(categoryId);
                        if (cat != null && cat.getName() != null && !cat.getName().trim().isEmpty()) {
                            desc = cat.getName().trim();
                        }
                    } catch (Exception ignored) {}
                }

                Expense exp = new Expense();
                exp.setId(id);
                exp.setUserId(user.getId());
                exp.setCategoryId(categoryId);
                exp.setAmount(amount);
                exp.setPaymentMode(pm);
                exp.setExpenseDate(date);
                exp.setDescription(desc);

                expenseService.updateExpense(exp);
                sendJsonResponse(ex, 200, Map.of("success", true));

            } else if ("DELETE".equals(method)) {
                Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                int id = Integer.parseInt(q.get("id"));
                expenseService.deleteExpense(id, user.getId());
                sendJsonResponse(ex, 200, Map.of("success", true));
            }
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    // --- Categories Handler ---

    private void handleCategories(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        String method = ex.getRequestMethod().toUpperCase();
        try {
            if ("GET".equals(method)) {
                List<Category> list = categoryService.getCategoriesForUser(user.getId());
                sendJsonResponse(ex, 200, list);
            } else if ("POST".equals(method)) {
                JsonObject json = parseJsonBody(ex);
                String name = json.get("name").getAsString();
                String icon = json.has("iconName") ? json.get("iconName").getAsString() : "OTHERS";
                String color = json.has("color") ? json.get("color").getAsString() : "#78BFA0";
                Category created = categoryService.addCustomCategory(user.getId(), name, icon, color);
                sendJsonResponse(ex, 201, created);
            } else if ("DELETE".equals(method)) {
                Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                int id = Integer.parseInt(q.get("id"));
                categoryService.deleteCategory(id, user.getId());
                sendJsonResponse(ex, 200, Map.of("success", true));
            }
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    // --- Calendar Handler ---

    private void handleCalendar(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        int month = q.containsKey("month") ? Integer.parseInt(q.get("month")) : DateUtil.getCurrentMonth();
        int year = q.containsKey("year") ? Integer.parseInt(q.get("year")) : DateUtil.getCurrentYear();

        try {
            Map<Integer, Double> daily = expenseService.getDailySpending(user.getId(), month, year);
            Map<String, Object> resp = new HashMap<>();
            resp.put("dailySpending", daily);
            resp.put("month", month);
            resp.put("year", year);
            resp.put("monthYearTitle", DateUtil.formatMonthYear(month, year));

            if (q.containsKey("selectedDate")) {
                LocalDate selDate = LocalDate.parse(q.get("selectedDate"));
                List<Expense> expenses = expenseService.getExpensesForDate(user.getId(), selDate);
                resp.put("selectedDateExpenses", expenses);
            }

            sendJsonResponse(ex, 200, resp);
        } catch (Exception e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // --- Analytics Handler ---

    private void handleAnalytics(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        int month = q.containsKey("month") ? Integer.parseInt(q.get("month")) : DateUtil.getCurrentMonth();
        int year = q.containsKey("year") ? Integer.parseInt(q.get("year")) : DateUtil.getCurrentYear();

        LocalDate start = DateUtil.getStartOfMonth(month, year);
        LocalDate end = DateUtil.getEndOfMonth(month, year);

        try {
            Map<String, Object> resp = new HashMap<>();
            double totalSpent = expenseService.getTotalSpentMonth(user.getId(), month, year);
            double dailyAvg = analysisService.calculateDailyAverage(user.getId(), month, year);
            resp.put("totalSpent", totalSpent);
            resp.put("dailyAverage", dailyAvg);

            // Category breakdown
            Map<Category, Double> catMap = expenseService.getCategoryBreakdown(user.getId(), start, end);
            List<Map<String, Object>> catList = new ArrayList<>();
            for (Map.Entry<Category, Double> entry : catMap.entrySet()) {
                Map<String, Object> item = new HashMap<>();
                item.put("categoryName", entry.getKey().getName());
                item.put("color", entry.getKey().getColor());
                item.put("amount", entry.getValue());
                catList.add(item);
            }
            resp.put("categoryAnalysis", catList);

            // Payment breakdown
            Map<String, Double> payMap = expenseService.getPaymentBreakdown(user.getId(), start, end);
            resp.put("paymentAnalysis", payMap);

            // Daily trend
            Map<Integer, Double> daily = expenseService.getDailySpending(user.getId(), month, year);
            resp.put("dailyTrend", daily);

            // Month Comparison
            AnalysisService.MonthComparison cmp = analysisService.compareWithPreviousMonth(user.getId(), month, year);
            resp.put("monthComparison", cmp);

            // Yearly Analysis
            AnalysisService.YearlyAnalysisResult yearly = analysisService.getYearlyAnalysis(user.getId(), year);
            resp.put("yearlyAnalysis", yearly);

            sendJsonResponse(ex, 200, resp);
        } catch (Exception e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // --- Budget Handler ---

    private void handleBudget(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        String method = ex.getRequestMethod().toUpperCase();
        try {
            if ("GET".equals(method)) {
                Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                int month = q.containsKey("month") ? Integer.parseInt(q.get("month")) : DateUtil.getCurrentMonth();
                int year = q.containsKey("year") ? Integer.parseInt(q.get("year")) : DateUtil.getCurrentYear();

                BudgetService.SmartBudgetSummary summary = budgetService.getSmartBudgetSummary(user.getId(), month, year);
                Budget budget = budgetService.getBudgetForMonth(user.getId(), month, year);

                Map<String, Object> resp = new HashMap<>();
                resp.put("summary", summary);
                resp.put("budget", budget);
                sendJsonResponse(ex, 200, resp);

            } else if ("POST".equals(method)) {
                JsonObject json = parseJsonBody(ex);
                int month = json.get("month").getAsInt();
                int year = json.get("year").getAsInt();
                double total = json.get("totalBudget").getAsDouble();

                Budget b = budgetService.getBudgetForMonth(user.getId(), month, year);
                if (b == null) {
                    b = new Budget();
                    b.setUserId(user.getId());
                    b.setMonth(month);
                    b.setYear(year);
                }
                b.setTotalBudget(total);

                if (json.has("categoryBudgets")) {
                    List<CategoryBudget> cbList = new ArrayList<>();
                    json.getAsJsonArray("categoryBudgets").forEach(elem -> {
                        JsonObject obj = elem.getAsJsonObject();
                        CategoryBudget cb = new CategoryBudget();
                        cb.setCategoryId(obj.get("categoryId").getAsInt());
                        cb.setAmount(obj.get("amount").getAsDouble());
                        cbList.add(cb);
                    });
                    b.setCategoryBudgets(cbList);
                }

                budgetService.saveBudget(b);
                sendJsonResponse(ex, 200, Map.of("success", true));
            }
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    // --- Income Handler ---

    private void handleIncome(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        String method = ex.getRequestMethod().toUpperCase();
        try {
            if ("GET".equals(method)) {
                Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                int month = q.containsKey("month") ? Integer.parseInt(q.get("month")) : DateUtil.getCurrentMonth();
                int year = q.containsKey("year") ? Integer.parseInt(q.get("year")) : DateUtil.getCurrentYear();

                List<Income> list = incomeService.getIncomeForMonth(user.getId(), month, year);
                double totalIncome = incomeService.getTotalIncomeForMonth(user.getId(), month, year);
                double totalExpenses = expenseService.getTotalSpentMonth(user.getId(), month, year);
                double balance = incomeService.calculateNetBalance(totalIncome, totalExpenses);

                Map<String, Object> resp = new HashMap<>();
                resp.put("incomeList", list);
                resp.put("totalIncome", totalIncome);
                resp.put("totalExpenses", totalExpenses);
                resp.put("netBalance", balance);
                sendJsonResponse(ex, 200, resp);

            } else if ("POST".equals(method)) {
                JsonObject json = parseJsonBody(ex);
                double amount = json.get("amount").getAsDouble();
                String source = json.get("source").getAsString();
                LocalDate date = LocalDate.parse(json.get("incomeDate").getAsString());
                String desc = json.has("description") ? json.get("description").getAsString() : "";

                Income created = incomeService.addIncome(user.getId(), source, amount, date, desc);
                sendJsonResponse(ex, 201, created);

            } else if ("DELETE".equals(method)) {
                Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                int id = Integer.parseInt(q.get("id"));
                incomeService.deleteIncome(id, user.getId());
                sendJsonResponse(ex, 200, Map.of("success", true));
            }
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    // --- Recurring Handler ---

    private void handleRecurring(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        String method = ex.getRequestMethod().toUpperCase();
        try {
            if ("GET".equals(method)) {
                List<RecurringExpense> list = recurringService.getRecurringList(user.getId());
                sendJsonResponse(ex, 200, list);
            } else if ("POST".equals(method)) {
                JsonObject json = parseJsonBody(ex);
                if (json.has("action") && "logNow".equals(json.get("action").getAsString())) {
                    int id = json.get("id").getAsInt();
                    List<RecurringExpense> list = recurringService.getRecurringList(user.getId());
                    RecurringExpense target = list.stream().filter(r -> r.getId() == id).findFirst().orElse(null);
                    if (target != null) {
                        Expense exp = recurringService.processDueRecurringExpense(target, expenseService);
                        sendJsonResponse(ex, 200, Map.of("success", true, "expense", exp));
                    } else {
                        sendError(ex, 404, "Recurring expense not found");
                    }
                    return;
                }

                String title = json.get("title").getAsString();
                int categoryId = json.get("categoryId").getAsInt();
                double amount = json.get("amount").getAsDouble();
                String freq = json.get("frequency").getAsString();
                PaymentMode pm = PaymentMode.fromString(json.get("paymentMode").getAsString());
                LocalDate nextDate = LocalDate.parse(json.get("nextDueDate").getAsString());

                RecurringExpense created = recurringService.addRecurring(user.getId(), title, categoryId, amount, freq, pm, nextDate);
                sendJsonResponse(ex, 201, created);

            } else if ("DELETE".equals(method)) {
                Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                int id = Integer.parseInt(q.get("id"));
                recurringService.deleteRecurring(id, user.getId());
                sendJsonResponse(ex, 200, Map.of("success", true));
            }
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    // --- Reports PDF & CSV Download Handlers ---

    private void handleReportPdf(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        int month = q.containsKey("month") ? Integer.parseInt(q.get("month")) : DateUtil.getCurrentMonth();
        int year = q.containsKey("year") ? Integer.parseInt(q.get("year")) : DateUtil.getCurrentYear();

        try {
            File tempPdf = File.createTempFile("ExpenseBook_Report_", ".pdf");
            tempPdf.deleteOnExit();

            reportService.generateMonthlyReportPDF(tempPdf, user.getId(), month, year);

            byte[] pdfBytes = java.nio.file.Files.readAllBytes(tempPdf.toPath());
            ex.getResponseHeaders().set("Content-Type", "application/pdf");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"ExpenseBook_Report_" + month + "_" + year + ".pdf\"");
            ex.sendResponseHeaders(200, pdfBytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(pdfBytes);
            }
        } catch (Exception e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    private void handleReportCsv(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        int month = q.containsKey("month") ? Integer.parseInt(q.get("month")) : DateUtil.getCurrentMonth();
        int year = q.containsKey("year") ? Integer.parseInt(q.get("year")) : DateUtil.getCurrentYear();

        try {
            LocalDate start = DateUtil.getStartOfMonth(month, year);
            LocalDate end = DateUtil.getEndOfMonth(month, year);
            List<Expense> expenses = expenseService.filterExpenses(user.getId(), start, end, null, null, null, "NEWEST");

            File tempCsv = File.createTempFile("ExpenseBook_CSV_", ".csv");
            tempCsv.deleteOnExit();
            reportService.exportExpensesToCSV(tempCsv, expenses);

            byte[] bytes = java.nio.file.Files.readAllBytes(tempCsv.toPath());
            ex.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"ExpenseBook_Transactions_" + month + "_" + year + ".csv\"");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // --- Settings Handler ---

    private void handleSettings(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        try {
            JsonObject json = parseJsonBody(ex);
            String action = json.get("action").getAsString();

            if ("updateProfile".equals(action)) {
                String name = json.get("fullName").getAsString();
                String currency = json.get("currency").getAsString();
                userService.updateProfile(user.getId(), name, currency);
                user.setFullName(name);
                user.setCurrency(currency);
                sendJsonResponse(ex, 200, Map.of("success", true, "user", user));

            } else if ("changePassword".equals(action)) {
                String curPass = json.get("currentPassword").getAsString();
                String newPass = json.get("newPassword").getAsString();
                String confPass = json.get("confirmPassword").getAsString();
                userService.changePassword(user.getId(), curPass, newPass, confPass);
                sendJsonResponse(ex, 200, Map.of("success", true));

            } else if ("switchMode".equals(action)) {
                String modeStr = json.get("mode").getAsString();
                FinancialMode newMode = FinancialMode.fromString(modeStr);
                userService.switchFinancialMode(user.getId(), newMode);
                user.setFinancialMode(newMode);
                sendJsonResponse(ex, 200, Map.of("success", true, "user", user));
            }
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    // --- Admin Handler ---

    private void handleAdmin(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null || !user.isAdmin()) {
            sendError(ex, 403, "Access denied. Administrator privileges required.");
            return;
        }
        String method = ex.getRequestMethod().toUpperCase();
        try {
            if ("GET".equals(method)) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("totalUsers", userService.getTotalUsers());
                resp.put("activeUsers", userService.getActiveUsers());
                resp.put("systemCategoriesCount", categoryService.getSystemDefaultCategories().size());
                resp.put("dbStatus", DBConnection.getDatabaseStatusText());
                resp.put("users", userService.listAllUsers());
                resp.put("categories", categoryService.getSystemDefaultCategories());

                String activeKey = geminiAiService.resolveApiKey(null);
                resp.put("geminiConfigured", activeKey != null && !activeKey.isEmpty());
                resp.put("geminiMaskedKey", maskApiKey(activeKey));
                sendJsonResponse(ex, 200, resp);

            } else if ("POST".equals(method)) {
                JsonObject json = parseJsonBody(ex);
                String action = json.get("action").getAsString();

                if ("toggleStatus".equals(action)) {
                    int targetId = json.get("userId").getAsInt();
                    boolean active = json.get("active").getAsBoolean();
                    userService.toggleUserStatus(targetId, active);
                    sendJsonResponse(ex, 200, Map.of("success", true));
                } else if ("deleteUser".equals(action)) {
                    int targetId = json.get("userId").getAsInt();
                    userService.deleteUser(targetId);
                    sendJsonResponse(ex, 200, Map.of("success", true));
                } else if ("saveAiKey".equals(action)) {
                    String apiKey = json.has("apiKey") && !json.get("apiKey").isJsonNull() ? json.get("apiKey").getAsString().trim() : "";
                    saveGeminiApiKey(apiKey);
                    sendJsonResponse(ex, 200, Map.of("success", true, "configured", !apiKey.isEmpty(), "maskedKey", maskApiKey(apiKey)));
                } else if ("testAiKey".equals(action)) {
                    String apiKey = json.has("apiKey") && !json.get("apiKey").isJsonNull() ? json.get("apiKey").getAsString().trim() : null;
                    if (apiKey == null || apiKey.isEmpty()) {
                        apiKey = geminiAiService.resolveApiKey(null);
                    }
                    Map<String, Object> testResult = testGeminiApiKey(apiKey);
                    sendJsonResponse(ex, 200, testResult);
                }
            }
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    private void saveGeminiApiKey(String apiKey) {
        Properties props = new Properties();
        File propFile = new File("db.properties");
        if (propFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propFile)) {
                props.load(fis);
            } catch (Exception ignored) {}
        }
        if (apiKey != null && !apiKey.isEmpty()) {
            props.setProperty("gemini.api.key", apiKey);
            System.setProperty("gemini.api.key", apiKey);
        } else {
            props.remove("gemini.api.key");
            System.clearProperty("gemini.api.key");
        }
        try (FileOutputStream fos = new FileOutputStream(propFile)) {
            props.store(fos, "ExPense Book DB Configuration");
        } catch (Exception e) {
            System.err.println("Failed to save gemini.api.key: " + e.getMessage());
        }
    }

    private String maskApiKey(String key) {
        if (key == null || key.trim().isEmpty()) return "";
        String trimmed = key.trim();
        if (trimmed.length() <= 8) return "********";
        return trimmed.substring(0, 6) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private Map<String, Object> testGeminiApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Map.of("success", false, "error", "No Gemini API key entered or configured on server.");
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey.trim()))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                int count = 0;
                List<String> sample = new ArrayList<>();
                if (root.has("models") && root.get("models").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("models")) {
                        JsonObject m = el.getAsJsonObject();
                        if (m.has("name")) {
                            String name = m.get("name").getAsString();
                            if (name.startsWith("models/")) name = name.substring(7);
                            if (name.contains("flash") || name.contains("pro")) {
                                sample.add(name);
                            }
                        }
                        count++;
                    }
                }
                Map<String, Object> res = new HashMap<>();
                res.put("success", true);
                res.put("modelsCount", count);
                res.put("sampleModels", sample.stream().limit(4).toList());
                return res;
            } else {
                String errDetail = resp.body();
                try {
                    JsonObject errObj = JsonParser.parseString(errDetail).getAsJsonObject();
                    if (errObj.has("error") && errObj.getAsJsonObject("error").has("message")) {
                        errDetail = errObj.getAsJsonObject("error").get("message").getAsString();
                    }
                } catch (Exception ignored) {}
                return Map.of("success", false, "error", "HTTP " + resp.statusCode() + ": " + errDetail);
            }
        } catch (Exception e) {
            return Map.of("success", false, "error", "Connection error: " + e.getMessage());
        }
    }

    // --- Database Configuration Handler ---

    private void handleDatabase(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod().toUpperCase();
        try {
            if ("GET".equals(method)) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("isMySQL", DBConnection.isMySQL());
                resp.put("dbType", DBConnection.getDbType());
                resp.put("statusText", DBConnection.getDatabaseStatusText());
                sendJsonResponse(ex, 200, resp);
            } else if ("POST".equals(method)) {
                JsonObject json = parseJsonBody(ex);
                String action = json.has("action") ? json.get("action").getAsString() : "test";
                String dbType = json.has("dbType") ? json.get("dbType").getAsString() : "sqlite";
                String host = json.has("host") ? json.get("host").getAsString() : "localhost";
                int port = json.has("port") ? json.get("port").getAsInt() : 3306;
                String database = json.has("database") ? json.get("database").getAsString() : "expensebook";
                String user = json.has("user") ? json.get("user").getAsString() : "root";
                String password = json.has("password") ? json.get("password").getAsString() : "";

                if ("test".equals(action)) {
                    boolean ok = DBConnection.testConnection(host, port, database, user, password);
                    sendJsonResponse(ex, 200, Map.of("success", true, "connected", ok));
                } else if ("save".equals(action)) {
                    DBConnection.saveProperties(dbType, host, port, database, user, password);
                    DBConnection.getConnection(); // Refresh
                    sendJsonResponse(ex, 200, Map.of(
                            "success", true,
                            "isMySQL", DBConnection.isMySQL(),
                            "dbType", DBConnection.getDbType(),
                            "statusText", DBConnection.getDatabaseStatusText()
                    ));
                }
            }
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    // --- Helper Utilities ---

    private User authenticate(HttpExchange ex) {
        String token = getToken(ex);
        if (token != null && activeSessions.containsKey(token)) {
            return activeSessions.get(token);
        }
        // Fallback to currently logged in session in SessionManager
        return SessionManager.getCurrentUser();
    }

    private String getToken(HttpExchange ex) {
        String authHeader = ex.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) {
            for (String pair : cookie.split(";")) {
                String[] kv = pair.trim().split("=");
                if (kv.length == 2 && "token".equalsIgnoreCase(kv[0])) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private JsonObject parseJsonBody(HttpExchange ex) throws IOException {
        InputStream is = ex.getRequestBody();
        String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2) {
                map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private void sendJsonResponse(HttpExchange ex, int statusCode, Object data) throws IOException {
        String json = gson.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange ex, int statusCode, String message) throws IOException {
        Map<String, Object> err = new HashMap<>();
        err.put("success", false);
        err.put("error", message);
        sendJsonResponse(ex, statusCode, err);
    }

    private void sendMethodNotAllowed(HttpExchange ex) throws IOException {
        sendError(ex, 405, "Method not allowed");
    }

    // --- AI Assistant Handlers ---

    private void handleAiStatus(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendMethodNotAllowed(ex);
            return;
        }
        String key = geminiAiService.resolveApiKey(null);
        boolean configured = key != null && !key.isEmpty();
        Map<String, Object> resp = new HashMap<>();
        resp.put("configured", configured);
        resp.put("model", "gemini-2.5-flash");
        sendJsonResponse(ex, 200, resp);
    }

    private void handleAiChat(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendMethodNotAllowed(ex);
            return;
        }

        try {
            JsonObject body = parseJsonBody(ex);
            if (!body.has("message") || body.get("message").getAsString().trim().isEmpty()) {
                sendError(ex, 400, "Message cannot be empty.");
                return;
            }
            String message = body.get("message").getAsString().trim();
            String apiKey = body.has("apiKey") && !body.get("apiKey").isJsonNull() ? body.get("apiKey").getAsString().trim() : null;

            List<GeminiAiService.ChatMessage> historyList = new ArrayList<>();
            if (body.has("history") && !body.get("history").isJsonNull() && body.get("history").isJsonArray()) {
                body.getAsJsonArray("history").forEach(el -> {
                    if (el.isJsonObject()) {
                        JsonObject o = el.getAsJsonObject();
                        String role = o.has("role") && !o.get("role").isJsonNull() ? o.get("role").getAsString() : "user";
                        String content = o.has("content") && !o.get("content").isJsonNull() ? o.get("content").getAsString() : "";
                        historyList.add(new GeminiAiService.ChatMessage(role, content));
                    }
                });
            }

            GeminiAiService.AiResponse response = geminiAiService.chat(user, message, historyList, apiKey);
            sendJsonResponse(ex, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "AI processing failed: " + e.getMessage());
        }
    }

    // =========================================================
    // ExpenseBook UPI Handlers (STRICTLY SEPARATED FROM MAIN EXPENSEBOOK)
    // =========================================================

    private void handleUpiDashboard(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendMethodNotAllowed(ex);
            return;
        }

        try {
            Map<String, String> query = parseQuery(ex.getRequestURI().getQuery());
            int month = query.containsKey("month") ? Integer.parseInt(query.get("month")) : DateUtil.getCurrentMonth();
            int year = query.containsKey("year") ? Integer.parseInt(query.get("year")) : DateUtil.getCurrentYear();

            LocalDate start = DateUtil.getStartOfMonth(month, year);
            LocalDate end = DateUtil.getEndOfMonth(month, year);

            Map<String, Object> stats = upiService.getUpiDashboard(user.getId(), start, end);
            stats.put("currency", user.getCurrency());
            stats.put("monthYearTitle", DateUtil.formatMonthYear(month, year));
            sendJsonResponse(ex, 200, stats);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Failed to load UPI dashboard: " + e.getMessage());
        }
    }

    private void handleUpiTransactions(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }

        String method = ex.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method)) {
                List<UpiTransaction> list = upiService.getUpiTransactions(user.getId(), 100);
                Map<String, Object> resp = new HashMap<>();
                resp.put("transactions", list);
                resp.put("currency", user.getCurrency());
                sendJsonResponse(ex, 200, resp);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                Map<String, String> query = parseQuery(ex.getRequestURI().getQuery());
                if (!query.containsKey("id")) {
                    sendError(ex, 400, "Missing UPI transaction ID.");
                    return;
                }
                int upiId = Integer.parseInt(query.get("id"));
                boolean deleted = upiService.deleteUpiTransaction(upiId, user.getId());
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", deleted);
                sendJsonResponse(ex, 200, resp);
            } else {
                sendMethodNotAllowed(ex);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Failed to handle UPI transactions: " + e.getMessage());
        }
    }

    private void handleUpiImport(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendMethodNotAllowed(ex);
            return;
        }

        try {
            JsonObject body = parseJsonBody(ex);
            if (!body.has("upiTransactionId")) {
                sendError(ex, 400, "Missing upiTransactionId to import.");
                return;
            }
            int upiId = body.get("upiTransactionId").getAsInt();
            Expense created = upiService.importToExpenseBook(user, upiId);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Successfully imported to ExpenseBook!");
            resp.put("expense", created);
            sendJsonResponse(ex, 200, resp);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 400, e.getMessage());
        }
    }

    private void handleUpiWebhook(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (!"POST".equalsIgnoreCase(method) && !"GET".equalsIgnoreCase(method)) {
            sendMethodNotAllowed(ex);
            return;
        }

        try {
            Map<String, String> queryParams = parseQuery(ex.getRequestURI().getQuery());
            String token = queryParams.get("token");
            String rawText = queryParams.get("text");
            if (rawText == null) rawText = queryParams.get("msg");
            if (rawText == null) rawText = queryParams.get("body");
            if (rawText == null) rawText = queryParams.get("sms");
            String sourceHint = queryParams.get("source");

            JsonObject body = null;
            if ("POST".equalsIgnoreCase(method)) {
                try {
                    body = parseJsonBody(ex);
                    if (body != null) {
                        if (token == null && body.has("token")) token = body.get("token").getAsString();
                        if (rawText == null && body.has("text")) rawText = body.get("text").getAsString();
                        if (rawText == null && body.has("message")) rawText = body.get("message").getAsString();
                        if (rawText == null && body.has("body")) rawText = body.get("body").getAsString();
                        if (sourceHint == null && body.has("source")) sourceHint = body.get("source").getAsString();
                    }
                } catch (Exception ignored) {}
            }

            User user = authenticate(ex);
            if (user == null && token != null) {
                Integer userId = upiService.getUserIdForWebhookToken(token);
                if (userId != null) {
                    user = userService.getUserById(userId);
                }
            }

            if (user == null) {
                sendError(ex, 401, "Unauthorized: Valid session or webhook ?token= parameter required.");
                return;
            }

            if (rawText == null || rawText.trim().isEmpty()) {
                sendError(ex, 400, "Missing transaction text or SMS message payload.");
                return;
            }

            UpiService.UpiProcessResult result = upiService.processUpiMessage(user, rawText, sourceHint);
            sendJsonResponse(ex, 200, result);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "UPI webhook processing failed: " + e.getMessage());
        }
    }

    private void handleUpiParseText(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendMethodNotAllowed(ex);
            return;
        }

        try {
            JsonObject body = parseJsonBody(ex);
            if (!body.has("text") || body.get("text").getAsString().trim().isEmpty()) {
                sendError(ex, 400, "Text to parse cannot be empty.");
                return;
            }

            String text = body.get("text").getAsString().trim();
            String source = body.has("source") ? body.get("source").getAsString() : null;

            UpiService.UpiProcessResult result = upiService.processUpiMessage(user, text, source);
            sendJsonResponse(ex, 200, result);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Failed to parse UPI text: " + e.getMessage());
        }
    }

    private void handleUpiSimulate(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendMethodNotAllowed(ex);
            return;
        }

        try {
            JsonObject body = parseJsonBody(ex);
            String app = body.has("app") ? body.get("app").getAsString() : "PhonePe";
            double amount = body.has("amount") ? body.get("amount").getAsDouble() : 350.0;
            String merchant = body.has("merchant") ? body.get("merchant").getAsString() : "Swiggy";
            String category = body.has("category") ? body.get("category").getAsString() : "Food";

            UpiService.UpiProcessResult result = upiService.simulatePayment(user, app, amount, merchant, category);
            sendJsonResponse(ex, 200, result);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Simulation failed: " + e.getMessage());
        }
    }

    private void handleUpiStatus(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) {
            sendError(ex, 401, "Unauthorized");
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendMethodNotAllowed(ex);
            return;
        }

        try {
            String token = upiService.getWebhookTokenForUser(user);
            String localIp = getLocalIpAddress();
            String webhookUrl = "http://" + localIp + ":" + port + "/api/upi/webhook?token=" + token;

            Map<String, Object> resp = new HashMap<>();
            resp.put("webhookToken", token);
            resp.put("webhookUrl", webhookUrl);
            resp.put("supportedApps", List.of(
                    "PhonePe", "Google Pay", "Paytm", "CRED", "BHIM",
                    "HDFC Bank", "State Bank of India (SBI)", "ICICI Bank", "Axis Bank", "Kotak Mahindra Bank"
            ));

            sendJsonResponse(ex, 200, resp);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Failed to retrieve UPI status: " + e.getMessage());
        }
    }

    // Static Web UI File Handler
    private static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }

            String path = ex.getRequestURI().getPath();
            if ("/".equals(path) || path.isEmpty()) {
                path = "/index.html";
            }

            byte[] bytes = null;
            java.io.File localFile = new java.io.File("src/main/resources/web" + path);
            if (localFile.exists() && localFile.isFile()) {
                bytes = java.nio.file.Files.readAllBytes(localFile.toPath());
            } else {
                String resourcePath = "/web" + path;
                try (InputStream is = WebServer.class.getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        bytes = is.readAllBytes();
                    }
                }
            }

            if (bytes == null) {
                // Fallback to index.html for SPA routes
                java.io.File localIndex = new java.io.File("src/main/resources/web/index.html");
                if (localIndex.exists() && localIndex.isFile()) {
                    bytes = java.nio.file.Files.readAllBytes(localIndex.toPath());
                } else {
                    try (InputStream fallbackIs = WebServer.class.getResourceAsStream("/web/index.html")) {
                        if (fallbackIs != null) {
                            bytes = fallbackIs.readAllBytes();
                        }
                    }
                }
                if (bytes == null) {
                    String notFound = "404 Not Found";
                    ex.sendResponseHeaders(404, notFound.length());
                    ex.getResponseBody().write(notFound.getBytes());
                    ex.close();
                    return;
                }
                path = "/index.html";
            }

            String mimeType = "text/plain";
            if (path.endsWith(".html")) mimeType = "text/html; charset=utf-8";
            else if (path.endsWith(".css")) mimeType = "text/css; charset=utf-8";
            else if (path.endsWith(".js")) mimeType = "application/javascript; charset=utf-8";
            else if (path.endsWith(".json")) mimeType = "application/json; charset=utf-8";
            else if (path.endsWith(".png")) mimeType = "image/png";
            else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) mimeType = "image/jpeg";
            else if (path.endsWith(".svg")) mimeType = "image/svg+xml";
            else if (path.endsWith(".webp")) mimeType = "image/webp";
            else if (path.endsWith(".ico")) mimeType = "image/x-icon";
            else if (path.endsWith(".woff2")) mimeType = "font/woff2";
            else if (path.endsWith(".woff")) mimeType = "font/woff";

            ex.getResponseHeaders().set("Content-Type", mimeType);
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");

            // Cache static assets, but don't cache index.html
            if (path.endsWith(".html")) {
                ex.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            } else {
                ex.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
            }

            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
