package com.danavalerie.matrixmudrelay.util;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class AnsiColorParser {
    private static final Color DEFAULT_COLOR = new Color(220, 220, 220);

    public record Segment(String text, Color color, boolean bold) {
    }

    public List<Segment> parse(String input) {
        ParseResult result = parseStreaming(input);
        return result.segments();
    }

    public ParseResult parseStreaming(String input) {
        List<Segment> segments = new ArrayList<>();
        if (input == null || input.isEmpty()) {
            return new ParseResult(segments, "");
        }

        StringBuilder buffer = new StringBuilder();
        Color currentColor = DEFAULT_COLOR;
        boolean bold = false;

        int length = input.length();
        int i = 0;
        String tail = "";
        while (i < length) {
            char ch = input.charAt(i);
            if (ch == 0x1b && i + 1 < length && input.charAt(i + 1) == '[') {
                int end = findAnsiEnd(input, i + 2);
                if (end != -1) {
                    flush(buffer, segments, currentColor, bold);
                    String code = input.substring(i + 2, end);
                    AnsiState state = applySgr(code, currentColor, bold);
                    currentColor = state.color();
                    bold = state.bold();
                    i = end + 1;
                    continue;
                }
                tail = input.substring(i);
                break;
            }

            if (ch == '<') {
                flush(buffer, segments, currentColor, bold);
                int end = findTagEnd(input, i + 1);
                if (end != -1) {
                    String tag = input.substring(i + 1, end);
                    String lower = tag.toLowerCase().trim();
                    if (lower.equals("br")) {
                        buffer.append('\n');
                        i = end + 1;
                        continue;
                    }
                    if (lower.startsWith("c ") || lower.startsWith("color ") || isColorName(lower)) {
                        flush(buffer, segments, currentColor, bold);
                        String colorSpec = lower;
                        if (!isColorName(lower)) {
                            colorSpec = tag.substring(lower.startsWith("c ") ? 2 : 6).trim();
                        }
                        currentColor = parseColor(colorSpec, currentColor);
                        i = end + 1;
                        continue;
                    }
                    if (lower.startsWith("font")) {
                        String colorSpec = extractAttribute(tag, "color");
                        if (colorSpec != null) {
                            flush(buffer, segments, currentColor, bold);
                            currentColor = parseColor(colorSpec, currentColor);
                        }
                        i = end + 1;
                        continue;
                    }
                    if (lower.equals("/c") || lower.equals("/color") || lower.equals("/font")) {
                        flush(buffer, segments, currentColor, bold);
                        currentColor = DEFAULT_COLOR;
                        i = end + 1;
                        continue;
                    }
                    i = end + 1;
                    continue;
                }
                tail = input.substring(i);
                break;
            }

            if (isPrintable(ch)) {
                buffer.append(ch);
            }
            i++;
        }

        flush(buffer, segments, currentColor, bold);
        return new ParseResult(segments, tail);
    }

    private static boolean isPrintable(char ch) {
        if (ch == '\n' || ch == '\t') {
            return true;
        }
        return ch >= 0x20 && ch != 0x7F;
    }

    private static void flush(StringBuilder buffer, List<Segment> segments, Color color, boolean bold) {
        if (buffer.length() == 0) {
            return;
        }
        segments.add(new Segment(buffer.toString(), color, bold));
        buffer.setLength(0);
    }

    private static AnsiState applySgr(String code, Color current, boolean bold) {
        if (code.isBlank()) {
            return new AnsiState(DEFAULT_COLOR, false);
        }
        String[] parts = code.split(";");
        int idx = 0;
        Color color = current;
        boolean nextBold = bold;
        while (idx < parts.length) {
            String part = parts[idx];
            int value = parseInt(part, -1);
            if (value == 0) {
                color = DEFAULT_COLOR;
                nextBold = false;
            } else if (value == 1) {
                nextBold = true;
            } else if (value == 22) {
                nextBold = false;
            } else if (value == 39) {
                color = DEFAULT_COLOR;
            } else if (value >= 30 && value <= 37) {
                color = basicColor(value - 30, false);
            } else if (value >= 90 && value <= 97) {
                color = basicColor(value - 90, true);
            } else if (value == 38) {
                if (idx + 1 < parts.length && "5".equals(parts[idx + 1])) {
                    int colorIndex = parseInt(parts[idx + 2], -1);
                    if (colorIndex >= 0) {
                        color = xtermColor(colorIndex);
                    }
                    idx += 2;
                } else if (idx + 1 < parts.length && "2".equals(parts[idx + 1])) {
                    int r = parseInt(parts[idx + 2], -1);
                    int g = parseInt(parts[idx + 3], -1);
                    int b = parseInt(parts[idx + 4], -1);
                    if (r >= 0 && g >= 0 && b >= 0) {
                        color = new Color(clamp(r), clamp(g), clamp(b));
                    }
                    idx += 4;
                }
            }
            idx++;
        }
        return new AnsiState(color, nextBold);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Color basicColor(int index, boolean bright) {
        return switch (index) {
            case 0 -> bright ? new Color(100, 100, 100) : new Color(0, 0, 0);
            case 1 -> bright ? new Color(255, 85, 85) : new Color(170, 0, 0);
            case 2 -> bright ? new Color(85, 255, 85) : new Color(0, 170, 0);
            case 3 -> bright ? new Color(255, 255, 85) : new Color(170, 85, 0);
            case 4 -> bright ? new Color(85, 85, 255) : new Color(0, 0, 170);
            case 5 -> bright ? new Color(255, 85, 255) : new Color(170, 0, 170);
            case 6 -> bright ? new Color(85, 255, 255) : new Color(0, 170, 170);
            case 7 -> bright ? new Color(255, 255, 255) : new Color(170, 170, 170);
            default -> DEFAULT_COLOR;
        };
    }

    private static Color xtermColor(int index) {
        if (index < 0) {
            return DEFAULT_COLOR;
        }
        if (index < 16) {
            return basicColor(index % 8, index >= 8);
        }
        if (index >= 16 && index <= 231) {
            int idx = index - 16;
            int r = idx / 36;
            int g = (idx / 6) % 6;
            int b = idx % 6;
            return new Color(scaleXterm(r), scaleXterm(g), scaleXterm(b));
        }
        if (index >= 232 && index <= 255) {
            int gray = 8 + (index - 232) * 10;
            return new Color(gray, gray, gray);
        }
        return DEFAULT_COLOR;
    }

    private static int scaleXterm(int value) {
        return value == 0 ? 0 : 55 + (value * 40);
    }

    private static Color parseColor(String value, Color fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("#") && trimmed.length() == 7) {
            try {
                int rgb = Integer.parseInt(trimmed.substring(1), 16);
                return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        String lower = trimmed.toLowerCase();
        return switch (lower) {
            case "black" -> Color.BLACK;
            case "red" -> Color.RED;
            case "green" -> Color.GREEN;
            case "yellow" -> Color.YELLOW;
            case "blue" -> Color.BLUE;
            case "magenta" -> Color.MAGENTA;
            case "cyan" -> Color.CYAN;
            case "white" -> Color.WHITE;
            case "gray", "grey" -> Color.GRAY;
            case "silver" -> new Color(192, 192, 192);
            case "maroon" -> new Color(128, 0, 0);
            case "olive" -> new Color(128, 128, 0);
            case "navy" -> new Color(0, 0, 128);
            case "purple" -> new Color(128, 0, 128);
            case "teal" -> new Color(0, 128, 128);
            default -> fallback;
        };
    }

    private static String extractAttribute(String tag, String attr) {
        String lower = tag.toLowerCase();
        String needle = attr.toLowerCase() + "=";
        int idx = lower.indexOf(needle);
        if (idx == -1) {
            return null;
        }
        int start = idx + needle.length();
        if (start >= tag.length()) {
            return null;
        }
        char quote = tag.charAt(start);
        int end;
        if (quote == '"' || quote == '\'') {
            end = tag.indexOf(quote, start + 1);
            if (end == -1) {
                return null;
            }
            return tag.substring(start + 1, end).trim();
        }
        end = tag.indexOf(' ', start);
        if (end == -1) {
            end = tag.length();
        }
        return tag.substring(start, end).trim();
    }

    private static boolean isColorName(String s) {
        switch (s.toLowerCase()) {
            case "black", "red", "green", "yellow", "blue", "magenta", "cyan", "white",
                    "gray", "grey", "silver", "maroon", "olive", "navy", "purple", "teal" -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private record AnsiState(Color color, boolean bold) {
    }

    public record ParseResult(List<Segment> segments, String tail) {
    }

    private static int findTagEnd(String input, int start) {
        boolean inQuote = false;
        char quoteChar = 0;
        int len = input.length();
        for (int i = start; i < len; i++) {
            char ch = input.charAt(i);
            if ((ch == '"' || ch == '\'') && (i == start || input.charAt(i - 1) != '\\')) {
                if (inQuote && ch == quoteChar) {
                    inQuote = false;
                    quoteChar = 0;
                } else if (!inQuote) {
                    inQuote = true;
                    quoteChar = ch;
                }
                continue;
            }
            if (ch == '>' && !inQuote) {
                return i;
            }
        }
        return -1;
    }

    private static int findAnsiEnd(String input, int start) {
        int m = input.indexOf('m', start);
        int z = input.indexOf('z', start);
        if (m == -1) {
            return z;
        }
        if (z == -1) {
            return m;
        }
        return Math.min(m, z);
    }
}
