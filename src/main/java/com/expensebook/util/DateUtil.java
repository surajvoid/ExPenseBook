package com.expensebook.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    public static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    public static final DateTimeFormatter SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM");
    public static final DateTimeFormatter MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");
    public static final DateTimeFormatter DB_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DISPLAY_DATE_FORMAT);
    }

    public static String formatShortDate(LocalDate date) {
        if (date == null) return "";
        return date.format(SHORT_DATE_FORMAT);
    }

    public static String formatMonthYear(int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        return ym.format(MONTH_YEAR_FORMAT);
    }

    public static String getCurrentMonthYearString() {
        return LocalDate.now().format(MONTH_YEAR_FORMAT);
    }

    public static int getCurrentMonth() {
        return LocalDate.now().getMonthValue();
    }

    public static int getCurrentYear() {
        return LocalDate.now().getYear();
    }

    public static LocalDate getStartOfMonth(int month, int year) {
        return LocalDate.of(year, month, 1);
    }

    public static LocalDate getEndOfMonth(int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        return ym.atEndOfMonth();
    }

    public static int getDaysPassedInMonth(int month, int year) {
        LocalDate now = LocalDate.now();
        if (now.getYear() == year && now.getMonthValue() == month) {
            return now.getDayOfMonth();
        } else if (YearMonth.of(year, month).isBefore(YearMonth.now())) {
            return YearMonth.of(year, month).lengthOfMonth();
        } else {
            return 0;
        }
    }

    public static int getDaysRemainingInMonth(int month, int year) {
        LocalDate now = LocalDate.now();
        if (now.getYear() == year && now.getMonthValue() == month) {
            int totalDays = YearMonth.of(year, month).lengthOfMonth();
            return Math.max(0, totalDays - now.getDayOfMonth());
        } else if (YearMonth.of(year, month).isBefore(YearMonth.now())) {
            return 0;
        } else {
            return YearMonth.of(year, month).lengthOfMonth();
        }
    }

    public static String getGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour >= 4 && hour < 12) {
            return "Good Morning";
        } else if (hour >= 12 && hour < 17) {
            return "Good Afternoon";
        } else {
            return "Good Evening";
        }
    }
}
