package com.expensebook.util;

import com.expensebook.model.FinancialMode;
import com.expensebook.model.User;

import java.util.ArrayList;
import java.util.List;

public class SessionManager {

    private static User currentUser;
    private static final List<Runnable> sessionListeners = new ArrayList<>();

    public static void setCurrentUser(User user) {
        currentUser = user;
        notifyListeners();
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static boolean isAdmin() {
        return currentUser != null && currentUser.isAdmin();
    }

    public static int getCurrentUserId() {
        return currentUser != null ? currentUser.getId() : 0;
    }

    public static String getCurrentCurrency() {
        return currentUser != null ? currentUser.getCurrency() : "₹";
    }

    public static FinancialMode getCurrentMode() {
        return currentUser != null ? currentUser.getFinancialMode() : FinancialMode.TRACK_ONLY;
    }

    public static boolean isBudgetMode() {
        return getCurrentMode() == FinancialMode.TRACK_AND_BUDGET;
    }

    public static void logout() {
        currentUser = null;
        notifyListeners();
    }

    public static void addListener(Runnable listener) {
        if (!sessionListeners.contains(listener)) {
            sessionListeners.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        sessionListeners.remove(listener);
    }

    private static void notifyListeners() {
        for (Runnable r : new ArrayList<>(sessionListeners)) {
            try {
                r.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
