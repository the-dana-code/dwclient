package com.danavalerie.matrixmudrelay.util;

import java.time.Instant;

public final class DiscworldTimeUtils {
    private DiscworldTimeUtils() {}

    public static String formatDiscworldTime(long unixSeconds) {
        long minutes = (unixSeconds % (18 * 60)) / 18;
        long hours = (unixSeconds % (18 * 60 * 24)) / (18 * 60);
        long hoursTwelve = hours % 12;
        if (hoursTwelve == 0) hoursTwelve = 12;
        String ampm = hours > 11 ? "pm" : "am";
        return String.format("%d:%02d%s", hoursTwelve, (int) minutes, ampm);
    }

    public static String getCurrentDiscworldTime() {
        return formatDiscworldTime(Instant.now().getEpochSecond());
    }
}
