package com.danavalerie.matrixmudrelay.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

public class KeepOpenRadioMenuItemTest {

    @Test
    public void testSelectionOrder() {
        KeepOpenRadioMenuItem item1 = new KeepOpenRadioMenuItem("Item 1", true, null, null);
        KeepOpenRadioMenuItem item2 = new KeepOpenRadioMenuItem("Item 2", false, null, null);
        KeepOpenRadioMenuItem.RadioMenuGroup group = new KeepOpenRadioMenuItem.RadioMenuGroup();
        group.add(item1);
        group.add(item2);
        
        // We need to re-initialize them with the group correctly if we want to use the group
        item1 = new KeepOpenRadioMenuItem("Item 1", true, group, null);
        item2 = new KeepOpenRadioMenuItem("Item 2", false, group, null);
        
        final KeepOpenRadioMenuItem finalItem2 = item2;
        AtomicBoolean stateInListener = new AtomicBoolean();
        
        item2.addActionListener(e -> {
            stateInListener.set(finalItem2.isSelected());
        });
        
        // Initial state of item2 is false.
        // When clicked, it should become true.
        item2.doClick();
        
        assertTrue(item2.isSelected(), "Item 2 should be selected after click");
        assertTrue(stateInListener.get(), "Listener should have seen the NEW state (true)");
        assertFalse(item1.isSelected(), "Item 1 should be deselected");
    }
}
