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

    @Test
    public void testInterMapShortPath() throws Exception {
        RoomMapService service = new RoomMapService("database.db");

        // bridge over Lancre Gorge (Map 32)
        String bridgeRoomId = "01343c88667e057f6c3cb2ee57c70218bcab5abf";
        // southern edge of Lancre Town (Map 38)
        String targetRoomId = "6807ec4154dd0f93c7ffd8a8a3211f9229032953";

        RoomMapService.RouteResult result = service.findRoute(bridgeRoomId, targetRoomId, true, "lesa");

        assertNotNull(result);
        // Ensure it did NOT use a teleport. Teleports in this project usually start with "tp "
        for (RoomMapService.RouteStep step : result.steps()) {
            assertFalse(step.exit().startsWith("tp "), "Should not have used teleport, but used: " + step.exit());
        }

        // It should be a short walking route.
        assertTrue(result.steps().size() <= 3, "Route should be short (3 steps or less), but was " + result.steps().size());
    }
}
