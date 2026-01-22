package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.config.DeliveryRouteMappings;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.mud.CurrentRoomInfo;
import com.danavalerie.matrixmudrelay.mud.TelnetDecoder;
import com.danavalerie.matrixmudrelay.util.BackgroundSaver;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UULibraryPersistenceTest {
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        BackgroundSaver.resetForTests();
        UULibraryService.getInstance().reset();
    }

    @AfterEach
    void tearDown() {
        BackgroundSaver.waitForIdle();
    }

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
        @Override public void setUULibraryDistortion(boolean distortion) {}
        @Override public void playUULibraryReadySound() {}
        @Override public void playUULibraryAlertSound() {}
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
        cfg.mud.host = "localhost";
        cfg.mud.port = 1234;
        StubMudClient mud = new StubMudClient();
        
        JsonObject charInfo = new JsonObject();
        charInfo.addProperty("capname", "TestChar");
        mud.cri.update("char.info", charInfo);

        RoomMapService mapService = new RoomMapService(new MapDataService());
        MudCommandProcessor processor = new MudCommandProcessor(cfg, configPath, mud, mapService, new WritTracker(), new StoreInventoryTracker(), null, () -> new DeliveryRouteMappings(List.of()), new StubClientOutput());

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

        // Verify config file also reflects the removal
        try {
            BackgroundSaver.waitForIdle();
            BotConfig loaded = com.danavalerie.matrixmudrelay.config.ConfigLoader.load(configPath);
            assertNull(loaded.characters.get("TestChar").uuLibrary, "Config file should have uuLibrary as null after leaving library");
        } catch (Exception e) {
            fail("Failed to load config: " + e.getMessage());
        }

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

    @Test
    void testRemoveStateOnWalkOut() throws Exception {
        Path configPath = tempDir.resolve("config_walkout.json");
        BotConfig cfg = new BotConfig();
        cfg.mud.host = "localhost";
        cfg.mud.port = 1234;
        StubMudClient mud = new StubMudClient();

        // 1. Initial state: Character name available
        JsonObject charInfo = new JsonObject();
        charInfo.addProperty("capname", "Walker");
        mud.cri.update("char.info", charInfo);

        RoomMapService mapService = new RoomMapService(new MapDataService());
        MudCommandProcessor processor = new MudCommandProcessor(cfg, configPath, mud, mapService, new WritTracker(), new StoreInventoryTracker(), null, () -> new DeliveryRouteMappings(List.of()), new StubClientOutput());
        UULibraryService service = UULibraryService.getInstance();

        // 2. Enter Library
        JsonObject roomLibrary = new JsonObject();
        roomLibrary.addProperty("id", "UULibrary");
        mud.cri.update("room.info", roomLibrary);
        
        processor.onGmcp(new TelnetDecoder.GmcpMessage("room.info", roomLibrary));
        assertTrue(service.isActive());
        
        // Move to ensure something is saved (skipping entry save is intended)
        service.processCommand("rt"); 
        
        BotConfig.CharacterConfig charCfg = cfg.characters.values().stream().findFirst().orElse(null);
        assertNotNull(charCfg, "CharacterConfig should exist after movement");
        assertNotNull(charCfg.uuLibrary, "uuLibrary state should not be null after movement");

        // 3. Walk out (New room ID)
        JsonObject roomOutside = new JsonObject();
        roomOutside.addProperty("id", "outside_library");
        mud.cri.update("room.info", roomOutside);
        processor.onGmcp(new TelnetDecoder.GmcpMessage("room.info", roomOutside));

        // 4. Verify
        assertFalse(service.isActive());
        assertNull(cfg.characters.get("Walker").uuLibrary, "uuLibrary state should be null in memory");

        BackgroundSaver.waitForIdle();
        BotConfig loaded = com.danavalerie.matrixmudrelay.config.ConfigLoader.load(configPath);
        assertNull(loaded.characters.get("Walker").uuLibrary, "uuLibrary state should be null in config file");
    }

    @Test
    void testRestoreStateWhenRoomInfoArrivesBeforeCharInfo() {
        Path configPath = tempDir.resolve("config_late_char.json");
        BotConfig cfg = new BotConfig();
        cfg.mud.host = "localhost";
        cfg.mud.port = 1234;
        StubMudClient mud = new StubMudClient();

        // Saved state for character "LateChar"
        BotConfig.CharacterConfig charCfg = new BotConfig.CharacterConfig();
        charCfg.uuLibrary = new BotConfig.UULibraryState(10, 20, "SOUTH");
        cfg.characters.put("LateChar", charCfg);

        RoomMapService mapService = new RoomMapService(new MapDataService());
        MudCommandProcessor processor = new MudCommandProcessor(cfg, configPath, mud, mapService, new WritTracker(), new StoreInventoryTracker(), null, () -> new DeliveryRouteMappings(List.of()), new StubClientOutput());
        UULibraryService service = UULibraryService.getInstance();

        // 1. room.info arrives first. No character name known yet.
        JsonObject roomInfo = new JsonObject();
        roomInfo.addProperty("id", "UULibrary");
        mud.cri.update("room.info", roomInfo);
        processor.onGmcp(new TelnetDecoder.GmcpMessage("room.info", roomInfo));

        assertTrue(service.isActive());
        // Should be at default position because charName was null
        assertEquals(1, service.getCurRow());
        assertEquals(5, service.getCurCol());

        // 2. char.info arrives later
        JsonObject charInfo = new JsonObject();
        charInfo.addProperty("capname", "LateChar");
        mud.cri.update("char.info", charInfo);
        processor.onGmcp(new TelnetDecoder.GmcpMessage("char.info", charInfo));

        // Now it SHOULD be restored
        assertEquals(10, service.getCurRow(), "Should have restored row after char.info arrived");
        assertEquals(20, service.getCurCol());
        assertEquals(UULibraryService.Orientation.SOUTH, service.getOrientation());
    }
}
