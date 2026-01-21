package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.StatsHudRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    @Test
    void testGpRateSamplePersistenceAndRotation() throws Exception {
        final StatsPanel[] panelRef = new StatsPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panelRef[0] = new StatsPanel();
        });
        StatsPanel statsPanel = panelRef[0];

        // 1. Load samples
        List<Integer> initialSamples = Arrays.asList(10, 20, 30, 40, 50);
        SwingUtilities.invokeAndWait(() -> {
            statsPanel.loadGpSamples(initialSamples);
        });

        // 2. Verify rotation logic
        Method getSaveableSamples = StatsPanel.class.getDeclaredMethod("getSaveableGpSamples");
        getSaveableSamples.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Integer> saved = (List<Integer>) getSaveableSamples.invoke(statsPanel);
        assertEquals(initialSamples, saved, "Saved samples should match initial after load");

        // 3. Test rotation after recording
        Method recordGpRate = StatsPanel.class.getDeclaredMethod("recordGpRate", int.class);
        recordGpRate.setAccessible(true);

        SwingUtilities.invokeAndWait(() -> {
            try {
                // Should overwrite index 0 (value 10) because gpRateIndex was 0
                recordGpRate.invoke(statsPanel, 60);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // After recordGpRate:
        // gpRateSamples = [60, 20, 30, 40, 50]
        // gpRateIndex = 1
        // getSaveableSamples (rotating from index 1): [20, 30, 40, 50, 60]

        @SuppressWarnings("unchecked")
        List<Integer> rotated = (List<Integer>) getSaveableSamples.invoke(statsPanel);
        assertEquals(Arrays.asList(20, 30, 40, 50, 60), rotated, "Samples should be rotated correctly");
    }

    @Test
    void testCharacterSwitchingLoadsSamples() throws Exception {
        final StatsPanel[] panelRef = new StatsPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panelRef[0] = new StatsPanel();
        });
        StatsPanel statsPanel = panelRef[0];

        List<Integer> char1Samples = Arrays.asList(1, 2, 3, 4, 5);
        List<Integer> char2Samples = Arrays.asList(6, 7, 8, 9, 10);

        statsPanel.setCharacterGpSamplesLoader(name -> {
            if ("Char1".equals(name)) return char1Samples;
            if ("Char2".equals(name)) return char2Samples;
            return null;
        });

        Method getSaveableSamples = StatsPanel.class.getDeclaredMethod("getSaveableGpSamples");
        getSaveableSamples.setAccessible(true);

        SwingUtilities.invokeAndWait(() -> {
            statsPanel.updateStats(new StatsHudRenderer.StatsHudData("Char1", 100, 100, 50, 50, 0, 1000L));
        });
        SwingUtilities.invokeAndWait(() -> {});

        @SuppressWarnings("unchecked")
        List<Integer> saved1 = (List<Integer>) getSaveableSamples.invoke(statsPanel);
        assertEquals(char1Samples, saved1);

        SwingUtilities.invokeAndWait(() -> {
            statsPanel.updateStats(new StatsHudRenderer.StatsHudData("Char2", 100, 100, 50, 50, 0, 1000L));
        });
        SwingUtilities.invokeAndWait(() -> {});

        @SuppressWarnings("unchecked")
        List<Integer> saved2 = (List<Integer>) getSaveableSamples.invoke(statsPanel);
        assertEquals(char2Samples, saved2);
    }

    @Test
    void testHpRateSamplePersistenceAndRotation() throws Exception {
        final StatsPanel[] panelRef = new StatsPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panelRef[0] = new StatsPanel();
        });
        StatsPanel statsPanel = panelRef[0];

        // 1. Load samples
        List<Integer> initialSamples = Arrays.asList(5, 15, 25, 35, 45);
        SwingUtilities.invokeAndWait(() -> {
            statsPanel.loadHpSamples(initialSamples);
        });

        // 2. Verify rotation logic
        Method getSaveableSamples = StatsPanel.class.getDeclaredMethod("getSaveableHpSamples");
        getSaveableSamples.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Integer> saved = (List<Integer>) getSaveableSamples.invoke(statsPanel);
        assertEquals(initialSamples, saved, "Saved HP samples should match initial after load");

        // 3. Test rotation after recording
        Method recordHpRate = StatsPanel.class.getDeclaredMethod("recordHpRate", int.class);
        recordHpRate.setAccessible(true);

        SwingUtilities.invokeAndWait(() -> {
            try {
                recordHpRate.invoke(statsPanel, 55);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        @SuppressWarnings("unchecked")
        List<Integer> rotated = (List<Integer>) getSaveableSamples.invoke(statsPanel);
        assertEquals(Arrays.asList(15, 25, 35, 45, 55), rotated, "HP samples should be rotated correctly");
    }
}
