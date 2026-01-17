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
    private static final Color DEFAULT_COLOR = new Color(220, 220, 220);

    public ChitchatPane() {
        setEditable(false);
        updateTheme(true);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
    }

    public void updateTheme(boolean inverted) {
        // ALWAYS use dark theme for chitchat/tells
        setBackground(MapPanel.BACKGROUND_DARK);
        setForeground(MapPanel.FOREGROUND_LIGHT);
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

