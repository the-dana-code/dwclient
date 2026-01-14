package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.RoomMapService;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class MapPanelTest {

    @Test
    public void testBuildScaledSpeedwalkPathNullElements() throws Exception {
        MapPanel mapPanel = new MapPanel(100, zoom -> {});
        
        // Prepare speedwalkPath with locations on different maps to trigger adding null to points
        RoomMapService.RoomLocation loc1 = new RoomMapService.RoomLocation("1", 1, 10, 10, "Room 1");
        RoomMapService.RoomLocation loc2 = new RoomMapService.RoomLocation("2", 2, 20, 20, "Room 2");
        
        mapPanel.setSpeedwalkPath(List.of(loc1, loc2));
        
        RoomMapService.MapImage mapImage = new RoomMapService.MapImage(
            new byte[0], 100, 100, "image/png", "Map 1", 10, 10, false, 1, 0, 0, 2, 1, 1, "1", 10, 10, "Room 1"
        );

        // We want to verify that buildScaledSpeedwalkPath throws NPE when it tries to use List.copyOf with nulls
        // Since it's private, we'll use reflection or just call updateDisplayedImage if we can ensure it runs.
        // Actually, let's just make it package-private for a moment to test it easily.
        
        java.lang.reflect.Method method = MapPanel.class.getDeclaredMethod("buildScaledSpeedwalkPath", 
            RoomMapService.MapImage.class, Integer.class, int.class);
        method.setAccessible(true);
        
        // This should NOT throw NPE anymore
        List<Point> result = (List<Point>) method.invoke(mapPanel, mapImage, 1, 100);
        
        org.junit.jupiter.api.Assertions.assertNotNull(result);
        System.out.println("[DEBUG_LOG] buildScaledSpeedwalkPath executed successfully after fix");
    }
}
