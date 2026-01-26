package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.mud.CurrentRoomInfo;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TeleportBannerTest {

    static class MockOutput implements MudCommandProcessor.ClientOutput {
        List<String> teleportBanners = new ArrayList<>();
        List<String> queuedTeleports = new ArrayList<>();
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
        @Override public void setTeleportQueued(String command, String targetName) { queuedTeleports.add(command); }
        @Override public void showEditPasswordDialog(Runnable onPasswordStored) {}
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
    private ClientConfig cfg;
    private MudClient mudClient;

    @BeforeEach
    void setUp() {
        UULibraryService.getInstance().reset();
        cfg = new ClientConfig();
        ClientConfig.CharacterConfig charCfg = new ClientConfig.CharacterConfig();
        charCfg.teleports = new ClientConfig.CharacterTeleports();
        charCfg.teleports.locations = new ArrayList<>();
        ClientConfig.TeleportLocation loc = new ClientConfig.TeleportLocation();
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
                cfg, new com.danavalerie.matrixmudrelay.config.UiConfig(), Paths.get("config.json"), mudClient, null, null, null, null, () -> null, output
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
    void testReliableTeleportDoesNotQueueUiPanel() {
        cfg.characters.get("Dana").teleports.reliable = true;
        TeleportRegistry.initialize(cfg.characters);

        processor.handleInput("tp home", true);
        assertFalse(output.teleportBanners.isEmpty(), "Banner SHOULD be triggered");
        assertTrue(output.queuedTeleports.isEmpty(), "UI Panel SHOULD NOT be queued when reliable");
    }

    @Test
    void testUnreliableTeleportQueuesUiPanel() {
        cfg.characters.get("Dana").teleports.reliable = false;
        TeleportRegistry.initialize(cfg.characters);

        processor.handleInput("tp home", true);
        assertFalse(output.teleportBanners.isEmpty(), "Banner SHOULD be triggered");
        assertFalse(output.queuedTeleports.isEmpty(), "UI Panel SHOULD be queued when unreliable");
        assertEquals("tp home", output.queuedTeleports.get(0));
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
                cfg, new com.danavalerie.matrixmudrelay.config.UiConfig(), Paths.get("config.json"), mudClient, mapService, null, null, null, () -> null, output
        );

        processor.speedwalkTo("room1");
        assertFalse(output.teleportBanners.isEmpty(), "Banner SHOULD be triggered by speedwalk");
        assertTrue(output.teleportBanners.get(0).contains("Home"));
    }
}
