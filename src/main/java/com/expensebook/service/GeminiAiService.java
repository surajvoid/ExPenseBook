package com.expensebook.service;

import com.expensebook.model.Category;
import com.expensebook.model.Expense;
import com.expensebook.model.User;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DateUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GeminiAiService {

    // Candidate models to try in sequence if dynamic discovery is unavailable
    private static final List<String> FALLBACK_CANDIDATE_MODELS = List.of(
            "gemini-2.0-flash",
            "gemini-2.0-flash-exp",
            "gemini-1.5-flash-latest",
            "gemini-1.5-flash",
            "gemini-1.5-flash-001",
            "gemini-1.5-flash-002",
            "gemini-2.5-flash",
            "gemini-1.5-pro",
            "gemini-pro"
    );

    // Caches to avoid redundant network discovery calls
    private static final Map<String, List<String>> discoveredModelsCache = new ConcurrentHashMap<>();
    private static final Map<String, String> workingModelCache = new ConcurrentHashMap<>();

    private final ExpenseService expenseService = new ExpenseService();
    private final BudgetService budgetService = new BudgetService();
    private final IncomeService incomeService = new IncomeService();
    private final AnalysisService analysisService = new AnalysisService();

    private final HttpClient httpClient;

    public GeminiAiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public static class ChatMessage {
        public String role; // "user" or "model" / "assistant"
        public String content;

        public ChatMessage() {}

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static class AiResponse {
        public String reply;
        public String source; // "gemini" or "rule-based"
        public String model;
        public String timestamp;

        public AiResponse(String reply, String source, String model) {
            this.reply = reply;
            this.source = source;
            this.model = model;
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
        }
    }

    /**
     * Resolves the active Gemini API key from user input, environment variable, or config file.
     */
    public String resolveApiKey(String userProvidedKey) {
        if (userProvidedKey != null && !userProvidedKey.trim().isEmpty()) {
            return userProvidedKey.trim();
        }
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        String sysProp = System.getProperty("gemini.api.key");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp.trim();
        }
        // Check db.properties
        try (FileInputStream fis = new FileInputStream("db.properties")) {
            Properties props = new Properties();
            props.load(fis);
            String propKey = props.getProperty("gemini.api.key");
            if (propKey != null && !propKey.trim().isEmpty()) {
                return propKey.trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Main conversation dispatch.
     * Queries Google Gemini using dynamic model discovery with sequential fallback,
     * and gracefully falls back to the intelligent built-in engine if needed.
     */
    public AiResponse chat(User user, String userMessage, List<ChatMessage> history, String userProvidedKey) {
        String apiKey = resolveApiKey(userProvidedKey);

        if (apiKey != null && !apiKey.isEmpty()) {
            List<String> modelsToTry = getPrioritizedCandidateModels(apiKey);
            Exception lastError = null;

            for (String modelName : modelsToTry) {
                // First try v1beta API version
                try {
                    String response = callGemini(user, userMessage, history, apiKey, modelName, "v1beta");
                    workingModelCache.put(apiKey, modelName);
                    return new AiResponse(response, "gemini", modelName);
                } catch (Exception e1) {
                    lastError = e1;
                    System.err.println("Gemini model '" + modelName + "' (v1beta) returned: " + e1.getMessage());

                    // If HTTP 404 (model not found on v1beta), try v1 endpoint before giving up on this model
                    if (e1.getMessage() != null && e1.getMessage().contains("404")) {
                        try {
                            String response = callGemini(user, userMessage, history, apiKey, modelName, "v1");
                            workingModelCache.put(apiKey, modelName);
                            return new AiResponse(response, "gemini", modelName);
                        } catch (Exception e2) {
                            lastError = e2;
                            System.err.println("Gemini model '" + modelName + "' (v1) also returned: " + e2.getMessage());
                        }
                    }
                }
            }

            // If all Gemini models failed, fall back smoothly to the smart local engine with a clean notice
            System.err.println("All Gemini candidate models failed. Falling back to local smart engine.");
            String notice = buildFriendlyNotice(lastError);
            String localReply = buildRuleBasedResponse(user, userMessage);
            return new AiResponse(localReply + notice, "rule-based", "local-engine");
        }

        // Zero-key rule-based intelligent response
        String reply = buildRuleBasedResponse(user, userMessage);
        return new AiResponse(reply, "rule-based", "local-engine");
    }

    /**
     * Dynamically fetches models available for this API key via ListModels,
     * prioritizing fast flash models, or falls back to prioritized candidate list.
     */
    private List<String> getPrioritizedCandidateModels(String apiKey) {
        List<String> result = new ArrayList<>();

        // 1. If we already know a working model for this key, put it first!
        String knownWorking = workingModelCache.get(apiKey);
        if (knownWorking != null) {
            result.add(knownWorking);
        }

        // 2. Check cached discovered models or discover them now
        List<String> discovered = discoveredModelsCache.get(apiKey);
        if (discovered == null || discovered.isEmpty()) {
            discovered = fetchAvailableModelsFromGoogle(apiKey);
            if (!discovered.isEmpty()) {
                discoveredModelsCache.put(apiKey, discovered);
            }
        }

        for (String m : discovered) {
            if (!result.contains(m)) {
                result.add(m);
            }
        }

        // 3. Append static candidates as final backups
        for (String fb : FALLBACK_CANDIDATE_MODELS) {
            if (!result.contains(fb)) {
                result.add(fb);
            }
        }

        return result;
    }

    /**
     * Calls Google Gemini ListModels API to discover all models supporting generateContent for this key.
     */
    private List<String> fetchAvailableModelsFromGoogle(String apiKey) {
        List<String> models = new ArrayList<>();
        String[] versions = new String[]{"v1beta", "v1"};

        for (String ver : versions) {
            try {
                String url = "https://generativelanguage.googleapis.com/" + ver + "/models?key=" + apiKey;
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200) {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (root.has("models") && root.get("models").isJsonArray()) {
                        for (JsonElement el : root.getAsJsonArray("models")) {
                            JsonObject m = el.getAsJsonObject();
                            boolean canGenerate = false;

                            if (m.has("supportedGenerationMethods") && m.get("supportedGenerationMethods").isJsonArray()) {
                                for (JsonElement gm : m.getAsJsonArray("supportedGenerationMethods")) {
                                    if ("generateContent".equalsIgnoreCase(gm.getAsString())) {
                                        canGenerate = true;
                                        break;
                                    }
                                }
                            } else if (m.has("supportedActions") && m.get("supportedActions").isJsonArray()) {
                                for (JsonElement gm : m.getAsJsonArray("supportedActions")) {
                                    if ("generateContent".equalsIgnoreCase(gm.getAsString())) {
                                        canGenerate = true;
                                        break;
                                    }
                                }
                            }

                            if (canGenerate && m.has("name")) {
                                String name = m.get("name").getAsString();
                                if (name.startsWith("models/")) {
                                    name = name.substring("models/".length());
                                }
                                models.add(name);
                            }
                        }
                    }

                    if (!models.isEmpty()) {
                        // Sort models by preference (flash models first, then pro, then others)
                        models.sort((a, b) -> Integer.compare(scoreModel(b), scoreModel(a)));
                        System.out.println("Discovered available Gemini models via " + ver + ": " + models);
                        return models;
                    }
                }
            } catch (Exception e) {
                System.err.println("Could not discover models via " + ver + ": " + e.getMessage());
            }
        }

        return models;
    }

    private int scoreModel(String name) {
        String n = name.toLowerCase();
        if (n.contains("2.5-flash")) return 100;
        if (n.contains("2.0-flash")) return 95;
        if (n.contains("1.5-flash-latest")) return 90;
        if (n.contains("1.5-flash")) return 85;
        if (n.contains("flash")) return 80;
        if (n.contains("2.5-pro")) return 75;
        if (n.contains("2.0-pro")) return 70;
        if (n.contains("1.5-pro")) return 65;
        if (n.contains("pro")) return 60;
        return 50;
    }

    /**
     * Call Google Gemini generateContent REST endpoint with system instruction and error recovery.
     */
    private String callGemini(User user, String userMessage, List<ChatMessage> history, String apiKey, String model, String apiVersion) throws Exception {
        String cleanModel = model.startsWith("models/") ? model.substring("models/".length()) : model;
        String url = "https://generativelanguage.googleapis.com/" + apiVersion + "/models/" + cleanModel + ":generateContent?key=" + apiKey;
        String systemInstructionText = buildSystemPrompt(user);

        // Try standard payload with systemInstruction
        try {
            return executeGeminiHttpRequest(url, systemInstructionText, userMessage, history, true);
        } catch (Exception e) {
            // If model or API version does not support systemInstruction, retry with system prompt prepended in user content
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("systeminstruction") || msg.contains("not supported") || msg.contains("invalid argument")) {
                return executeGeminiHttpRequest(url, systemInstructionText, userMessage, history, false);
            }
            throw e;
        }
    }

    private String executeGeminiHttpRequest(String url, String systemInstructionText, String userMessage, List<ChatMessage> history, boolean useSystemInstructionField) throws Exception {
        JsonObject root = new JsonObject();

        if (useSystemInstructionField && systemInstructionText != null && !systemInstructionText.isEmpty()) {
            JsonObject systemInstruction = new JsonObject();
            JsonArray sysParts = new JsonArray();
            JsonObject sysPart = new JsonObject();
            sysPart.addProperty("text", systemInstructionText);
            sysParts.add(sysPart);
            systemInstruction.add("parts", sysParts);
            root.add("systemInstruction", systemInstruction);
        }

        // Contents
        JsonArray contents = new JsonArray();
        boolean prependedSystem = false;

        if (history != null) {
            for (ChatMessage msg : history) {
                if (msg.content == null || msg.content.trim().isEmpty()) continue;
                JsonObject turn = new JsonObject();
                String role = "model".equalsIgnoreCase(msg.role) || "assistant".equalsIgnoreCase(msg.role) ? "model" : "user";
                turn.addProperty("role", role);
                JsonArray parts = new JsonArray();
                JsonObject p = new JsonObject();

                String text = msg.content;
                if (!useSystemInstructionField && !prependedSystem && "user".equals(role)) {
                    text = "[System Instructions: " + systemInstructionText + "]\n\n" + text;
                    prependedSystem = true;
                }
                p.addProperty("text", text);
                parts.add(p);
                turn.add("parts", parts);
                contents.add(turn);
            }
        }

        // Current turn
        JsonObject currentTurn = new JsonObject();
        currentTurn.addProperty("role", "user");
        JsonArray currentParts = new JsonArray();
        JsonObject currentPart = new JsonObject();

        String currentText = userMessage;
        if (!useSystemInstructionField && !prependedSystem) {
            currentText = "[System Instructions: " + systemInstructionText + "]\n\n" + currentText;
        }
        currentPart.addProperty("text", currentText);
        currentParts.add(currentPart);
        currentTurn.add("parts", currentParts);
        contents.add(currentTurn);

        root.add("contents", contents);

        // Generation Config
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("temperature", 0.7);
        genConfig.addProperty("maxOutputTokens", 2048);
        root.add("generationConfig", genConfig);

        String jsonPayload = root.toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            String errDetail = response.body();
            try {
                JsonObject errObj = JsonParser.parseString(errDetail).getAsJsonObject();
                if (errObj.has("error") && errObj.getAsJsonObject("error").has("message")) {
                    errDetail = errObj.getAsJsonObject("error").get("message").getAsString();
                }
            } catch (Exception ignored) {}
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + errDetail);
        }

        // Parse candidate text
        JsonObject respJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray candidates = respJson.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("No candidates returned by Gemini.");
        }

        JsonObject candidate = candidates.get(0).getAsJsonObject();
        JsonObject content = candidate.getAsJsonObject("content");
        JsonArray parts = content.getAsJsonArray("parts");
        StringBuilder sb = new StringBuilder();
        for (JsonElement el : parts) {
            JsonObject part = el.getAsJsonObject();
            if (part.has("text")) {
                sb.append(part.get("text").getAsString());
            }
        }
        return sb.toString().trim();
    }

    private String buildFriendlyNotice(Exception lastError) {
        if (lastError == null || lastError.getMessage() == null) {
            return "\n\n> ℹ️ *Note: Gemini API is temporarily unavailable. Showing response from ExPenseBook's Smart Financial Engine.*";
        }
        String msg = lastError.getMessage();
        if (msg.contains("API key not valid")) {
            return "\n\n> ⚠️ **Notice**: The configured Gemini API key appears to be invalid. Please contact your administrator to update the API key. PennyWise's local engine generated the response above.";
        }
        if (msg.contains("429") || msg.contains("quota") || msg.contains("RESOURCE_EXHAUSTED")) {
            return "\n\n> ⚠️ **Notice**: Gemini API rate limit or quota exceeded. ExPenseBook's local engine generated the response above.";
        }
        return "\n\n> ℹ️ *Note: Gemini API connection encountered a temporary issue. ExPenseBook's Smart Financial Engine answered your query above.*";
    }

    /**
     * Builds comprehensive financial context and persona for the system prompt.
     */
    private String buildSystemPrompt(User user) {
        int month = DateUtil.getCurrentMonth();
        int year = DateUtil.getCurrentYear();
        String monthName = DateUtil.formatMonthYear(month, year);
        String curr = user.getCurrency() != null ? user.getCurrency() : "₹";
        int daysRemaining = DateUtil.getDaysRemainingInMonth(month, year);
        int daysPassed = DateUtil.getDaysPassedInMonth(month, year);

        double totalSpent = 0.0;
        double todaySpent = 0.0;
        double dailyAvg = 0.0;
        Map<Category, Double> categoryMap = Collections.emptyMap();
        List<Expense> recentExpenses = Collections.emptyList();
        BudgetService.SmartBudgetSummary budgetSummary = null;
        double totalIncome = 0.0;
        double netBalance = 0.0;

        try {
            totalSpent = expenseService.getTotalSpentMonth(user.getId(), month, year);
            todaySpent = expenseService.getTodaySpent(user.getId());
            dailyAvg = analysisService.calculateDailyAverage(user.getId(), month, year);
            LocalDate start = DateUtil.getStartOfMonth(month, year);
            LocalDate end = DateUtil.getEndOfMonth(month, year);
            categoryMap = expenseService.getCategoryBreakdown(user.getId(), start, end);
            recentExpenses = expenseService.getRecentExpenses(user.getId(), 8);
            if (user.isBudgetMode()) {
                budgetSummary = budgetService.getSmartBudgetSummary(user.getId(), month, year);
            }
            totalIncome = incomeService.getTotalIncomeForMonth(user.getId(), month, year);
            netBalance = incomeService.calculateNetBalance(totalIncome, totalSpent);
        } catch (Exception e) {
            System.err.println("Error assembling financial context for AI: " + e.getMessage());
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are PennyWise AI, the smart, friendly, and versatile AI Assistant embedded inside the ExPenseBook personal finance application.\n\n");
        prompt.append("### CORE PERSONALITY & CAPABILITIES:\n");
        prompt.append("1. **Financial Acumen**: You are an expert financial advisor. You give sharp, practical, numbers-backed advice on budgeting, saving, cutting frivolous expenses, and reaching financial goals.\n");
        prompt.append("2. **Live Context Awareness**: You have real-time access to the user's live financial data shown below. Always reference these exact figures whenever the user asks about their spending, budgets, balances, or transactions.\n");
        prompt.append("3. **Complete Versatility & General Intelligence**: If the user asks general, casual, philosophical, creative, or 'unnecessary' questions (e.g. coding, general knowledge, jokes, trivia, recipes, life advice, math, science, small talk, or fun banter), YOU MUST ANSWER ENTHUSIASTICALLY, ACCURATELY, AND CHARMINGLY. NEVER refuse to answer by saying 'I only answer financial questions'. You are a brilliant, well-rounded AI companion who happens to also be their master financial advisor.\n");
        prompt.append("4. **Formatting**: Format your responses cleanly with markdown: bold headings, bullet points, and emoji where appropriate for readability. Keep replies crisp, engaging, and to the point.\n\n");

        prompt.append("### CURRENT USER FINANCIAL SNAPSHOT (LIVE DATA):\n");
        prompt.append("- User Name: ").append(user.getFullName()).append("\n");
        prompt.append("- Preferred Currency: ").append(curr).append("\n");
        prompt.append("- Mode: ").append(user.getFinancialMode().name()).append(" (")
                .append(user.isBudgetMode() ? "Track + Budget" : "Track Only").append(")\n");
        prompt.append("- Current Billing Period: ").append(monthName).append("\n");
        prompt.append("- Days Passed: ").append(daysPassed).append(" days | Days Left in Month: ").append(daysRemaining).append(" days\n");
        prompt.append("- Total Spent This Month: ").append(curr).append(String.format(Locale.US, "%.2f", totalSpent)).append("\n");
        prompt.append("- Spent Today: ").append(curr).append(String.format(Locale.US, "%.2f", todaySpent)).append("\n");
        prompt.append("- Average Daily Burn Rate: ").append(curr).append(String.format(Locale.US, "%.2f", dailyAvg)).append("/day\n");
        prompt.append("- Total Income Recorded: ").append(curr).append(String.format(Locale.US, "%.2f", totalIncome)).append("\n");
        prompt.append("- Net Balance: ").append(curr).append(String.format(Locale.US, "%.2f", netBalance)).append("\n");

        if (budgetSummary != null && budgetSummary.totalBudget > 0) {
            prompt.append("- Monthly Budget: ").append(curr).append(String.format(Locale.US, "%.2f", budgetSummary.totalBudget)).append("\n");
            prompt.append("- Remaining Budget: ").append(curr).append(String.format(Locale.US, "%.2f", budgetSummary.remainingBudget)).append("\n");
            prompt.append("- Budget Used: ").append(String.format(Locale.US, "%.1f", budgetSummary.spentPercentage)).append("%\n");
            prompt.append("- Recommended Daily Spending: ").append(curr).append(String.format(Locale.US, "%.2f", budgetSummary.recommendedDaily)).append("/day\n");
            prompt.append("- Projected Month-End Spend: ").append(curr).append(String.format(Locale.US, "%.2f", budgetSummary.projectedMonthEnd)).append("\n");
            if (!budgetSummary.alerts.isEmpty()) {
                prompt.append("- Active Budget Alerts: ").append(String.join("; ", budgetSummary.alerts)).append("\n");
            }
        } else if (user.isBudgetMode()) {
            prompt.append("- Monthly Budget: Not yet configured (User can set one in the Budget tab).\n");
        }

        prompt.append("\n### CATEGORY BREAKDOWN THIS MONTH:\n");
        if (categoryMap.isEmpty()) {
            prompt.append("- No expenses recorded yet this month.\n");
        } else {
            for (Map.Entry<Category, Double> entry : categoryMap.entrySet()) {
                double pct = totalSpent > 0 ? (entry.getValue() / totalSpent) * 100.0 : 0.0;
                prompt.append(String.format(Locale.US, "- %s: %s%.2f (%.1f%%)\n",
                        entry.getKey().getName(), curr, entry.getValue(), pct));
            }
        }

        prompt.append("\n### RECENT TRANSACTIONS:\n");
        if (recentExpenses.isEmpty()) {
            prompt.append("- No recent transactions.\n");
        } else {
            for (Expense e : recentExpenses) {
                String desc = e.getDescription() != null && !e.getDescription().isEmpty() ? e.getDescription() : e.getCategoryName();
                prompt.append(String.format(Locale.US, "- [%s] %s: -%s%.2f via %s (%s)\n",
                        e.getExpenseDate(), desc, curr, e.getAmount(), e.getPaymentMode(), e.getCategoryName()));
            }
        }

        return prompt.toString();
    }

    /**
     * Built-in intelligent rule-based fallback when no Gemini API key is configured
     * or when external connectivity is temporarily lost.
     */
    private String buildRuleBasedResponse(User user, String query) {
        String q = query.toLowerCase().trim();
        int month = DateUtil.getCurrentMonth();
        int year = DateUtil.getCurrentYear();
        String curr = user.getCurrency() != null ? user.getCurrency() : "₹";
        String monthName = DateUtil.formatMonthYear(month, year);
        int daysLeft = DateUtil.getDaysRemainingInMonth(month, year);

        try {
            double totalSpent = expenseService.getTotalSpentMonth(user.getId(), month, year);
            double todaySpent = expenseService.getTodaySpent(user.getId());
            double dailyAvg = analysisService.calculateDailyAverage(user.getId(), month, year);
            double totalIncome = incomeService.getTotalIncomeForMonth(user.getId(), month, year);
            double netBalance = incomeService.calculateNetBalance(totalIncome, totalSpent);
            LocalDate start = DateUtil.getStartOfMonth(month, year);
            LocalDate end = DateUtil.getEndOfMonth(month, year);
            Map<Category, Double> catMap = expenseService.getCategoryBreakdown(user.getId(), start, end);
            List<Expense> recent = expenseService.getRecentExpenses(user.getId(), 5);

            // 1. Specific Query: 50/30/20 Budgeting Rule & General Budgeting Principles
            if (q.contains("50/30/20") || q.contains("50 30 20") || q.contains("50-30-20")
                    || (q.contains("budget") && (q.contains("rule") || q.contains("explain") || q.contains("what is") || q.contains("how to") || q.contains("principle") || q.contains("formula") || q.contains("guide")))) {
                StringBuilder sb = new StringBuilder();
                sb.append("📊 **The 50/30/20 Budgeting Rule Explained**\n\n");
                sb.append("The 50/30/20 framework is the world's most popular, time-tested rule for stress-free financial health:\n\n");
                sb.append("### 1. 🏠 **50% — Needs (Essential Survival)**\n");
                sb.append("Covers mandatory living expenses you cannot avoid:\n");
                sb.append("- Rent/mortgage, utilities (water, electricity, WiFi)\n");
                sb.append("- Groceries and essential health care/medicines\n");
                sb.append("- Transportation (commute, fuel) & minimum debt obligations\n\n");

                sb.append("### 2. 🍿 **30% — Wants (Lifestyle & Enjoyment)**\n");
                sb.append("Discretionary spending that makes life fun and enriching:\n");
                sb.append("- Dining out, cafes, and weekend takeaways\n");
                sb.append("- Streaming subscriptions (Netflix, Spotify, Prime)\n");
                sb.append("- Hobbies, shopping, gadgets, and entertainment\n\n");

                sb.append("### 3. 💰 **20% — Savings & Wealth Building**\n");
                sb.append("Secures your future and buys you financial peace of mind:\n");
                sb.append("- Emergency fund contributions (liquid cash buffer)\n");
                sb.append("- Monthly SIPs, mutual funds, index investing, retirement (PF/401k)\n");
                sb.append("- Paying down high-interest debt beyond minimums\n\n");

                sb.append("### 🧮 **Real-World Math Examples**:\n");
                if (totalIncome > 0) {
                    double n = totalIncome * 0.50;
                    double w = totalIncome * 0.30;
                    double s = totalIncome * 0.20;
                    sb.append(String.format("Based on your recorded income of **%s%.2f** this month:\n", curr, totalIncome));
                    sb.append(String.format("- **Needs Target (50%%)**: %s%.2f\n", curr, n));
                    sb.append(String.format("- **Wants Target (30%%)**: %s%.2f\n", curr, w));
                    sb.append(String.format("- **Savings Target (20%%)**: %s%.2f\n\n", curr, s));
                } else {
                    sb.append(String.format("- **On %s50,000/month**: %s25,000 Needs • %s15,000 Wants • %s10,000 Savings\n", curr, curr, curr, curr));
                    sb.append(String.format("- **On %s1,00,000/month**: %s50,000 Needs • %s30,000 Wants • %s20,000 Savings\n\n", curr, curr, curr, curr));
                }

                sb.append("💡 **How to track this in ExPenseBook**:\n");
                sb.append("Go to the **Budget** tab and assign spending limits to your categories so your total monthly expenses stay well within your 80% spending ceiling!");
                return sb.toString();
            }

            // 2. Emergency Fund
            if (q.contains("emergency") || q.contains("rainy day") || q.contains("contingency")) {
                return String.format("🛡️ **The Emergency Fund Blueprint**:\n\n" +
                        "1. **Target Size**: Save **3 to 6 months** of essential living expenses (rent, food, bills).\n" +
                        "2. **Starter Milestone**: If you're just starting, aim for a starter cushion of **%s15,000 to %s25,000** first.\n" +
                        "3. **Where to park it**: Keep it in a high-yield savings account or liquid mutual fund with sweep-in FD facility. NEVER risk it in stocks or lock it in illiquid property.\n" +
                        "4. **Golden Rule**: Only touch it for genuine emergencies (job loss, medical urgency, critical repairs)—never for vacations or shopping!",
                        curr, curr);
            }

            // 3. Compound Interest & Investing
            if (q.contains("compound") || q.contains("interest") || q.contains("invest") || q.contains("sip") || q.contains("mutual fund") || q.contains("stock") || q.contains("wealth") || q.contains("grow money")) {
                return String.format("📈 **The Power of Compound Interest & Investing**:\n\n" +
                        "Albert Einstein famously called compound interest the *8th wonder of the world*:\n\n" +
                        "1. **The Rule of 72**: Divide 72 by your expected annual return to find how many years it takes to double your money!\n" +
                        "   - At 12%% annual return: 72 / 12 = **Doubles every 6 years!**\n" +
                        "2. **The Magic of SIP**: Investing just **%s5,000/month** in an index fund yielding 12%% CAGR:\n" +
                        "   - In 10 years: Invested %s6,00,000 ➔ Grows to **~%s11.6 Lakhs**!\n" +
                        "   - In 20 years: Invested %s12,00,000 ➔ Grows to **~%s50.0 Lakhs**!\n" +
                        "3. **Your 3-Step Wealth Ladder**:\n" +
                        "   - Step 1: Wipe out high-interest credit card debt.\n" +
                        "   - Step 2: Build a 3-month emergency fund.\n" +
                        "   - Step 3: Automate monthly index fund SIPs on payday.",
                        curr, curr, curr, curr, curr);
            }

            // 4. Jokes & Financial Humor
            if (q.contains("joke") || q.contains("funny") || q.contains("laugh") || q.contains("humor") || q.contains("meme")) {
                String[] jokes = {
                        "💳 **Why did the credit card go to therapy?**\n\nIt had too much baggage and needed to address its balance issues!",
                        "🧅 **My wallet is just like an onion:**\n\nEvery time I open it, it makes me cry! 😂",
                        "🪙 **Why are coins so great at sports?**\n\nBecause they're always flipping!",
                        "🛒 **I told my doctor I get heart palpitations every month:**\n\nHe checked my chart and said, *\"That's just your credit card statement arriving!\"* 💸"
                };
                int idx = (int) (System.currentTimeMillis() % jokes.length);
                return jokes[idx] + "\n\n*(Have a question about your spending or budgets? Ask me anytime!)*";
            }

            // 5. Debt Payoff Strategies
            if (q.contains("debt") || q.contains("loan") || q.contains("credit card bill") || q.contains("snowball") || q.contains("avalanche") || q.contains("pay off")) {
                return "💳 **Two Best Strategies to Eliminate Debt**:\n\n" +
                        "1. 🏔️ **The Debt Avalanche (Mathematically Superior)**:\n" +
                        "   - Pay minimums on all debts, and throw all extra cash at the debt with the **highest interest rate** (e.g. credit cards at 36-42%).\n" +
                        "   - Saves you the most money in interest!\n\n" +
                        "2. ⛄ **The Debt Snowball (Psychologically Powerful)**:\n" +
                        "   - Pay minimums on all debts, and attack the **smallest balance** first.\n" +
                        "   - Once that's cleared, roll that payment into the next smallest.\n" +
                        "   - Delivers fast dopamine wins to keep you motivated!";
            }

            // 6. Today's Spending
            if (q.contains("today")) {
                return String.format("📅 **Today's Spending Update**:\n\n" +
                        "- **Spent Today**: %s%.2f\n" +
                        "- **Daily Average This Month**: %s%.2f/day\n" +
                        "- **Days Left in %s**: %d days\n\n" +
                        "%s",
                        curr, todaySpent, curr, dailyAvg, monthName, daysLeft,
                        todaySpent > dailyAvg ? "⚠️ *Today's spending is slightly higher than your daily average.*" : "✅ *Great pacing! You are within your daily burn rate today.*");
            }

            // 7. Overall Spending / How much spent / Burn Rate
            if (q.contains("total") || q.contains("how much") || q.contains("spend") || q.contains("spent") || q.contains("cost") || q.contains("burn")) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("📊 **Spending Overview for %s**:\n\n", monthName));
                sb.append(String.format("- **Total Spent**: %s%.2f\n", curr, totalSpent));
                sb.append(String.format("- **Spent Today**: %s%.2f\n", curr, todaySpent));
                sb.append(String.format("- **Daily Average**: %s%.2f/day\n", curr, dailyAvg));
                sb.append(String.format("- **Days Remaining**: %d days\n", daysLeft));

                if (totalIncome > 0) {
                    sb.append(String.format("- **Total Income**: %s%.2f\n", curr, totalIncome));
                    sb.append(String.format("- **Net Balance**: %s%.2f\n", curr, netBalance));
                }

                if (user.isBudgetMode()) {
                    BudgetService.SmartBudgetSummary bs = budgetService.getSmartBudgetSummary(user.getId(), month, year);
                    if (bs != null && bs.totalBudget > 0) {
                        sb.append(String.format("- **Budget Remaining**: %s%.2f (%.0f%% used)\n", curr, bs.remainingBudget, bs.spentPercentage));
                        sb.append(String.format("- **Recommended Daily**: %s%.2f/day\n", curr, bs.recommendedDaily));
                    }
                }
                return sb.toString();
            }

            // 8. Days left / Countdown
            if (q.contains("day") || q.contains("days left") || q.contains("month end") || q.contains("remaining")) {
                return String.format("⏳ There are **%d days remaining** in %s.\n\n" +
                                "- **Current Pace**: %s%.2f/day\n" +
                                "- **Projected Spend**: %s%.2f by month end.\n" +
                                "To keep expenses lean, aim to keep discretionary purchases below **%s%.2f/day** for the rest of the month!",
                        daysLeft, monthName, curr, dailyAvg, curr, dailyAvg * (daysLeft + DateUtil.getDaysPassedInMonth(month, year)), curr, Math.max(100.0, dailyAvg * 0.85));
            }

            // 9. Category Breakdown / Where did money go
            if (q.contains("category") || q.contains("categories") || q.contains("where") || q.contains("most") || q.contains("highest") || q.contains("breakdown")) {
                if (catMap.isEmpty()) {
                    return "You haven't recorded any expenses yet for " + monthName + ". Tap **+ Add Expense** to get started!";
                }
                StringBuilder sb = new StringBuilder("🏷️ **Where your money went this month**:\n\n");
                catMap.entrySet().stream()
                        .sorted(Map.Entry.<Category, Double>comparingByValue().reversed())
                        .forEach(e -> {
                            double pct = totalSpent > 0 ? (e.getValue() / totalSpent) * 100.0 : 0;
                            sb.append(String.format("- **%s**: %s%.2f (%.1f%%)\n", e.getKey().getName(), curr, e.getValue(), pct));
                        });
                return sb.toString();
            }

            // 10. Specific App Budget status (Only when checking the user's specific monthly limit)
            if (q.contains("my budget") || q.contains("budget status") || q.contains("current budget") || q.contains("check budget") || q.contains("remaining budget") || q.contains("target")) {
                if (!user.isBudgetMode()) {
                    return "You are currently in **Track Only** mode.\n\nYou can switch to **Track + Budget** anytime in **Settings** to set monthly limits, track category envelopes, and get live daily spend pacing!";
                }
                BudgetService.SmartBudgetSummary bs = budgetService.getSmartBudgetSummary(user.getId(), month, year);
                if (bs == null || bs.totalBudget <= 0) {
                    return "You haven't set a budget for " + monthName + " yet. Head over to the **Budget** tab to set your monthly limit!";
                }
                return String.format("🎯 **Your Live Budget Status**:\n\n" +
                                "- **Monthly Limit**: %s%.2f\n" +
                                "- **Total Spent**: %s%.2f\n" +
                                "- **Remaining Budget**: %s%.2f\n" +
                                "- **Budget Used**: %.1f%%\n" +
                                "- **Safe Daily Spend**: %s%.2f/day to finish the month comfortably.",
                        curr, bs.totalBudget, curr, bs.totalSpent, curr, bs.remainingBudget, bs.spentPercentage, curr, bs.recommendedDaily);
            }

            // 11. Recent Transactions
            if (q.contains("recent") || q.contains("transaction") || q.contains("history") || q.contains("latest") || q.contains("past")) {
                if (recent.isEmpty()) {
                    return "No transactions recorded yet. Tap **+ Add Expense** to log your first transaction!";
                }
                StringBuilder sb = new StringBuilder("💳 **Your Recent Transactions**:\n\n");
                for (Expense e : recent) {
                    String desc = e.getDescription() != null && !e.getDescription().isEmpty() ? e.getDescription() : e.getCategoryName();
                    sb.append(String.format("- **%s**: -%s%.2f (%s • %s)\n",
                            desc, curr, e.getAmount(), e.getExpenseDate(), e.getCategoryName()));
                }
                return sb.toString();
            }

            // 12. Savings Tips / Advice
            if (q.contains("save") || q.contains("saving") || q.contains("tip") || q.contains("advice") || q.contains("cut") || q.contains("reduce")) {
                StringBuilder sb = new StringBuilder("💡 **Smart Savings Tips for You**:\n\n");
                if (!catMap.isEmpty()) {
                    Category topCat = catMap.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse(null);
                    if (topCat != null) {
                        sb.append(String.format("1. **Target %s**: This is your highest expenditure category (%s%.2f). Cutting just 15%% here saves you **%s%.2f** this month!\n",
                                topCat.getName(), curr, catMap.get(topCat), curr, catMap.get(topCat) * 0.15));
                    }
                }
                sb.append(String.format("2. **Daily Pacing**: Maintain a daily spend below **%s%.2f** over the remaining %d days.\n",
                        curr, Math.max(100.0, dailyAvg * 0.85), daysLeft));
                sb.append("3. **The 24-Hour Rule**: Wait 24 hours before making any impulse purchase over " + curr + "1,000. 70% of the time, the urge will pass!\n");
                sb.append("4. **Audit Subscriptions**: Cancel recurring apps or OTT services you haven't opened in the past 3 weeks.\n");
                return sb.toString();
            }

            // 13. Greetings
            if (q.startsWith("hi") || q.startsWith("hello") || q.startsWith("hey") || q.equals("namaste") || q.contains("good morning") || q.contains("good evening") || q.contains("who are you")) {
                return "👋 **Hello " + user.getFullName().split(" ")[0] + "!** I'm PennyWise AI, your personal financial advisor and smart assistant.\n\n" +
                        "I have live access to your transactions, monthly spending (" + curr + String.format("%.2f", totalSpent) + "), and budgets.\n\n" +
                        "Ask me anything! For example:\n" +
                        "- *\"How much have I spent this month?\"*\n" +
                        "- *\"Can you explain the 50/30/20 budgeting rule?\"*\n" +
                        "- *\"What is my top spending category?\"*\n" +
                        "- *\"Tell me a financial joke!\"*\n" +
                        "- *\"Give me tips to save money\"*";
            }

        } catch (Exception e) {
            System.err.println("Rule fallback error: " + e.getMessage());
        }

        // 14. Fallback for General / Unmatched queries
        return "👋 I'm **PennyWise AI**, your ExPenseBook smart assistant.\n\n" +
                "I can help with your **spending**, **budgets**, **categories**, **savings tips**, **transactions**, and much more!\n\n" +
                "Try asking me:\n" +
                "- *\"How much have I spent this month?\"*\n" +
                "- *\"Can you explain the 50/30/20 budgeting rule?\"*\n" +
                "- *\"What is my top spending category?\"*\n" +
                "- *\"How many days left in the month?\"*\n" +
                "- *\"Give me tips to save money\"*\n" +
                "- *\"Show my recent transactions\"*";
    }
}
