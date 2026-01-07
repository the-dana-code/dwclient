package com.danavalerie.matrixmudrelay.ui;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import java.awt.Color;
import java.awt.Font;

public final class ChitchatPane extends JTextPane {
    private static final Color BACKGROUND = new Color(15, 15, 18);
    private static final Color DEFAULT_COLOR = new Color(220, 220, 220);

    public ChitchatPane() {
        setEditable(false);
        setBackground(BACKGROUND);
        setForeground(DEFAULT_COLOR);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
    }

    public void appendChitchatLine(String text, Color color) {
        if (text == null || text.isBlank()) {
            return;
        }
        String normalized = ensureTrailingNewline(text);
        Runnable appendTask = () -> {
            try {
                getDocument().insertString(getDocument().getLength(), normalized, buildAttributes(color));
            } catch (BadLocationException ignored) {
            }
            setCaretPosition(getDocument().getLength());
        };
        runOnEdt(appendTask);
    }

    private AttributeSet buildAttributes(Color color) {
        StyleContext context = StyleContext.getDefaultStyleContext();
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color != null ? color : DEFAULT_COLOR);
        return context.addAttributes(SimpleAttributeSet.EMPTY, attrs);
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
