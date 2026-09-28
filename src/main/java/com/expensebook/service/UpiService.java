package com.expensebook.service;

import com.expensebook.dao.UpiTransactionDAO;
import com.expensebook.model.*;

import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for ExpenseBook UPI.
 * STRICT SEPARATION:
 * - All UPI transactions are recorded ONLY in upi_transactions table.
 * - UPI data NEVER touches ExpenseBook's expenses table automatically.
 * - Import to ExpenseBook is 100% explicit via importToExpenseBook().
 */
public class UpiService {

    private final UpiTransactionDAO upiDAO = new UpiTransactionDAO();
    private final ExpenseService expenseService = new ExpenseService();
    private final CategoryService categoryService = new CategoryService();

    // Webhook tokens mapped to User IDs (token -> userId)
    private static final Map<String, Integer> webhookTokens = new ConcurrentHashMap<>();
    private static final Map<Integer, String> userTokens = new ConcurrentHashMap<>();

    public static class UpiProcessResult {
        public boolean success;
        public boolean isDuplicate;
        public String message;
        public UpiTransaction transaction;

        public UpiProcessResult(boolean success, boolean isDuplicate, String message, UpiTransaction transaction) {
            this.success = success;
            this.isDuplicate = isDuplicate;
            this.message = message;
            this.transaction = transaction;
        }
    }

    /**
     * Ingests, parses, and saves a transaction to the separate upi_transactions table.
     * STRICT SEPARATION: Does NOT touch the main expenses table.
     */
    public UpiProcessResult processUpiMessage(User user, String rawMessage, String sourceHint) {
        if (user == null) {
            return new UpiProcessResult(false, false, "Unauthorized: Valid user required.", null);
        }

        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return new UpiProcessResult(false, false, "Empty UPI notification/SMS text.", null);
        }

        UpiTransaction txn = UpiTransactionParser.parse(rawMessage, sourceHint);
        if (!txn.isParsedSuccessfully()) {
            return new UpiProcessResult(false, false, txn.getStatusMessage(), txn);
        }
        txn.setUserId(user.getId());

