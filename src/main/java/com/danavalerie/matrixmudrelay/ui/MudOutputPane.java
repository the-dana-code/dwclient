package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.util.AnsiColorParser;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import java.awt.Color;
import java.awt.Font;
import java.util.List;

public final class MudOutputPane extends JTextPane {
    private static final Color BACKGROUND = new Color(15, 15, 18);
    private static final Color SYSTEM_COLOR = new Color(120, 200, 255);
    private static final Color COMMAND_COLOR = new Color(255, 215, 0);
    private static final Color DEFAULT_COLOR = new Color(220, 220, 220);
    private final AnsiColorParser parser = new AnsiColorParser();
    private final AttributeSet systemAttributes;
    private final AttributeSet commandAttributes;
    private String pendingTail = "";
    private final StringBuilder pendingEntity = new StringBuilder();
    private Color pendingEntityColor;
    private boolean pendingEntityBold;
    private Color currentColor = AnsiColorParser.defaultColor();
    private boolean currentBold = false;

    public MudOutputPane() {
        setEditable(false);
        setBackground(BACKGROUND);
        setForeground(DEFAULT_COLOR);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));

        StyleContext context = StyleContext.getDefaultStyleContext();
        SimpleAttributeSet system = new SimpleAttributeSet();
        StyleConstants.setForeground(system, SYSTEM_COLOR);
        StyleConstants.setBold(system, true);
        systemAttributes = context.addAttributes(SimpleAttributeSet.EMPTY, system);

        SimpleAttributeSet command = new SimpleAttributeSet();
        StyleConstants.setForeground(command, COMMAND_COLOR);
        commandAttributes = context.addAttributes(SimpleAttributeSet.EMPTY, command);
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
            setCaretPosition(getDocument().getLength());
        };
        runOnEdt(appendTask);
    }

    public void appendCommandEcho(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String normalized = ensureTrailingNewline(decodeEntitiesOnce(text));
        Runnable appendTask = () -> {
            try {
                getDocument().insertString(getDocument().getLength(), normalized, commandAttributes);
            } catch (BadLocationException ignored) {
            }
            setCaretPosition(getDocument().getLength());
        };
        runOnEdt(appendTask);
    }

    private void appendSegments(List<AnsiColorParser.Segment> segments) {
        if (segments.isEmpty()) {
            return;
        }
        Runnable appendTask = () -> {
            for (AnsiColorParser.Segment segment : segments) {
                AttributeSet attributes = buildAttributes(segment.color(), segment.bold());
                try {
                    getDocument().insertString(getDocument().getLength(), segment.text(), attributes);
                } catch (BadLocationException ignored) {
                }
            }
            setCaretPosition(getDocument().getLength());
        };
        runOnEdt(appendTask);
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
}
