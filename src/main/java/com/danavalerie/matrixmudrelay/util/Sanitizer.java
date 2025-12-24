package com.danavalerie.matrixmudrelay.util;

public final class Sanitizer {
    private Sanitizer() {}

    /**
     * Remove control chars from MUD output before posting to Matrix.
     * Keeps tabs; removes ASCII control range and DEL.
     */
    public static String sanitizeMudOutput(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

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
