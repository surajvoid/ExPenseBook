package com.expensebook.model;

public enum PaymentMode {
    UPI("UPI"),
    CASH("Cash"),
    DEBIT_CARD("Debit Card"),
    CREDIT_CARD("Credit Card"),
    NET_BANKING("Net Banking"),
    WALLET("Wallet");

    private final String displayName;

    PaymentMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PaymentMode fromString(String text) {
        if (text == null) return UPI;
        for (PaymentMode mode : values()) {
            if (mode.name().equalsIgnoreCase(text) || mode.displayName.equalsIgnoreCase(text)) {
                return mode;
            }
        }
        return UPI;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
