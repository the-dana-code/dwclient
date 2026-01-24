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
import com.danavalerie.matrixmudrelay.mud.CurrentRoomInfo;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonObject;

import static org.junit.jupiter.api.Assertions.*;

class RouteCommandUpdateTest {

    static class MockOutput implements MudCommandProcessor.ClientOutput {
        List<String> systemMessages = new ArrayList<>();
        @Override public void appendSystem(String text) { systemMessages.add(text); }
        @Override public void appendCommandEcho(String text) {}
        @Override public void addToHistory(String command) {}
        @Override public void updateCurrentRoom(String roomId, String roomName) {}
        @Override public void updateMap(String roomId) {}
        @Override public void updateStats(com.danavalerie.matrixmudrelay.core.StatsHudRenderer.StatsHudData data) {}
        @Override public void updateContextualResults(ContextualResultList results) {}
        @Override public void updateSpeedwalkPath(List<RoomMapService.RoomLocation> path) {}
        @Override public void updateConnectionState(boolean connected) {}
        @Override public void setUULibraryButtonsEnabled(boolean enabled) {}
        @Override public void setUULibraryDistortion(boolean distortion) {}
        @Override public void playUULibraryReadySound() {}
        @Override public void playUULibraryAlertSound() {}
        @Override public void onCharacterChanged(String characterName) {}
        @Override public void updateRepeatLastSpeedwalkItem() {}
    }

    static class StubMudClient extends MudClient {
        private final CurrentRoomInfo roomInfo = new CurrentRoomInfo();
        public StubMudClient() {
            super(null, null, null);
            JsonObject roomObj = new JsonObject();
            roomObj.addProperty("identifier", "startRoom");
            roomObj.addProperty("short", "Start Room");
            roomInfo.update("room.info", roomObj);
            
            JsonObject charObj = new JsonObject();
            charObj.addProperty("capname", "testChar");
            roomInfo.update("char.info", charObj);
        }
        @Override public boolean isConnected() { return true; }
        @Override public CurrentRoomInfo.Snapshot getCurrentRoomSnapshot() { return roomInfo.getSnapshot(); }
    }

    static class StubRoomMapService extends RoomMapService {
        public StubRoomMapService() { super(null); }
        @Override public RouteResult findRoute(String startRoomId, String targetRoomId, boolean useTeleports, String characterName) {
            return new RouteResult(List.of(new RouteStep("north", "targetRoom")));
        }
        @Override public String getMapDisplayName(int mapId) { return "Map"; }
        @Override public RoomLocation lookupRoomLocation(String roomId) {
            if ("targetRoom".equals(roomId)) return new RoomLocation("targetRoom", 1, 0, 0, "Target Room");
            return null;
        }
    }

    @Test
    void testRouteCommandUpdatesLastSpeedwalk() throws Exception {
        BotConfig cfg = new BotConfig();
        Path configPath = Paths.get("config.json");
        MockOutput output = new MockOutput();
        
        MudClient mud = new StubMudClient();
        RoomMapService mapService = new StubRoomMapService();

        MudCommandProcessor processor = new MudCommandProcessor(
                cfg, configPath, mud, mapService, null, null, null, () -> null, output
        );

        // Inject last room search results
        java.lang.reflect.Field resultsField = MudCommandProcessor.class.getDeclaredField("lastRoomSearchResults");
        resultsField.setAccessible(true);
        RoomMapService.RoomSearchResult searchResult = new RoomMapService.RoomSearchResult("targetRoom", 1, 0, 0, "Target Room", "type", "source");
        resultsField.set(processor, List.of(searchResult));

        // Initially no speedwalk
        assertFalse(processor.hasLastSpeedwalk());
        
        // Execute /route 1
        processor.handleInput("/route 1");
        
        // Check if last speedwalk target is updated
        assertTrue(processor.hasLastSpeedwalk(), "lastSpeedwalkTargetRoomId should be updated after /route");
        assertEquals("Target Room", processor.getLastSpeedwalkTargetName());
    }
}
