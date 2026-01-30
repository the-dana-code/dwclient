/*
 * Lesa's Discworld MUD client.
 * Copyright (C) 2026 Dana Reese
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.core.data.ItemData;
import com.danavalerie.matrixmudrelay.core.data.RoomData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RoomMapServiceTest {
    @BeforeEach
    public void setup() {
        ClientConfig.CharacterConfig lesa = new ClientConfig.CharacterConfig();
        lesa.teleports.reliable = true;
        lesa.teleports.locations = List.of(
                new ClientConfig.TeleportLocation("tp blackglass", "087e9ce0a29cb5e1885352a7965d744bf398dfaf")
        );
        TeleportRegistry.initialize(Map.of("lesa", lesa));
    }

    @Test
    public void testReproduction() throws Exception {
        RoomMapService service = new RoomMapService(new MapDataService());
        
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
        RoomMapService service = new RoomMapService(new MapDataService());

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
    
    @Test
    public void testConfigurablePenalty() throws Exception {
        RoomMapService service = new RoomMapService(new MapDataService());

        // Drum teleport room ID
        String drumRoomId = "4b11616f93c94e3c766bb5ad9cba3b61dcc73979";
        // Bakery room ID (which is far from Drum, but near Blackglass)
        String bakeryRoomId = "09f2edffdc50c9b865efeefe7e74ee640dc952ef";

        // Setup a character with a VERY high penalty
        ClientConfig.CharacterConfig highPenaltyChar = new ClientConfig.CharacterConfig();
        highPenaltyChar.teleports.speedwalkingPenalty = 9999;
        highPenaltyChar.teleports.locations = List.of(
                new ClientConfig.TeleportLocation("tp blackglass", "087e9ce0a29cb5e1885352a7965d744bf398dfaf")
        );
        TeleportRegistry.initialize(Map.of("highpenalty", highPenaltyChar));

        RoomMapService.RouteResult result = service.findRoute(drumRoomId, bakeryRoomId, true, "highpenalty");

        assertNotNull(result);
        for (RoomMapService.RouteStep step : result.steps()) {
            assertFalse(step.exit().startsWith("tp "), "Should not have used teleport due to high penalty, but used: " + step.exit());
        }

        // Setup a character with a VERY low penalty (0)
        ClientConfig.CharacterConfig lowPenaltyChar = new ClientConfig.CharacterConfig();
        lowPenaltyChar.teleports.speedwalkingPenalty = 0;
        lowPenaltyChar.teleports.locations = List.of(
                new ClientConfig.TeleportLocation("tp blackglass", "087e9ce0a29cb5e1885352a7965d744bf398dfaf")
        );
        TeleportRegistry.initialize(Map.of("lowpenalty", lowPenaltyChar));

        RoomMapService.RouteResult resultLow = service.findRoute(drumRoomId, bakeryRoomId, true, "lowpenalty");
        assertNotNull(resultLow);
        assertTrue(resultLow.steps().get(0).exit().startsWith("tp "), "Should have used teleport due to low penalty");
    }

    @Test
    public void testNoTeleportFlagPreventsTeleportOutOfRoom() throws Exception {
        MapDataService dataService = new MapDataService();
        dataService.getRooms().clear();

        RoomData start = new RoomData("A", 1, 0, 0, "Start", "outside");
        start.setExits(Map.of("e", "B"));
        start.setFlags(List.of(RoomData.FLAG_NO_TELEPORT));
        RoomData mid = new RoomData("B", 1, 1, 0, "Mid", "outside");
        mid.setExits(Map.of("e", "C"));
        RoomData target = new RoomData("C", 1, 2, 0, "Target", "outside");
        target.setExits(Map.of());

        dataService.getRooms().put(start.getRoomId(), start);
        dataService.getRooms().put(mid.getRoomId(), mid);
        dataService.getRooms().put(target.getRoomId(), target);

        ClientConfig.CharacterConfig tester = new ClientConfig.CharacterConfig();
        tester.teleports.speedwalkingPenalty = 0;
        tester.teleports.locations = List.of(
                new ClientConfig.TeleportLocation("tp town", "C")
        );
        TeleportRegistry.initialize(Map.of("tester", tester));

        RoomMapService service = new RoomMapService(dataService);
        RoomMapService.RouteResult result = service.findRoute("A", "C", true, "tester");

        assertNotNull(result);
        assertEquals(2, result.steps().size(), "Expected walk + teleport route");
        assertEquals("e", result.steps().get(0).exit(), "Should walk out before teleporting");
        assertEquals("tp town", result.steps().get(1).exit(), "Should allow teleport after leaving flagged room");
    }

    @Test
    public void testRenderDarkMap() throws Exception {
        RoomMapService service = new RoomMapService(new MapDataService());
        // Drum teleport room ID
        String roomInAm = "4b11616f93c94e3c766bb5ad9cba3b61dcc73979"; 
        
        RoomMapService.MapImage lightImage = service.renderMapImage(roomInAm, false);
        RoomMapService.MapImage darkImage = service.renderMapImage(roomInAm, true);
        
        assertNotNull(lightImage);
        assertNotNull(darkImage);
        assertTrue(darkImage.isDark());
        assertFalse(lightImage.isDark());
        assertNotEquals(lightImage.data().length, darkImage.data().length);
    }

    @Test
    public void testItemSearchDedupesCaseVariantsPrefersCapitalized() throws Exception {
        MapDataService dataService = new MapDataService();
        dataService.getItems().clear();

        ItemData lower = new ItemData();
        lower.setItemName("meat pie");
        ItemData upper = new ItemData();
        upper.setItemName("Meat Pie");

        dataService.getItems().put(lower.getItemName(), lower);
        dataService.getItems().put(upper.getItemName(), upper);

        RoomMapService service = new RoomMapService(dataService);
        List<RoomMapService.ItemSearchResult> results = service.searchItemsByName("meat pie", 10);

        assertEquals(1, results.size());
        assertEquals("Meat Pie", results.get(0).itemName());
    }
}
