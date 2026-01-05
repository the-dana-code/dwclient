package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.mud.TelnetDecoder;
import com.danavalerie.matrixmudrelay.util.Sanitizer;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MudCommandProcessor implements MudClient.MudGmcpListener {
    private static final Logger log = LoggerFactory.getLogger(MudCommandProcessor.class);
    private static final int ROOM_SEARCH_LIMIT = 100;

    public interface ClientOutput {
        void appendSystem(String text);

        void updateMap(String roomId);

        void updateStats(StatsHudRenderer.StatsHudData data);
    }

    private final BotConfig cfg;
    private final MudClient mud;
    private final TranscriptLogger transcript;
    private final RoomMapService mapService;
    private final WritTracker writTracker;
    private final ClientOutput output;
    private final ExecutorService background;

    private List<RoomMapService.RoomSearchResult> lastRoomSearchResults = List.of();
    private List<RoomMapService.ItemSearchResult> lastItemSearchResults = List.of();
    private boolean useTeleports = true;
    private volatile String lastRoomId = null;

    public MudCommandProcessor(BotConfig cfg,
                               MudClient mud,
                               TranscriptLogger transcript,
                               WritTracker writTracker,
                               ClientOutput output) {
        this.cfg = cfg;
        this.mud = mud;
        this.transcript = transcript;
        this.writTracker = writTracker;
        this.output = output;
        this.mapService = new RoomMapService("database.db");
        this.background = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mud-command");
            t.setDaemon(true);
            return t;
        });
    }

    public void shutdown() {
        background.shutdownNow();
    }

    public void handleInput(String input) {
        if (input == null) {
            return;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            try {
                mud.sendLinesFromController(List.of(""));
            } catch (IllegalStateException e) {
                output.appendSystem("Error: " + e.getMessage());
            }
            return;
        }

        String normalized = normalizeInput(trimmed);
        String lower = normalized.toLowerCase(Locale.ROOT);

        if (lower.startsWith("#mm")) {
            handleMm(normalized.substring(1));
            return;
        }
        if (lower.startsWith("mm")) {
            handleMm(normalized);
            return;
        }

        try {
            if (tryAlias(normalized)) {
                return;
            }
            String sanitized = Sanitizer.sanitizeMudInput(normalized);
            transcript.logClientToMud(sanitized);
            mud.sendLinesFromController(List.of(sanitized));
        } catch (IllegalStateException e) {
            output.appendSystem("Error: " + e.getMessage());
        }
    }

    @Override
    public void onGmcp(TelnetDecoder.GmcpMessage message) {
        String roomId = mud.getCurrentRoomSnapshot().roomId();
        if (roomId != null && !roomId.isBlank() && !roomId.equals(lastRoomId)) {
            lastRoomId = roomId;
            output.updateMap(roomId);
        }
        if (message == null) {
            return;
        }
        String command = message.command();
        if (command == null) {
            return;
        }
        if ("char.vitals".equalsIgnoreCase(command.trim()) || "char.info".equalsIgnoreCase(command.trim())) {
            StatsHudRenderer.StatsHudData data = StatsHudRenderer.extract(mud.getCurrentRoomSnapshot());
            output.updateStats(data);
        }
    }

    private void handleConnect() {
        if (mud.isConnected()) {
            output.appendSystem("Already connected.");
            return;
        }
        output.appendSystem("Connecting to MUD...");
        background.submit(() -> {
            try {
                mud.connect();
                output.appendSystem("Connected.");
            } catch (Exception e) {
                log.warn("connect failed err={}", e.toString());
                output.appendSystem("Connect failed: " + e.getMessage());
            }
        });
    }

    private void handleDisconnect() {
        if (!mud.isConnected()) {
            output.appendSystem("Already disconnected.");
            return;
        }
        mud.disconnect("controller requested", null);
        output.appendSystem("Disconnected.");
    }

    private void handleStatus() {
        output.appendSystem("Status: " + (mud.isConnected() ? "CONNECTED" : "DISCONNECTED"));
    }

    private void handleInfo() {
        output.appendSystem(mud.getCurrentRoomSnapshot().formatForDisplay());
    }

    private void handleMap(String body) {
        String[] parts = body.trim().split("\\s+");
        String targetRoomId;
        if (parts.length < 3) {
            targetRoomId = mud.getCurrentRoomSnapshot().roomId();
            if (targetRoomId == null || targetRoomId.isBlank()) {
                output.appendSystem("Error: Can't determine your location.");
                return;
            }
        } else {
            if (lastRoomSearchResults.isEmpty()) {
                output.appendSystem("Error: No recent room search results. Use mm loc first.");
                return;
            }
            int selection;
            try {
                selection = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                output.appendSystem("Usage: mm map <number>");
                return;
            }
            if (selection < 1 || selection > lastRoomSearchResults.size()) {
                output.appendSystem("Error: Map selection must be between 1 and " + lastRoomSearchResults.size() + ".");
                return;
            }
            targetRoomId = lastRoomSearchResults.get(selection - 1).roomId();
        }
        output.updateMap(targetRoomId);
        output.appendSystem("Map updated.");
    }

    private void handleMm(String body) {
        String remainder = body.length() > 3 ? body.substring(3).trim() : "";
        if (remainder.isBlank()) {
            handleRoomSearchQuery("");
            return;
        }
        String[] parts = remainder.split("\\s+", 2);
        String subcommand = parts[0].toLowerCase(Locale.ROOT);
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
        if ("loc".equals(subcommand)) {
            handleRoomSearchQuery(query);
            return;
        }
        if ("writ".equals(subcommand)) {
            handleWrit(query);
            return;
        }
        if ("route".equals(subcommand)) {
            handleRoute(query);
            return;
        }
        if ("tp".equals(subcommand)) {
            useTeleports = true;
            output.appendSystem("Teleport-assisted routing enabled.");
            return;
        }
        if ("notp".equals(subcommand)) {
            useTeleports = false;
            output.appendSystem("Teleport-assisted routing disabled.");
            return;
        }
        try {
            if (tryAlias("#" + remainder)) {
                return;
            }
        } catch (IllegalStateException e) {
            output.appendSystem("Error: " + e.getMessage());
            return;
        }
        output.appendSystem("Usage: mm loc <room name fragment>");
    }

    private void handleWrit(String query) {
        List<WritTracker.WritRequirement> requirements = writTracker.getRequirements();
        if (query.isBlank()) {
            if (requirements.isEmpty()) {
                output.appendSystem("No writ requirements tracked yet.");
                return;
            }
            StringBuilder out = new StringBuilder("Current writ requirements:");
            for (int i = 0; i < requirements.size(); i++) {
                WritTracker.WritRequirement req = requirements.get(i);
                out.append("\n")
                        .append(i + 1)
                        .append(") ")
                        .append(req.quantity())
                        .append("x ")
                        .append(req.item())
                        .append(" -> ")
                        .append(req.npc())
                        .append(" @ ")
                        .append(req.location());
            }
            output.appendSystem(out.toString());
            return;
        }
        String[] parts = query.split("\\s+");
        if (parts.length != 2) {
            output.appendSystem("Usage: mm writ <number> [item|npc|loc|deliver]");
            return;
        }
        int selection;
        try {
            selection = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            output.appendSystem("Usage: mm writ <number> [item|npc|loc|deliver]");
            return;
        }
        String subcommand = parts[1].toLowerCase(Locale.ROOT);
        if (selection < 1 || selection > requirements.size()) {
            output.appendSystem("Error: Writ selection must be between 1 and " + requirements.size() + ".");
            return;
        }
        WritTracker.WritRequirement req = requirements.get(selection - 1);
        switch (subcommand) {
            case "item" -> handleItemSearchQuery(req.item());
            case "npc" -> handleNpcSearchQuery(req.npc());
            case "loc" -> handleRoomSearchQuery(req.location());
            case "deliver" -> {
                String command = Sanitizer.sanitizeMudInput("deliver " + req.item() + " to " + req.npc());
                transcript.logClientToMud(command);
                try {
                    mud.sendLinesFromController(List.of(command));
                } catch (IllegalStateException e) {
                    output.appendSystem("Error: " + e.getMessage());
                }
            }
            default -> output.appendSystem("Usage: mm writ <number> [item|npc|loc|deliver]");
        }
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
        transcript.logClientToMud("[alias:" + trigger + "] " + String.join(" | ", lines));
        mud.sendLinesFromController(lines);
        return true;
    }

    private void handleRoomSearchQuery(String query) {
        if (query.isBlank()) {
            output.appendSystem("Usage: mm loc <room name fragment>");
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
                output.appendSystem("No rooms found matching \"" + query + "\".");
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
            output.appendSystem(out.toString());
        } catch (RoomMapService.MapLookupException e) {
            output.appendSystem("Error: " + e.getMessage());
        } catch (Exception e) {
            log.warn("room search failed err={}", e.toString());
            output.appendSystem("Error: Unable to search rooms.");
        }
    }

    private void handleItemSearchQuery(String query) {
        if (query.isBlank()) {
            output.appendSystem("Usage: mm item <item name fragment>");
            return;
        }
        lastRoomSearchResults = List.of();
        try {
            ItemSearchResponse response = searchItemsWithFallback(query);
            List<RoomMapService.ItemSearchResult> results = response.results();
            boolean truncated = results.size() > ROOM_SEARCH_LIMIT;
            if (truncated) {
                results = results.subList(0, ROOM_SEARCH_LIMIT);
            }
            lastItemSearchResults = List.copyOf(results);
            if (results.isEmpty()) {
                output.appendSystem("No items found matching \"" + query + "\".");
                return;
            }
            StringBuilder out = new StringBuilder();
            out.append("Item search for \"").append(response.termUsed()).append("\":");
            if (!response.termUsed().equalsIgnoreCase(query.trim())) {
                out.append(" (from \"").append(query.trim()).append("\")");
            }
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
            output.appendSystem(out.toString());
        } catch (RoomMapService.MapLookupException e) {
            output.appendSystem("Error: " + e.getMessage());
        } catch (Exception e) {
            log.warn("item search failed err={}", e.toString());
            output.appendSystem("Error: Unable to search items.");
        }
    }

    private ItemSearchResponse searchItemsWithFallback(String query) throws Exception {
        List<String> terms = buildItemSearchTerms(query);
        for (String term : terms) {
            List<RoomMapService.ItemSearchResult> results = mapService.searchItemsByName(term, ROOM_SEARCH_LIMIT + 1);
            if (!results.isEmpty()) {
                return new ItemSearchResponse(term, results);
            }
        }
        return new ItemSearchResponse(query.trim(), List.of());
    }

    private List<String> buildItemSearchTerms(String query) {
        String trimmed = query.trim();
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (!trimmed.isBlank()) {
            terms.add(trimmed);
            for (String singular : singularizePhrase(trimmed)) {
                terms.add(singular);
            }
        }
        return List.copyOf(terms);
    }

    private List<String> singularizePhrase(String phrase) {
        String[] parts = phrase.trim().split("\\s+");
        if (parts.length == 0) {
            return List.of();
        }
        String last = parts[parts.length - 1];
        List<String> singulars = singularizeWord(last);
        if (singulars.isEmpty()) {
            return List.of();
        }
        List<String> phrases = new ArrayList<>();
        for (String singular : singulars) {
            if (singular.equalsIgnoreCase(last)) {
                continue;
            }
            parts[parts.length - 1] = singular;
            phrases.add(String.join(" ", parts));
        }
        return phrases;
    }

    private List<String> singularizeWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (lower.endsWith("ies") && lower.length() > 3) {
            candidates.add(word.substring(0, word.length() - 1));
            candidates.add(word.substring(0, word.length() - 3) + "y");
        }
        if (lower.endsWith("oes") && lower.length() > 3) {
            candidates.add(word.substring(0, word.length() - 1));
        }
        if (lower.endsWith("ves") && lower.length() > 3) {
            candidates.add(word.substring(0, word.length() - 3) + "f");
            candidates.add(word.substring(0, word.length() - 3) + "fe");
        }
        if (lower.endsWith("men") && lower.length() > 3) {
            candidates.add(word.substring(0, word.length() - 3) + "man");
        }
        if (lower.endsWith("es") && lower.length() > 2 && !lower.endsWith("ies") && !lower.endsWith("oes")) {
            candidates.add(word.substring(0, word.length() - 2));
        }
        if (lower.endsWith("s") && lower.length() > 1) {
            candidates.add(word.substring(0, word.length() - 1));
        }
        return new ArrayList<>(candidates);
    }

    private record ItemSearchResponse(String termUsed, List<RoomMapService.ItemSearchResult> results) {
    }

    private void handleItemSelection(int selection) {
        if (lastItemSearchResults.isEmpty()) {
            output.appendSystem("Error: No recent item search results. Use mm item first.");
            return;
        }
        if (selection < 1 || selection > lastItemSearchResults.size()) {
            output.appendSystem("Error: Item selection must be between 1 and " + lastItemSearchResults.size() + ".");
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
                output.appendSystem("No rooms found for item \"" + itemName + "\".");
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
            output.appendSystem(out.toString());
        } catch (RoomMapService.MapLookupException e) {
            output.appendSystem("Error: " + e.getMessage());
        } catch (Exception e) {
            log.warn("item room search failed err={}", e.toString());
            output.appendSystem("Error: Unable to search item locations.");
        }
    }

    private void handleNpcSearchQuery(String query) {
        if (query.isBlank()) {
            output.appendSystem("Usage: mm npc <npc name fragment>");
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
                output.appendSystem("No NPCs found matching \"" + query + "\".");
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
            output.appendSystem(out.toString());
        } catch (RoomMapService.MapLookupException e) {
            output.appendSystem("Error: " + e.getMessage());
        } catch (Exception e) {
            log.warn("npc search failed err={}", e.toString());
            output.appendSystem("Error: Unable to search NPCs.");
        }
    }

    private void handleRoute(String body) {
        if (!mud.isConnected()) {
            output.appendSystem("Error: MUD is disconnected. Send `mm connect` first.");
            return;
        }
        if (lastRoomSearchResults.isEmpty()) {
            output.appendSystem("Error: No recent room search results.");
            return;
        }
        int selection;
        try {
            selection = Integer.parseInt(body);
        } catch (NumberFormatException e) {
            output.appendSystem("Usage: mm route <number>");
            return;
        }
        if (selection < 1 || selection > lastRoomSearchResults.size()) {
            output.appendSystem("Error: Route selection must be between 1 and " + lastRoomSearchResults.size() + ".");
            return;
        }
        RoomMapService.RoomSearchResult target = lastRoomSearchResults.get(selection - 1);
        String currentRoomId = mud.getCurrentRoomSnapshot().roomId();
        if (currentRoomId == null || currentRoomId.isBlank()) {
            output.appendSystem("Error: No room info available yet.");
            return;
        }
        if (currentRoomId.equals(target.roomId())) {
            output.appendSystem("Already in " + target.roomShort() + ".");
            return;
        }
        try {
            RoomMapService.RouteResult route = mapService.findRoute(currentRoomId, target.roomId(), useTeleports);
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
            output.appendSystem(out.toString());
        } catch (RoomMapService.MapLookupException e) {
            output.appendSystem("Error: " + e.getMessage());
        } catch (Exception e) {
            log.warn("route search failed err={}", e.toString());
            output.appendSystem("Error: Unable to calculate route.");
        }
    }

    private static String normalizeInput(String body) {
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
