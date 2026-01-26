package com.danavalerie.matrixmudrelay.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

public class KeepOpenCheckBoxMenuItemTest {

    @Test
    public void testToggleOrder() {
        KeepOpenCheckBoxMenuItem item = new KeepOpenCheckBoxMenuItem("Test", true);
        AtomicBoolean stateInListener = new AtomicBoolean();
        
        item.addActionListener(e -> {
            stateInListener.set(item.isChecked());
        });
        
        // Initial state is true.
        // When clicked, it should toggle to false.
        // If the listener runs after toggle, it should see false.
        // If the listener runs before toggle, it should see true.
        item.doClick();
        
        assertFalse(item.isChecked(), "Item should be unchecked after click");
        assertFalse(stateInListener.get(), "Listener should have seen the NEW state (false)");
    }
}
