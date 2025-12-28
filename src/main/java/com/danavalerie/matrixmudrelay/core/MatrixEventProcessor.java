package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.matrix.RetryingMatrixSender;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.util.Sanitizer;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public final class MatrixEventProcessor {
    private static final Logger log = LoggerFactory.getLogger(MatrixEventProcessor.class);

    private final BotConfig cfg;
    private final String roomId;
    private final RetryingMatrixSender sender;
    private final MudClient mud;
    private final TranscriptLogger transcript;
    private final RoomMapService mapService;

    public MatrixEventProcessor(BotConfig cfg, String roomId, RetryingMatrixSender sender, MudClient mud, TranscriptLogger transcript) {
        this.cfg = cfg;
        this.roomId = roomId;
        this.sender = sender;
        this.mud = mud;
        this.transcript = transcript;
        this.mapService = new RoomMapService("database.db");
    }

    public void onMatrixEvent(JsonObject ev) {
        log.debug("onMatrixEvent: {}", ev);
        String type = text(ev.get("type"));
        if (!"m.room.message".equals(type)) {
            if ("m.room.encrypted".equals(type)) {
                log.warn("RECEIVED ENCRYPTED EVENT. This bot does NOT support encryption. Please disable encryption in the Matrix room settings.");
            }
            return;
        }

        String senderId = text(ev.get("sender"));
        if (senderId == null) return;

        // Ignore our own messages
        if (senderId.equals(cfg.matrix.userId)) return;

        JsonElement content = ev.get("content");
        if (content == null || !content.isJsonObject()) return;

        String msgtype = text(content.getAsJsonObject().get("msgtype"));
        if (msgtype != null && !"m.text".equals(msgtype)) return;

        String body = text(content.getAsJsonObject().get("body"));
        if (body == null) return;

        body = body.trim();
        if (body.isEmpty()) return;

        boolean isController = senderId.equals(cfg.matrix.controllingUserId);
        if (!isController) {
            if (cfg.matrix.respondToUnauthorized) {
                sender.sendText(roomId, "Ignored: only " + cfg.matrix.controllingUserId + " may control this relay.", false);
            }
            return;
        }

        String lower = body.toLowerCase();

        // Reserved words precedence
        if (!mud.isConnected() && lower.equals("#connect")) {
            handleConnect();
            return;
        }
        if (mud.isConnected() && lower.equals("#disconnect")) {
            handleDisconnect();
            return;
        }
        if (lower.equals("#status")) {
            handleStatus();
            return;
        }
        if (lower.equals("#info")) {
            handleInfo();
            return;
        }
        if (lower.equals("#map")) {
            handleMap();
            return;
        }

        // Hard safety rule: never send controller text to MUD unless currently connected
        if (!mud.isConnected()) {
            sender.sendText(roomId, "Error: MUD is disconnected. Send `#connect` first.", false);
            return;
        }

        // Alias expansion (exact match, reserved words excluded)
        Map<String, List<String>> aliases = cfg.aliases;
        try {
            if (aliases != null && aliases.containsKey(body)) {
                List<String> lines = aliases.get(body);
                if (lines == null || lines.isEmpty()) return;
                transcript.logMatrixToMud("[alias:" + body + "] " + String.join(" | ", lines));
                mud.sendLinesFromController(lines);
                return;
            }

            String sanitized = Sanitizer.sanitizeMudInput(body);
            transcript.logMatrixToMud(sanitized);
            mud.sendLinesFromController(List.of(sanitized));
        } catch (IllegalStateException e) {
            sender.sendText(roomId, "Error: " + e.getMessage(), false);
        }
    }

    private void handleConnect() {
        if (mud.isConnected()) {
            sender.sendText(roomId, "Already connected.", false);
            return;
        }
        sender.sendText(roomId, "Connecting to MUD...", false);
        try {
            mud.connect();
            sender.sendText(roomId, "Connected.", false);
        } catch (Exception e) {
            log.warn("connect failed err={}", e.toString());
            sender.sendText(roomId, "Connect failed: " + e.getMessage(), false);
        }
    }

    private void handleDisconnect() {
        if (!mud.isConnected()) {
            sender.sendText(roomId, "Already disconnected.", false);
            return;
        }
        mud.disconnect("controller requested");
        sender.sendText(roomId, "Disconnected.", false);
    }

    private void handleStatus() {
        sender.sendText(roomId, "Status: " + (mud.isConnected() ? "CONNECTED" : "DISCONNECTED"), false);
    }

    private void handleInfo() {
        sender.sendText(roomId, mud.getCurrentRoomSnapshot().formatForDisplay(), false);
    }

    private void handleMap() {
        String currentRoomId = mud.getCurrentRoomSnapshot().roomId();
        if (currentRoomId == null || currentRoomId.isBlank()) {
            sender.sendText(roomId, "Error: No room info available yet.", false);
            return;
        }
        try {
            RoomMapService.MapImage mapImage = mapService.renderMapImage(currentRoomId);
            sender.sendImage(roomId, mapImage.body(), mapImage.data(), "mud-map.png", mapImage.mimeType(),
                    mapImage.width(), mapImage.height(), false);
        } catch (RoomMapService.MapLookupException e) {
            sender.sendText(roomId, "Error: " + e.getMessage(), false);
        } catch (Exception e) {
            log.warn("map render failed err={}", e.toString());
            sender.sendText(roomId, "Error: Unable to render map.", false);
        }
    }

    private static String text(JsonElement n) {
        if (n == null || n.isJsonNull()) return null;
        return n.getAsString();
    }
}
