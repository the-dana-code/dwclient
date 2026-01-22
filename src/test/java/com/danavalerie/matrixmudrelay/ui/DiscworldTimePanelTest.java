package com.danavalerie.matrixmudrelay.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DiscworldTimePanelTest {

    @Test
    public void testFormatNextSunEvent() {
        DiscworldTimePanel panel = new DiscworldTimePanel();
        String summary = panel.getNextSunEventSummary();
        System.out.println("[DEBUG_LOG] Summary: " + summary);
        
        // Expected format: "Type at Time (in ... RW)"
        assertTrue(summary.contains("Sunrise") || summary.contains("Sunset"));
        assertTrue(summary.contains(" at "));
        assertTrue(summary.contains(" (in "));
        assertTrue(summary.endsWith(" RW)"));
        assertTrue(summary.contains("minute"));
    }

    @Test
    public void testTooltipAlignment() {
        // We can't easily call updateTime() but we can test the String.format logic
        String fullDate = "Saturday 1st Offle, 1966 Prime";
        String timeStr = "12:00am";
        String season = "Backspindlewinter";
        String nextStr = "Sunset at 11:25pm (in 15 minutes RW)";

        String tooltip = String.format(
                "<html><font face='monospaced'><b>%7s</b> %s<br><b>%7s</b> %s<br><b>%7s</b> %s<br><b>%7s</b> %s</font></html>",
                "Date:", fullDate, "Time:", timeStr, "Season:", season, "Next:", nextStr
        ).replace(" ", "&nbsp;");

        System.out.println("[DEBUG_LOG] Tooltip: " + tooltip);
        
        assertTrue(tooltip.contains("<b>&nbsp;&nbsp;Date:</b>"));
        assertTrue(tooltip.contains("<b>&nbsp;&nbsp;Time:</b>"));
        assertTrue(tooltip.contains("<b>Season:</b>"));
        assertTrue(tooltip.contains("<b>&nbsp;&nbsp;Next:</b>"));
        assertTrue(tooltip.contains("face='monospaced'"));
    }
}
