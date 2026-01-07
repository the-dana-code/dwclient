package com.danavalerie.matrixmudrelay.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RoomMapServiceTest {
    @Test
    public void testReproduction() throws Exception {
        RoomMapService service = new RoomMapService("database.db");
        
        // Drum teleport room ID
        String drumRoomId = "4b11616f93c94e3c766bb5ad9cba3b61dcc73979";
        // Bakery room ID
        String bakeryRoomId = "09f2edffdc50c9b865efeefe7e74ee640dc952ef";

        RoomMapService.RouteResult result = service.findRoute(drumRoomId, bakeryRoomId, true, "lesa");
        
        assertNotNull(result);
        assertFalse(result.steps().isEmpty(), "Route should not be empty");
        
        String firstStep = result.steps().get(0).exit();
        assertTrue(firstStep.contains("blackglass"), "Should have chosen blackglass teleport, but chose: " + firstStep);
    }
}
