package com.danavalerie.matrixmudrelay.ui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class UULibraryButtonPanelTest {
    @Test
    public void testComponents() {
        com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance().setRoomId("None");
        com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance().setRoomId("UULibrary");
        // Room (3,3) has all 4 cardinal exits: north, south, east, west
        com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance().setState(3, 3, com.danavalerie.matrixmudrelay.core.UULibraryService.Orientation.NORTH);

        List<String> commands = new java.util.concurrent.CopyOnWriteArrayList<>();
        UULibraryButtonPanel panel = new UULibraryButtonPanel(commands::add);
        panel.rebuildLayout();

        Component[] components = panel.getComponents();
        assertEquals(4, components.length);

        assertTrue(components[0] instanceof JLabel);
        assertEquals("forward", ((JLabel)components[0]).getText());

        assertTrue(components[1] instanceof JLabel);
        assertEquals("backward", ((JLabel)components[1]).getText());

        assertTrue(components[2] instanceof JLabel);
        assertEquals("right", ((JLabel)components[2]).getText());

        assertTrue(components[3] instanceof JLabel);
        assertEquals("left", ((JLabel)components[3]).getText());

        JLabel forwardBtn = (JLabel) components[0];

        // Test action and disabling
        assertTrue(forwardBtn.isEnabled());
        
        click(forwardBtn);
        waitForCommands(commands, 2);
        assertEquals(2, commands.size()); // forward command + look distortion
        assertEquals("forward", commands.get(0));
        assertEquals("look distortion", commands.get(1));

        // Should be disabled and "invisible" (empty text) now, but still taking space
        assertFalse(forwardBtn.isEnabled());
        assertTrue(forwardBtn.isVisible());
        assertEquals("", forwardBtn.getText());
        assertFalse(((JLabel)components[1]).isEnabled());
        assertTrue(((JLabel)components[1]).isVisible());
        assertEquals("", ((JLabel)components[1]).getText());

        // Clicking again when disabled/invisible should not add commands
        click(forwardBtn);
        assertEquals(2, commands.size());

        // Test re-enabling
        panel.setButtonsEnabled(true);
        assertTrue(forwardBtn.isEnabled());
        assertTrue(forwardBtn.isVisible());
        
        click(forwardBtn);
        waitForCommands(commands, 4);
        assertEquals(4, commands.size());
    }

    private void waitForCommands(List<String> commands, int expectedCount) {
        long start = System.currentTimeMillis();
        while (commands.size() < expectedCount && System.currentTimeMillis() - start < 2000) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void click(Component c) {
        for (MouseListener ml : c.getMouseListeners()) {
            ml.mousePressed(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 0, 0, 1, false));
            ml.mouseReleased(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 0, 0, 1, false));
            ml.mouseClicked(new MouseEvent(c, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false));
        }
    }

    @Test
    public void testVisibilityAndColors() {
        com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance().setRoomId("UULibrary");
        com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance().setState(3, 3, com.danavalerie.matrixmudrelay.core.UULibraryService.Orientation.NORTH);
        
        UULibraryButtonPanel panel = new UULibraryButtonPanel(cmd -> {});
        panel.rebuildLayout();
        panel.updateTheme(Color.BLACK, Color.WHITE);

        JLabel forwardBtn = (JLabel) panel.getComponents()[0];
        assertEquals("forward", forwardBtn.getText());

        // Enabled should be visible and use theme background (BLACK)
        panel.setButtonsEnabled(true);
        assertTrue(forwardBtn.isVisible());
        assertEquals(Color.BLACK, forwardBtn.getBackground());
        assertEquals(Color.WHITE, forwardBtn.getForeground());

        // Disabled should be empty/transparent but still visible (space-taking)
        panel.setButtonsEnabled(false);
        assertTrue(forwardBtn.isVisible());
        assertEquals("", forwardBtn.getText());
        assertFalse(forwardBtn.isOpaque());
    }

    @Test
    public void testLayoutStability() {
        com.danavalerie.matrixmudrelay.core.UULibraryService service = com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance();
        service.setRoomId("UULibrary");
        
        UULibraryButtonPanel panel = new UULibraryButtonPanel(cmd -> {});
        panel.updateTheme(Color.BLACK, Color.WHITE);
        panel.onFontChange(new Font("Arial", Font.PLAIN, 12));
        
        // State 1: 4 buttons
        service.setState(3, 3, com.danavalerie.matrixmudrelay.core.UULibraryService.Orientation.NORTH);
        panel.rebuildLayout();
        Dimension size4 = panel.getPreferredSize();
        
        // State 2: 2 buttons
        service.setState(1, 3, com.danavalerie.matrixmudrelay.core.UULibraryService.Orientation.NORTH);
        panel.rebuildLayout();
        Dimension size2 = panel.getPreferredSize();
        
        assertEquals(size4, size2, "Panel size should be identical between rooms with different exit counts");
        
        // State 3: Disabled
        panel.setButtonsEnabled(false);
        Dimension sizeDisabled = panel.getPreferredSize();
        assertEquals(size4, sizeDisabled, "Panel size should be identical when disabled");
    }

    @Test
    public void testFontChange() {
        com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance().setRoomId("UULibrary");
        UULibraryButtonPanel panel = new UULibraryButtonPanel(cmd -> {});
        panel.rebuildLayout();

        // Initially some default font
        Font originalFont = panel.getComponents()[0].getFont();
        
        Font newFont = new Font("Serif", Font.ITALIC, 24);
        panel.onFontChange(newFont);
        
        for (Component c : panel.getComponents()) {
            if (c instanceof JLabel) {
                assertEquals(newFont, c.getFont());
            }
        }
    }

    @Test
    public void testHorizontalLayoutOrdering() {
        com.danavalerie.matrixmudrelay.core.UULibraryService service = com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance();
        service.setRoomId("UULibrary");
        
        UULibraryButtonPanel panel = new UULibraryButtonPanel(cmd -> {});

        // Room (3,3) has all 4 cardinal exits: north, south, east, west
        // NORTH orientation: forward:N(0), right:E(1), backward:S(2), left:W(3)
        // Order {0,2,1,3} -> [0]fwd, [2]bk, [1]rt, [3]lt
        service.setState(3, 3, com.danavalerie.matrixmudrelay.core.UULibraryService.Orientation.NORTH);
        panel.rebuildLayout();
        assertButtonOrder(panel, "forward", "backward", "right", "left");

        // EAST orientation: forward:E(1), right:S(2), backward:W(3), left:N(0)
        // Order {0,2,1,3} -> [0]lt, [2]rt, [1]fwd, [3]bk
        service.setState(3, 3, com.danavalerie.matrixmudrelay.core.UULibraryService.Orientation.EAST);
        panel.rebuildLayout();
        assertButtonOrder(panel, "left", "right", "forward", "backward");

        // SOUTH orientation: forward:S(2), right:W(3), backward:N(0), left:E(1)
        // Order {0,2,1,3} -> [0]bk, [2]fwd, [1]lt, [3]rt
        service.setState(3, 3, com.danavalerie.matrixmudrelay.core.UULibraryService.Orientation.SOUTH);
        panel.rebuildLayout();
        assertButtonOrder(panel, "backward", "forward", "left", "right");

        // WEST orientation: forward:W(3), right:N(0), backward:E(1), left:S(2)
        // Order {0,2,1,3} -> [0]rt, [2]lt, [1]bk, [3]fwd
        service.setState(3, 3, com.danavalerie.matrixmudrelay.core.UULibraryService.Orientation.WEST);
        panel.rebuildLayout();
        assertButtonOrder(panel, "right", "left", "backward", "forward");
    }

    @Test
    public void testHiddenButtons() {
        com.danavalerie.matrixmudrelay.core.UULibraryService service = com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance();
        service.setRoomId("UULibrary");

        UULibraryButtonPanel panel = new UULibraryButtonPanel(cmd -> {});

        // Room (1,3) only has "east" and "west" exits.
        // If orientation is NORTH:
        // forward (NORTH) - unavailable
        // right (EAST) - available
        // backward (SOUTH) - unavailable
        // left (WEST) - available
        service.setState(1, 3, com.danavalerie.matrixmudrelay.core.UULibraryService.Orientation.NORTH);
        panel.rebuildLayout();
        assertButtonOrder(panel, "right", "left");

        // If orientation is EAST:
        // forward (EAST) - available
        // right (SOUTH) - unavailable
        // backward (WEST) - available
        // left (NORTH) - unavailable
        service.setState(1, 3, com.danavalerie.matrixmudrelay.core.UULibraryService.Orientation.EAST);
        panel.rebuildLayout();
        assertButtonOrder(panel, "forward", "backward");
    }

    @Test
    public void testInvisibleTakingSpace() {
        com.danavalerie.matrixmudrelay.core.UULibraryService service = com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance();
        service.setRoomId("UULibrary");
        // Room (1,3) only has "east" and "west" exits.
        service.setState(1, 3, com.danavalerie.matrixmudrelay.core.UULibraryService.Orientation.NORTH);

        UULibraryButtonPanel panel = new UULibraryButtonPanel(cmd -> {});
        panel.rebuildLayout();
        panel.setButtonsEnabled(true);

        Component[] components = panel.getComponents();
        assertEquals(4, components.length);

        JLabel btn0 = (JLabel) components[0]; // "right" (EAST)
        JLabel btn1 = (JLabel) components[1]; // "left" (WEST)
        JLabel btn2 = (JLabel) components[2]; // empty
        JLabel btn3 = (JLabel) components[3]; // empty

        assertEquals("right", btn0.getText());
        assertTrue(btn0.isVisible());
        assertTrue(btn0.isOpaque());

        assertEquals("left", btn1.getText());
        assertTrue(btn1.isVisible());
        assertTrue(btn1.isOpaque());

        assertEquals("", btn2.getText());
        assertTrue(btn2.isVisible()); // Taking space
        assertFalse(btn2.isOpaque()); // Invisible

        assertEquals("", btn3.getText());
        assertTrue(btn3.isVisible()); // Taking space
        assertFalse(btn3.isOpaque()); // Invisible
    }

    private void assertButtonOrder(UULibraryButtonPanel panel, String... expectedLabels) {
        Component[] components = panel.getComponents();
        assertEquals(4, components.length);
        for (int i = 0; i < 4; i++) {
            String expected = i < expectedLabels.length ? expectedLabels[i] : "";
            assertEquals(expected, ((JLabel)components[i]).getText(), "Mismatch at index " + i);
        }
    }
}
