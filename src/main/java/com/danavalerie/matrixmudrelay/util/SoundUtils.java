package com.danavalerie.matrixmudrelay.util;

import java.awt.Toolkit;

public final class SoundUtils {
    private SoundUtils() {}

    public static void playUULibraryReadySound() {
        // "Good sound" to prompt the user that the system is ready for the next command.
        // On Windows, win.sound.asterisk is the "Information" sound.
        Runnable sound = (Runnable) Toolkit.getDefaultToolkit().getDesktopProperty("win.sound.asterisk");
        if (sound != null) {
            sound.run();
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    public static void playUULibraryAlertSound() {
        // "High alert sound" because the user needs to know to stop immediately.
        // On Windows, win.sound.hand is the "Critical Stop" sound.
        Runnable sound = (Runnable) Toolkit.getDefaultToolkit().getDesktopProperty("win.sound.hand");
        if (sound != null) {
            sound.run();
        } else {
            // Fallback for non-Windows or if property is missing: three beeps.
            new Thread(() -> {
                for (int i = 0; i < 3; i++) {
                    Toolkit.getDefaultToolkit().beep();
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }).start();
        }
    }
}
