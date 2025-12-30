package com.danavalerie.matrixmudrelay.core;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public final class TellHighlightRenderer {
    private static final int IMAGE_WIDTH = 640;
    private static final int PADDING = 12;
    private static final int LINE_GAP = 4;
    private static final Color COLOR_BACKGROUND = new Color(0, 0, 0);
    private static final Color COLOR_TEXT = new Color(255, 235, 59);
    private static final Font TEXT_FONT = new Font("SansSerif", Font.BOLD, 16);

    private TellHighlightRenderer() {
    }

    public static HighlightImage render(String line) throws IOException {
        if (line == null) {
            throw new IllegalArgumentException("line is required");
        }

        FontMetrics metrics = metricsForFont(TEXT_FONT);
        int usableWidth = Math.max(1, IMAGE_WIDTH - (PADDING * 2));
        List<String> wrapped = wrapLines(line, metrics, usableWidth);
        if (wrapped.isEmpty()) {
            wrapped = List.of("");
        }

        int lineHeight = metrics.getHeight();
        int imageHeight = PADDING + PADDING + (wrapped.size() * lineHeight) + ((wrapped.size() - 1) * LINE_GAP);
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(COLOR_BACKGROUND);
        g2.fillRect(0, 0, IMAGE_WIDTH, imageHeight);

        g2.setFont(TEXT_FONT);
        g2.setColor(COLOR_TEXT);
        int y = PADDING + metrics.getAscent();
        for (String row : wrapped) {
            g2.drawString(row, PADDING, y);
            y += lineHeight + LINE_GAP;
        }

        g2.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new HighlightImage(line, out.toByteArray(), "image/png", IMAGE_WIDTH, imageHeight);
    }

    private static List<String> wrapLines(String text, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] paragraphs = text.split("\\n", -1);
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i];
            wrapParagraph(paragraph, metrics, maxWidth, lines);
            if (i < paragraphs.length - 1) {
                lines.add("");
            }
        }
        return lines;
    }

    private static void wrapParagraph(String paragraph, FontMetrics metrics, int maxWidth, List<String> lines) {
        if (paragraph.isEmpty()) {
            lines.add("");
            return;
        }
        int start = 0;
        while (start < paragraph.length()) {
            int end = start;
            int lastSpace = -1;
            while (end < paragraph.length()) {
                String candidate = paragraph.substring(start, end + 1);
                if (metrics.stringWidth(candidate) > maxWidth) {
                    break;
                }
                if (paragraph.charAt(end) == ' ') {
                    lastSpace = end;
                }
                end++;
            }

            if (end == start) {
                lines.add(paragraph.substring(start, start + 1));
                start++;
                continue;
            }

            if (end >= paragraph.length()) {
                lines.add(paragraph.substring(start));
                break;
            }

            if (lastSpace >= start) {
                lines.add(paragraph.substring(start, lastSpace));
                start = lastSpace + 1;
            } else {
                lines.add(paragraph.substring(start, end));
                start = end;
            }
        }
    }

    private static FontMetrics metricsForFont(Font font) {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        g2.dispose();
        return fm;
    }

    public record HighlightImage(String body, byte[] data, String mimeType, int width, int height) {
    }
}
