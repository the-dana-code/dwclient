package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.RoomMapService;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class MapPanelTest {

    @Test
    public void testBuildScaledSpeedwalkPathNullElements() throws Exception {
        MapPanel mapPanel = new MapPanel(100, zoom -> {}, false, invert -> {});
        
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

    @Test
    public void testToggleInvertInvertsImage() throws Exception {
        MapPanel mapPanel = new MapPanel(100, zoom -> {}, false, invert -> {});
        
        // Create a simple test image
        BufferedImage testImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        int originalColor = new Color(10, 20, 30, 255).getRGB();
        testImage.setRGB(0, 0, originalColor);
        
        // Use reflection to set lastBaseImage
        java.lang.reflect.Field field = MapPanel.class.getDeclaredField("lastBaseImage");
        field.setAccessible(true);
        field.set(mapPanel, testImage);
        
        // Initial state: not inverted
        java.lang.reflect.Method toggleMethod = MapPanel.class.getDeclaredMethod("toggleInvert");
        toggleMethod.setAccessible(true);
        
        // Toggle once: should be inverted
        toggleMethod.invoke(mapPanel);
        
        int invertedColor = testImage.getRGB(0, 0);
        Color c = new Color(invertedColor);
        org.junit.jupiter.api.Assertions.assertEquals(255 - 10, c.getRed());
        org.junit.jupiter.api.Assertions.assertEquals(255 - 20, c.getGreen());
        org.junit.jupiter.api.Assertions.assertEquals(255 - 30, c.getBlue());
        
        // Toggle again: should be back to original
        toggleMethod.invoke(mapPanel);
        org.junit.jupiter.api.Assertions.assertEquals(originalColor, testImage.getRGB(0, 0));
        
        System.out.println("[DEBUG_LOG] testToggleInvertInvertsImage passed");
    }

    @Test
    public void testToggleInvertInvertsCache() throws Exception {
        MapPanel mapPanel = new MapPanel(100, zoom -> {}, false, invert -> {});
        
        // Create a simple test image
        BufferedImage testImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        int originalColor = new Color(10, 20, 30, 255).getRGB();
        testImage.setRGB(0, 0, originalColor);
        
        // Use reflection to set baseImageCache
        java.lang.reflect.Field cacheField = MapPanel.class.getDeclaredField("baseImageCache");
        cacheField.setAccessible(true);
        java.util.concurrent.atomic.AtomicReference<BufferedImage> cache = 
            (java.util.concurrent.atomic.AtomicReference<BufferedImage>) cacheField.get(mapPanel);
        cache.set(testImage);
        
        // Initial state: not inverted
        java.lang.reflect.Method toggleMethod = MapPanel.class.getDeclaredMethod("toggleInvert");
        toggleMethod.setAccessible(true);
        
        // Toggle once: should be inverted
        toggleMethod.invoke(mapPanel);
        
        int invertedColor = testImage.getRGB(0, 0);
        Color c = new Color(invertedColor);
        org.junit.jupiter.api.Assertions.assertEquals(255 - 10, c.getRed());
        
        System.out.println("[DEBUG_LOG] testToggleInvertInvertsCache passed");
    }

    @Test
    public void testMapBackgroundColors() {
        // Inverted (true) should now be DARK (BACKGROUND_DARK = 12, 12, 18)
        MapPanel mapPanelInverted = new MapPanel(100, z -> {}, true, i -> {});
        org.junit.jupiter.api.Assertions.assertEquals(new Color(12, 12, 18), mapPanelInverted.getBackground());

        // Not inverted (false) should now be LIGHT (BACKGROUND_LIGHT = 245, 245, 240)
        MapPanel mapPanelNotInverted = new MapPanel(100, z -> {}, false, i -> {});
        org.junit.jupiter.api.Assertions.assertEquals(new Color(245, 245, 240), mapPanelNotInverted.getBackground());
        
        System.out.println("[DEBUG_LOG] testMapBackgroundColors passed");
    }
}
