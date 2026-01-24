package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RestartCommandTest {
    @BeforeEach
    void setUp() {
        UULibraryService.getInstance().reset();
    }

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
        @Override public void appendTeleportBanner(String banner) {}
    }

    @Test
    void testRestartCommandLogic() throws Exception {
        BotConfig cfg = new BotConfig();
        Path configPath = Paths.get("config.json");
        MockOutput output = new MockOutput();
        
        MudCommandProcessor processor = new MudCommandProcessor(
                cfg, configPath, null, null, null, null, null, () -> null, output
        );

        Field targetField = MudCommandProcessor.class.getDeclaredField("lastSpeedwalkTargetRoomId");
        targetField.setAccessible(true);
        Field postCommandsField = MudCommandProcessor.class.getDeclaredField("lastSpeedwalkPostCommands");
        postCommandsField.setAccessible(true);

        // Initially no speedwalk
        assertFalse(processor.hasLastSpeedwalk());
        assertNull(processor.getLastSpeedwalkTargetName());
        assertNull(targetField.get(processor));
        
        // Execute /restart -> error message
        processor.handleInput("/restart");
        assertTrue(output.systemMessages.stream().anyMatch(m -> m.contains("No previous speedwalk available")));
        
        // Set speedwalk
        try {
            processor.speedwalkTo("room1");
        } catch (Exception ignored) {}
        
        assertTrue(processor.hasLastSpeedwalk());
        assertEquals("room1", processor.getLastSpeedwalkTargetName());
        assertEquals("room1", targetField.get(processor));
        assertNull(postCommandsField.get(processor));

        // Set speedwalk with commands
        try {
            processor.speedwalkToThenCommand("room2", "open door");
        } catch (Exception ignored) {}

        assertTrue(processor.hasLastSpeedwalk());
        assertEquals("room2", targetField.get(processor));
        assertEquals(List.of("open door"), postCommandsField.get(processor));
    }
}
