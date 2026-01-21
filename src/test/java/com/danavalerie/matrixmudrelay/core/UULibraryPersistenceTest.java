package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.config.DeliveryRouteMappings;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.mud.CurrentRoomInfo;
import com.danavalerie.matrixmudrelay.mud.TelnetDecoder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UULibraryPersistenceTest {

    @TempDir
    Path tempDir;

    static class StubClientOutput implements MudCommandProcessor.ClientOutput {
        @Override public void appendSystem(String text) {}
        @Override public void appendCommandEcho(String text) {}
        @Override public void addToHistory(String command) {}
        @Override public void updateCurrentRoom(String roomId, String roomName) {}
        @Override public void updateMap(String roomId) {}
        @Override public void updateStats(StatsHudRenderer.StatsHudData data) {}
        @Override public void updateContextualResults(ContextualResultList results) {}
        @Override public void updateSpeedwalkPath(List<RoomMapService.RoomLocation> path) {}
        @Override public void updateConnectionState(boolean connected) {}
        @Override public void setUULibraryButtonsEnabled(boolean enabled) {}
    }

    static class StubMudClient extends MudClient {
        CurrentRoomInfo cri = new CurrentRoomInfo();

        public StubMudClient() {
            super(new BotConfig.Mud(), null, null);
        }

        @Override
        public CurrentRoomInfo.Snapshot getCurrentRoomSnapshot() {
            return cri.getSnapshot();
        }
    }

    @Test
    void testSaveAndRestoreState() {
        Path configPath = tempDir.resolve("config.json");
        BotConfig cfg = new BotConfig();
        StubMudClient mud = new StubMudClient();
        
        JsonObject charInfo = new JsonObject();
        charInfo.addProperty("capname", "TestChar");
        mud.cri.update("char.info", charInfo);
        
        MudCommandProcessor processor = new MudCommandProcessor(cfg, configPath, mud, new WritTracker(), new StoreInventoryTracker(), null, () -> new DeliveryRouteMappings(List.of()), new StubClientOutput());

        UULibraryService service = UULibraryService.getInstance();
        service.setRoomId("UULibrary");
        
        // Move a bit
        service.processCommand("rt"); // 1,5 NORTH -> turn EAST -> 1,6 EAST
        service.processCommand("lt"); // 1,6 EAST -> turn NORTH -> 2,6 NORTH
        
        assertEquals(2, service.getCurRow());
        assertEquals(6, service.getCurCol());
        assertEquals(UULibraryService.Orientation.NORTH, service.getOrientation());

        // Verify it's in the config
        assertNotNull(cfg.characters.get("TestChar").uuLibrary);
        assertEquals(2, cfg.characters.get("TestChar").uuLibrary.row);
        assertEquals(6, cfg.characters.get("TestChar").uuLibrary.col);
        assertEquals("NORTH", cfg.characters.get("TestChar").uuLibrary.orientation);

        // Deactivate
        service.setRoomId("OtherRoom");
        assertNull(cfg.characters.get("TestChar").uuLibrary);

        // Put it back manually to simulate loading from config
        cfg.characters.get("TestChar").uuLibrary = new BotConfig.UULibraryState(3, 4, "WEST");
        
        // Trigger entering library
        JsonObject roomInfo = new JsonObject();
        roomInfo.addProperty("id", "UULibrary");
        roomInfo.addProperty("short", "UU Library");
        mud.cri.update("room.info", roomInfo);
        processor.onGmcp(new TelnetDecoder.GmcpMessage("room.info", roomInfo));
        
        assertTrue(service.isActive());
        assertEquals(3, service.getCurRow());
        assertEquals(4, service.getCurCol());
        assertEquals(UULibraryService.Orientation.WEST, service.getOrientation());
    }
}
