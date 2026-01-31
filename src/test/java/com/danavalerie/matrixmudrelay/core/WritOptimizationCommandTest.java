package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.config.DeliveryRouteMappings;
import com.danavalerie.matrixmudrelay.config.UiConfig;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WritOptimizationCommandTest {

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
        @Override public void onCharacterChanged(String characterName) {}
        @Override public void updateRepeatLastSpeedwalkItem() {}
        @Override public void appendTeleportBanner(String banner) {}
        @Override public void showEditPasswordDialog(Runnable onPasswordStored) {}
        @Override public void setTeleportQueued(String command, String targetName) {}
        @Override public void clearTeleportQueued() {}
    }

    static class StubMudClient extends MudClient {
        List<String> sentCommands = new ArrayList<>();
        public StubMudClient() {
            super(new ClientConfig.Mud(), null, null);
        }
        @Override
        public void sendLinesFromController(List<String> lines) {
            sentCommands.addAll(lines);
        }
    }

    @Test
    void testCommandFollowsOptimizedOrder() {
        WritTracker tracker = new WritTracker();
        // Original order: 1: Petit Fours, 2: Lab Robe, 3: Harp, 4: Sticky Bun
        String writText = "You read the official employment writ:\n" +
                "[ ] a petit fours to Stuck at Stuck's Bar on Speedwell Street\n" +
                "[ ] a second-hand lab robe to the ice cream troll at Ye Olde Ice Cream Parlour on Sator Square\n" +
                "[ ] a wooden harp to Jacinthe Bockxse at Batchev's World of Maps on Ankh Street\n" +
                "[ ] a sticky bun to Starspirit Moonfire at Krazzander's Magik Shoppe on Cockbill Street\n" +
                "You have until Sat Jan 31 21:16:03 2026 to complete this job.";
        tracker.ingest(writText);

        List<WritTracker.WritRequirement> originalReqs = tracker.getRequirements();
        assertEquals(4, originalReqs.size());
        assertEquals("petit fours", originalReqs.get(0).item());

        // Simulate optimization where Sticky Bun (original index 3) becomes the first item
        WritTracker.WritRequirement stickyBun = originalReqs.get(3);
        WritTracker.WritRequirement petitFours = originalReqs.get(0);
        WritTracker.WritRequirement labRobe = originalReqs.get(1);
        WritTracker.WritRequirement harp = originalReqs.get(2);

        List<WritTracker.WritRequirement> optimizedReqs = List.of(stickyBun, labRobe, petitFours, harp);
        
        // Update tracker with optimized order (simulating what DesktopClientFrame now does)
        tracker.setRequirements(new ArrayList<>(optimizedReqs));

        // Set up MudCommandProcessor
        StubMudClient mud = new StubMudClient();
        RoomMapService mapService = new RoomMapService(new MapDataService());
        Supplier<DeliveryRouteMappings> routeMappingsSupplier = () -> new DeliveryRouteMappings(Collections.emptyList());
        
        MudCommandProcessor processor = new MudCommandProcessor(
                new ClientConfig(), new UiConfig(), null, mud, mapService, tracker,
                new StoreInventoryTracker(), null, routeMappingsSupplier, new StubClientOutput()
        );

        // Execute /writ 1 deliver
        processor.handleInput("/writ 1 deliver");

        // Verify that it delivers the sticky bun (which is now #1 in tracker)
        assertEquals(1, mud.sentCommands.size());
        assertEquals("deliver sticky bun to Starspirit Moonfire", mud.sentCommands.get(0));
        
        // Execute /writ 3 deliver
        processor.handleInput("/writ 3 deliver");
        assertEquals(2, mud.sentCommands.size());
        assertEquals("deliver petit fours to Stuck", mud.sentCommands.get(1));
    }
}
