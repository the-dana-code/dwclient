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

        List<String> commands = new java.util.concurrent.CopyOnWriteArrayList<>();
        UULibraryButtonPanel panel = new UULibraryButtonPanel(commands::add);

        Component[] components = panel.getComponents();
        assertEquals(4, components.length);

        assertTrue(components[0] instanceof JLabel);
        assertEquals("forward", ((JLabel)components[0]).getText());

        assertTrue(components[1] instanceof JLabel);
        assertEquals("left", ((JLabel)components[1]).getText());

        assertTrue(components[2] instanceof JLabel);
        assertEquals("right", ((JLabel)components[2]).getText());

        assertTrue(components[3] instanceof JLabel);
        assertEquals("backward", ((JLabel)components[3]).getText());

        JLabel forwardBtn = (JLabel) components[0];

        // Test action and disabling
        assertTrue(forwardBtn.isEnabled());
        
        click(forwardBtn);
        waitForCommands(commands, 2);
        assertEquals(2, commands.size()); // forward command + look distortion
        assertEquals("forward", commands.get(0));
        assertEquals("look distortion", commands.get(1));

        // Should be disabled now
        assertFalse(forwardBtn.isEnabled());
        assertFalse(((JLabel)components[1]).isEnabled());

        // Clicking again when disabled should not add commands
        click(forwardBtn);
        assertEquals(2, commands.size());

        // Test re-enabling
        panel.setButtonsEnabled(true);
        assertTrue(forwardBtn.isEnabled());
        
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
    public void testColors() {
        UULibraryButtonPanel panel = new UULibraryButtonPanel(cmd -> {});
        panel.updateTheme(Color.BLACK, Color.WHITE);

        JLabel forwardBtn = (JLabel) panel.getComponents()[0];

        // Enabled should be red
        panel.setButtonsEnabled(true);
        assertEquals(Color.RED, forwardBtn.getBackground());
        assertEquals(Color.WHITE, forwardBtn.getForeground());

        // Disabled should be theme background (BLACK)
        panel.setButtonsEnabled(false);
        assertEquals(Color.BLACK, forwardBtn.getBackground());
        assertEquals(Color.WHITE, forwardBtn.getForeground());
    }
}
