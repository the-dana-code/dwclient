package com.danavalerie.matrixmudrelay.util;

import java.awt.Color;
import java.util.Locale;

public final class ColorLookup {
    private ColorLookup() {
    }

    public static Color fromName(String name, Color fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "black" -> Color.BLACK;
            case "white" -> Color.WHITE;
            case "red" -> Color.RED;
            case "green" -> Color.GREEN;
            case "blue" -> Color.BLUE;
            case "yellow" -> Color.YELLOW;
            case "cyan" -> Color.CYAN;
            case "magenta" -> Color.MAGENTA;
            case "gray", "grey" -> Color.GRAY;
            case "light_gray", "lightgrey" -> Color.LIGHT_GRAY;
            case "dark_gray", "darkgrey" -> Color.DARK_GRAY;
            case "orange" -> Color.ORANGE;
            case "pink" -> Color.PINK;
            default -> fallback;
        };
    }
}
