package com.expensebook.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public class CurrencyUtil {

    private static final NumberFormat INDIAN_CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.##");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.#");

    public static String format(double amount, String currencySymbol) {
        String sym = (currencySymbol != null && !currencySymbol.isEmpty()) ? currencySymbol : "₹";
        return sym + " " + AMOUNT_FORMAT.format(amount);
    }

    public static String format(double amount) {
        String currency = SessionManager.getCurrentCurrency();
        return format(amount, currency);
    }

    public static String formatPercent(double percent) {
        return PERCENT_FORMAT.format(percent) + "%";
    }

    public static String formatCompact(double amount) {
        if (amount >= 10000000) {
            return String.format("%.1fCr", amount / 10000000.0);
        } else if (amount >= 100000) {
            return String.format("%.1fL", amount / 100000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fk", amount / 1000.0);
        }
        return AMOUNT_FORMAT.format(amount);
    }
}
