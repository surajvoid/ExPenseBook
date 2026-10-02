package com.expensebook.util;

import com.expensebook.dao.UserDAO;
import com.expensebook.model.FinancialMode;
import com.expensebook.model.User;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SessionManager {

    private static User currentUser;
    private static final List<Runnable> sessionListeners = new ArrayList<>();
    private static final String SESSION_FILE_NAME = ".expensebook_desktop_session";

    private static File getSessionFile() {
        return new File(System.getProperty("user.home"), SESSION_FILE_NAME);
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
        persistSession(user);
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
        clearPersistedSession();
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

    private static void persistSession(User user) {
        try {
            File file = getSessionFile();
            if (user != null) {
                String token = SessionTokenUtil.generateToken(user);
                if (token != null) {
                    Files.writeString(file.toPath(), token);
                }
            } else {
                clearPersistedSession();
            }
        } catch (Exception ignored) {
        }
    }

    private static void clearPersistedSession() {
        try {
            File file = getSessionFile();
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception ignored) {
        }
    }

    public static User restorePersistedSession() {
        try {
            File file = getSessionFile();
            if (file.exists() && file.canRead()) {
                String token = Files.readString(file.toPath()).trim();
                if (!token.isEmpty()) {
                    UserDAO userDAO = new UserDAO();
                    User restored = SessionTokenUtil.verifyToken(token, userDAO);
                    if (restored != null && restored.isActive()) {
                        currentUser = restored;
                        return restored;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Notice: Could not restore desktop session: " + e.getMessage());
        }
        return null;
    }
}
