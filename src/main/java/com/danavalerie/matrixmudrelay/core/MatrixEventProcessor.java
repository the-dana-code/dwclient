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
    private static final int ROOM_SEARCH_LIMIT = 100;

    private final BotConfig cfg;
    private final String roomId;
    private final RetryingMatrixSender sender;
    private final MudClient mud;
    private final TranscriptLogger transcript;
    private final RoomMapService mapService;
    private List<RoomMapService.RoomSearchResult> lastRoomSearchResults = List.of();
    private List<RoomMapService.ItemSearchResult> lastItemSearchResults = List.of();

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

        body = normalizeMatrixInput(body);
        String lower = body.toLowerCase();

        // Reserved words precedence
        if (lower.startsWith("mm")) {
            handleMm(body);
            return;
        }

        // Alias expansion (exact match, reserved words excluded)
        try {


            if (tryAlias(body)) {
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
        mud.disconnect("controller requested", null);
        sender.sendText(roomId, "Disconnected.", false);
    }

    private void handleStatus() {
        sender.sendText(roomId, "Status: " + (mud.isConnected() ? "CONNECTED" : "DISCONNECTED"), false);
    }

    private void handleInfo() {
        sender.sendText(roomId, mud.getCurrentRoomSnapshot().formatForDisplay(), false);
    }

    private void handleMap(String body) {
        String[] parts = body.trim().split("\\s+");
        String targetRoomId;
        if (parts.length == 2) {
            targetRoomId = mud.getCurrentRoomSnapshot().roomId();
            if (targetRoomId == null || targetRoomId.isBlank()) {
                sender.sendText(roomId, "Error: Can't determine your location.", false);
                return;
            }
        } else {
            if (lastRoomSearchResults.isEmpty()) {
                sender.sendText(roomId, "Error: No recent room search results. Use #mm first.", false);
                return;
            }
            int selection;
            try {
                selection = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                sender.sendText(roomId, "Usage: #map <number>", false);
                return;
            }
            if (selection < 1 || selection > lastRoomSearchResults.size()) {
                sender.sendText(roomId, "Error: Map selection must be between 1 and " + lastRoomSearchResults.size() + ".", false);
                return;
            }
            targetRoomId = lastRoomSearchResults.get(selection - 1).roomId();
        }
        try {
            RoomMapService.MapImage mapImage = mapService.renderMapImage(targetRoomId);
            sender.sendImage(roomId, mapImage.body(), mapImage.data(), "mud-map.png", mapImage.mimeType(),
                    mapImage.width(), mapImage.height(), false);
        } catch (RoomMapService.MapLookupException e) {
            sender.sendText(roomId, "Error: " + e.getMessage(), false);
        } catch (Exception e) {
            log.warn("map render failed err={}", e.toString());
            sender.sendText(roomId, "Error: Unable to render map.", false);
        }
    }

    private void handleMm(String body) {
        String remainder = body.length() > 3 ? body.substring(3).trim() : "";
        if (remainder.isBlank()) {
            handleRoomSearchQuery("");
            return;
        }
        String[] parts = remainder.split("\\s+", 2);
        String subcommand = parts[0].toLowerCase();
        String query = parts.length > 1 ? parts[1].trim() : "";
        if ("connect".equals(subcommand)) {
            handleConnect();
            return;
        }
        if ("disconnect".equals(subcommand)) {
            handleDisconnect();
            return;
        }
        if ("status".equals(subcommand)) {
            handleStatus();
            return;
        }
        if ("info".equals(subcommand)) {
            handleInfo();
            return;
        }
        if ("map".startsWith(subcommand)) {
            handleMap(body);
            return;
        }
        if ("npc".equals(subcommand)) {
            handleNpcSearchQuery(query);
            return;
        }
        if ("item".equals(subcommand)) {
            try {
                int number = Integer.parseInt(query);
                handleItemSelection(number);
            } catch (NumberFormatException e) {
                handleItemSearchQuery(query);
            }
            return;
        }
        if ("route".equals(subcommand)) {
            handleRoute(query);
            return;
        }
        try {
            if (tryAlias("#" + remainder)) {
                return;
            }
        } catch (IllegalStateException e) {
            sender.sendText(roomId, "Error: " + e.getMessage(), false);
            return;
        }
        handleRoomSearchQuery(remainder);
    }

    private boolean tryAlias(String trigger) {
        Map<String, List<String>> aliases = cfg.aliases;
        if (aliases == null || !aliases.containsKey(trigger)) {
            return false;
        }
        List<String> lines = aliases.get(trigger);
        if (lines == null || lines.isEmpty()) {
            return true;
        }
        transcript.logMatrixToMud("[alias:" + trigger + "] " + String.join(" | ", lines));
        mud.sendLinesFromController(lines);
        return true;
    }

    private void handleRoomSearchQuery(String query) {
        if (query.isBlank()) {
            sender.sendText(roomId, "Usage: #mm <room name fragment>", false);
            return;
        }
        try {
            List<RoomMapService.RoomSearchResult> results = mapService.searchRoomsByName(query, ROOM_SEARCH_LIMIT + 1);
            boolean truncated = results.size() > ROOM_SEARCH_LIMIT;
            if (truncated) {
                results = results.subList(0, ROOM_SEARCH_LIMIT);
            }
            lastRoomSearchResults = List.copyOf(results);
            if (results.isEmpty()) {
                sender.sendText(roomId, "No rooms found matching \"" + query + "\".", false);
                return;
            }
            StringBuilder out = new StringBuilder();
            out.append("Room search for \"").append(query).append("\":");
            for (int i = 0; i < results.size(); i++) {
                RoomMapService.RoomSearchResult result = results.get(i);
                out.append("\n")
                        .append(i + 1)
                        .append(") ")
                        .append(result.sourceInfo() != null ? "[" + result.sourceInfo() + "] " : "")
                        .append(mapService.getMapDisplayName(result.mapId()))
                        .append(": ")
                        .append(result.roomShort());
            }
            if (truncated) {
                out.append("\nShowing first ").append(ROOM_SEARCH_LIMIT).append(" matches. Refine your search.");
            }
            sender.sendText(roomId, out.toString(), false);
        } catch (RoomMapService.MapLookupException e) {
            sender.sendText(roomId, "Error: " + e.getMessage(), false);
        } catch (Exception e) {
            log.warn("room search failed err={}", e.toString());
            sender.sendText(roomId, "Error: Unable to search rooms.", false);
        }
    }

    private void handleItemSearchQuery(String query) {
        if (query.isBlank()) {
            sender.sendText(roomId, "Usage: #mm item <item name fragment>", false);
            return;
        }
        try {
            List<RoomMapService.ItemSearchResult> results = mapService.searchItemsByName(query, ROOM_SEARCH_LIMIT + 1);
            boolean truncated = results.size() > ROOM_SEARCH_LIMIT;
            if (truncated) {
                results = results.subList(0, ROOM_SEARCH_LIMIT);
            }
            lastItemSearchResults = List.copyOf(results);
            if (results.isEmpty()) {
                sender.sendText(roomId, "No items found matching \"" + query + "\".", false);
                return;
            }
            StringBuilder out = new StringBuilder();
            out.append("Item search for \"").append(query).append("\":");
            for (int i = 0; i < results.size(); i++) {
                RoomMapService.ItemSearchResult result = results.get(i);
                out.append("\n")
                        .append(i + 1)
                        .append(") ")
                        .append(result.itemName());
            }
            if (truncated) {
                out.append("\nShowing first ").append(ROOM_SEARCH_LIMIT).append(" matches. Refine your search.");
            }
            out.append("\nUse 'mm item <number>' to view room locations.");
            sender.sendText(roomId, out.toString(), false);
        } catch (RoomMapService.MapLookupException e) {
            sender.sendText(roomId, "Error: " + e.getMessage(), false);
        } catch (Exception e) {
            log.warn("item search failed err={}", e.toString());
            sender.sendText(roomId, "Error: Unable to search items.", false);
        }
    }

    private void handleItemSelection(int selection) {
        if (lastItemSearchResults.isEmpty()) {
            sender.sendText(roomId, "Error: No recent item search results. Use #mm item first.", false);
            return;
        }
        if (selection < 1 || selection > lastItemSearchResults.size()) {
            sender.sendText(roomId, "Error: Item selection must be between 1 and " + lastItemSearchResults.size() + ".", false);
            return;
        }
        String itemName = lastItemSearchResults.get(selection - 1).itemName();
        try {
            List<RoomMapService.RoomSearchResult> results = mapService.searchRoomsByItemName(itemName, ROOM_SEARCH_LIMIT + 1);
            boolean truncated = results.size() > ROOM_SEARCH_LIMIT;
            if (truncated) {
                results = results.subList(0, ROOM_SEARCH_LIMIT);
            }
            lastRoomSearchResults = List.copyOf(results);
            if (results.isEmpty()) {
                sender.sendText(roomId, "No rooms found for item \"" + itemName + "\".", false);
                return;
            }
            StringBuilder out = new StringBuilder();
            out.append("Item locations for \"").append(itemName).append("\":");
            for (int i = 0; i < results.size(); i++) {
                RoomMapService.RoomSearchResult result = results.get(i);
                out.append("\n")
                        .append(i + 1)
                        .append(") ")
                        .append(result.sourceInfo() != null ? "[" + result.sourceInfo() + "] " : "")
                        .append(mapService.getMapDisplayName(result.mapId()))
                        .append(": ")
                        .append(result.roomShort());
            }
            if (truncated) {
                out.append("\nShowing first ").append(ROOM_SEARCH_LIMIT).append(" matches. Refine your search.");
            }
            sender.sendText(roomId, out.toString(), false);
        } catch (RoomMapService.MapLookupException e) {
            sender.sendText(roomId, "Error: " + e.getMessage(), false);
        } catch (Exception e) {
            log.warn("item room search failed err={}", e.toString());
            sender.sendText(roomId, "Error: Unable to search item locations.", false);
        }
    }

    private void handleNpcSearchQuery(String query) {
        if (query.isBlank()) {
            sender.sendText(roomId, "Usage: #mm npc <npc name fragment>", false);
            return;
        }
        try {
            List<RoomMapService.NpcSearchResult> results = mapService.searchNpcsByName(query, ROOM_SEARCH_LIMIT + 1);
            boolean truncated = results.size() > ROOM_SEARCH_LIMIT;
            if (truncated) {
                results = results.subList(0, ROOM_SEARCH_LIMIT);
            }
            lastRoomSearchResults = results.stream()
                    .map(result -> new RoomMapService.RoomSearchResult(
                            result.roomId(),
                            result.mapId(),
                            result.xpos(),
                            result.ypos(),
                            result.roomShort(),
                            result.roomType(),
                            null))
                    .toList();
            if (results.isEmpty()) {
                sender.sendText(roomId, "No NPCs found matching \"" + query + "\".", false);
                return;
            }
            StringBuilder out = new StringBuilder();
            out.append("NPC search for \"").append(query).append("\":");
            for (int i = 0; i < results.size(); i++) {
                RoomMapService.NpcSearchResult result = results.get(i);
                out.append("\n")
                        .append(i + 1)
                        .append(") ")
                        .append(mapService.getMapDisplayName(result.mapId()))
                        .append(": ")
                        .append(result.npcName())
                        .append(" - ")
                        .append(result.roomShort());
            }
            if (truncated) {
                out.append("\nShowing first ").append(ROOM_SEARCH_LIMIT).append(" matches. Refine your search.");
            }
            sender.sendText(roomId, out.toString(), false);
        } catch (RoomMapService.MapLookupException e) {
            sender.sendText(roomId, "Error: " + e.getMessage(), false);
        } catch (Exception e) {
            log.warn("npc search failed err={}", e.toString());
            sender.sendText(roomId, "Error: Unable to search NPCs.", false);
        }
    }

    private void handleRoute(String body) {
        if (!mud.isConnected()) {
            sender.sendText(roomId, "Error: MUD is disconnected. Send `#connect` first.", false);
            return;
        }
        if (lastRoomSearchResults.isEmpty()) {
            sender.sendText(roomId, "Error: No recent room search results.", false);
            return;
        }
        int selection;
        try {
            selection = Integer.parseInt(body);
        } catch (NumberFormatException e) {
            sender.sendText(roomId, "Usage: mm route <number>", false);
            return;
        }
        if (selection < 1 || selection > lastRoomSearchResults.size()) {
            sender.sendText(roomId, "Error: Route selection must be between 1 and " + lastRoomSearchResults.size() + ".", false);
            return;
        }
        RoomMapService.RoomSearchResult target = lastRoomSearchResults.get(selection - 1);
        String currentRoomId = mud.getCurrentRoomSnapshot().roomId();
        if (currentRoomId == null || currentRoomId.isBlank()) {
            sender.sendText(roomId, "Error: No room info available yet.", false);
            return;
        }
        if (currentRoomId.equals(target.roomId())) {
            sender.sendText(roomId, "Already in " + target.roomShort() + ".", false);
            return;
        }
        try {
            RoomMapService.RouteResult route = mapService.findRoute(currentRoomId, target.roomId());
            List<String> exits = route.steps().stream()
                    .map(RoomMapService.RouteStep::exit)
                    .toList();
            StringBuilder out = new StringBuilder();
            out.append("Route to ")
                    .append(target.roomShort())
                    .append(" (")
                    .append(target.roomId())
                    .append("):");
            if (exits.isEmpty()) {
                out.append("\nAlready there.");
            } else if (exits.size() > 150) {
                out.append("\nRoute too long (" + exits.size() + " moves).");
            } else {
                out.append("\n").append(String.join(" -> ", exits));
                out.append("\nSteps: ").append(exits.size());
                String aliasName = "MooMooQuowsRun";
                String aliasCommand = "alias " + aliasName + " " + String.join(";", exits);
                mud.sendLinesFromController(List.of(aliasCommand));
                out.append("\nAlias: ").append(aliasName);
            }
            sender.sendText(roomId, out.toString(), false);
        } catch (RoomMapService.MapLookupException e) {
            sender.sendText(roomId, "Error: " + e.getMessage(), false);
        } catch (Exception e) {
            log.warn("route search failed err={}", e.toString());
            sender.sendText(roomId, "Error: Unable to calculate route.", false);
        }
    }

    private static String text(JsonElement n) {
        if (n == null || n.isJsonNull()) return null;
        return n.getAsString();
    }

    private static String normalizeMatrixInput(String body) {
        if (body.isEmpty()) {
            return body;
        }
        int firstCodePoint = body.codePointAt(0);
        if (!Character.isUpperCase(firstCodePoint)) {
            return body;
        }
        int lowerFirst = Character.toLowerCase(firstCodePoint);
        StringBuilder normalized = new StringBuilder(body.length());
        normalized.appendCodePoint(lowerFirst);
        normalized.append(body.substring(Character.charCount(firstCodePoint)));
        return normalized.toString();
    }
}
