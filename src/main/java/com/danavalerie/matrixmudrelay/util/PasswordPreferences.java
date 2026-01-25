package com.danavalerie.matrixmudrelay.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class PasswordPreferences {
    private static final Logger log = LoggerFactory.getLogger(PasswordPreferences.class);
    private static final String PASSWORD_KEY = "mud_password";
    private static final Preferences prefs = Preferences.userNodeForPackage(PasswordPreferences.class);

    private static String cachedPassword = null;
    private static boolean cacheLoaded = false;

    public static synchronized String getPassword() {
        if (!cacheLoaded) {
            try {
                cachedPassword = prefs.get(PASSWORD_KEY, null);
                if (cachedPassword != null) {
                    log.info("Password loaded from preferences.");
                }
            } catch (Exception e) {
                log.error("Failed to read password from preferences: {}", e.getMessage());
            }
            cacheLoaded = true;
        }
        return cachedPassword;
    }

    public static synchronized void setPassword(String password) {
        if (password == null || password.isBlank()) {
            log.info("Clearing password from preferences.");
            prefs.remove(PASSWORD_KEY);
            cachedPassword = null;
        } else {
            if (!password.equals(cachedPassword)) {
                log.info("Updating password in preferences.");
                prefs.put(PASSWORD_KEY, password);
                cachedPassword = password;
            }
        }
        cacheLoaded = true;
        flushPrefs();
    }

    public static synchronized boolean hasPassword() {
        return getPassword() != null;
    }

    private static void flushPrefs() {
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            log.error("Failed to persist password preferences: {}", e.getMessage());
        }
    }
}
