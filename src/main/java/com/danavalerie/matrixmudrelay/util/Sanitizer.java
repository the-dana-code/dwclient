package com.danavalerie.matrixmudrelay.util;

import java.util.regex.Pattern;

public final class Sanitizer {
    private Sanitizer() {}

    private static final Pattern ANSI_PATTERN = Pattern.compile("\\e\\[[\\d;]*[^\\d;]");

    /**
     * Remove ANSI codes and other control chars from MUD output before posting to Matrix.
     * Keeps tabs; removes ASCII control range and DEL.
     */
    public static String sanitizeMudOutput(String s) {
        if (s == null || s.isEmpty()) return "";

        // First, strip ANSI escape sequences
        String stripped = ANSI_PATTERN.matcher(s).replaceAll("");

        StringBuilder sb = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char ch = stripped.charAt(i);

            if (ch == '\t') { sb.append(ch); continue; }
            if (ch < 0x20) continue;
            if (ch == 0x7F) continue;

            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * Basic input sanitization for MUD input lines.
     * - strips CR/LF to enforce line-oriented sending
     */
    public static String sanitizeMudInput(String s) {
        if (s == null) return "";
        return s.replace("\r", "").replace("\n", "");
    }
}
