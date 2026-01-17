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
