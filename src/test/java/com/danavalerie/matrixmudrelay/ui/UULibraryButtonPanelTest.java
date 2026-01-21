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
        // Label, ComboBox, Button
        assertEquals(3, components.length);

        assertTrue(components[0] instanceof JLabel);
        assertEquals("Target:", ((JLabel)components[0]).getText());

        assertTrue(components[1] instanceof JComboBox);
        JComboBox<?> combo = (JComboBox<?>) components[1];
        assertEquals(18, combo.getItemCount());
        assertEquals("Exit", combo.getItemAt(0));
        assertEquals("15", combo.getItemAt(15));
        assertEquals("16", combo.getItemAt(16));
        assertEquals("Gap", combo.getItemAt(17));

        assertTrue(components[2] instanceof JLabel);
        JLabel stepButton = (JLabel) components[2];
        assertEquals("Step", stepButton.getText());

        // Test action and disabling
        assertTrue(stepButton.isEnabled());
        assertTrue(combo.isEnabled());
        
        // Select "Gap" which is at index 16
        combo.setSelectedIndex(16);
        
        click(stepButton);
        waitForCommands(commands, 2);
        assertEquals(2, commands.size()); // step command + look distortion
        // Should be disabled now
        assertFalse(stepButton.isEnabled());
        assertFalse(combo.isEnabled());

        // Clicking again when disabled should not add commands
        click(stepButton);
        assertEquals(2, commands.size());

        // Test re-enabling
        panel.setButtonsEnabled(true);
        assertTrue(stepButton.isEnabled());
        assertTrue(combo.isEnabled());
        
        click(stepButton);
        waitForCommands(commands, 4);
        assertEquals(4, commands.size());
        
        assertTrue(List.of("fw", "bw", "lt", "rt").contains(commands.get(2)));
        assertEquals("look distortion", commands.get(3));
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

        JLabel stepButton = (JLabel) panel.getComponents()[2];

        // Enabled should be red
        panel.setButtonsEnabled(true);
        assertEquals(Color.RED, stepButton.getBackground());
        assertEquals(Color.WHITE, stepButton.getForeground());

        // Disabled should be theme background (BLACK)
        panel.setButtonsEnabled(false);
        assertEquals(Color.BLACK, stepButton.getBackground());
        assertEquals(Color.WHITE, stepButton.getForeground());
    }
}
