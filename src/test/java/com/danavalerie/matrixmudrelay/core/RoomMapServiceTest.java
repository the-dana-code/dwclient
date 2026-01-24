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

import com.danavalerie.matrixmudrelay.config.BotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RoomMapServiceTest {
    @BeforeEach
    public void setup() {
        BotConfig.CharacterConfig lesa = new BotConfig.CharacterConfig();
        lesa.teleports.reliable = true;
        lesa.teleports.locations = List.of(
                new BotConfig.TeleportLocation("tp blackglass", "087e9ce0a29cb5e1885352a7965d744bf398dfaf")
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
        BotConfig.CharacterConfig highPenaltyChar = new BotConfig.CharacterConfig();
        highPenaltyChar.teleports.speedwalkingPenalty = 9999;
        highPenaltyChar.teleports.locations = List.of(
                new BotConfig.TeleportLocation("tp blackglass", "087e9ce0a29cb5e1885352a7965d744bf398dfaf")
        );
        TeleportRegistry.initialize(Map.of("highpenalty", highPenaltyChar));

        RoomMapService.RouteResult result = service.findRoute(drumRoomId, bakeryRoomId, true, "highpenalty");

        assertNotNull(result);
        for (RoomMapService.RouteStep step : result.steps()) {
            assertFalse(step.exit().startsWith("tp "), "Should not have used teleport due to high penalty, but used: " + step.exit());
        }

        // Setup a character with a VERY low penalty (0)
        BotConfig.CharacterConfig lowPenaltyChar = new BotConfig.CharacterConfig();
        lowPenaltyChar.teleports.speedwalkingPenalty = 0;
        lowPenaltyChar.teleports.locations = List.of(
                new BotConfig.TeleportLocation("tp blackglass", "087e9ce0a29cb5e1885352a7965d744bf398dfaf")
        );
        TeleportRegistry.initialize(Map.of("lowpenalty", lowPenaltyChar));

        RoomMapService.RouteResult resultLow = service.findRoute(drumRoomId, bakeryRoomId, true, "lowpenalty");
        assertNotNull(resultLow);
        assertTrue(resultLow.steps().get(0).exit().startsWith("tp "), "Should have used teleport due to low penalty");
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
}

