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

import org.junit.jupiter.api.Test;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyleConstants;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MudOutputPaneTest {

    @Test
    public void testAwardedExperienceColoring() throws BadLocationException {
        MudOutputPane pane = new MudOutputPane();
        StyledDocument doc = (StyledDocument) pane.getDocument();
        
        String awardedLine = "You have been awarded 123164 experience points for completing this job.\n";
        pane.appendMudText(awardedLine);

        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
        } catch (Exception e) {
            e.printStackTrace();
        }

        // The line should be at the end of the document
        int start = 0; // Assuming it's the first line for simplicity in this test
        AttributeSet attributes = doc.getCharacterElement(start).getAttributes();
        
        Color fg = (Color) attributes.getAttribute(StyleConstants.Foreground);
        Boolean bold = (Boolean) attributes.getAttribute(StyleConstants.Bold);

        assertEquals(Color.WHITE, fg, "Foreground color should be WHITE for awarded experience lines");
        assertTrue(bold != null && bold, "Text should be bold for awarded experience lines");
    }

    @Test
    public void testFumbleAlertColoring() throws BadLocationException {
        MudOutputPane pane = new MudOutputPane();
        StyledDocument doc = (StyledDocument) pane.getDocument();

        String fumbleLine = "Whoops!  You tried to carry too many things and fumbled a heavy iron key.\n";
        pane.appendMudText(fumbleLine);

        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
        } catch (Exception e) {
            e.printStackTrace();
        }

        AttributeSet attributes = doc.getCharacterElement(0).getAttributes();

        Color fg = (Color) attributes.getAttribute(StyleConstants.Foreground);
        Color bg = (Color) attributes.getAttribute(StyleConstants.Background);
        Boolean bold = (Boolean) attributes.getAttribute(StyleConstants.Bold);

        assertEquals(Color.WHITE, fg, "Foreground color should be WHITE for fumble lines");
        assertEquals(Color.RED, bg, "Background color should be RED for fumble lines");
        assertTrue(bold != null && bold, "Text should be bold for fumble lines");
    }

    @Test
    public void testAppendCommandEchoEmpty() throws BadLocationException {
        MudOutputPane pane = new MudOutputPane();
        Document doc = pane.getDocument();
        int initialLength = doc.getLength();

        pane.appendCommandEcho("");
        
        // appendCommandEcho runs on EDT, so we need to wait or use invokeAndWait
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Now it should increase by at least 1 (the newline)
        assertTrue(doc.getLength() > initialLength, "Document length should increase for empty command echo (it should add a newline)");
        assertEquals("\n", doc.getText(initialLength, doc.getLength() - initialLength), "Should have appended a newline");
    }

    @Test
    public void testAppendCommandEchoNormal() throws BadLocationException {
        MudOutputPane pane = new MudOutputPane();
        Document doc = pane.getDocument();
        int initialLength = doc.getLength();

        pane.appendCommandEcho("look");

        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
        } catch (Exception e) {
            e.printStackTrace();
        }

        assertEquals("look\n", doc.getText(initialLength, doc.getLength() - initialLength));
    }
}

