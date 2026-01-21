package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.RoomNoteService;
import com.danavalerie.matrixmudrelay.core.data.RoomButton;
import com.danavalerie.matrixmudrelay.util.BackgroundSaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class RoomButtonBarPanelTest {
    @AfterEach
    public void tearDown() {
        BackgroundSaver.waitForIdle();
    }

    @Test
    public void testAlignment(@TempDir Path tempDir) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RoomNoteService service = new RoomNoteService(tempDir.resolve("notes.json"));
            RoomButtonBarPanel panel = new RoomButtonBarPanel(service, cmd -> {});
            
            LayoutManager layout = panel.getLayout();
            assertTrue(layout instanceof FlowLayout);
            assertEquals(FlowLayout.RIGHT, ((FlowLayout)layout).getAlignment());
        });
    }

    @Test
    public void testWrapping(@TempDir Path tempDir) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RoomNoteService service = new RoomNoteService(tempDir.resolve("notes.json"));
            RoomButtonBarPanel panel = new RoomButtonBarPanel(service, cmd -> {});
            
            // Add many buttons
            for (int i = 0; i < 20; i++) {
                service.addButton("room1", "Room 1", new RoomButton("Button " + i, "cmd " + i));
            }
            panel.updateRoom("room1", "Room 1");
            
            // Set a narrow width
            panel.setSize(200, 100);
            
            Dimension prefSize = panel.getPreferredSize();
            
            // With 20 buttons and 200px width, it MUST wrap to multiple lines.
            // A single button is usually at least 20-30px high.
            // Multiple lines should be > 50px.
            assertTrue(prefSize.height > 40, "Preferred height should reflect multiple lines, but was " + prefSize.height);
            
            // Now set a very wide width
            panel.setSize(2000, 100);
            Dimension widePrefSize = panel.getPreferredSize();
            
            assertTrue(widePrefSize.height < prefSize.height, "Height should be smaller when wide enough for one line");
        });
    }

    @Test
    public void testEmptyPanelHeightAndBorder(@TempDir Path tempDir) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RoomNoteService service = new RoomNoteService(tempDir.resolve("notes.json"));
            RoomButtonBarPanel panel = new RoomButtonBarPanel(service, cmd -> {});
            
            // Empty panel
            panel.updateRoom("room1", "Room 1");
            
            Dimension prefSize = panel.getPreferredSize();
            assertTrue(prefSize.height >= 24, "Empty panel should have minimum height of 24, but was " + prefSize.height);
            
            assertNotNull(panel.getBorder(), "Panel should have a border even when empty");
        });
    }

    @Test
    public void testFontChange(@TempDir Path tempDir) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RoomNoteService service = new RoomNoteService(tempDir.resolve("notes_font.json"));
            RoomButtonBarPanel panel = new RoomButtonBarPanel(service, cmd -> {});
            service.addButton("room1", "Room 1", new RoomButton("Button 1", "cmd 1"));
            panel.updateRoom("room1", "Room 1");

            Font newFont = new Font("Monospaced", Font.BOLD, 20);
            panel.onFontChange(newFont);

            Component[] components = panel.getComponents();
            assertTrue(components.length > 0);
            for (Component c : components) {
                if (c instanceof JButton) {
                    assertEquals(newFont, c.getFont());
                }
            }

            // Verify that NEW buttons also get the font
            service.addButton("room1", "Room 1", new RoomButton("Button 2", "cmd 2"));
            panel.updateRoom("room1", "Room 1"); // This calls refreshButtons()

            components = panel.getComponents();
            assertEquals(2, components.length);
            for (Component c : components) {
                if (c instanceof JButton) {
                    assertEquals(newFont, c.getFont());
                }
            }
        });
    }
}
