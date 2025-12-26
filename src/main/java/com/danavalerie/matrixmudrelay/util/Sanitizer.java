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

            if (ch == '\t' || ch == '\n' || ch == '\r') { sb.append(ch); continue; }
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

    public static String escapeHtml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            // only &lt; needs to be escaped for Element X
            switch (ch) {
                case '<' -> sb.append("&lt;");
//                case '>' -> sb.append("&gt;");
//                case '&' -> sb.append("&amp;");
//                case '"' -> sb.append("&quot;");
//                case '\'' -> sb.append("&#39;");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static final class MxpResult {
        public final String plain;
        public final String html;

        public MxpResult(String plain, String html) {
            this.plain = plain;
            this.html = html;
        }
    }

    public static MxpResult processMxp(String input) {
        if (input == null) return new MxpResult("", "");

        StringBuilder plain = new StringBuilder(input.length());
        StringBuilder html = new StringBuilder(input.length() + 32);
        html.append("<pre>");

        boolean fontOpen = false;

        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c == '<') {
                int j = input.indexOf('>', i);
                if (j != -1) {
                    String tag = input.substring(i + 1, j);
                    String lower = tag.toLowerCase().trim();

                    if (lower.equals("br")) {
                        plain.append("\n");
                        html.append("<br/>");
                        i = j;
                        continue;
                    }

                    if (lower.startsWith("c ") || lower.startsWith("color ") || isColorName(lower)) {
                        String color;
                        if (isColorName(lower)) {
                            color = lower;
                        } else {
                            color = tag.substring(lower.startsWith("c ") ? 2 : 6).trim();
                        }
                        if (fontOpen) html.append("</font>");
                        html.append("<font color=\"").append(color).append("\">");
                        fontOpen = true;
                        i = j;
                        continue;
                    }

                    if (lower.equals("/c") || lower.equals("/color") || lower.equals("/font")) {
                        if (fontOpen) {
                            html.append("</font>");
                            fontOpen = false;
                        }
                        i = j;
                        continue;
                    }

                    if (lower.startsWith("exit") || lower.equals("/exit") || lower.startsWith("!en")) {
                        i = j;
                        continue;
                    }

                    if (lower.matches("^[a-z0-9]+$")) {
                        i = j;
                        continue;
                    }
                }
            }

            plain.append(c);
            html.append(escapeHtml(String.valueOf(c)));
        }

        if (fontOpen) {
            html.append("</font>");
        }
        html.append("</pre>");

        return new MxpResult(plain.toString(), html.toString());
    }

    private static boolean isColorName(String s) {
        switch (s.toLowerCase()) {
            case "black", "red", "green", "yellow", "blue", "magenta", "cyan", "white",
                 "gray", "grey", "silver", "maroon", "olive", "navy", "purple", "teal" -> { return true; }
            default -> { return false; }
        }
    }
}
