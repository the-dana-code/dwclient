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

public final class TextImageRenderer {
    private static final int IMAGE_WIDTH = 640;
    private static final int PADDING = 12;
    private static final int LINE_GAP = 4;
    private static final Color COLOR_BACKGROUND = new Color(0, 0, 0);
    private static final Font TEXT_FONT = new Font("SansSerif", Font.BOLD, 16);

    private TextImageRenderer() {
    }

    public static RenderedImage render(String body, List<Segment> segments, Color fallbackColor) throws IOException {
        if (body == null) {
            throw new IllegalArgumentException("body is required");
        }
        Color safeFallback = fallbackColor == null ? Color.WHITE : fallbackColor;
        List<Segment> safeSegments = segments == null ? List.of() : segments;
        if (safeSegments.isEmpty()) {
            safeSegments = List.of(new Segment("", safeFallback));
        }

        FontMetrics metrics = metricsForFont(TEXT_FONT);
        int usableWidth = Math.max(1, IMAGE_WIDTH - (PADDING * 2));
        List<List<Segment>> wrapped = wrapSegments(safeSegments, metrics, usableWidth, safeFallback);
        if (wrapped.isEmpty()) {
            wrapped = List.of(List.of(new Segment("", safeFallback)));
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
        int y = PADDING + metrics.getAscent();
        for (List<Segment> row : wrapped) {
            int x = PADDING;
            for (Segment segment : row) {
                if (segment.text.isEmpty()) {
                    continue;
                }
                g2.setColor(segment.color);
                g2.drawString(segment.text, x, y);
                x += metrics.stringWidth(segment.text);
            }
            y += lineHeight + LINE_GAP;
        }

        g2.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new RenderedImage(body, out.toByteArray(), "image/png", IMAGE_WIDTH, imageHeight);
    }

    private static List<List<Segment>> wrapSegments(List<Segment> segments, FontMetrics metrics, int maxWidth,
                                                    Color fallbackColor) {
        List<List<Segment>> lines = new ArrayList<>();
        List<Token> tokens = tokenizeSegments(segments, fallbackColor);
        List<Segment> currentLine = new ArrayList<>();
        int currentWidth = 0;

        for (Token token : tokens) {
            if (token.text.isEmpty()) {
                continue;
            }
            int tokenWidth = metrics.stringWidth(token.text);
            if (currentLine.isEmpty()) {
                if (tokenWidth <= maxWidth) {
                    if (!token.isWhitespace) {
                        addSegment(currentLine, token.text, token.color);
                        currentWidth = tokenWidth;
                    }
                    continue;
                }
                SplitResult result = splitToken(token, metrics, maxWidth, currentLine, lines);
                currentLine = result.line;
                currentWidth = result.width;
                continue;
            }

            if (currentWidth + tokenWidth <= maxWidth) {
                addSegment(currentLine, token.text, token.color);
                currentWidth += tokenWidth;
                continue;
            }

            lines.add(currentLine);
            currentLine = new ArrayList<>();
            currentWidth = 0;
            if (token.isWhitespace) {
                continue;
            }
            if (tokenWidth <= maxWidth) {
                addSegment(currentLine, token.text, token.color);
                currentWidth = tokenWidth;
            } else {
                SplitResult result = splitToken(token, metrics, maxWidth, currentLine, lines);
                currentLine = result.line;
                currentWidth = result.width;
            }
        }

        if (currentLine.isEmpty()) {
            currentLine.add(new Segment("", fallbackColor));
        }
        lines.add(currentLine);
        return lines;
    }

    private static SplitResult splitToken(Token token, FontMetrics metrics, int maxWidth,
                                          List<Segment> currentLine, List<List<Segment>> lines) {
        String text = token.text;
        int start = 0;
        int currentWidth = 0;
        List<Segment> workingLine = currentLine;
        while (start < text.length()) {
            int end = start;
            int lastWidth = 0;
            while (end < text.length()) {
                String candidate = text.substring(start, end + 1);
                int width = metrics.stringWidth(candidate);
                if (width > maxWidth) {
                    break;
                }
                lastWidth = width;
                end++;
            }
            if (end == start) {
                end = start + 1;
                lastWidth = metrics.stringWidth(text.substring(start, end));
            }
            addSegment(workingLine, text.substring(start, end), token.color);
            currentWidth = lastWidth;
            if (end < text.length()) {
                lines.add(workingLine);
                workingLine = new ArrayList<>();
                currentWidth = 0;
            }
            start = end;
        }
        return new SplitResult(workingLine, currentWidth);
    }

    private static List<Token> tokenizeSegments(List<Segment> segments, Color fallbackColor) {
        List<Token> tokens = new ArrayList<>();
        for (Segment segment : segments) {
            String text = segment.text;
            if (text.isEmpty()) {
                continue;
            }
            StringBuilder current = new StringBuilder();
            boolean currentWhitespace = Character.isWhitespace(text.charAt(0));
            Color color = segment.color == null ? fallbackColor : segment.color;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                boolean isWhitespace = Character.isWhitespace(ch);
                if (isWhitespace != currentWhitespace) {
                    tokens.add(new Token(current.toString(), color, currentWhitespace));
                    current.setLength(0);
                    currentWhitespace = isWhitespace;
                }
                current.append(ch);
            }
            if (!current.isEmpty()) {
                tokens.add(new Token(current.toString(), color, currentWhitespace));
            }
        }
        return tokens;
    }

    private static void addSegment(List<Segment> line, String text, Color color) {
        if (text.isEmpty()) {
            return;
        }
        if (!line.isEmpty()) {
            Segment last = line.get(line.size() - 1);
            if (last.color.equals(color)) {
                line.set(line.size() - 1, new Segment(last.text + text, color));
                return;
            }
        }
        line.add(new Segment(text, color));
    }

    private static FontMetrics metricsForFont(Font font) {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        g2.dispose();
        return fm;
    }

    public record Segment(String text, Color color) {
    }

    private static final class Token {
        private final String text;
        private final Color color;
        private final boolean isWhitespace;

        private Token(String text, Color color, boolean isWhitespace) {
            this.text = text;
            this.color = color;
            this.isWhitespace = isWhitespace;
        }
    }

    private static final class SplitResult {
        private final List<Segment> line;
        private final int width;

        private SplitResult(List<Segment> line, int width) {
            this.line = line;
            this.width = width;
        }
    }

    public record RenderedImage(String body, byte[] data, String mimeType, int width, int height) {
    }
}
