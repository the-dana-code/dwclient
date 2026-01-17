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

package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.mud.CurrentRoomInfo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

public final class StatsHudRenderer {
    private static final int IMAGE_WIDTH = 420;
    private static final int PADDING = 16;
    private static final int HEADER_GAP = 10;
    private static final int BAR_HEIGHT = 24;
    private static final int BAR_GAP = 12;
    private static final Color COLOR_BACKGROUND = new Color(0, 0, 0);
    private static final Color COLOR_BAR_BG = new Color(26, 26, 26);
    private static final Color COLOR_BAR_BORDER = new Color(90, 90, 90);
    private static final Color COLOR_TEXT = new Color(255, 255, 255);
    private static final Color COLOR_HP = new Color(0, 150, 0);
    private static final Color COLOR_GP = new Color(0, 60, 160);
    private static final Color COLOR_BURDEN = new Color(0, 130, 0);
    private static final Color COLOR_XP = new Color(200, 110, 0);
    private static final long XP_CAP = 1_000_000L;
    private static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 18);
    private static final Font BAR_FONT = new Font("SansSerif", Font.PLAIN, 14);
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private StatsHudRenderer() {
    }

    public static StatsHudData extract(CurrentRoomInfo.Snapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, JsonElement> data = snapshot.data();
        JsonElement vitalsElement = data.get("char.vitals");
        if (vitalsElement == null || !vitalsElement.isJsonObject()) {
            return null;
        }
        JsonObject vitals = vitalsElement.getAsJsonObject();
        Integer hp = intValue(vitals, "hp");
        Integer maxHp = intValue(vitals, "maxhp");
        Integer gp = intValue(vitals, "gp");
        Integer maxGp = intValue(vitals, "maxgp");
        Integer burden = intValue(vitals, "burden");
        Long xp = longValue(vitals, "xp");
        if (hp == null || maxHp == null || gp == null || maxGp == null || burden == null || xp == null) {
            return null;
        }
        String name = extractCapname(data.get("char.info"));
        if (name == null || name.isBlank()) {
            name = "Unknown";
        }
        return new StatsHudData(name, hp, maxHp, gp, maxGp, burden, xp);
    }

    public static StatsHudImage render(StatsHudData data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("StatsHudData is required");
        }
        int barCount = 4;
        FontMetrics headerMetrics = metricsForFont(HEADER_FONT);
        int headerHeight = headerMetrics.getHeight();
        int imageHeight = PADDING + headerHeight + HEADER_GAP + (barCount * BAR_HEIGHT) + ((barCount - 1) * BAR_GAP) + PADDING;
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(COLOR_BACKGROUND);
        g2.fillRect(0, 0, IMAGE_WIDTH, imageHeight);

        g2.setFont(HEADER_FONT);
        g2.setColor(COLOR_TEXT);
        int headerY = PADDING + headerMetrics.getAscent();
        g2.drawString("Character: " + data.name(), PADDING, headerY);

        int barWidth = IMAGE_WIDTH - (PADDING * 2);
        int currentY = PADDING + headerHeight + HEADER_GAP;

        g2.setFont(BAR_FONT);
        drawBar(g2, PADDING, currentY, barWidth, BAR_HEIGHT, COLOR_HP,
                data.hp(), data.maxHp(), "HP " + format(data.hp()) + " / " + format(data.maxHp()));
        currentY += BAR_HEIGHT + BAR_GAP;

        drawBar(g2, PADDING, currentY, barWidth, BAR_HEIGHT, COLOR_GP,
                data.gp(), data.maxGp(), "GP " + format(data.gp()) + " / " + format(data.maxGp()));
        currentY += BAR_HEIGHT + BAR_GAP;

        int burdenValue = clamp(data.burden(), 0, 100);
        drawBar(g2, PADDING, currentY, barWidth, BAR_HEIGHT, COLOR_BURDEN,
                burdenValue, 100, "Burden " + burdenValue + "%");
        currentY += BAR_HEIGHT + BAR_GAP;

        long xpValue = Math.max(0, data.xp());
        double xpRatio = Math.min(1.0, xpValue / (double) XP_CAP);
        drawBar(g2, PADDING, currentY, barWidth, BAR_HEIGHT, COLOR_XP,
                xpRatio, "XP " + format(xpValue));

        g2.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        String body = "Stats for " + data.name();
        return new StatsHudImage(body, out.toByteArray(), "image/png", IMAGE_WIDTH, imageHeight);
    }

    private static void drawBar(Graphics2D g2, int x, int y, int width, int height, Color fill, int current, int max,
                                String label) {
        double ratio = max <= 0 ? 0 : Math.min(1.0, Math.max(0.0, current / (double) max));
        drawBar(g2, x, y, width, height, fill, ratio, label);
    }

    private static void drawBar(Graphics2D g2, int x, int y, int width, int height, Color fill, double ratio,
                                String label) {
        g2.setColor(COLOR_BAR_BG);
        g2.fillRect(x, y, width, height);
        int fillWidth = (int) Math.round(width * clampRatio(ratio));
        if (fillWidth > 0) {
            g2.setColor(fill);
            g2.fillRect(x, y, fillWidth, height);
        }
        g2.setColor(COLOR_BAR_BORDER);
        g2.drawRect(x, y, width, height);

        g2.setColor(COLOR_TEXT);
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(label);
        int textX = x + Math.max(0, (width - textWidth) / 2);
        int textY = y + ((height - fm.getHeight()) / 2) + fm.getAscent();
        g2.drawString(label, textX, textY);
    }

    private static FontMetrics metricsForFont(Font font) {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        g2.dispose();
        return fm;
    }

    private static double clampRatio(double ratio) {
        return Math.max(0.0, Math.min(1.0, ratio));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String format(long value) {
        return NUMBER_FORMAT.format(value);
    }

    private static Integer intValue(JsonObject obj, String key) {
        JsonElement val = obj.get(key);
        if (val == null || val.isJsonNull()) {
            return null;
        }
        try {
            return val.getAsInt();
        } catch (Exception e) {
            return null;
        }
    }

    private static Long longValue(JsonObject obj, String key) {
        JsonElement val = obj.get(key);
        if (val == null || val.isJsonNull()) {
            return null;
        }
        try {
            return val.getAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractCapname(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonElement capname = element.getAsJsonObject().get("capname");
        if (capname == null || capname.isJsonNull() || !capname.isJsonPrimitive()) {
            return null;
        }
        return capname.getAsString();
    }

    public record StatsHudData(String name, int hp, int maxHp, int gp, int maxGp, int burden, long xp) {
    }

    public record StatsHudImage(String body, byte[] data, String mimeType, int width, int height) {
    }
}

