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
    private static final Color DEFAULT_COLOR = new Color(220, 220, 220);
    private final AnsiColorParser parser = new AnsiColorParser();
    private final AttributeSet systemAttributes;
    private String pendingTail = "";

    public MudOutputPane() {
        setEditable(false);
        setBackground(BACKGROUND);
        setForeground(DEFAULT_COLOR);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

        StyleContext context = StyleContext.getDefaultStyleContext();
        SimpleAttributeSet system = new SimpleAttributeSet();
        StyleConstants.setForeground(system, SYSTEM_COLOR);
        StyleConstants.setBold(system, true);
        systemAttributes = context.addAttributes(SimpleAttributeSet.EMPTY, system);
    }

    public void appendMudText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String normalized = ensureTrailingNewline(text.replace("\r", ""));
        String combined = pendingTail + normalized;
        AnsiColorParser.ParseResult result = parser.parseStreaming(combined);
        pendingTail = result.tail();
        appendSegments(result.segments());
    }

    public void appendSystemText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String normalized = ensureTrailingNewline(text);
        Runnable appendTask = () -> {
            try {
                getDocument().insertString(getDocument().getLength(), normalized, systemAttributes);
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
}
