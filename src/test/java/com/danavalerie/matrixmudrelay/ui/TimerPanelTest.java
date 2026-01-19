package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.core.TimerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
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
}
