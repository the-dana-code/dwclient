package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.core.TimerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.nio.file.Path;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimerPanelTest {

    @Test
    public void testTableSelectionMode(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.json");
        BotConfig config = new BotConfig();
        TimerService service = new TimerService(config, configPath);

        SwingUtilities.invokeAndWait(() -> {
            TimerPanel panel = new TimerPanel(service, () -> "TestChar");
            try {
                Field tableField = TimerPanel.class.getDeclaredField("table");
                tableField.setAccessible(true);
                JTable table = (JTable) tableField.get(panel);
                assertEquals(ListSelectionModel.SINGLE_SELECTION, table.getSelectionModel().getSelectionMode(), "JTable should be in single selection mode");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testFocusLostClearsSelection(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.json");
        BotConfig config = new BotConfig();
        TimerService service = new TimerService(config, configPath);
        service.setTimer("TestChar", "TestTimer", 10000);

        SwingUtilities.invokeAndWait(() -> {
            TimerPanel panel = new TimerPanel(service, () -> "TestChar");
            try {
                Field tableField = TimerPanel.class.getDeclaredField("table");
                tableField.setAccessible(true);
                JTable table = (JTable) tableField.get(panel);

                table.setRowSelectionInterval(0, 0);
                assertEquals(1, table.getSelectedRowCount());

                // Simulate focus lost to a component OUTSIDE TimerPanel
                JButton externalButton = new JButton("External");
                FocusEvent event = new FocusEvent(table, FocusEvent.FOCUS_LOST, false, externalButton);
                for (java.awt.event.FocusListener listener : table.getFocusListeners()) {
                    listener.focusLost(event);
                }

                assertEquals(0, table.getSelectedRowCount(), "Selection should be cleared when focus lost to external component");

                // Re-select
                table.setRowSelectionInterval(0, 0);
                assertEquals(1, table.getSelectedRowCount());

                // Simulate focus lost to a component INSIDE TimerPanel (e.g. addButton)
                Field addButtonField = TimerPanel.class.getDeclaredField("addButton");
                addButtonField.setAccessible(true);
                JButton addButton = (JButton) addButtonField.get(panel);

                FocusEvent internalEvent = new FocusEvent(table, FocusEvent.FOCUS_LOST, false, addButton);
                for (java.awt.event.FocusListener listener : table.getFocusListeners()) {
                    listener.focusLost(internalEvent);
                }

                assertEquals(1, table.getSelectedRowCount(), "Selection should NOT be cleared when focus lost to internal component");

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
