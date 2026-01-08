package com.danavalerie.matrixmudrelay.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;

public final class ThreadUtils {
    private static final Logger log = LoggerFactory.getLogger(ThreadUtils.class);

    private ThreadUtils() {}

    public static void checkEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            log.warn("UI access from non-EDT thread: {}", Thread.currentThread().getName(), new Throwable());
        }
    }

    public static void checkNotEdt() {
        if (SwingUtilities.isEventDispatchThread()) {
            log.warn("Blocking operation from EDT thread: {}", Thread.currentThread().getName(), new Throwable());
        }
    }
}
