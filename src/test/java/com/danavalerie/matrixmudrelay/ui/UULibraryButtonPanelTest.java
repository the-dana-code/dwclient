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
        List<String> commands = new ArrayList<>();
        UULibraryButtonPanel panel = new UULibraryButtonPanel(commands::add);
        
        Component[] components = panel.getComponents();
        assertEquals(18, components.length);
        
        assertTrue(components[0] instanceof JButton);
        assertEquals("1", ((JButton)components[0]).getText());
        
        assertEquals("16", ((JButton)components[15]).getText());
        assertEquals("G", ((JButton)components[16]).getText());
        assertEquals("X", ((JButton)components[17]).getText());
        
        // Test action
        ((JButton)components[0]).doClick();
        assertEquals(1, commands.size());
        assertEquals("1", commands.get(0));
        
        ((JButton)components[16]).doClick();
        assertEquals(2, commands.size());
        assertEquals("G", commands.get(1));
        
        ((JButton)components[17]).doClick();
        assertEquals(3, commands.size());
        assertEquals("X", commands.get(2));
    }
}
