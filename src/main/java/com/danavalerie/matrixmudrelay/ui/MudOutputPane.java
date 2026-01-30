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

package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.util.AnsiColorParser;
import com.danavalerie.matrixmudrelay.util.SoundUtils;
import com.danavalerie.matrixmudrelay.util.ThreadUtils;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class MudOutputPane extends JTextPane implements AutoScrollable {
    private static final Color SYSTEM_COLOR = new Color(120, 200, 255);
    private static final Color COMMAND_COLOR = new Color(255, 215, 0);
    private static final Color ERROR_COLOR = new Color(255, 80, 80);
    private static final Color DEFAULT_COLOR = new Color(220, 220, 220);
    private static final Color ALERT_FOREGROUND = new Color(0, 0, 0);
    private static final Color ALERT_BACKGROUND = new Color(255, 255, 255);
    private static final Color TELL_COLOR = new Color(255, 235, 80);
    private static final Color TALKER_COLOR = new Color(180, 120, 255);

    private List<AlertPattern> alertPatterns = new ArrayList<>();
    private final AnsiColorParser parser = new AnsiColorParser();
    private AttributeSet systemAttributes;
    private AttributeSet commandAttributes;
    private AttributeSet errorAttributes;
    private String pendingTail = "";
    private final StringBuilder pendingEntity = new StringBuilder();
    private Color pendingEntityColor;
    private boolean pendingEntityBold;
    private Color currentColor = AnsiColorParser.defaultColor();
    private boolean currentBold = false;
    private final StringBuilder lineBuffer = new StringBuilder();
    private int lineStartOffset = 0;
    private BiConsumer<String, Color> chitchatListener;
    private Consumer<String> lineListener;
    private boolean autoScroll = true;

    public MudOutputPane() {
        setEditable(false);
        updateTheme(true);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));

        // Hide the caret and prevent automatic scrolling on insertion
        javax.swing.text.DefaultCaret caret = new javax.swing.text.DefaultCaret() {
            @Override
            public void paint(java.awt.Graphics g) {
                // do nothing
            }
        };
        caret.setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        setCaret(caret);
    }

    public void updateTheme(boolean inverted) {
        // ALWAYS use dark theme for MUD output to preserve ANSI color readability
        setBackground(MapPanel.BACKGROUND_DARK);
        setForeground(MapPanel.FOREGROUND_LIGHT);

        if (currentColor.equals(MapPanel.FOREGROUND_DARK)) {
            currentColor = MapPanel.FOREGROUND_LIGHT;
        }

        StyleContext context = StyleContext.getDefaultStyleContext();

        SimpleAttributeSet system = new SimpleAttributeSet();
        StyleConstants.setForeground(system, SYSTEM_COLOR);
        StyleConstants.setBold(system, true);
        systemAttributes = context.addAttributes(SimpleAttributeSet.EMPTY, system);

        SimpleAttributeSet command = new SimpleAttributeSet();
        StyleConstants.setForeground(command, COMMAND_COLOR);
        commandAttributes = context.addAttributes(SimpleAttributeSet.EMPTY, command);

        SimpleAttributeSet error = new SimpleAttributeSet();
        StyleConstants.setForeground(error, ERROR_COLOR);
        StyleConstants.setBold(error, true);
        errorAttributes = context.addAttributes(SimpleAttributeSet.EMPTY, error);
    }

    public void appendMudText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String normalized = text.replace("\r", "");
        String combined = pendingTail + normalized;
        AnsiColorParser.ParseResult result = parser.parseStreaming(combined, currentColor, currentBold);
        pendingTail = result.tail();
        currentColor = result.color();
        currentBold = result.bold();
        appendSegments(decodeEntities(result.segments()));
    }

    public void appendSystemText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String normalized = ensureTrailingNewline(decodeEntitiesOnce(text));
        Runnable appendTask = () -> {
            try {
                getDocument().insertString(getDocument().getLength(), normalized, systemAttributes);
            } catch (BadLocationException ignored) {
            }
            if (autoScroll) {
                setCaretPosition(getDocument().getLength());
            }
        };
        runOnEdt(appendTask);
    }

    public void appendCommandEcho(String text) {
        if (text == null) {
            return;
        }
        String normalized = ensureTrailingNewline(decodeEntitiesOnce(text));
        Runnable appendTask = () -> {
            try {
                getDocument().insertString(getDocument().getLength(), normalized, commandAttributes);
            } catch (BadLocationException ignored) {
            }
            if (autoScroll) {
                setCaretPosition(getDocument().getLength());
            }
        };
        runOnEdt(appendTask);
    }

    public void appendErrorText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String normalized = ensureTrailingNewline(decodeEntitiesOnce(text));
        Runnable appendTask = () -> {
            try {
                getDocument().insertString(getDocument().getLength(), normalized, errorAttributes);
            } catch (BadLocationException ignored) {
            }
            if (autoScroll) {
                setCaretPosition(getDocument().getLength());
            }
        };
        runOnEdt(appendTask);
    }

    private void appendSegments(List<AnsiColorParser.Segment> segments) {
        if (segments.isEmpty()) {
            return;
        }
        Runnable appendTask = () -> {
            ThreadUtils.checkEdt();
            for (AnsiColorParser.Segment segment : segments) {
                appendSegment(segment);
            }
            if (autoScroll) {
                setCaretPosition(getDocument().getLength());
            }
        };
        runOnEdt(appendTask);
    }

    private void appendSegment(AnsiColorParser.Segment segment) {
        String text = segment.text();
        if (text.isEmpty()) {
            return;
        }
        AttributeSet attributes = buildAttributes(segment.color(), segment.bold());
        int index = 0;
        while (index < text.length()) {
            int newlineIndex = text.indexOf('\n', index);
            if (newlineIndex == -1) {
                String chunk = text.substring(index);
                insertChunk(chunk, attributes);
                lineBuffer.append(chunk);
                break;
            }
            String chunk = text.substring(index, newlineIndex + 1);
            insertChunk(chunk, attributes);
            lineBuffer.append(text, index, newlineIndex);
            handleCompletedLine();
            index = newlineIndex + 1;
        }
    }

    private void insertChunk(String chunk, AttributeSet attributes) {
        ThreadUtils.checkEdt();
        try {
            getDocument().insertString(getDocument().getLength(), chunk, attributes);
        } catch (BadLocationException ignored) {
        }
    }

    private void handleCompletedLine() {
        String fullLine = lineBuffer.toString();
        lineBuffer.setLength(0);
        String trimmed = fullLine.strip();
        AlertPattern alertPattern = matchAlert(trimmed);
        if (alertPattern != null) {
            int lineEndOffset = getDocument().getLength();
            try {
                getDocument().remove(lineStartOffset, lineEndOffset - lineStartOffset);
                AttributeSet attributes = buildAlertAttributes(alertPattern);
                getDocument().insertString(lineStartOffset, fullLine + "\n", attributes);
                if (alertPattern.sound() != null) {
                    alertPattern.sound().run();
                }
            } catch (BadLocationException ignored) {
            }
        }
        if (alertPattern != null && alertPattern.sendToChitchat() && chitchatListener != null) {
            Color color = alertPattern.foreground() != null ? alertPattern.foreground() : DEFAULT_COLOR;
            chitchatListener.accept(fullLine, color);
        }
        if (lineListener != null) {
            lineListener.accept(trimmed);
        }
        lineStartOffset = getDocument().getLength();
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    private AttributeSet buildAlertAttributes(AlertPattern alertPattern) {
        SimpleAttributeSet alert = new SimpleAttributeSet();
        Color foreground = alertPattern.foreground() != null ? alertPattern.foreground() : DEFAULT_COLOR;
        StyleConstants.setForeground(alert, foreground);
        if (alertPattern.background() != null) {
            StyleConstants.setBackground(alert, alertPattern.background());
        }
        if (alertPattern.bold()) {
            StyleConstants.setBold(alert, true);
        }
        return alert;
    }

    public void setTriggers(List<ClientConfig.Trigger> triggers) {
        List<AlertPattern> patterns = new ArrayList<>();
        for (ClientConfig.Trigger t : triggers) {
            try {
                Pattern p = Pattern.compile(t.pattern);
                Color fg = (t.foreground != null && !t.foreground.isBlank()) ? Color.decode(t.foreground) : null;
                Color bg = (t.background != null && !t.background.isBlank()) ? Color.decode(t.background) : null;
                Runnable sound = null;
                if (t.systemBeep) {
                    sound = SoundUtils::playBeep;
                } else if (t.useSoundFile && t.soundFile != null && !t.soundFile.isBlank()) {
                    final String soundFile = t.soundFile;
                    sound = () -> {
                        try {
                            SoundUtils.playSound(soundFile);
                        } catch (Exception e) {
                            System.err.println("Error playing sound: " + e.getMessage());
                        }
                    };
                }
                patterns.add(new AlertPattern(p, fg, bg, t.bold, sound, t.sendToChitchat));
            } catch (Exception e) {
                System.err.println("Error parsing trigger: " + t.pattern + " - " + e.getMessage());
            }
        }
        this.alertPatterns = patterns;
    }

    private AlertPattern matchAlert(String line) {
        if (line.isEmpty()) {
            return null;
        }
        for (AlertPattern alertPattern : alertPatterns) {
            if (alertPattern.pattern().matcher(line).matches()) {
                return alertPattern;
            }
        }
        return null;
    }

    private AttributeSet buildAttributes(Color color, boolean bold) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        StyleConstants.setBold(attrs, bold);
        return attrs;
    }

    private static void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private static String ensureTrailingNewline(String text) {
        if (text.endsWith("\n")) {
            return text;
        }
        return text + "\n";
    }

    public void setChitchatListener(BiConsumer<String, Color> chitchatListener) {
        this.chitchatListener = chitchatListener;
    }

    public void setLineListener(Consumer<String> lineListener) {
        this.lineListener = lineListener;
    }

    @Override
    public void setAutoScroll(boolean autoScroll) {
        this.autoScroll = autoScroll;
    }

    @Override
    public boolean isAutoScroll() {
        return autoScroll;
    }

    @Override
    public void scrollToBottom() {
        runOnEdt(() -> {
            setCaretPosition(getDocument().getLength());
            setAutoScroll(true);
        });
    }

    private List<AnsiColorParser.Segment> decodeEntities(List<AnsiColorParser.Segment> segments) {
        if (segments.isEmpty()) {
            return segments;
        }
        List<AnsiColorParser.Segment> decoded = new java.util.ArrayList<>();
        for (AnsiColorParser.Segment segment : segments) {
            String text = segment.text();
            if (text.isEmpty()) {
                continue;
            }
            StringBuilder literal = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (pendingEntity.length() == 0) {
                    if (ch == '&') {
                        flushLiteral(decoded, literal, segment.color(), segment.bold());
                        startPendingEntity(segment.color(), segment.bold());
                    } else {
                        literal.append(ch);
                    }
                } else {
                    if (ch == '&') {
                        flushPendingEntityLiteral(decoded);
                        startPendingEntity(segment.color(), segment.bold());
                    } else {
                        pendingEntity.append(ch);
                        if (ch == ';') {
                            String decodedEntity = decodeEntity(pendingEntity.toString());
                            appendSegment(decoded, decodedEntity, pendingEntityColor, pendingEntityBold);
                            pendingEntity.setLength(0);
                        }
                    }
                }
            }
            flushLiteral(decoded, literal, segment.color(), segment.bold());
        }
        return decoded;
    }

    private void startPendingEntity(Color color, boolean bold) {
        pendingEntity.setLength(0);
        pendingEntity.append('&');
        pendingEntityColor = color;
        pendingEntityBold = bold;
    }

    private void flushPendingEntityLiteral(List<AnsiColorParser.Segment> segments) {
        if (pendingEntity.length() == 0) {
            return;
        }
        appendSegment(segments, pendingEntity.toString(), pendingEntityColor, pendingEntityBold);
        pendingEntity.setLength(0);
    }

    private static void flushLiteral(List<AnsiColorParser.Segment> segments,
                                     StringBuilder literal,
                                     Color color,
                                     boolean bold) {
        if (literal.length() == 0) {
            return;
        }
        appendSegment(segments, literal.toString(), color, bold);
        literal.setLength(0);
    }

    private static void appendSegment(List<AnsiColorParser.Segment> segments,
                                      String text,
                                      Color color,
                                      boolean bold) {
        if (text.isEmpty()) {
            return;
        }
        if (!segments.isEmpty()) {
            AnsiColorParser.Segment last = segments.get(segments.size() - 1);
            if (last.color().equals(color) && last.bold() == bold) {
                segments.set(segments.size() - 1,
                        new AnsiColorParser.Segment(last.text() + text, color, bold));
                return;
            }
        }
        segments.add(new AnsiColorParser.Segment(text, color, bold));
    }

    private static String decodeEntity(String entity) {
        return switch (entity) {
            case "&lt;" -> "<";
            case "&gt;" -> ">";
            case "&amp;" -> "&";
            default -> entity;
        };
    }

    private static String decodeEntitiesOnce(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch != '&') {
                out.append(ch);
                i++;
                continue;
            }
            int end = text.indexOf(';', i + 1);
            if (end == -1) {
                out.append(text.substring(i));
                break;
            }
            String entity = text.substring(i, end + 1);
            out.append(decodeEntity(entity));
            i = end + 1;
        }
        return out.toString();
    }

    private record AlertPattern(Pattern pattern,
                                Color foreground,
                                Color background,
                                boolean bold,
                                Runnable sound,
                                boolean sendToChitchat) {
    }
}

