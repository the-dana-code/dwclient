package com.danavalerie.matrixmudrelay.util;

public class TeleportBannerUtils {
    public static String generateBanner(String targetName, String command) {
        StringBuilder sb = new StringBuilder();
        sb.append("################################################################################\n");
        sb.append("#  Teleporting...                                                              #\n");
        sb.append(String.format("#  Command: %-66s #\n", truncate(command, 66)));
        sb.append(String.format("#  Target:  %-66s #\n", truncate(targetName, 66)));
        sb.append("################################################################################\n");
        return sb.toString();
    }

    private static String truncate(String s, int maxLength) {
        if (s == null) return "";
        if (s.length() <= maxLength) return s;
        return s.substring(0, maxLength - 3) + "...";
    }
}
