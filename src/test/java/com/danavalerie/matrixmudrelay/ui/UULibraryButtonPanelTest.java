package com.danavalerie.matrixmudrelay.ui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class UULibraryButtonPanelTest {
    @Test
    public void testButtons() {
        com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance().setRoomId("None");
        com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance().setRoomId("UULibrary");

        List<String> commands = new ArrayList<>();
        UULibraryButtonPanel panel = new UULibraryButtonPanel(commands::add);
        
        Component[] components = panel.getComponents();
        assertEquals(18, components.length);
        
        assertTrue(components[0] instanceof JButton);
        assertEquals("1", ((JButton)components[0]).getText());
        
        assertEquals("16", ((JButton)components[15]).getText());
        assertEquals("G", ((JButton)components[16]).getText());
        assertEquals("X", ((JButton)components[17]).getText());
        
        // Test action and disabling
        JButton btn1 = (JButton) components[0];
        assertTrue(btn1.isEnabled());
        btn1.doClick();
        assertEquals(1, commands.size());
        assertFalse(btn1.isEnabled());
        for (Component c : components) {
            assertFalse(c.isEnabled());
        }

        // Test re-enabling
        panel.setButtonsEnabled(true);
        assertTrue(btn1.isEnabled());
        for (Component c : components) {
            assertTrue(c.isEnabled());
        }
        
        ((JButton)components[16]).doClick(); // Button "G" -> Room 10 (12,6)
        assertEquals(2, commands.size());
        assertTrue(List.of("fw", "bw", "lt", "rt").contains(commands.get(1)));
    }
}
