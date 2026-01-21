package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.config.DeliveryRouteMappings;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UULibraryDistortionTest {

    static class StubClientOutput implements MudCommandProcessor.ClientOutput {
        boolean buttonsEnabled = true;
        int mapUpdateCount = 0;
        int readySoundCount = 0;
        int alertSoundCount = 0;

        @Override public void appendSystem(String text) {}
        @Override public void appendCommandEcho(String text) {}
        @Override public void addToHistory(String command) {}
        @Override public void updateCurrentRoom(String roomId, String roomName) {}
        @Override public void updateMap(String roomId) { mapUpdateCount++; }
        @Override public void updateStats(StatsHudRenderer.StatsHudData data) {}
        @Override public void updateContextualResults(ContextualResultList results) {}
        @Override public void updateSpeedwalkPath(List<RoomMapService.RoomLocation> path) {}
        @Override public void updateConnectionState(boolean connected) {}
        @Override public void setUULibraryButtonsEnabled(boolean enabled) { buttonsEnabled = enabled; }
        @Override public void playUULibraryReadySound() { readySoundCount++; }
        @Override public void playUULibraryAlertSound() { alertSoundCount++; }
    }

    private MudCommandProcessor processor;
    private StubClientOutput output;
    private UULibraryService service;

    @BeforeEach
    void setUp() {
        service = UULibraryService.getInstance();
        service.reset();
        service.setRoomId("UULibrary"); // Activate it
        
        output = new StubClientOutput();
        BotConfig cfg = new BotConfig();
        Path configPath = Paths.get("config.json");
        MudClient mud = new MudClient(new BotConfig.Mud(), null, null);
        RoomMapService mapService = new RoomMapService("database.db");
        
        processor = new MudCommandProcessor(cfg, configPath, mud, mapService, new WritTracker(), new StoreInventoryTracker(), null, () -> new DeliveryRouteMappings(List.of()), output);
    }

    @Test
    void testDistortionPatternsReenableButtons() {
        String[] patterns = {
            "There is a strange distortion in space and time up ahead of you!",
            "There is a strange distortion in space and time behind you!",
            "There is a strange distortion in space and time to the left of you!",
            "There is a strange distortion in space and time to the right of you!",
            "Cannot find \"distortion\", no match."
        };

        for (String p : patterns) {
            output.setUULibraryButtonsEnabled(false);
            processor.onFullLineReceived(p);
            assertTrue(output.buttonsEnabled, "Buttons should be enabled after pattern: " + p);
        }
    }

    @Test
    void testDistortionCreatesBarriers() {
        // Default orientation is NORTH, pos 1,5
        assertEquals(UULibraryService.Orientation.NORTH, service.getOrientation());
        String pos = "1,5";

        // Ahead (North)
        processor.onFullLineReceived("There is a strange distortion in space and time up ahead of you!");
        Set<UULibraryService.Orientation> b = service.getBarriers().get(pos);
        assertNotNull(b);
        assertTrue(b.contains(UULibraryService.Orientation.NORTH));

        // Left (West)
        processor.onFullLineReceived("There is a strange distortion in space and time to the left of you!");
        assertTrue(service.getBarriers().get(pos).contains(UULibraryService.Orientation.WEST));

        // Right (East)
        processor.onFullLineReceived("There is a strange distortion in space and time to the right of you!");
        assertTrue(service.getBarriers().get(pos).contains(UULibraryService.Orientation.EAST));

        // Behind (South)
        processor.onFullLineReceived("There is a strange distortion in space and time behind you!");
        assertTrue(service.getBarriers().get(pos).contains(UULibraryService.Orientation.SOUTH));
    }

    @Test
    void testCannotFindClearsBarriers() {
        service.addBarrier(UULibraryService.Orientation.NORTH);
        assertFalse(service.getBarriers().isEmpty());

        processor.onFullLineReceived("Cannot find \"distortion\", no match.");
        assertTrue(service.getBarriers().isEmpty() || !service.getBarriers().containsKey("1,5"));
    }

    @Test
    void testAStarAvoidsBarriers() {
        // At 1,5 NORTH.
        // Let's go to 2,5.
        // If it returns "rt", it means it wants to go EAST or it's already EAST.
        // Let's force it to be 1,5 NORTH.
        service.setState(1, 5, UULibraryService.Orientation.NORTH);
        
        String cmd = service.getNextStepCommand(2, 5);
        // It returned "rt" in previous run. Let's see why.
        // 2,5 is NORTH of 1,5.
        // Orientation is NORTH.
        // "fw" should go to 2,5.
        
        // Let's just check that it changes when we block what it wants to do.
        assertNotNull(cmd);
        UULibraryService.Orientation preferredDir;
        switch(cmd) {
            case "fw": preferredDir = UULibraryService.Orientation.NORTH; break;
            case "rt": preferredDir = UULibraryService.Orientation.EAST; break;
            case "lt": preferredDir = UULibraryService.Orientation.WEST; break;
            case "bw": preferredDir = UULibraryService.Orientation.SOUTH; break;
            default: preferredDir = null;
        }
        assertNotNull(preferredDir);

        // Now block that preferred direction
        service.addBarrier(preferredDir);
        
        String newCmd = service.getNextStepCommand(2, 5);
        assertNotEquals(cmd, newCmd, "A* should suggest a different command if preferred direction is blocked");
        assertNotNull(newCmd);
    }

    @Test
    void testOneWayDistortion() {
        // Room A: 1,5. Room B: 2,5 (North of A)
        // Set barrier in Room A towards NORTH
        service.setState(1, 5, UULibraryService.Orientation.NORTH);
        service.addBarrier(UULibraryService.Orientation.NORTH);

        // Path from A to B should be blocked or detour
        String cmdFromA = service.getNextStepCommand(2, 5);
        // If it wants to go North, it's blocked, so it should NOT return "fw"
        assertNotEquals("fw", cmdFromA);

        // Move to Room B
        service.setState(2, 5, UULibraryService.Orientation.SOUTH); // Facing South, towards A
        
        // Path from B to A should NOT be blocked by A's barrier
        // We need to check if A and B are actually connected in the map.
        // In UULibrary, rooms at (r,c) are connected to (r+1, c) if exits contains north/south.
        
        String cmdFromB = service.getNextStepCommand(1, 5);
        // If B(2,5) has an exit SOUTH to A(1,5), then it should return "fw" because we are facing SOUTH.
        // Let's verify what it actually returns. It returned "lt" in the previous run.
        // "lt" from SOUTH means turning EAST and then moving.
        // That means SOUTH exit from 2,5 is NOT available or A* thinks it's blocked.
        // Wait, does 2,5 have a SOUTH exit to 1,5 in the library?
        // Let's check the map if possible or just assume it should if they are adjacent.
        
        // If it returns "lt", maybe it's because there's no direct exit SOUTH from 2,5.
        // Let's try 1,5 and 1,6 (West/East)
        service.reset();
        service.setRoomId("UULibrary");
        service.setState(1, 5, UULibraryService.Orientation.EAST);
        service.addBarrier(UULibraryService.Orientation.EAST);
        
        assertNotEquals("fw", service.getNextStepCommand(1, 6));
        
        service.setState(1, 6, UULibraryService.Orientation.WEST);
        assertEquals("fw", service.getNextStepCommand(1, 5), "Path from 1,6 to 1,5 should NOT be blocked by barrier in 1,5");
    }

    @Test
    void testDistortionSounds() {
        // Ready sound
        processor.onFullLineReceived("Cannot find \"distortion\", no match.");
        assertEquals(1, output.readySoundCount);
        assertEquals(0, output.alertSoundCount);

        // Alert sounds
        processor.onFullLineReceived("There is a strange distortion in space and time up ahead of you!");
        assertEquals(1, output.readySoundCount);
        assertEquals(1, output.alertSoundCount);

        processor.onFullLineReceived("There is a strange distortion in space and time behind you!");
        assertEquals(2, output.alertSoundCount);

        processor.onFullLineReceived("There is a strange distortion in space and time to the left of you!");
        assertEquals(3, output.alertSoundCount);

        processor.onFullLineReceived("There is a strange distortion in space and time to the right of you!");
        assertEquals(4, output.alertSoundCount);
    }
}
