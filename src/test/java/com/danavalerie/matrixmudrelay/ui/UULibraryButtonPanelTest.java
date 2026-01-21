package com.danavalerie.matrixmudrelay.ui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class UULibraryButtonPanelTest {
    @Test
    public void testComponents() {
        com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance().setRoomId("None");
        com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance().setRoomId("UULibrary");

        List<String> commands = new ArrayList<>();
        UULibraryButtonPanel panel = new UULibraryButtonPanel(commands::add);

        Component[] components = panel.getComponents();
        // Label, ComboBox, Button
        assertEquals(3, components.length);

        assertTrue(components[0] instanceof JLabel);
        assertEquals("Target:", ((JLabel)components[0]).getText());

        assertTrue(components[1] instanceof JComboBox);
        JComboBox<?> combo = (JComboBox<?>) components[1];
        assertEquals(18, combo.getItemCount());
        assertEquals("1", combo.getItemAt(0));
        assertEquals("16", combo.getItemAt(15));
        assertEquals("Gap", combo.getItemAt(16));
        assertEquals("Exit", combo.getItemAt(17));

        assertTrue(components[2] instanceof JButton);
        JButton stepButton = (JButton) components[2];
        assertEquals("Step", stepButton.getText());

        // Test action and disabling
        assertTrue(stepButton.isEnabled());
        assertTrue(combo.isEnabled());
        
        // Select "Gap" which is at index 16
        combo.setSelectedIndex(16);
        
        stepButton.doClick();
        assertEquals(2, commands.size()); // step command + look distortion
        // Should stay enabled but logical state is inactive
        assertTrue(stepButton.isEnabled());
        assertTrue(combo.isEnabled());

        // Clicking again when logically disabled should not add commands
        stepButton.doClick();
        assertEquals(2, commands.size());

        // Test re-enabling
        panel.setButtonsEnabled(true);
        assertTrue(stepButton.isEnabled());
        assertTrue(combo.isEnabled());
        
        stepButton.doClick();
        assertEquals(4, commands.size());
        
        assertTrue(List.of("fw", "bw", "lt", "rt").contains(commands.get(0)));
        assertEquals("look distortion", commands.get(1));
    }

    @Test
    public void testColors() {
        UULibraryButtonPanel panel = new UULibraryButtonPanel(cmd -> {});
        panel.updateTheme(Color.BLACK, Color.WHITE);

        JButton stepButton = (JButton) panel.getComponents()[2];

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
