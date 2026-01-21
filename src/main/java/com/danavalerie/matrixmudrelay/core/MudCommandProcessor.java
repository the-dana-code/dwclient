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
import com.danavalerie.matrixmudrelay.config.ConfigLoader;
import com.danavalerie.matrixmudrelay.config.DeliveryRouteMappings;
import com.danavalerie.matrixmudrelay.mud.CurrentRoomInfo;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.mud.TelnetDecoder;
import com.danavalerie.matrixmudrelay.util.GrammarUtils;
import com.danavalerie.matrixmudrelay.util.Sanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class MudCommandProcessor implements MudClient.MudGmcpListener, MudClient.MudConnectListener {
    private static final Logger log = LoggerFactory.getLogger(MudCommandProcessor.class);
    private static final int ROOM_SEARCH_LIMIT = 999;
    private static final Pattern UU_LIBRARY_RE_ENABLE_PATTERN = Pattern.compile("^Cannot find \"distortion\", no match\\.$");

    public interface ClientOutput {
        void appendSystem(String text);

        void appendCommandEcho(String text);

        void addToHistory(String command);

        void updateCurrentRoom(String roomId, String roomName);

        void updateMap(String roomId);

        void updateStats(StatsHudRenderer.StatsHudData data);

        void updateContextualResults(ContextualResultList results);

        void updateSpeedwalkPath(List<RoomMapService.RoomLocation> path);
        void updateConnectionState(boolean connected);
        void setUULibraryButtonsEnabled(boolean enabled);
    }

    private final BotConfig cfg;
    private final Path configPath;
    private final MudClient mud;
    private final RoomMapService mapService;
    private final WritTracker writTracker;
    private final StoreInventoryTracker storeInventoryTracker;
    private final TimerService timerService;
    private final java.util.function.Supplier<DeliveryRouteMappings> routeMappingsSupplier;
    private final ClientOutput output;

    private List<RoomMapService.RoomSearchResult> lastRoomSearchResults = List.of();
    private List<RoomMapService.ItemSearchResult> lastItemSearchResults = List.of();
    private boolean useTeleports = true;
    private volatile String lastRoomId = null;
    private volatile String lastRoomName = null;
    private boolean isRestoring = false;
    private String uuLibraryRestoredForChar = null;

    public MudCommandProcessor(BotConfig cfg,
                               Path configPath,
                               MudClient mud,
                               WritTracker writTracker,
                               StoreInventoryTracker storeInventoryTracker,
                               TimerService timerService,
                               java.util.function.Supplier<DeliveryRouteMappings> routeMappingsSupplier,
                               ClientOutput output) {
        this.cfg = cfg;
        this.configPath = configPath;
        this.mud = mud;
        this.writTracker = writTracker;
        this.storeInventoryTracker = storeInventoryTracker;
        this.timerService = timerService;
        this.routeMappingsSupplier = routeMappingsSupplier;
        this.output = output;
        this.mapService = new RoomMapService("database.db");

        UULibraryService.getInstance().addListener(this::saveUULibraryState);
    }

    private void saveUULibraryState() {
        UULibraryService service = UULibraryService.getInstance();
        if (isRestoring && service.isActive()) return;

        String charName = mud.getCurrentRoomSnapshot().characterName();
        if (charName == null || charName.isBlank()) return;

        BotConfig.CharacterConfig charCfg = cfg.characters.computeIfAbsent(charName, k -> new BotConfig.CharacterConfig());

        if (service.isActive()) {
            charCfg.uuLibrary = new BotConfig.UULibraryState(
                    service.getCurRow(),
                    service.getCurCol(),
                    service.getOrientation().name()
            );
        } else {
            charCfg.uuLibrary = null;
        }

        if (configPath != null) {
            try {
                ConfigLoader.save(configPath, cfg);
            } catch (Exception e) {
                log.warn("Failed to save config for UULibrary state: {}", e.getMessage());
            }
        }
    }

    public void shutdown() {
    }

    public void handleInput(String input) {
        if (input == null) {
            return;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            sendToMud("");
            return;
        }

        output.addToHistory(trimmed);

        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (lower.startsWith("//")) {
            sendToMud(trimmed.substring(1));
            return;
        }

        if (lower.startsWith("/")) {
            handleInternalCommand(trimmed.substring(1));
            return;
        }

        if (lower.equals("mm") || lower.startsWith("mm ")) {
            output.appendSystem("Note: Internal commands now use slashes (e.g., /room) instead of 'mm'. Type /help for help.");
            return;
        }

        if (tryAlias(trimmed)) {
            return;
        }

        if (UULibraryService.getInstance().isActive()) {
            UULibraryService.getInstance().processCommand(trimmed);
            output.updateMap("UULibrary");
        }

        sendToMud(trimmed);
    }

    public void onFullLineReceived(String line) {
        if (UU_LIBRARY_RE_ENABLE_PATTERN.matcher(line).matches()) {
            output.setUULibraryButtonsEnabled(true);
        }
    }

    private void sendToMud(String line) {
        sendToMud(List.of(line), false);
    }

    private void sendToMud(List<String> lines) {
        sendToMud(lines, false);
    }

    private void sendToMud(List<String> lines, boolean maskEcho) {
        for (String line : lines) {
            String sanitized = Sanitizer.sanitizeMudInput(line);
            output.appendCommandEcho(maskEcho ? "(password)" : sanitized);
            if (!maskEcho) {
                output.addToHistory(sanitized);
            }
        }
        try {
            mud.sendLinesFromController(lines);
        } catch (IllegalStateException e) {
            output.appendSystem("Error: " + e.getMessage());
        }
    }

    @Override
    public void onGmcp(TelnetDecoder.GmcpMessage message) {
        CurrentRoomInfo.Snapshot snapshot = mud.getCurrentRoomSnapshot();
        String roomId = snapshot.roomId();
        String roomName = snapshot.roomName();

        if (roomName == null && roomId != null && !roomId.isBlank()) {
            try {
                RoomMapService.RoomLocation loc = mapService.lookupRoomLocation(roomId);
                if (loc != null) {
                    roomName = loc.roomShort();
                }
            } catch (Exception ignored) {}
        }

        if (roomId != null && !roomId.isBlank()) {
            boolean wasActive = UULibraryService.getInstance().isActive();
            isRestoring = true;
            try {
                UULibraryService.getInstance().setRoomId(roomId);
                if (UULibraryService.getInstance().isActive()) {
                    if (!wasActive) {
                        uuLibraryRestoredForChar = null;
                    }
                    String charName = snapshot.characterName();
                    if (charName != null && !charName.equals(uuLibraryRestoredForChar)) {
                        BotConfig.CharacterConfig charCfg = cfg.characters.get(charName);
                        if (charCfg != null && charCfg.uuLibrary != null) {
                            try {
                                UULibraryService.getInstance().setState(
                                        charCfg.uuLibrary.row,
                                        charCfg.uuLibrary.col,
                                        UULibraryService.Orientation.valueOf(charCfg.uuLibrary.orientation)
                                );
                            } catch (Exception e) {
                                log.warn("Failed to restore UULibrary state: {}", e.getMessage());
                            }
                        }
                        uuLibraryRestoredForChar = charName;
                    }
                }
            } finally {
                isRestoring = false;
            }
            if (!roomId.equals(lastRoomId) || !Objects.equals(roomName, lastRoomName)) {
                boolean roomChanged = !roomId.equals(lastRoomId);
                lastRoomId = roomId;
                lastRoomName = roomName;
                output.updateCurrentRoom(roomId, roomName);
                if (roomChanged) {
                    output.updateMap(roomId);
                    storeInventoryTracker.clearInventory();
                }
            }
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
        mud.connectAsync();
    }

    @Override
    public void onConnected() {
        output.appendSystem("Connected.");
        output.updateConnectionState(true);
    }

    @Override
    public void onConnectFailed(String message) {
        output.appendSystem("Connect failed: " + message);
    }

    private void handleDisconnect() {
        if (!mud.isConnected()) {
            output.appendSystem("Already disconnected.");
            return;
        }
        mud.disconnect("controller requested", null);
        output.appendSystem("Disconnected.");
        output.updateConnectionState(false);
    }

    private void handleStatus() {
        output.appendSystem("Status: " + (mud.isConnected() ? "CONNECTED" : "DISCONNECTED"));
    }

    private void handleInfo() {
        output.appendSystem(mud.getCurrentRoomSnapshot().formatForDisplay());
    }

    private void handleMap(String query) {
        String targetRoomId;
        if (query.isBlank()) {
            targetRoomId = mud.getCurrentRoomSnapshot().roomId();
            if (targetRoomId == null || targetRoomId.isBlank()) {
                output.appendSystem("Error: Can't determine your location.");
                return;
            }
        } else {
            if (lastRoomSearchResults.isEmpty()) {
                output.appendSystem("Error: No recent room search results. Use /room first.");
                return;
            }
            int selection;
            try {
                selection = Integer.parseInt(query);
            } catch (NumberFormatException e) {
                output.appendSystem("Usage: /map <number>");
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

    private void handleInternalCommand(String input) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            output.appendSystem("Unknown command. Type /help for help.");
            return;
        }
        String[] parts = trimmed.split("\\s+", 2);
        String subcommand = parts[0].toLowerCase(Locale.ROOT);
        String query = parts.length > 1 ? parts[1].trim() : "";
        if ("loc".equals(subcommand)) {
            handleCurrentLocation();
            return;
        }
        if ("help".equals(subcommand)) {
            handleHelp();
            return;
        }
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
            handleMap(query);
            return;
        }
        if ("npc".equals(subcommand)) {
            handleNpcSearchQuery(query);
            return;
        }
        if ("password".equals(subcommand) || "pw".equals(subcommand)) {
            tryAlias("#password");
            return;
        }
        if ("item".equals(subcommand)) {
            if ("exact".equalsIgnoreCase(query) || query.toLowerCase(Locale.ROOT).startsWith("exact ")) {
                String exactQuery = query.length() > 5 ? query.substring(5).trim() : "";
                handleItemSearchQueryExact(exactQuery);
                return;
            }
            try {
                int number = Integer.parseInt(query);
                handleItemSelection(number);
            } catch (NumberFormatException e) {
                handleItemSearchQuery(query);
            }
            return;
        }
        if ("room".equals(subcommand)) {
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
        if ("timers".equals(subcommand)) {
            handleTimers();
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
        if ("reset".equals(subcommand)) {
            handleReset();
            return;
        }
        try {
            if (tryAlias("#" + trimmed)) {
                return;
            }
        } catch (IllegalStateException e) {
            output.appendSystem("Error: " + e.getMessage());
            return;
        }
        output.appendSystem("Unknown command. Type /help for help.");
    }

    private void handleHelp() {
        //noinspection StringBufferReplaceableByString
        StringBuilder sb = new StringBuilder("Available slash commands:\n");
        sb.append("  /loc            - Show current location and room ID\n");
        sb.append("  /help           - Show this help message\n");
        sb.append("  /connect        - Connect to the MUD\n");
        sb.append("  /disconnect     - Disconnect from the MUD\n");
        sb.append("  /status         - Show connection status\n");
        sb.append("  /info           - Show detailed room and character info (GMCP)\n");
        sb.append("  /room <query>   - Search for rooms by name\n");
        sb.append("  /npc <query>    - Search for NPCs by name\n");
        sb.append("  /item <query>   - Search for items in shops\n");
        sb.append("  /map [number]   - Show map for current room or search result\n");
        sb.append("  /route <number> - Calculate speedwalk to a search result\n");
        sb.append("  /timers         - Show active timers for current character\n");
        sb.append("  /writ [number]  - Show tracked writ requirements or specific writ details\n");
        sb.append("  /pw             - Send the configured password\n");
        sb.append("  /tp             - Enable/disable teleport-assisted routing\n");
        sb.append("  /notp           - Disable teleport-assisted routing\n");
        sb.append("  /reset          - Reset MUD terminal options\n");
        sb.append("\nStarting a line with // will send a single / to the MUD.");
        output.appendSystem(sb.toString());
    }

    private void handleTimers() {
        String charName = mud.getCurrentRoomSnapshot().characterName();
        if (charName == null || charName.isBlank()) {
            output.appendSystem("Error: No character logged in. Can't show timers.");
            return;
        }

        Map<String, BotConfig.TimerData> timers = timerService.getTimers(charName);
        if (timers.isEmpty()) {
            output.appendSystem("No active timers for " + charName + ".");
            return;
        }

        StringBuilder out = new StringBuilder("Timers for ").append(charName).append(":\n");
        long now = System.currentTimeMillis();

        // Find max timer name length for padding
        int maxNameLen = timers.keySet().stream().mapToInt(String::length).max().orElse(0);

        for (Map.Entry<String, BotConfig.TimerData> entry : timers.entrySet()) {
            String name = entry.getKey();
            BotConfig.TimerData data = entry.getValue();
            long remaining = data.expirationTime - now;

            out.append(String.format("%-" + (maxNameLen + 2) + "s", name + ":"))
                    .append(timerService.formatRemainingTime(remaining))
                    .append("\n");
        }
        output.appendSystem(out.toString().trim());
    }

    private void handleCurrentLocation() {
        String roomId = mud.getCurrentRoomSnapshot().roomId();
        if (roomId == null || roomId.isBlank()) {
            output.appendSystem("Error: Can't determine your location.");
            return;
        }
        try {
            RoomMapService.RoomLocation location = mapService.lookupRoomLocation(roomId);
            StringBuilder out = new StringBuilder("Current location: ");
            if (location.roomShort() != null && !location.roomShort().isBlank()) {
                out.append(location.roomShort()).append(" - ");
            }
            out.append(mapService.getMapDisplayName(location.mapId()))
                    .append(" ")
                    .append(location.roomId());
            output.appendSystem(out.toString());
        } catch (RoomMapService.MapLookupException e) {
            output.appendSystem("Error: " + e.getMessage());
        } catch (Exception e) {
            log.warn("current room lookup failed err={}", e.toString());
            output.appendSystem("Error: Unable to lookup current room.");
        }
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
                        .append(req.locationDisplay());
            }
            output.appendSystem(out.toString());
            return;
        }
        String[] parts = query.split("\\s+");
        if (parts.length != 2) {
            output.appendSystem("Usage: /writ <number> [item|npc|room|deliver]");
            return;
        }
        int selection;
        try {
            selection = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            output.appendSystem("Usage: /writ <number> [item|npc|room|deliver]");
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
            case "room" -> handleRoomSearchQuery(req.locationName());
            case "deliver" -> {
                String npcName = routeMappingsSupplier.get().findRoutePlan(req.npc(), req.locationDisplay())
                        .map(DeliveryRouteMappings.RoutePlan::npcOverride)
                        .filter(override -> override != null && !override.isBlank())
                        .orElseGet(() -> removeTitle(req));
                String command = Sanitizer.sanitizeMudInput(
                        "deliver "
                                + req.item()
                                // "bright and colourful kimono" -> "bright colourful kimono" -- the 'and' messes up the game's parser
                                .replaceAll(" and ", " ")
                                + " to "
                                + npcName
                );
                sendToMud(command);
            }
            default -> output.appendSystem("Usage: /writ <number> [item|npc|room|deliver]");
        }
    }

    private static String removeTitle(WritTracker.WritRequirement req) {
        String npcName = req.npc();
        npcName = removeLeading(npcName, "Mr or Ms ");
        npcName = removeLeading(npcName, "Mr ");
        npcName = removeLeading(npcName, "Mr. ");
        npcName = removeLeading(npcName, "Ms ");
        npcName = removeLeading(npcName, "Ms. ");
        npcName = removeLeading(npcName, "Mrs ");
        npcName = removeLeading(npcName, "Mrs. ");
        return npcName;
    }

    private static String removeLeading(String npcName, String leadingString) {
        if (npcName.startsWith(leadingString)) {
            npcName = npcName.substring(leadingString.length());
        }
        return npcName;
    }

    private void handleReset() {
        List<String> lines = List.of(
                "alias LesaClientReset options terminal encoding = UTF-8; options output prompt =",
                "LesaClientReset"
        );
        sendToMud(lines);
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
        boolean maskEcho = "#password".equalsIgnoreCase(trigger);
        sendToMud(lines, maskEcho);
        return true;
    }

    private void handleRoomSearchQuery(String query) {
        if (query.isBlank()) {
            output.appendSystem("Usage: /room <room name fragment>");
            return;
        }
        try {
            List<RoomMapService.RoomSearchResult> results = mapService.searchRoomsByName(query, ROOM_SEARCH_LIMIT + 1);
            boolean truncated = results.size() > ROOM_SEARCH_LIMIT;
            if (truncated) {
                results = results.subList(0, ROOM_SEARCH_LIMIT);
            }
            lastRoomSearchResults = List.copyOf(results);
            updateContextualResults(buildRoomResultsList(query, results, truncated));
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
        handleItemSearchQuery(query, false);
    }

    private void handleItemSearchQueryExact(String query) {
        handleItemSearchQuery(query, true);
    }

    private void handleItemSearchQuery(String query, boolean exact) {
        if (query.isBlank()) {
            output.appendSystem(exact
                    ? "Usage: /item exact <item name>"
                    : "Usage: /item <item name fragment>");
            return;
        }
        lastRoomSearchResults = List.of();
        try {
            ItemSearchResponse response = exact
                    ? searchItemsExactWithFallback(query)
                    : searchItemsWithFallback(query);
            List<RoomMapService.ItemSearchResult> results = response.results();
            boolean truncated = results.size() > ROOM_SEARCH_LIMIT;
            if (truncated) {
                results = results.subList(0, ROOM_SEARCH_LIMIT);
            }
            lastItemSearchResults = List.copyOf(results);
            updateContextualResults(buildItemResultsList(query, response.termUsed(), results, truncated, exact));
            if (results.isEmpty()) {
                output.appendSystem("No items found matching \"" + query + "\".");
                return;
            }
            StringBuilder out = new StringBuilder();
            out.append(exact ? "Item exact search for \"" : "Item search for \"")
                    .append(response.termUsed()).append("\":");
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
            out.append("\nUse '/item <number>' to view room locations.");
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

    private ItemSearchResponse searchItemsExactWithFallback(String query) throws Exception {
        List<String> terms = buildItemSearchTerms(query);
        for (String term : terms) {
            List<RoomMapService.ItemSearchResult> results = mapService.searchItemsByExactName(term, ROOM_SEARCH_LIMIT + 1);
            if (!results.isEmpty()) {
                return new ItemSearchResponse(term, results);
            }
        }
        return new ItemSearchResponse(query.trim(), List.of());
    }

    private static List<String> buildItemSearchTerms(String query) {
        String trimmed = query.trim();
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (!trimmed.isBlank()) {
            terms.add(trimmed);
            for (String singular : GrammarUtils.singularizePhrase(trimmed)) {
                terms.add(singular);
            }
        }
        return List.copyOf(terms);
    }

    private record ItemSearchResponse(String termUsed, List<RoomMapService.ItemSearchResult> results) {
    }

    private void handleItemSelection(int selection) {
        if (lastItemSearchResults.isEmpty()) {
            output.appendSystem("Error: No recent item search results. Use /item first.");
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
            updateContextualResults(buildItemLocationResultsList(itemName, results, truncated));
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
            output.appendSystem("Usage: /npc <npc name fragment>");
            return;
        }
        if (query.startsWith("the ")) {
            query = query.substring(4);
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
            updateContextualResults(buildNpcResultsList(query, results, truncated));
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

    private void handleRoute(String query) {
        if (!mud.isConnected()) {
            output.appendSystem("Error: MUD is disconnected. Send `/connect` first.");
            return;
        }
        if (lastRoomSearchResults.isEmpty()) {
            output.appendSystem("Error: No recent room search results.");
            return;
        }
        int selection;
        try {
            selection = Integer.parseInt(query);
        } catch (NumberFormatException e) {
            output.appendSystem("Usage: /route <number>");
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
        String characterName = mud.getCurrentRoomSnapshot().characterName();
        if (currentRoomId.equals(target.roomId())) {
            output.appendSystem("Already in " + target.roomShort() + ".");
            return;
        }
        try {
            RoomMapService.RouteResult route = calculateSpeedwalkRoute(currentRoomId, target.roomId(), characterName);
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
            } else {
                out.append("\n").append(String.join(" -> ", exits));
                out.append("\nSteps: ").append(exits.size());
                String aliasName = "LesaClientSpeedwalk";
                String aliasCommand = "alias " + aliasName + " " + String.join(";", exits);
                sendToMud(List.of(aliasCommand, aliasName));
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

    public void speedwalkTo(String roomId) {
        try {
            performSpeedwalk(roomId);
        } catch (Exception e) {
            log.warn("speedwalk failed", e);
            output.appendSystem("Error: Speedwalk failed: " + e.getMessage());
        }
    }

    public void speedwalkToThenCommand(String roomId, String command) {
        speedwalkToThenCommands(roomId, List.of(command));
    }

    public void speedwalkToThenCommands(String roomId, List<String> commands) {
        try {
            performSpeedwalk(roomId);
        } catch (Exception e) {
            log.warn("speedwalk failed", e);
            output.appendSystem("Error: Speedwalk failed: " + e.getMessage());
        } finally {
            runPostSpeedwalkCommands(commands);
        }
    }

    private void runPostSpeedwalkCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        String characterName = mud.getCurrentRoomSnapshot().characterName();
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String resolved = command;
            if (characterName != null && !characterName.isBlank()) {
                resolved = resolved.replace("{character}", characterName);
            }
            handleInput(resolved);
        }
    }

    private void performSpeedwalk(String targetRoomId) throws Exception {
        if (!mud.isConnected()) {
            output.appendSystem("Error: MUD is disconnected. Send `/connect` first.");
            return;
        }
        String currentRoomId = mud.getCurrentRoomSnapshot().roomId();
        if (currentRoomId == null || currentRoomId.isBlank()) {
            output.appendSystem("Error: No room info available yet.");
            return;
        }

        if (targetRoomId == null || targetRoomId.isBlank()) {
            output.appendSystem("Error: Target room ID is missing.");
            return;
        }

        String characterName = mud.getCurrentRoomSnapshot().characterName();
        RoomMapService.RouteResult route = calculateSpeedwalkRoute(currentRoomId, targetRoomId, characterName);

        List<String> exits = route.steps().stream()
                .map(RoomMapService.RouteStep::exit)
                .toList();

        if (exits.isEmpty()) {
            output.appendSystem("Already there.");
        } else {
            String aliasName = "LesaClientSpeedwalk";
            String aliasCommand = "alias " + aliasName + " " + String.join(";", exits);
            sendToMud(List.of(aliasCommand, aliasName));
            output.appendSystem("Speedwalking to room " + targetRoomId + " (" + exits.size() + " steps)");
        }
    }

    private RoomMapService.RouteResult calculateSpeedwalkRoute(String currentRoomId,
                                                               String targetRoomId,
                                                               String characterName)
            throws RoomMapService.MapLookupException, java.sql.SQLException {
        RoomMapService.RouteResult route = mapService.findRoute(
                currentRoomId,
                targetRoomId,
                useTeleports,
                characterName
        );
        updateSpeedwalkPath(currentRoomId, route);
        return route;
    }

    private void updateSpeedwalkPath(String currentRoomId, RoomMapService.RouteResult route) {
        if (route == null) {
            output.updateSpeedwalkPath(List.of());
            return;
        }
        List<String> roomIds = new ArrayList<>();
        if (currentRoomId != null && !currentRoomId.isBlank()) {
            roomIds.add(currentRoomId);
        }
        for (RoomMapService.RouteStep step : route.steps()) {
            if (step.roomId() != null && !step.roomId().isBlank()) {
                if (step.exit() != null && step.exit().startsWith("tp ")) {
                    roomIds.add(null);
                }
                roomIds.add(step.roomId());
            }
        }
        if (roomIds.isEmpty()) {
            output.updateSpeedwalkPath(List.of());
            return;
        }
        try {
            List<RoomMapService.RoomLocation> locations = mapService.lookupRoomLocations(roomIds);
            output.updateSpeedwalkPath(locations);
        } catch (RoomMapService.MapLookupException | java.sql.SQLException e) {
            log.warn("speedwalk path lookup failed err={}", e.toString());
            output.updateSpeedwalkPath(List.of());
        }
    }

    private void updateContextualResults(ContextualResultList results) {
        output.updateContextualResults(results);
    }

    private ContextualResultList buildRoomResultsList(String query,
                                                      List<RoomMapService.RoomSearchResult> results,
                                                      boolean truncated) {
        List<ContextualResultList.ContextualResult> list = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            RoomMapService.RoomSearchResult result = results.get(i);
            String label = (result.sourceInfo() != null ? "[" + result.sourceInfo() + "] " : "")
                    + mapService.getMapDisplayName(result.mapId())
                    + ": "
                    + result.roomShort();
            list.add(new ContextualResultList.ContextualResult(
                    label,
                    "/route " + (i + 1),
                    "/map " + (i + 1)));
        }
        String title = "Room search for \"" + query + "\"";
        String empty = "No rooms found matching \"" + query + "\".";
        String footer = truncated ? "Showing first " + ROOM_SEARCH_LIMIT + " matches. Refine your search." : null;
        return new ContextualResultList(title, list, empty, footer);
    }

    private ContextualResultList buildItemResultsList(String rawQuery,
                                                      String termUsed,
                                                      List<RoomMapService.ItemSearchResult> results,
                                                      boolean truncated,
                                                      boolean exact) {
        List<ContextualResultList.ContextualResult> list = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            RoomMapService.ItemSearchResult result = results.get(i);
            list.add(new ContextualResultList.ContextualResult(
                    result.itemName(),
                    "/item " + (i + 1),
                    null));
        }
        String title = (exact ? "Item exact search for \"" : "Item search for \"") + termUsed + "\"";
        String empty = "No items found matching \"" + rawQuery + "\".";
        List<String> notes = new ArrayList<>();
        if (rawQuery != null && !rawQuery.isBlank()
                && termUsed != null
                && !termUsed.equalsIgnoreCase(rawQuery.trim())) {
            notes.add("Showing results for \"" + termUsed + "\" (from \"" + rawQuery.trim() + "\").");
        }
        notes.add("Select an item to view locations.");
        if (truncated) {
            notes.add("Showing first " + ROOM_SEARCH_LIMIT + " matches. Refine your search.");
        }
        String footer = notes.stream().filter(note -> note != null && !note.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse(null);
        return new ContextualResultList(title, list, empty, footer);
    }

    private ContextualResultList buildItemLocationResultsList(String itemName,
                                                              List<RoomMapService.RoomSearchResult> results,
                                                              boolean truncated) {
        List<ContextualResultList.ContextualResult> list = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            RoomMapService.RoomSearchResult result = results.get(i);
            String label = (result.sourceInfo() != null ? "[" + result.sourceInfo() + "] " : "")
                    + mapService.getMapDisplayName(result.mapId())
                    + ": "
                    + result.roomShort();
            list.add(new ContextualResultList.ContextualResult(
                    label,
                    "/route " + (i + 1),
                    "/map " + (i + 1)));
        }
        String title = "Item locations for \"" + itemName + "\"";
        String empty = "No rooms found for item \"" + itemName + "\".";
        String footer = truncated ? "Showing first " + ROOM_SEARCH_LIMIT + " matches. Refine your search." : null;
        return new ContextualResultList(title, list, empty, footer);
    }

    private ContextualResultList buildNpcResultsList(String query,
                                                     List<RoomMapService.NpcSearchResult> results,
                                                     boolean truncated) {
        List<ContextualResultList.ContextualResult> list = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            RoomMapService.NpcSearchResult result = results.get(i);
            String label = mapService.getMapDisplayName(result.mapId())
                    + ": "
                    + result.npcName()
                    + " - "
                    + result.roomShort();
            list.add(new ContextualResultList.ContextualResult(
                    label,
                    "/route " + (i + 1),
                    "/map " + (i + 1)));
        }
        String title = "NPC search for \"" + query + "\"";
        String empty = "No NPCs found matching \"" + query + "\".";
        String footer = truncated ? "Showing first " + ROOM_SEARCH_LIMIT + " matches. Refine your search." : null;
        return new ContextualResultList(title, list, empty, footer);
    }

}

