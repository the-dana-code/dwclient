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
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MudOutputPaneTest {

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

