package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.StatsHudRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class StatsPanelTest {

    @Test
    void testGetCurrentCharacterName() throws InterruptedException, InvocationTargetException {
        final StatsPanel[] panelRef = new StatsPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panelRef[0] = new StatsPanel();
        });

        StatsPanel statsPanel = panelRef[0];

        SwingUtilities.invokeAndWait(() -> {
            // Initial state should be null (representing "--")
            assertNull(statsPanel.getCurrentCharacterName(), "Initial name should be null");
        });

        SwingUtilities.invokeAndWait(() -> {
            // Update stats with a name
            StatsHudRenderer.StatsHudData data = new StatsHudRenderer.StatsHudData(
                    "TestChar", 100, 100, 50, 50, 0, 1000L
            );
            statsPanel.updateStats(data);
        });

        // Wait for updateStats' invokeLater to finish
        SwingUtilities.invokeAndWait(() -> {});

        SwingUtilities.invokeAndWait(() -> {
            assertEquals("TestChar", statsPanel.getCurrentCharacterName(), "Name should be TestChar after update");
        });

        SwingUtilities.invokeAndWait(() -> {
            // Set unavailable (back to "--")
            statsPanel.updateStats(null);
        });

        // Wait for updateStats' invokeLater to finish
        SwingUtilities.invokeAndWait(() -> {});

        SwingUtilities.invokeAndWait(() -> {
            assertNull(statsPanel.getCurrentCharacterName(), "Name should be null after setUnavailable");
        });
    }
}
