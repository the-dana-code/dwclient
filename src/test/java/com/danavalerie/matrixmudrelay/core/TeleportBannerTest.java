package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.mud.CurrentRoomInfo;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TeleportBannerTest {

    static class MockOutput implements MudCommandProcessor.ClientOutput {
        List<String> teleportBanners = new ArrayList<>();
        @Override public void appendSystem(String text) {}
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
        @Override public void appendTeleportBanner(String banner) { teleportBanners.add(banner); }
    }

    static class StubMudClient extends MudClient {
        private final CurrentRoomInfo.Snapshot snapshot;
        public StubMudClient(CurrentRoomInfo.Snapshot snapshot) {
            super(null, null, null);
            this.snapshot = snapshot;
        }
        @Override public CurrentRoomInfo.Snapshot getCurrentRoomSnapshot() { return snapshot; }
        @Override public boolean isConnected() { return true; }
    }

    private MudCommandProcessor processor;
    private MockOutput output;
    private BotConfig cfg;
    private MudClient mudClient;

    @BeforeEach
    void setUp() {
        cfg = new BotConfig();
        BotConfig.CharacterConfig charCfg = new BotConfig.CharacterConfig();
        charCfg.teleports = new BotConfig.CharacterTeleports();
        charCfg.teleports.locations = new ArrayList<>();
        BotConfig.TeleportLocation loc = new BotConfig.TeleportLocation();
        loc.name = "Home";
        loc.command = "tp home";
        loc.roomId = "room1";
        charCfg.teleports.locations.add(loc);
        cfg.characters.put("Dana", charCfg);
        
        TeleportRegistry.initialize(cfg.characters);

        output = new MockOutput();
        
        CurrentRoomInfo roomInfo = new CurrentRoomInfo();
        JsonObject charInfo = new JsonObject();
        charInfo.addProperty("capname", "Dana");
        roomInfo.update("char.info", charInfo);
        
        JsonObject roomData = new JsonObject();
        roomData.addProperty("id", "startRoom");
        roomInfo.update("room.info", roomData);
        
        mudClient = new StubMudClient(roomInfo.getSnapshot());

        processor = new MudCommandProcessor(
                cfg, Paths.get("config.json"), mudClient, null, null, null, null, () -> null, output
        );
    }

    @Test
    void testTypingTeleportCommandDoesNotTriggerBanner() {
        processor.handleInput("tp home");
        assertTrue(output.teleportBanners.isEmpty(), "Banner should NOT be triggered by typing");
    }

    @Test
    void testSystemTeleportCommandTriggersBanner() {
        processor.handleInput("tp home", true);
        assertFalse(output.teleportBanners.isEmpty(), "Banner SHOULD be triggered by system");
        assertTrue(output.teleportBanners.get(0).contains("Home"));
    }

    @Test
    void testAliasTriggersBanner() {
        // Setup alias: "h" -> "tp home"
        cfg.aliases.put("h", List.of("tp home"));
        
        // Re-initialize registry (it's already done in setUp but good to be sure)
        TeleportRegistry.initialize(cfg.characters);

        // We need to re-create processor because aliases are loaded in constructor
        processor = new MudCommandProcessor(
                cfg, Paths.get("config.json"), mudClient, null, null, null, null, () -> null, output
        );

        processor.handleInput("h"); // User types "h"
        assertFalse(output.teleportBanners.isEmpty(), "Banner SHOULD be triggered by alias");
        assertTrue(output.teleportBanners.get(0).contains("Home"));
    }

    static class StubMapService extends RoomMapService {
        private final RoomMapService.RouteResult result;
        public StubMapService(RoomMapService.RouteResult result) {
            super(null);
            this.result = result;
        }
        @Override public RoomMapService.RouteResult findRoute(String start, String target, boolean useTp, String charName) {
            return result;
        }
        @Override public List<RoomMapService.RoomLocation> lookupRoomLocations(List<String> roomIds) {
            return List.of();
        }
    }

    @Test
    void testSpeedwalkTriggersBanner() throws Exception {
        RoomMapService.RouteStep step = new RoomMapService.RouteStep("tp home", "room1");
        RoomMapService.RouteResult route = new RoomMapService.RouteResult(List.of(step));
        RoomMapService mapService = new StubMapService(route);

        processor = new MudCommandProcessor(
                cfg, Paths.get("config.json"), mudClient, mapService, null, null, null, () -> null, output
        );

        processor.speedwalkTo("room1");
        assertFalse(output.teleportBanners.isEmpty(), "Banner SHOULD be triggered by speedwalk");
        assertTrue(output.teleportBanners.get(0).contains("Home"));
    }
}
