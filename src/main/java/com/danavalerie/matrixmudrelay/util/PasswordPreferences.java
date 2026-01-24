package com.danavalerie.matrixmudrelay.util;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class PasswordPreferences {
    private static final String PASSWORD_KEY = "mud_password";
    private static final Preferences prefs = Preferences.userNodeForPackage(PasswordPreferences.class);

    public static String getPassword() {
        return prefs.get(PASSWORD_KEY, null);
    }

    public static void setPassword(String password) {
        if (password == null) {
            prefs.remove(PASSWORD_KEY);
        } else {
            prefs.put(PASSWORD_KEY, password);
        }
        flushPrefs();
    }

    public static boolean hasPassword() {
        return getPassword() != null;
    }

    private static void flushPrefs() {
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            System.err.println("Failed to persist password preferences: " + e.getMessage());
        }
    }
}
