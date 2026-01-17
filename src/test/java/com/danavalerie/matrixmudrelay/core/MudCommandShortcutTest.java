package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MudCommandShortcutTest {

    static class StubClientOutput implements MudCommandProcessor.ClientOutput {
        List<String> systemMessages = new ArrayList<>();
        List<String> commandEchoes = new ArrayList<>();

        @Override public void appendSystem(String text) { systemMessages.add(text); }
        @Override public void appendCommandEcho(String text) { commandEchoes.add(text); }
        @Override public void addToHistory(String command) {}
        @Override public void updateCurrentRoom(String roomId) {}
        @Override public void updateMap(String roomId) {}
        @Override public void updateStats(StatsHudRenderer.StatsHudData data) {}
        @Override public void updateContextualResults(ContextualResultList results) {}
        @Override public void updateSpeedwalkPath(List<RoomMapService.RoomLocation> path) {}
        @Override public void updateConnectionState(boolean connected) {}
    }

    static class StubMudClient extends MudClient {
        List<String> sentLines = new ArrayList<>();

        public StubMudClient() {
            super(new BotConfig.Mud(), null, null, null);
        }

        @Override
        public void sendLinesFromController(List<String> lines) {
            sentLines.addAll(lines);
        }

        @Override
        public boolean isConnected() {
            return true;
        }
    }

    static class StubTranscriptLogger extends TranscriptLogger {
        public StubTranscriptLogger() {
            super(false, null, 0, 0);
        }
        @Override public void logClientToMud(String line) {}
        @Override public void logMudToClient(String line) {}
        @Override public void logSystem(String line) {}
    }

    @Test
    void testPwShortcut() {
        BotConfig cfg = new BotConfig();
        cfg.aliases.put("#password", List.of("supersecret"));

        StubMudClient mud = new StubMudClient();
        StubClientOutput output = new StubClientOutput();
        StubTranscriptLogger transcript = new StubTranscriptLogger();
        
        MudCommandProcessor processor = new MudCommandProcessor(cfg, mud, transcript, new WritTracker(), new StoreInventoryTracker(), output);
        
        processor.handleInput("pw");
        
        assertTrue(mud.sentLines.contains("supersecret"), "Password should have been sent to MUD");
        assertTrue(output.commandEchoes.contains("(password)"), "Password should have been masked in echo");
    }

    @Test
    void testMmPasswordSubcommand() {
        BotConfig cfg = new BotConfig();
        cfg.aliases.put("#password", List.of("supersecret"));

        StubMudClient mud = new StubMudClient();
        StubClientOutput output = new StubClientOutput();
        StubTranscriptLogger transcript = new StubTranscriptLogger();
        
        MudCommandProcessor processor = new MudCommandProcessor(cfg, mud, transcript, new WritTracker(), new StoreInventoryTracker(), output);
        
        processor.handleInput("mm password");
        
        assertTrue(mud.sentLines.contains("supersecret"), "Password should have been sent to MUD via mm password");
    }
}
