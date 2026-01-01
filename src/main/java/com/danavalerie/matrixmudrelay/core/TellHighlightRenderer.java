package com.danavalerie.matrixmudrelay.core;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TellHighlightRenderer {
    private static final Color COLOR_TEXT = new Color(255, 235, 59);
    private static final Color COLOR_TELL_SENDER = new Color(250, 128, 114);
    private static final Pattern TELL_SEND_PATTERN =
            Pattern.compile("(?i)<send\\s+href=['\\\"]tell[^>]*>(.*?)</send>");

    private TellHighlightRenderer() {
    }

    public static TextImageRenderer.RenderedImage render(String line) throws IOException {
        if (line == null) {
            throw new IllegalArgumentException("line is required");
        }

        List<Segment> segments = parseTellSegments(line);
        List<TextImageRenderer.Segment> rendererSegments = new ArrayList<>();
        for (Segment segment : segments) {
            rendererSegments.add(new TextImageRenderer.Segment(segment.text, segment.color));
        }
        return TextImageRenderer.render(line, rendererSegments, COLOR_TEXT);
    }

    private static List<Segment> parseTellSegments(String line) {
        List<Segment> segments = new ArrayList<>();
        Matcher matcher = TELL_SEND_PATTERN.matcher(line);
        int current = 0;
        while (matcher.find()) {
            if (matcher.start() > current) {
                segments.add(new Segment(line.substring(current, matcher.start()), COLOR_TEXT));
            }
            String sender = matcher.group(1);
            if (sender != null && !sender.isEmpty()) {
                segments.add(new Segment(sender, COLOR_TELL_SENDER));
            }
            current = matcher.end();
        }
        if (current < line.length()) {
            segments.add(new Segment(line.substring(current), COLOR_TEXT));
        }
        if (segments.isEmpty()) {
            segments.add(new Segment(line, COLOR_TEXT));
        }
        return segments;
    }

    private static final class Segment {
        private final String text;
        private final Color color;

        private Segment(String text, Color color) {
            this.text = text;
            this.color = color;
        }
    }

}
