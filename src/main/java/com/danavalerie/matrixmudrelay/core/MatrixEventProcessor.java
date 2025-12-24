package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.matrix.RetryingMatrixSender;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.util.Sanitizer;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;
import com.fasterxml.jackson.databind.JsonNode;
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

    public MatrixEventProcessor(BotConfig cfg, String roomId, RetryingMatrixSender sender, MudClient mud, TranscriptLogger transcript) {
        this.cfg = cfg;
        this.roomId = roomId;
        this.sender = sender;
        this.mud = mud;
        this.transcript = transcript;
    }

    public void onMatrixEvent(JsonNode ev) {
        String type = text(ev.get("type"));
        if (!"m.room.message".equals(type)) return;

        String senderId = text(ev.get("sender"));
        if (senderId == null) return;

        // Ignore our own messages
        if (senderId.equals(cfg.matrix.userId)) return;

        JsonNode content = ev.get("content");
        if (content == null) return;

        String msgtype = text(content.get("msgtype"));
        if (msgtype != null && !"m.text".equals(msgtype)) return;

        String body = text(content.get("body"));
        if (body == null) return;

        body = body.trim();
        if (body.isEmpty()) return;

        boolean isController = senderId.equals(cfg.matrix.controllingUserId);
        if (!isController) {
            if (cfg.matrix.respondToUnauthorized) {
                sender.sendText(roomId, "Ignored: only " + cfg.matrix.controllingUserId + " may control this relay.");
            }
            return;
        }

        String lower = body.toLowerCase();

        // Reserved words precedence
        if (lower.equals("connect")) {
            handleConnect();
            return;
        }
        if (lower.equals("disconnect")) {
            handleDisconnect();
            return;
        }
        if (lower.equals("status")) {
            handleStatus();
            return;
        }

        // Hard safety rule: never send controller text to MUD unless currently connected
        if (!mud.isConnected()) {
            sender.sendText(roomId, "Error: MUD is disconnected. Send `connect` first.");
            return;
        }

        // Alias expansion (exact match, reserved words excluded)
        Map<String, List<String>> aliases = cfg.aliases;
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
    }

    private void handleConnect() {
        if (mud.isConnected()) {
            sender.sendText(roomId, "Already connected.");
            return;
        }
        sender.sendText(roomId, "Connecting to MUD...");
        try {
            mud.connect();
            sender.sendText(roomId, "Connected.");
        } catch (Exception e) {
            log.warn("connect failed err={}", e.toString());
            sender.sendText(roomId, "Connect failed: " + e.getMessage());
        }
    }

    private void handleDisconnect() {
        if (!mud.isConnected()) {
            sender.sendText(roomId, "Already disconnected.");
            return;
        }
        mud.disconnect("controller requested");
        sender.sendText(roomId, "Disconnected.");
    }

    private void handleStatus() {
        sender.sendText(roomId, "Status: " + (mud.isConnected() ? "CONNECTED" : "DISCONNECTED"));
    }

    private static String text(JsonNode n) {
        if (n == null || n.isNull()) return null;
        return n.asText(null);
    }
}
