package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TriggerConfigDialogTest {

    @Test
    void testWavSettingSaved() throws Exception {
        // We need to run this in Headless mode if possible, but JDialog might need a display.
        // On many CI systems, it might fail.
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        SwingUtilities.invokeAndWait(() -> {
            List<ClientConfig.Trigger> triggers = new ArrayList<>();
            ClientConfig.Trigger t1 = new ClientConfig.Trigger();
            t1.pattern = "test";
            triggers.add(t1);

            TriggerConfigDialog dialog = new TriggerConfigDialog(null, triggers);
            
            // Find components
            JList<ClientConfig.Trigger> triggerList = findComponent(dialog, JList.class);
            JRadioButton wavRadio = findButtonByText(dialog, JRadioButton.class, "WAV");
            JTextField wavFileField = findWavFileField(dialog);
            
            triggerList.setSelectedIndex(0);
            
            // Simulate selecting WAV and entering a file
            wavRadio.setSelected(true);
            // Manually trigger action listeners since setSelected doesn't
            for (java.awt.event.ActionListener al : wavRadio.getActionListeners()) {
                al.actionPerformed(new java.awt.event.ActionEvent(wavRadio, java.awt.event.ActionEvent.ACTION_PERFORMED, ""));
            }
            
            wavFileField.setText("");
            // Document listener should trigger save
            
            List<ClientConfig.Trigger> resultTriggers = dialog.getTriggers();
            ClientConfig.Trigger savedTrigger = resultTriggers.get(0);
            
            assertEquals("", savedTrigger.soundFile, "Sound file should be empty string");
            assertFalse(savedTrigger.systemBeep, "System beep should be false");
            
            // Now simulate reloading by creating a new dialog with these triggers
            TriggerConfigDialog dialog2 = new TriggerConfigDialog(null, resultTriggers);
            JList<ClientConfig.Trigger> triggerList2 = findComponent(dialog2, JList.class);
            triggerList2.setSelectedIndex(0);
            
            JRadioButton wavRadio2 = findButtonByText(dialog2, JRadioButton.class, "WAV");
            assertTrue(wavRadio2.isSelected(), "WAV radio should be selected when reloading even if file is empty");
        });
    }

    private <T> T findComponent(Container container, Class<T> clazz) {
        for (Component c : container.getComponents()) {
            if (clazz.isInstance(c)) {
                return clazz.cast(c);
            }
            if (c instanceof Container) {
                T found = findComponent((Container) c, clazz);
                if (found != null) return found;
            }
        }
        return null;
    }

    private <T extends AbstractButton> T findButtonByText(Container container, Class<T> clazz, String text) {
        for (Component c : container.getComponents()) {
            if (clazz.isInstance(c) && text.equals(((T)c).getText())) {
                return clazz.cast(c);
            }
            if (c instanceof Container) {
                T found = findButtonByText((Container) c, clazz, text);
                if (found != null) return found;
            }
        }
        return null;
    }
    
    private JTextField findWavFileField(Container container) {
        // It's the one in the same panel as Browse... button or just after a label "WAV File:"
        // Looking at TriggerConfigDialog.java, it's added to a wavFilePanel which is then added to editorPanel.
        // Let's just find all JTextFields and pick the second one (first is pattern)
        List<JTextField> fields = new ArrayList<>();
        findAllComponents(container, JTextField.class, fields);
        return fields.size() > 1 ? fields.get(1) : fields.get(0);
    }

    private <T> void findAllComponents(Container container, Class<T> clazz, List<T> result) {
        for (Component c : container.getComponents()) {
            if (clazz.isInstance(c)) {
                result.add(clazz.cast(c));
            }
            if (c instanceof Container) {
                findAllComponents((Container) c, clazz, result);
            }
        }
    }
}
