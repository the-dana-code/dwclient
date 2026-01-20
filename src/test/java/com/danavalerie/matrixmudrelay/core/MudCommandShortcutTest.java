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
import com.danavalerie.matrixmudrelay.config.DeliveryRouteMappings;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.mud.CurrentRoomInfo;
import com.danavalerie.matrixmudrelay.mud.TelnetDecoder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MudCommandShortcutTest {

    static class StubClientOutput implements MudCommandProcessor.ClientOutput {
        List<String> systemMessages = new ArrayList<>();
        List<String> commandEchoes = new ArrayList<>();
        List<String> roomUpdates = new ArrayList<>();

        @Override public void appendSystem(String text) { systemMessages.add(text); }
        @Override public void appendCommandEcho(String text) { commandEchoes.add(text); }
        @Override public void addToHistory(String command) {}
        @Override public void updateCurrentRoom(String roomId, String roomName) {
            roomUpdates.add(roomId + ":" + roomName);
        }
        @Override public void updateMap(String roomId) {}
        @Override public void updateStats(StatsHudRenderer.StatsHudData data) {}
        @Override public void updateContextualResults(ContextualResultList results) {}
        @Override public void updateSpeedwalkPath(List<RoomMapService.RoomLocation> path) {}
        @Override public void updateConnectionState(boolean connected) {}
        @Override public void setUULibraryButtonsEnabled(boolean enabled) {}
    }

    static class StubMudClient extends MudClient {
        List<String> sentLines = new ArrayList<>();
        CurrentRoomInfo cri = new CurrentRoomInfo();

        public StubMudClient() {
            super(new BotConfig.Mud(), null, null);
        }

        @Override
        public void sendLinesFromController(List<String> lines) {
            sentLines.addAll(lines);
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public CurrentRoomInfo.Snapshot getCurrentRoomSnapshot() {
            return cri.getSnapshot();
        }
    }


    @Test
    void testPwShortcut() {
        BotConfig cfg = new BotConfig();
        cfg.aliases.put("#password", List.of("supersecret"));

        StubMudClient mud = new StubMudClient();
        StubClientOutput output = new StubClientOutput();
        TimerService timerService = new TimerService(cfg, Paths.get("config.json"));
        
        MudCommandProcessor processor = new MudCommandProcessor(cfg, mud, new WritTracker(), new StoreInventoryTracker(), timerService, () -> new DeliveryRouteMappings(List.of()), output);
        
        processor.handleInput("/pw");
        
        assertTrue(mud.sentLines.contains("supersecret"), "Password should have been sent to MUD");
        assertTrue(output.commandEchoes.contains("(password)"), "Password should have been masked in echo");
    }

    @Test
    void testMmPasswordSubcommand() {
        BotConfig cfg = new BotConfig();
        cfg.aliases.put("#password", List.of("supersecret"));

        StubMudClient mud = new StubMudClient();
        StubClientOutput output = new StubClientOutput();
        TimerService timerService = new TimerService(cfg, Paths.get("config.json"));
        
        MudCommandProcessor processor = new MudCommandProcessor(cfg, mud, new WritTracker(), new StoreInventoryTracker(), timerService, () -> new DeliveryRouteMappings(List.of()), output);
        
        processor.handleInput("/password");
        
        assertTrue(mud.sentLines.contains("supersecret"), "Password should have been sent to MUD via /password");
    }

    @Test
    void testLocCommand() {
        BotConfig cfg = new BotConfig();
        StubMudClient mud = new StubMudClient();
        StubClientOutput output = new StubClientOutput();
        TimerService timerService = new TimerService(cfg, Paths.get("config.json"));
        
        MudCommandProcessor processor = new MudCommandProcessor(cfg, mud, new WritTracker(), new StoreInventoryTracker(), timerService, () -> new DeliveryRouteMappings(List.of()), output);
        
        // Test /loc usage
        processor.handleInput("/loc");
        // handleCurrentLocation shows room ID, or error if can't determine.
        assertTrue(output.systemMessages.stream().anyMatch(m -> m.contains("location") || m.contains("Error")), "Should show location info or error for /loc");

        // Verify / is now unknown/shows usage
        output.systemMessages.clear();
        processor.handleInput("/");
        assertTrue(output.systemMessages.stream().anyMatch(m -> m.contains("Unknown command")), "Should show unknown command for /");
    }

    @Test
    void testRoomNameUpdateTrigger() {
        BotConfig cfg = new BotConfig();
        StubMudClient mud = new StubMudClient();
        StubClientOutput output = new StubClientOutput();
        TimerService timerService = new TimerService(cfg, Paths.get("config.json"));

        MudCommandProcessor processor = new MudCommandProcessor(cfg, mud, new WritTracker(), new StoreInventoryTracker(), timerService, () -> new DeliveryRouteMappings(List.of()), output);

        // Scenario: Room ID arrives, but Name is null
        JsonObject roomInfo1 = new JsonObject();
        roomInfo1.addProperty("id", "room1");

        mud.cri.update("room.info", roomInfo1);
        processor.onGmcp(new TelnetDecoder.GmcpMessage("room.info", roomInfo1));

        assertEquals(1, output.roomUpdates.size());
        assertEquals("room1:null", output.roomUpdates.get(0));

        // Scenario: Room name arrives later for same room ID
        JsonObject roomInfo2 = new JsonObject();
        roomInfo2.addProperty("id", "room1");
        roomInfo2.addProperty("short", "The Mended Drum");

        mud.cri.update("room.info", roomInfo2);
        processor.onGmcp(new TelnetDecoder.GmcpMessage("room.info", roomInfo2));

        assertEquals(2, output.roomUpdates.size(), "Should have triggered another update when name arrived");
        assertEquals("room1:The Mended Drum", output.roomUpdates.get(1));
    }

    @Test
    void testRoomNameFallback() {
        BotConfig cfg = new BotConfig();
        StubMudClient mud = new StubMudClient();
        StubClientOutput output = new StubClientOutput();
        TimerService timerService = new TimerService(cfg, Paths.get("config.json"));

        MudCommandProcessor processor = new MudCommandProcessor(cfg, mud, new WritTracker(), new StoreInventoryTracker(), timerService, () -> new DeliveryRouteMappings(List.of()), output);

        // Room ID for the Mended Drum from database.db
        String drumRoomId = "4b11616f93c94e3c766bb5ad9cba3b61dcc73979";
        JsonObject roomInfo = new JsonObject();
        roomInfo.addProperty("identifier", drumRoomId);
        // No "short" property here

        mud.cri.update("room.info", roomInfo);
        processor.onGmcp(new TelnetDecoder.GmcpMessage("room.info", roomInfo));

        // The name should be looked up from the database
        assertEquals(1, output.roomUpdates.size());
        assertEquals(drumRoomId + ":north end of Short Street outside the Mended Drum", output.roomUpdates.get(0));
    }

    @Test
    void testDeliverNpcOverride() {
        BotConfig cfg = new BotConfig();
        StubMudClient mud = new StubMudClient();
        StubClientOutput output = new StubClientOutput();
        TimerService timerService = new TimerService(cfg, Paths.get("config.json"));
        WritTracker writTracker = new WritTracker();

        String npc = "the Ephebian teacher in the Ephebian Embassy";
        String loc = "the small classroom in The Ephebian Embassy";
        String item = "a wicker tube";

        DeliveryRouteMappings routeMappings = new DeliveryRouteMappings(List.of(
                new DeliveryRouteMappings.RouteEntry(npc, loc, "room1", null, List.of(), "the teacher")
        ));

        MudCommandProcessor processor = new MudCommandProcessor(cfg, mud, writTracker, new StoreInventoryTracker(), timerService, () -> routeMappings, output);

        // Simulating parsing the writ line
        writTracker.ingest("You read the official employment writ\n[ ] " + item + " to " + npc + " at " + loc);

        processor.handleInput("/writ 1 deliver");

        assertTrue(mud.sentLines.contains("deliver wicker tube to the teacher"),
                "Should use NPC override 'the teacher' in deliver command. Sent: " + mud.sentLines);
    }
}

