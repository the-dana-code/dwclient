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
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
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
        TimerService timerService = new TimerService(cfg, Paths.get("config.json"));
        
        MudCommandProcessor processor = new MudCommandProcessor(cfg, mud, transcript, new WritTracker(), new StoreInventoryTracker(), timerService, output);
        
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
        TimerService timerService = new TimerService(cfg, Paths.get("config.json"));
        
        MudCommandProcessor processor = new MudCommandProcessor(cfg, mud, transcript, new WritTracker(), new StoreInventoryTracker(), timerService, output);
        
        processor.handleInput("/password");
        
        assertTrue(mud.sentLines.contains("supersecret"), "Password should have been sent to MUD via /password");
    }

    @Test
    void testRoomCommand() {
        BotConfig cfg = new BotConfig();
        StubMudClient mud = new StubMudClient();
        StubClientOutput output = new StubClientOutput();
        StubTranscriptLogger transcript = new StubTranscriptLogger();
        TimerService timerService = new TimerService(cfg, Paths.get("config.json"));
        
        MudCommandProcessor processor = new MudCommandProcessor(cfg, mud, transcript, new WritTracker(), new StoreInventoryTracker(), timerService, output);
        
        // Test /room usage
        processor.handleInput("/room");
        assertTrue(output.systemMessages.stream().anyMatch(m -> m.contains("Usage: /room")), "Should show usage for /room");

        // Verify /loc is unknown (or at least doesn't show room search usage)
        output.systemMessages.clear();
        processor.handleInput("/loc");
        assertTrue(output.systemMessages.stream().anyMatch(m -> m.contains("Unknown command")), "Should show unknown command for /loc");
    }
}

