package com.danavalerie.matrixmudrelay.ui;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ThemeTest {

    @Test
    public void testMudOutputPaneTheme() {
        MudOutputPane pane = new MudOutputPane();
        
        // Dark mode (inverted = true)
        pane.updateTheme(true);
        assertEquals(MapPanel.BACKGROUND_DARK, pane.getBackground());
        assertEquals(MapPanel.FOREGROUND_LIGHT, pane.getForeground());
        
        // Light mode (inverted = false) - should STILL be dark
        pane.updateTheme(false);
        assertEquals(MapPanel.BACKGROUND_DARK, pane.getBackground());
        assertEquals(MapPanel.FOREGROUND_LIGHT, pane.getForeground());
    }

    @Test
    public void testChitchatPaneTheme() {
        ChitchatPane pane = new ChitchatPane();
        
        // Dark mode
        pane.updateTheme(true);
        assertEquals(MapPanel.BACKGROUND_DARK, pane.getBackground());
        assertEquals(MapPanel.FOREGROUND_LIGHT, pane.getForeground());
        
        // Light mode - should STILL be dark
        pane.updateTheme(false);
        assertEquals(MapPanel.BACKGROUND_DARK, pane.getBackground());
        assertEquals(MapPanel.FOREGROUND_LIGHT, pane.getForeground());
    }

    @Test
    public void testStatsPanelTheme() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            StatsPanel panel = new StatsPanel();

            // Dark mode
            panel.updateTheme(true);
            assertEquals(MapPanel.BACKGROUND_DARK, panel.getBackground());

            // Light mode
            panel.updateTheme(false);
            assertEquals(MapPanel.BACKGROUND_LIGHT, panel.getBackground());
        });
    }
}
