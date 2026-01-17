/*
 * Lesa's Discworld MUD client.
 * Copyright (C) 2026 Dana Reese
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.danavalerie.matrixmudrelay.util;

import java.awt.image.BufferedImage;

/**
 * Natural dark-theme conversion that preserves hue (no RGB inversion).
 *
 * Algorithm:
 *  - RGB -> HSV
 *  - If saturation is low (grayscale-ish): remap Value with v' = (1 - v)^gamma
 *  - Else (colored): lift Value with v' = clamp(v * lift)
 *  - HSV -> RGB
 *
 * Fully in-memory: BufferedImage in -> BufferedImage out.
 */
public final class DarkThemeConverter {

    private static final float SAT_THRESHOLD = 0.10f; // s < threshold => treat as grayscale UI (background/text/lines)
    private static final float GAMMA = 0.72f;        // curve for grayscale Value inversion
    private static final float LIFT = 1.15f;         // boost for colored pixels

    private DarkThemeConverter() {}

    public static BufferedImage toDarkTheme(BufferedImage src) {
        final int w = src.getWidth();
        final int h = src.getHeight();

        // Preserve alpha; output in ARGB
        final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int argb = src.getRGB(x, y);
                out.setRGB(x, y, convertPixel(argb));
            }
        }

        return out;
    }

    /**
     * Converts a single ARGB pixel to its dark-theme equivalent.
     */
    public static int convertPixel(int argb) {
        final int a8 = (argb >>> 24) & 0xFF;
        final int r8 = (argb >>> 16) & 0xFF;
        final int g8 = (argb >>>  8) & 0xFF;
        final int b8 = (argb       ) & 0xFF;

        // Normalize RGB to 0..1
        final float r = r8 / 255.0f;
        final float g = g8 / 255.0f;
        final float b = b8 / 255.0f;

        // RGB -> HSV
        final float mx = max3(r, g, b);
        final float mn = min3(r, g, b);
        final float d  = mx - mn;

        final float v = mx;
        final float s = (mx == 0.0f) ? 0.0f : (d / mx);

        final float hHue = rgbToHue01(r, g, b, mx, d); // 0..1

        // Remap Value (brightness)
        final float v2;
        if (s < SAT_THRESHOLD) {
            // Grayscale-ish: invert brightness with a curve
            v2 = (float) Math.pow(clamp01(1.0f - v), GAMMA);
        } else {
            // Colored: slightly lift brightness so it reads on dark background
            v2 = clamp01(v * LIFT);
        }

        // HSV -> RGB
        final int rOut, gOut, bOut;
        {
            final float c = v2 * s;
            final float hp = hHue * 6.0f; // 0..6
            final float xcol = c * (1.0f - Math.abs((hp % 2.0f) - 1.0f));
            final float m = v2 - c;

            float rp, gp, bp;
            if (0.0f <= hp && hp < 1.0f)      { rp = c;    gp = xcol; bp = 0.0f; }
            else if (1.0f <= hp && hp < 2.0f) { rp = xcol; gp = c;    bp = 0.0f; }
            else if (2.0f <= hp && hp < 3.0f) { rp = 0.0f; gp = c;    bp = xcol; }
            else if (3.0f <= hp && hp < 4.0f) { rp = 0.0f; gp = xcol; bp = c;    }
            else if (4.0f <= hp && hp < 5.0f) { rp = xcol; gp = 0.0f; bp = c;    }
            else                               { rp = c;    gp = 0.0f; bp = xcol; }

            rOut = toByte(rp + m);
            gOut = toByte(gp + m);
            bOut = toByte(bp + m);
        }

        return (a8 << 24) | (rOut << 16) | (gOut << 8) | bOut;
    }

    /**
     * Converts a java.awt.Color to its dark-theme equivalent.
     */
    public static java.awt.Color convertColor(java.awt.Color color) {
        if (color == null) return null;
        return new java.awt.Color(convertPixel(color.getRGB()), true);
    }

    // --- Helpers ---

    private static float rgbToHue01(float r, float g, float b, float mx, float d) {
        if (d == 0.0f) return 0.0f;

        float hh;
        if (mx == r) {
            hh = ((g - b) / d);
            // ensure 0..6
            hh = (hh % 6.0f + 6.0f) % 6.0f;
        } else if (mx == g) {
            hh = ((b - r) / d) + 2.0f;
        } else {
            hh = ((r - g) / d) + 4.0f;
        }
        return (hh / 6.0f) % 1.0f;
    }

    private static float clamp01(float v) {
        return (v < 0.0f) ? 0.0f : ((v > 1.0f) ? 1.0f : v);
    }

    private static int toByte(float v01) {
        int v = Math.round(clamp01(v01) * 255.0f);
        return (v < 0) ? 0 : Math.min(v, 255);
    }

    private static float max3(float a, float b, float c) {
        return Math.max(a, Math.max(b, c));
    }

    private static float min3(float a, float b, float c) {
        return Math.min(a, Math.min(b, c));
    }
}

