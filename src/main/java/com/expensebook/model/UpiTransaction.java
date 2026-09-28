package com.expensebook.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Model representing an automatically parsed and tracked UPI transaction.
 * Stored in its own dedicated table (upi_transactions), strictly separated from ExpenseBook's expenses.
 */
public class UpiTransaction {
    private int id;
    private int userId;
    private double amount;
    private String merchant;
    private String categoryName;
    private String sourceApp;
    private String upiRef;
    private boolean isDebit;
    private LocalDate transactionDate;
    private LocalDateTime createdAt;
    private String rawMessage;
    private boolean importedToExpenseBook;
    private Integer importedExpenseId;

    // Transient fields for parser feedback
    private transient boolean parsedSuccessfully = true;
    private transient String statusMessage;

    public UpiTransaction() {
        this.transactionDate = LocalDate.now();
        this.createdAt = LocalDateTime.now();
        this.isDebit = true;
        this.importedToExpenseBook = false;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getSourceApp() { return sourceApp; }
    public void setSourceApp(String sourceApp) { this.sourceApp = sourceApp; }

    public String getUpiRef() { return upiRef; }
    public void setUpiRef(String upiRef) { this.upiRef = upiRef; }

    public boolean isDebit() { return isDebit; }
    public void setDebit(boolean debit) { isDebit = debit; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String rawMessage) { this.rawMessage = rawMessage; }

    public boolean isImportedToExpenseBook() { return importedToExpenseBook; }
    public void setImportedToExpenseBook(boolean importedToExpenseBook) { this.importedToExpenseBook = importedToExpenseBook; }

    public Integer getImportedExpenseId() { return importedExpenseId; }
    public void setImportedExpenseId(Integer importedExpenseId) { this.importedExpenseId = importedExpenseId; }

    public boolean isParsedSuccessfully() { return parsedSuccessfully; }
    public void setParsedSuccessfully(boolean parsedSuccessfully) { this.parsedSuccessfully = parsedSuccessfully; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
}
