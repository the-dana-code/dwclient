package com.danavalerie.matrixmudrelay.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SunLogTest {
    @TempDir
    Path tempDir;

    private Path sunLogPath;

    @BeforeEach
    void setUp() {
        sunLogPath = tempDir.resolve("sun.log");
        System.setProperty("SUNLOG_PATH", sunLogPath.toString());
        System.clearProperty("SUNLOG");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("SUNLOG");
        System.clearProperty("SUNLOG_PATH");
        UULibraryService.getInstance().reset();
    }

    @Test
    void testSunLogging() throws IOException {
        System.setProperty("SUNLOG", "true");
        
        MudCommandProcessor.ClientOutput output = new MudCommandProcessor.ClientOutput() {
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
        };
        MudCommandProcessor processor = new MudCommandProcessor(null, null, null, null, null, null, null, null, output);

        String sunriseMessage = "The turnwise sky starts to lighten as the sun peeks over the horizon.";
        processor.onFullLineReceived(sunriseMessage);

        assertTrue(Files.exists(sunLogPath), "sun.log should be created");
        List<String> lines = Files.readAllLines(sunLogPath);
        assertEquals(1, lines.size());
        String logLine = lines.get(0);
        assertTrue(logLine.endsWith(sunriseMessage));
        assertTrue(logLine.contains("GMT"));
        assertTrue(logLine.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} GMT \\| \\d{1,2}:\\d{2}(?:am|pm) \\| .*"),
                "Log line should match the expected format with milliseconds, in-game time and separators");
    }

    @Test
    void testSunLoggingDisabled() throws IOException {
        System.clearProperty("SUNLOG");
        
        MudCommandProcessor.ClientOutput output = new MudCommandProcessor.ClientOutput() {
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
        };
        MudCommandProcessor processor = new MudCommandProcessor(null, null, null, null, null, null, null, null, output);

        String sunriseMessage = "The turnwise sky starts to lighten as the sun peeks over the horizon.";
        processor.onFullLineReceived(sunriseMessage);

        assertFalse(Files.exists(sunLogPath), "sun.log should not be created when SUNLOG property is not set");
    }

    @Test
    void testMultipleMessages() throws IOException {
        System.setProperty("SUNLOG", "true");
        
        MudCommandProcessor.ClientOutput output = new MudCommandProcessor.ClientOutput() {
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
        };
        MudCommandProcessor processor = new MudCommandProcessor(null, null, null, null, null, null, null, null, output);

        processor.onFullLineReceived("The turnwise sky starts to lighten as the sun peeks over the horizon.");
        processor.onFullLineReceived("The sun sinks further below the widdershins horizon.");

        assertTrue(Files.exists(sunLogPath));
        List<String> lines = Files.readAllLines(sunLogPath);
        assertEquals(2, lines.size());
        
        for (String line : lines) {
            assertTrue(line.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} GMT \\| \\d{1,2}:\\d{2}(?:am|pm) \\| .*"),
                    "Log line should match the expected format with milliseconds, in-game time and separators");
        }
        
        assertTrue(lines.get(0).endsWith("The turnwise sky starts to lighten as the sun peeks over the horizon."));
        assertTrue(lines.get(1).endsWith("The sun sinks further below the widdershins horizon."));
    }
}
