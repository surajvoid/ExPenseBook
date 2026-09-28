package com.expensebook.model;

public enum FinancialMode {
    TRACK_ONLY("Just Track My Expenses", "Record and analyse your spending without setting a budget."),
    TRACK_AND_BUDGET("Track + Monthly Budget", "Track your expenses and manage a monthly spending limit.");

    private final String displayName;
    private final String description;

    FinancialMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static FinancialMode fromString(String text) {
        if (text == null) return TRACK_ONLY;
        for (FinancialMode mode : values()) {
            if (mode.name().equalsIgnoreCase(text) || mode.displayName.equalsIgnoreCase(text)) {
                return mode;
            }
        }
        return TRACK_ONLY;
    }
}