        try {
            // Deduplication check: is this UPI reference already in upi_transactions?
            if (upiDAO.isDuplicate(user.getId(), txn.getUpiRef())) {
                String dupMsg = String.format("Duplicate UPI transaction ignored: ₹%.2f to %s (Ref: %s was already recorded).",
                        txn.getAmount(), txn.getMerchant(), txn.getUpiRef());
                txn.setStatusMessage(dupMsg);
                return new UpiProcessResult(false, true, dupMsg, txn);
            }

            // Save exclusively to upi_transactions table
            UpiTransaction saved = upiDAO.save(txn);
            String successMsg = String.format("⚡ UPI Tracked: ₹%.2f %s to %s via %s (Saved in ExpenseBook UPI)",
                    saved.getAmount(), saved.isDebit() ? "paid" : "received", saved.getMerchant(), saved.getSourceApp());

            return new UpiProcessResult(true, false, successMsg, saved);

        } catch (SQLException e) {
            e.printStackTrace();
            return new UpiProcessResult(false, false, "Database error saving UPI transaction: " + e.getMessage(), txn);
        }
    }

    /**
     * Explicit import: Converts a selected UPI transaction into an official ExpenseBook expense.
     * Triggered ONLY after explicit user confirmation!
     */
    public Expense importToExpenseBook(User user, int upiTransactionId) throws Exception {
        if (user == null) throw new IllegalArgumentException("Unauthorized");

        UpiTransaction upiTxn = upiDAO.findById(upiTransactionId);
        if (upiTxn == null || upiTxn.getUserId() != user.getId()) {
            throw new IllegalArgumentException("UPI transaction not found.");
        }

        if (upiTxn.isImportedToExpenseBook()) {
            throw new IllegalStateException("This transaction has already been imported to ExpenseBook.");
        }

        // Match category in ExpenseBook
        int categoryId = resolveCategoryId(user.getId(), upiTxn.getCategoryName());

        String description = String.format("[UPI Import: %s] %s (Ref: %s)",
                upiTxn.getSourceApp(), upiTxn.getMerchant(), upiTxn.getUpiRef());

        // Create official Expense in main ExpenseBook table
        Expense created = expenseService.createExpense(
                user.getId(),
                categoryId,
                upiTxn.getAmount(),
                PaymentMode.UPI,
                upiTxn.getTransactionDate(),
                description
        );

        // Update upi_transactions to mark as imported
        upiDAO.markImported(upiTransactionId, created.getId());

        return created;
    }

    public List<UpiTransaction> getUpiTransactions(int userId, int limit) throws SQLException {
        return upiDAO.findByUserId(userId, limit);
    }

    public Map<String, Object> getUpiDashboard(int userId, LocalDate start, LocalDate end) throws SQLException {
        return upiDAO.getUpiDashboardStats(userId, start, end);
    }

    public boolean deleteUpiTransaction(int upiId, int userId) throws SQLException {
        return upiDAO.delete(upiId, userId);
    }

    /**
     * Generates a realistic mock UPI payment and records it in ExpenseBook UPI.
     */
    public UpiProcessResult simulatePayment(User user, String app, double amount, String merchant, String category) {
        String source = app != null ? app.toLowerCase() : "phonepe";
        long ref = 426000000000L + (long) (Math.random() * 999999999L);
        LocalDate today = LocalDate.now();

        String simulatedMessage;
        if (source.contains("phonepe")) {
            simulatedMessage = String.format("Paid ₹%.2f to %s. Txn ID T%d. Debited from your bank account via PhonePe.",
                    amount, merchant, ref);
        } else if (source.contains("gpay") || source.contains("google")) {
            simulatedMessage = String.format("Paid ₹%.2f to %s using Google Pay. UPI Ref: %d.",
                    amount, merchant, ref);
        } else if (source.contains("paytm")) {
            simulatedMessage = String.format("Paid ₹%.2f to %s on Paytm. UPI Reference: %d.",
                    amount, merchant, ref);
        } else if (source.contains("cred")) {
            simulatedMessage = String.format("Paid ₹%.2f at %s via CRED UPI (Ref: %d).",
                    amount, merchant, ref);
        } else if (source.contains("sbi")) {
            simulatedMessage = String.format("Dear Customer, INR %.2f debited from A/c **4321 on %s by UPI to %s. UPI Ref %d -SBI",
                    amount, today, merchant, ref);
        } else if (source.contains("hdfc")) {
            simulatedMessage = String.format("Debited INR %.2f from HDFC Bank A/c xx5678 to %s on %s. UPI: %d",
                    amount, merchant, today, ref);
        } else {
            simulatedMessage = String.format("Your A/c debited by Rs.%.2f on %s by UPI to %s. Ref: %d",
                    amount, today, merchant, ref);
        }

        return processUpiMessage(user, simulatedMessage, app);
    }

    private int resolveCategoryId(int userId, String categoryName) {
        try {
            List<Category> userCats = categoryService.getCategoriesForUser(userId);
            if (userCats != null && !userCats.isEmpty()) {
                for (Category c : userCats) {
                    if (c.getName().equalsIgnoreCase(categoryName)) return c.getId();
                }
                for (Category c : userCats) {
                    if (c.getName().toLowerCase().contains(categoryName.toLowerCase())) return c.getId();
                }
                for (Category c : userCats) {
                    if (c.getName().equalsIgnoreCase("Other") || c.getName().equalsIgnoreCase("Others")) return c.getId();
                }
                return userCats.get(0).getId();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 1;
    }

    public String getWebhookTokenForUser(User user) {
        return userTokens.computeIfAbsent(user.getId(), id -> {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest((user.getEmail() + "_upi_webhook_salt_" + user.getId()).getBytes());
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 16; i++) {
                    sb.append(String.format("%02x", hash[i]));
                }
                String token = "eb_upi_" + sb.toString();
                webhookTokens.put(token, user.getId());
                return token;
            } catch (Exception e) {
                String token = "eb_upi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                webhookTokens.put(token, user.getId());
                return token;
            }
        });
    }

    public Integer getUserIdForWebhookToken(String token) {
        if (token == null) return null;
        return webhookTokens.get(token.trim());
    }
}
