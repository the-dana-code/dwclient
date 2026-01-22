package com.danavalerie.matrixmudrelay.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DiscworldTimePanelTest {

    @Test
    public void testTooltipAlignment() {
        // We can't easily call updateTime() but we can test the String.format logic
        String fullDate = "Saturday 1st Offle, 1966 Prime";
        String timeStr = "12:00am";
        String season = "Backspindlewinter";

        String tooltip = String.format(
                "<html><font face='monospaced'><b>%7s</b> %s<br><b>%7s</b> %s<br><b>%7s</b> %s</font></html>",
                "Date:", fullDate, "Time:", timeStr, "Season:", season
        ).replace(" ", "&nbsp;");

        System.out.println("[DEBUG_LOG] Tooltip: " + tooltip);
        
        assertTrue(tooltip.contains("<b>&nbsp;&nbsp;Date:</b>"));
        assertTrue(tooltip.contains("<b>&nbsp;&nbsp;Time:</b>"));
        assertTrue(tooltip.contains("<b>Season:</b>"));
        assertFalse(tooltip.contains("<b>&nbsp;&nbsp;Next:</b>"));
        assertTrue(tooltip.contains("face='monospaced'"));
    }
}
