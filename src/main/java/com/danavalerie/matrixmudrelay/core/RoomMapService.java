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

import com.danavalerie.matrixmudrelay.core.data.ItemData;
import com.danavalerie.matrixmudrelay.core.data.NpcData;
import com.danavalerie.matrixmudrelay.core.data.RoomData;
import com.danavalerie.matrixmudrelay.core.data.ShopItem;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

public class RoomMapService {
    private static final int IMAGE_SCALE = 2;
    private static final int ROOM_PIXEL_SIZE = 5 * IMAGE_SCALE;
    private static final int ROOM_PIXEL_OFFSET_X = IMAGE_SCALE;
    private static final int ROOM_PIXEL_OFFSET_Y = IMAGE_SCALE;
    private static final int IMAGE_SPAN = 250;
    private static final int IMAGE_HALF_SPAN = IMAGE_SPAN / 2;
    private final MapDataService dataService;
    private final Map<String, Optional<BufferedImage>> backgroundCache = new HashMap<>();
    private final Map<String, MapImage> mapByIdCache = new HashMap<>();
    private BaseImageCache baseImageCache;

    public RoomMapService(MapDataService dataService) {
        this.dataService = dataService;
    }

    private RoomRecord toRecord(RoomData data) {
        if (data == null) return null;
        return new RoomRecord(data.getRoomId(), data.getMapId(), data.getXpos(), data.getYpos(), data.getRoomShort(), data.getRoomType());
    }

    private Map<String, RoomRecord> loadRoomsInArea(int mapId, int minX, int maxX, int minY, int maxY) {
        return dataService.getRooms().values().stream()
                .filter(r -> r.getMapId() == mapId && r.getXpos() >= minX && r.getXpos() <= maxX && r.getYpos() >= minY && r.getYpos() <= maxY)
                .collect(Collectors.toMap(RoomData::getRoomId, this::toRecord));
    }

    public MapImage renderMapImage(String currentRoomId) throws MapLookupException, IOException {
        return renderMapImage(currentRoomId, false);
    }

    public MapImage renderMapImage(String currentRoomId, boolean isDark) throws MapLookupException, IOException {
        if (currentRoomId != null && "UULibrary".equalsIgnoreCase(currentRoomId.trim())) {
            return renderMapByMapId(47, isDark);
        }
        if (currentRoomId == null || currentRoomId.isBlank()) {
            throw new MapLookupException("No room info available yet.");
        }

        RoomRecord current = loadRoom(currentRoomId);
        if (current == null) {
            throw new MapLookupException("Current room not found in map database.");
        }
        if (baseImageCache != null && (baseImageCache.mapId != current.mapId || baseImageCache.isDark != isDark)) {
            baseImageCache = null;
        }

        BufferedImage backgroundImage = loadMapBackground(current.mapId, isDark);
        int minX;
        int maxX;
        int minY;
        int maxY;
        int imageWidth;
        int imageHeight;
        if (backgroundImage == null) {
            BaseImageCache cached = baseImageCache;
            if (cached != null && cached.mapId == current.mapId && cached.isDark == isDark && cached.containsRoom(current)) {
                minX = cached.minX;
                maxX = cached.maxX;
                minY = cached.minY;
                maxY = cached.maxY;
                imageWidth = cached.imageWidth;
                imageHeight = cached.imageHeight;
            } else {
                minX = current.xpos - IMAGE_HALF_SPAN;
                maxX = minX + IMAGE_SPAN - 1;
                minY = current.ypos - IMAGE_HALF_SPAN;
                maxY = minY + IMAGE_SPAN - 1;
                imageWidth = IMAGE_SPAN * IMAGE_SCALE;
                imageHeight = IMAGE_SPAN * IMAGE_SCALE;
            }
        } else {
            minX = 0;
            minY = 0;
            maxX = backgroundImage.getWidth() - 1;
            maxY = backgroundImage.getHeight() - 1;
            imageWidth = backgroundImage.getWidth() * IMAGE_SCALE;
            imageHeight = backgroundImage.getHeight() * IMAGE_SCALE;
        }

        BaseImageCache cachedBase = baseImageCache;
        boolean reuseBase = cachedBase != null
                && cachedBase.matches(current.mapId, minX, maxX, minY, maxY, imageWidth, imageHeight, isDark);
        byte[] data = null;
        BufferedImage baseImage = null;
        if (!reuseBase) {
            BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            g2.setColor(isDark ? new Color(12, 12, 18) : new Color(240, 240, 245));
            g2.fillRect(0, 0, imageWidth, imageHeight);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.setStroke(new BasicStroke(IMAGE_SCALE));

            if (backgroundImage != null) {
                drawMapBackground(backgroundImage, g2, imageWidth, imageHeight, isStaticBackground(current.mapId));
            }

            g2.dispose();
            baseImageCache = new BaseImageCache(current.mapId, minX, maxX, minY, maxY, imageWidth, imageHeight,
                    image, isDark);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            data = out.toByteArray();
            baseImage = image;
        } else if (cachedBase != null) {
            baseImage = cachedBase.image;
        }

        int currentX = (current.xpos - minX) * IMAGE_SCALE + ROOM_PIXEL_OFFSET_X;
        int currentY = (current.ypos - minY) * IMAGE_SCALE + ROOM_PIXEL_OFFSET_Y;

        String mapName = getMapDisplayName(current.mapId);
        return new MapImage(
                data,
                imageWidth,
                imageHeight,
                "image/png",
                mapName,
                currentX,
                currentY,
                reuseBase,
                current.mapId,
                minX,
                minY,
                IMAGE_SCALE,
                ROOM_PIXEL_OFFSET_X,
                ROOM_PIXEL_OFFSET_Y,
                current.roomId,
                current.xpos,
                current.ypos,
                current.roomShort,
                isDark,
                isStaticBackground(current.mapId),
                baseImage
        );
    }

    public MapImage renderMapByMapId(int mapId, boolean isDark) throws MapLookupException, IOException {
        String cacheKey = mapId + (isDark ? "_dark" : "_light");
        MapImage cachedImage = mapByIdCache.get(cacheKey);
        if (cachedImage != null) {
            BufferedImage cachedBase = cachedImage.baseImage();
            if (cachedBase != null) {
                int scaledWidth = cachedImage.width() / cachedImage.imageScale();
                int scaledHeight = cachedImage.height() / cachedImage.imageScale();
                baseImageCache = new BaseImageCache(mapId, cachedImage.minX(), cachedImage.minX() + scaledWidth - 1,
                        cachedImage.minY(), cachedImage.minY() + scaledHeight - 1,
                        cachedImage.width(), cachedImage.height(), cachedBase, isDark);
            }
            return cachedImage;
        }
        BufferedImage backgroundImage = loadMapBackground(mapId, isDark);
        if (backgroundImage == null) {
            throw new MapLookupException("No background image for map " + mapId);
        }

        int minX = 0;
        int minY = 0;
        int maxX = backgroundImage.getWidth() - 1;
        int maxY = backgroundImage.getHeight() - 1;
        int imageWidth = backgroundImage.getWidth() * IMAGE_SCALE;
        int imageHeight = backgroundImage.getHeight() * IMAGE_SCALE;

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(isDark ? new Color(12, 12, 18) : new Color(240, 240, 245));
        g2.fillRect(0, 0, imageWidth, imageHeight);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setStroke(new BasicStroke(IMAGE_SCALE));

        drawMapBackground(backgroundImage, g2, imageWidth, imageHeight, isStaticBackground(mapId));

        if (mapId == 47) {
            UULibraryService lib = UULibraryService.getInstance();
            Map<String, Set<UULibraryService.Orientation>> barriers = lib.getBarriers();
            if (!barriers.isEmpty()) {
                g2.setColor(Color.RED);
                g2.setStroke(new BasicStroke(3 * IMAGE_SCALE));
                for (Map.Entry<String, Set<UULibraryService.Orientation>> entry : barriers.entrySet()) {
                    String[] parts = entry.getKey().split(",");
                    int r = Integer.parseInt(parts[0]);
                    int c = Integer.parseInt(parts[1]);
                    
                    // Center of room in background coords:
                    // x = (c - 1) * 30 + 45
                    // y = 4810 - (r - 1) * 30
                    int cx = (c - 1) * 30 + 45;
                    int cy = 4810 - (r - 1) * 30;
                    
                    int half = 15; // Room size is 30x30
                    int inset = 4; // Draw barrier 4 pixels inside the room
                    
                    for (UULibraryService.Orientation dir : entry.getValue()) {
                        int x1, y1, x2, y2;
                        switch (dir) {
                            case NORTH:
                                x1 = cx - half + inset; y1 = cy - half + inset;
                                x2 = cx + half - inset; y2 = cy - half + inset;
                                break;
                            case SOUTH:
                                x1 = cx - half + inset; y1 = cy + half - inset;
                                x2 = cx + half - inset; y2 = cy + half - inset;
                                break;
                            case EAST:
                                x1 = cx + half - inset; y1 = cy - half + inset;
                                x2 = cx + half - inset; y2 = cy + half - inset;
                                break;
                            case WEST:
                                x1 = cx - half + inset; y1 = cy - half + inset;
                                x2 = cx - half + inset; y2 = cy + half - inset;
                                break;
                            default:
                                continue;
                        }
                        g2.drawLine(x1 * IMAGE_SCALE, y1 * IMAGE_SCALE, x2 * IMAGE_SCALE, y2 * IMAGE_SCALE);
                    }
                }
            }
        }

        g2.dispose();

        baseImageCache = new BaseImageCache(mapId, minX, maxX, minY, maxY, imageWidth, imageHeight,
                image, isDark);

        String mapName = getMapDisplayName(mapId);
        MapImage mapImage = new MapImage(
                null,
                imageWidth,
                imageHeight,
                "image/png",
                mapName,
                -1, -1, // No current room marker
                false,
                mapId,
                minX,
                minY,
                IMAGE_SCALE,
                ROOM_PIXEL_OFFSET_X,
                ROOM_PIXEL_OFFSET_Y,
                null,
                -1, -1,
                null,
                isDark,
                isStaticBackground(mapId),
                image
        );
        mapByIdCache.put(cacheKey, mapImage);
        return mapImage;
    }

    public List<MapArea> listMapAreas() {
        List<MapArea> areas = new ArrayList<>();
        for (MapBackground background : MapBackground.values()) {
            areas.add(new MapArea(background.mapId, background.formatName()));
        }
        areas.sort(Comparator.comparing(MapArea::displayName));
        return areas;
    }

    public String findRepresentativeRoomId(int mapId) {
        return dataService.getRooms().values().stream()
                .filter(r -> r.getMapId() == mapId)
                .map(RoomData::getRoomId)
                .sorted()
                .findFirst()
                .orElse(null);
    }

    public List<RoomSearchResult> searchRoomsByName(String term, int limit) throws MapLookupException {
        if (term == null || term.isBlank()) {
            throw new MapLookupException("Search term cannot be blank.");
        }
        String trimmed = term.trim().toLowerCase();
        String normalized = stripLeadingRoomArticle(trimmed);
        if (normalized.isBlank()) {
            throw new MapLookupException("Search term cannot be blank.");
        }
        return dataService.getRooms().values().stream()
                .filter(r -> r.getRoomShort().toLowerCase().contains(normalized))
                .map(r -> new RoomSearchResult(r.getRoomId(), r.getMapId(), r.getXpos(), r.getYpos(), r.getRoomShort(), r.getRoomType(), null))
                .sorted(Comparator.comparing(RoomSearchResult::roomShort).thenComparing(RoomSearchResult::mapId).thenComparing(RoomSearchResult::roomId))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static String stripLeadingRoomArticle(String term) {
        if (term.startsWith("a ")) {
            return term.substring(2).trim();
        }
        if (term.startsWith("the ")) {
            return term.substring(4).trim();
        }
        return term;
    }

    public List<NpcSearchResult> searchNpcsByName(String term, int limit) throws MapLookupException {
        if (term == null || term.isBlank()) {
            throw new MapLookupException("Search term cannot be blank.");
        }
        String trimmed = term.trim().toLowerCase();
        return dataService.getNpcs().values().stream()
                .filter(n -> n.getNpcName().toLowerCase().contains(trimmed))
                .map(n -> {
                    RoomData r = dataService.getRoom(n.getRoomId());
                    if (r == null) return null;
                    return new NpcSearchResult(n.getNpcId(), n.getNpcName(), r.getRoomId(), r.getMapId(), r.getXpos(), r.getYpos(), r.getRoomShort(), r.getRoomType());
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(NpcSearchResult::npcName).thenComparing(NpcSearchResult::mapId).thenComparing(NpcSearchResult::roomId))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<ItemSearchResult> searchItemsByName(String term, int limit) throws MapLookupException {
        if (term == null || term.isBlank()) {
            throw new MapLookupException("Search term cannot be blank.");
        }
        String trimmed = term.trim().toLowerCase();
        return dataService.getItems().values().stream()
                .filter(i -> i.getItemName().toLowerCase().contains(trimmed))
                .map(i -> new ItemSearchResult(i.getItemName()))
                .sorted(Comparator.comparing(ItemSearchResult::itemName))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<ItemSearchResult> searchItemsByExactName(String term, int limit) throws MapLookupException {
        if (term == null || term.isBlank()) {
            throw new MapLookupException("Search term cannot be blank.");
        }
        String trimmed = term.trim().toLowerCase();
        return dataService.getItems().values().stream()
                .filter(i -> i.getItemName().equalsIgnoreCase(trimmed))
                .map(i -> new ItemSearchResult(i.getItemName()))
                .sorted(Comparator.comparing(ItemSearchResult::itemName))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<RoomSearchResult> searchRoomsByItemName(String itemName, int limit) throws MapLookupException {
        if (itemName == null || itemName.isBlank()) {
            throw new MapLookupException("Item name cannot be blank.");
        }
        String trimmed = itemName.trim().toLowerCase();
        List<RoomSearchResult> results = new ArrayList<>();

        // Search in shops
        dataService.getRooms().values().stream()
                .filter(r -> r.getShopItems().stream().anyMatch(si -> si.getName().equalsIgnoreCase(trimmed)))
                .forEach(r -> results.add(new RoomSearchResult(r.getRoomId(), r.getMapId(), r.getXpos(), r.getYpos(), r.getRoomShort(), r.getRoomType(), "Shop")));

        // Search in NPCs
        dataService.getNpcs().values().stream()
                .filter(n -> n.getItems().keySet().stream().anyMatch(i -> i.equalsIgnoreCase(trimmed)))
                .forEach(n -> {
                    RoomData r = dataService.getRoom(n.getRoomId());
                    if (r != null) {
                        results.add(new RoomSearchResult(r.getRoomId(), r.getMapId(), r.getXpos(), r.getYpos(), r.getRoomShort(), r.getRoomType(), "NPC: " + n.getNpcName()));
                    }
                });

        return results.stream()
                .sorted((a, b) -> {
                    int rankA = getSourceRank(a.sourceInfo());
                    int rankB = getSourceRank(b.sourceInfo());
                    if (rankA != rankB) return rankA - rankB;
                    int c = Integer.compare(a.mapId(), b.mapId());
                    if (c != 0) return c;
                    c = a.roomShort().compareTo(b.roomShort());
                    if (c != 0) return c;
                    return a.roomId().compareTo(b.roomId());
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Optional<ShopItem> findShopItem(String roomId, String itemName) {
        if (roomId == null || itemName == null) {
            return Optional.empty();
        }
        RoomData room = dataService.getRoom(roomId);
        if (room == null || room.getShopItems() == null) {
            return Optional.empty();
        }
        String trimmed = itemName.trim().toLowerCase();
        return room.getShopItems().stream()
                .filter(si -> si.getName().equalsIgnoreCase(trimmed))
                .findFirst();
    }

    public List<ShopItem> findShopItemsGlobally(String itemName) {
        if (itemName == null) return List.of();
        String trimmed = itemName.trim().toLowerCase();
        return dataService.getRooms().values().stream()
                .filter(r -> r.getShopItems() != null)
                .flatMap(r -> r.getShopItems().stream())
                .filter(si -> si.getName().equalsIgnoreCase(trimmed))
                .collect(Collectors.toList());
    }

    private int getSourceRank(String sourceInfo) {
        if (sourceInfo == null) return 2;
        if (sourceInfo.equals("Shop")) return 0;
        if (sourceInfo.startsWith("NPC:")) return 1;
        return 2;
    }

    public RouteResult findRoute(String startRoomId, String targetRoomId) throws MapLookupException {
        return findRoute(startRoomId, targetRoomId, true, null);
    }

    public RouteResult findRoute(String startRoomId, String targetRoomId, boolean useTeleports)
            throws MapLookupException {
        return findRoute(startRoomId, targetRoomId, useTeleports, null);
    }

    public RouteResult findRoute(String startRoomId, String targetRoomId, boolean useTeleports, String characterName)
            throws MapLookupException {
        if (startRoomId == null || startRoomId.isBlank()) {
            throw new MapLookupException("Start room not available.");
        }
        if (targetRoomId == null || targetRoomId.isBlank()) {
            throw new MapLookupException("Target room not available.");
        }

        RoomRecord start = loadRoom(startRoomId);
        if (start == null) {
            // Assume start room is unknown/outside if not in database
            start = new RoomRecord(startRoomId, -1, 0, 0, "Unknown Room", "outside");
        }
        RoomRecord target = loadRoom(targetRoomId);
        if (target == null) {
            throw new MapLookupException("Target room not found in map database.");
        }
        if (start.roomId.equals(target.roomId)) {
            return new RouteResult(List.of());
        }
        Map<String, RoomRecord> roomCache = new HashMap<>();
        roomCache.put(start.roomId, start);
        roomCache.put(target.roomId, target);

        Map<String, Integer> gScore = new HashMap<>();
        Map<String, PreviousStep> cameFrom = new HashMap<>();
        PriorityQueue<RouteNode> open = new PriorityQueue<>(Comparator.comparingDouble(RouteNode::fScore));
        gScore.put(start.roomId, 0);
        open.add(new RouteNode(start.roomId, estimateDistance(start, target)));
        TeleportRegistry.CharacterTeleports characterTeleports = TeleportRegistry.forCharacter(characterName);
        boolean teleportsReliable = characterTeleports.reliable();
        boolean outdoorOnly = characterTeleports.outdoorOnly();
        List<ResolvedTeleport> teleports = useTeleports ? resolveTeleports(roomCache, characterTeleports.teleports()) : List.of();

        while (!open.isEmpty()) {
            RouteNode current = open.poll();
            if (current.roomId.equals(target.roomId)) {
                List<RouteStep> steps = reconstructRoute(cameFrom, target.roomId);
                if (useTeleports && !teleportsReliable) {
                    steps = applyUnreliableTeleportRule(steps);
                }
                return new RouteResult(steps);
            }
            Integer currentScore = gScore.get(current.roomId);
            if (currentScore == null) {
                continue;
            }

            RoomData currentData = dataService.getRoom(current.roomId);
            if (currentData != null) {
                for (Map.Entry<String, String> entry : currentData.getExits().entrySet()) {
                    String exit = entry.getKey();
                    String neighborId = entry.getValue();
                    RoomRecord neighbor = getRoomCached(neighborId, roomCache);
                    if (neighbor == null) {
                        continue;
                    }
                    int tentativeScore = currentScore + 1;
                    Integer bestScore = gScore.get(neighborId);
                    if (bestScore == null || tentativeScore < bestScore) {
                        cameFrom.put(neighborId, new PreviousStep(current.roomId, exit));
                        gScore.put(neighborId, tentativeScore);
                        double fScore = tentativeScore + estimateDistance(neighbor, target);
                        open.add(new RouteNode(neighborId, fScore));
                    }
                }
            }

            if (useTeleports) {
                boolean isOutside = (currentData != null && "outside".equalsIgnoreCase(currentData.getRoomType()))
                        || currentData == null; // Assume outside if unknown
                if (!outdoorOnly || isOutside) {
                    for (ResolvedTeleport teleport : teleports) {
                        if (teleport.roomId.equals(current.roomId)) {
                            continue;
                        }
                        int tentativeScore = currentScore + characterTeleports.speedwalkingPenalty();
                        Integer bestScore = gScore.get(teleport.roomId);
                        if (bestScore == null || tentativeScore < bestScore) {
                            cameFrom.put(teleport.roomId, new PreviousStep(current.roomId, teleport.command()));
                            gScore.put(teleport.roomId, tentativeScore);
                            double fScore = tentativeScore + estimateDistance(teleport.room, target);
                            open.add(new RouteNode(teleport.roomId, fScore));
                        }
                    }
                }
            }
        }
        throw new MapLookupException("No route found between rooms.");
    }

    public String getMapDisplayName(int mapId) {
        return MapBackground.displayNameFor(mapId);
    }

    public RoomLocation lookupRoomLocation(String roomId) throws MapLookupException {
        if (roomId != null && "UULibrary".equalsIgnoreCase(roomId.trim())) {
            return new RoomLocation("UULibrary", 47, 165, 4810, "Unseen University Library");
        }
        if (roomId == null || roomId.isBlank()) {
            throw new MapLookupException("No room info available yet.");
        }
        RoomRecord room = loadRoom(roomId);
        if (room == null) {
            throw new MapLookupException("Current room not found in map database.");
        }
        return new RoomLocation(room.roomId, room.mapId, room.xpos, room.ypos, room.roomShort);
    }

    public List<RoomLocation> lookupRoomLocations(List<String> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        List<RoomLocation> results = new ArrayList<>();
        for (String roomId : roomIds) {
            if (roomId == null || roomId.isBlank()) {
                results.add(null);
                continue;
            }
            RoomRecord room = loadRoom(roomId);
            if (room == null) {
                results.add(null);
                continue;
            }
            results.add(new RoomLocation(room.roomId, room.mapId, room.xpos, room.ypos, room.roomShort));
        }
        return results;
    }

    public String findRoomIdByCoordinates(int mapId, int x, int y) {
        return dataService.getRooms().values().stream()
                .filter(r -> r.getMapId() == mapId && r.getXpos() == x && r.getYpos() == y)
                .map(RoomData::getRoomId)
                .findFirst()
                .orElse(null);
    }

    public RoomLocation findNearestRoom(int mapId, int x, int y) {
        return dataService.getRooms().values().stream()
                .filter(r -> r.getMapId() == mapId)
                .min(Comparator.comparingDouble(r -> Math.hypot(r.getXpos() - x, r.getYpos() - y)))
                .map(r -> new RoomLocation(r.getRoomId(), r.getMapId(), r.getXpos(), r.getYpos(), r.getRoomShort()))
                .orElse(null);
    }

    private RoomRecord loadRoom(String roomId) {
        return toRecord(dataService.getRoom(roomId));
    }

    private RoomRecord getRoomCached(String roomId, Map<String, RoomRecord> cache) {
        RoomRecord cached = cache.get(roomId);
        if (cached != null) {
            return cached;
        }
        RoomRecord loaded = loadRoom(roomId);
        if (loaded != null) {
            cache.put(roomId, loaded);
        }
        return loaded;
    }

    private List<ResolvedTeleport> resolveTeleports(Map<String, RoomRecord> cache,
                                                    List<TeleportRegistry.TeleportLocation> teleportsToResolve) {
        List<ResolvedTeleport> teleports = new ArrayList<>();
        for (TeleportRegistry.TeleportLocation teleport : teleportsToResolve) {
            String roomId = teleport.roomId();
            if (roomId == null) continue;
            RoomRecord room = getRoomCached(roomId, cache);
            if (room == null) {
                continue;
            }
            teleports.add(new ResolvedTeleport(teleport.command(), roomId, room));
        }
        return teleports;
    }

    private static List<RouteStep> applyUnreliableTeleportRule(List<RouteStep> steps) {
        List<RouteStep> result = new ArrayList<>();
        for (RouteStep step : steps) {
            result.add(step);
            if (step.exit().startsWith("tp ")) {
                break;
            }
        }
        return result;
    }

    private BufferedImage loadMapBackground(int mapId, boolean isDark) throws IOException {
        String cacheKey = mapId + (isDark ? "_dark" : "");
        Optional<BufferedImage> cached = backgroundCache.get(cacheKey);
        if (cached != null) {
            return cached.orElse(null);
        }
        Optional<MapBackground> background = MapBackground.forMapId(mapId);
        if (background.isEmpty()) {
            backgroundCache.put(cacheKey, Optional.empty());
            return null;
        }

        String filename = background.get().filename;
        if (isDark) {
            int dot = filename.lastIndexOf('.');
            String darkFilename = filename.substring(0, dot) + "_dark" + filename.substring(dot);
            
            // Try filesystem
            Path darkPath = Path.of("map-backgrounds", darkFilename);
            if (Files.exists(darkPath)) {
                BufferedImage loaded = ImageIO.read(darkPath.toFile());
                backgroundCache.put(cacheKey, Optional.ofNullable(loaded));
                return loaded;
            }
            
            // Try resource
            try (InputStream is = getClass().getResourceAsStream("/map-backgrounds/" + darkFilename)) {
                if (is != null) {
                    BufferedImage loaded = ImageIO.read(is);
                    backgroundCache.put(cacheKey, Optional.ofNullable(loaded));
                    return loaded;
                }
            }

            // Fallback: load light version and convert it
            BufferedImage light = loadMapBackground(mapId, false);
            if (light == null) {
                backgroundCache.put(cacheKey, Optional.empty());
                return null;
            }
            BufferedImage dark = com.danavalerie.matrixmudrelay.util.DarkThemeConverter.toDarkTheme(light);
            backgroundCache.put(cacheKey, Optional.ofNullable(dark));
            return dark;
        }

        // Try filesystem
        Path backgroundPath = Path.of("map-backgrounds", filename);
        if (Files.exists(backgroundPath)) {
            BufferedImage loaded = ImageIO.read(backgroundPath.toFile());
            backgroundCache.put(cacheKey, Optional.ofNullable(loaded));
            return loaded;
        }
        
        // Try resource
        try (InputStream is = getClass().getResourceAsStream("/map-backgrounds/" + filename)) {
            if (is != null) {
                BufferedImage loaded = ImageIO.read(is);
                backgroundCache.put(cacheKey, Optional.ofNullable(loaded));
                return loaded;
            }
        }

        backgroundCache.put(cacheKey, Optional.empty());
        return null;
    }

    private void drawMapBackground(BufferedImage source, Graphics2D g2, int targetWidth, int targetHeight, boolean preserveAspect) {
        if (source == null) {
            return;
        }
        if (!preserveAspect) {
            int destRight = source.getWidth() * IMAGE_SCALE;
            int destBottom = source.getHeight() * IMAGE_SCALE;
            g2.drawImage(source, 0, 0, destRight, destBottom, 0, 0, source.getWidth(), source.getHeight(), null);
            return;
        }
        double scale = Math.min(targetWidth / (double) source.getWidth(), targetHeight / (double) source.getHeight());
        int scaledWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int scaledHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int offsetX = (targetWidth - scaledWidth) / 2;
        int offsetY = (targetHeight - scaledHeight) / 2;
        g2.drawImage(source, offsetX, offsetY, offsetX + scaledWidth, offsetY + scaledHeight,
                0, 0, source.getWidth(), source.getHeight(), null);
    }

    private boolean isStaticBackground(int mapId) {
        return MapBackground.forMapId(mapId)
                .map(MapBackground::isStaticBackground)
                .orElse(false);
    }

    private record RoomRecord(String roomId, int mapId, int xpos, int ypos, String roomShort, String roomType) {
    }

    public record RoomLocation(String roomId, int mapId, int xpos, int ypos, String roomShort) {
    }

    public record RoomSearchResult(String roomId, int mapId, int xpos, int ypos, String roomShort, String roomType, String sourceInfo) {
    }

    public record NpcSearchResult(String npcId, String npcName, String roomId, int mapId, int xpos, int ypos,
                                  String roomShort, String roomType) {
    }

    public record MapArea(int mapId, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    public record ItemSearchResult(String itemName) {
    }

    public record RouteStep(String exit, String roomId) {
    }

    public record RouteResult(List<RouteStep> steps) {
        public RouteResult {
            steps = List.copyOf(steps);
        }
    }

    public record MapImage(byte[] data,
                           int width,
                           int height,
                           String mimeType,
                           String mapName,
                           int currentX,
                           int currentY,
                           boolean baseImageReused,
                           int mapId,
                           int minX,
                           int minY,
                           int imageScale,
                           int roomPixelOffsetX,
                           int roomPixelOffsetY,
                           String roomId,
                           int roomX,
                           int roomY,
                           String roomShort,
                           boolean isDark,
                           boolean staticBackground,
                           BufferedImage baseImage) {
    }

    private static final class BaseImageCache {
        private final int mapId;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int imageWidth;
        private final int imageHeight;
        private final BufferedImage image;
        private final boolean isDark;

        private BaseImageCache(int mapId, int minX, int maxX, int minY, int maxY, int imageWidth, int imageHeight,
                               BufferedImage image, boolean isDark) {
            this.mapId = mapId;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.image = image;
            this.isDark = isDark;
        }

        private boolean matches(int mapId, int minX, int maxX, int minY, int maxY, int imageWidth, int imageHeight, boolean isDark) {
            return this.mapId == mapId
                    && this.minX == minX
                    && this.maxX == maxX
                    && this.minY == minY
                    && this.maxY == maxY
                    && this.imageWidth == imageWidth
                    && this.imageHeight == imageHeight
                    && this.isDark == isDark;
        }

        private boolean containsRoom(RoomRecord room) {
            return room.xpos >= minX && room.xpos <= maxX && room.ypos >= minY && room.ypos <= maxY;
        }
    }

    public static class MapLookupException extends Exception {
        public MapLookupException(String message) {
            super(message);
        }
    }

    private record RouteNode(String roomId, double fScore) {
    }

    private record PreviousStep(String fromRoomId, String exit) {
    }

    private record ResolvedTeleport(String command, String roomId, RoomRecord room) {
    }

    private static List<RouteStep> reconstructRoute(Map<String, PreviousStep> cameFrom, String targetRoomId) {
        List<RouteStep> steps = new ArrayList<>();
        String current = targetRoomId;
        while (cameFrom.containsKey(current)) {
            PreviousStep step = cameFrom.get(current);
            steps.add(new RouteStep(step.exit, current));
            current = step.fromRoomId;
        }
        Collections.reverse(steps);
        return steps;
    }

    private static double estimateDistance(RoomRecord a, RoomRecord b) {
        if (a.mapId != b.mapId) {
            return 0;
        }
        return (Math.abs(a.xpos - b.xpos) + Math.abs(a.ypos - b.ypos)) / 10.0;
    }

    private enum MapBackground {
        ANKH_MORPORK(1, "am.png"),
        AM_ASSASSINS(2, "am_assassins.png"),
        AM_BUILDINGS(3, "am_buildings.png"),
        AM_CRUETS(4, "am_cruets.png"),
        AM_DOCKS(5, "am_docks.png"),
        AM_GUILDS(6, "am_guilds.png"),
        AM_ISLE_OF_GODS(7, "am_isle_gods.png"),
        AM_SHADES(8, "am_shades.png"),
        AM_SMALL_GODS(9, "am_smallgods.png"),
        AM_TEMPLES(10, "am_temples.png"),
        AM_THIEVES(11, "am_thieves.png"),
        AM_UNSEEN_UNIVERSITY(12, "am_uu.png"),
        AM_WARRIORS(13, "am_warriors.png"),
        AM_WATCH_HOUSE(14, "am_watch_house.png"),
        MAGPYR(15, "magpyr.png"),
        BOIS(16, "bois.png"),
        BES_PELARGIC(17, "bp.png"),
        BP_BUILDINGS(18, "bp_buildings.png"),
        BP_ESTATES(19, "bp_estates.png"),
        BP_WIZARDS(20, "bp_wizards.png"),
        BROWN_ISLANDS(21, "brown_islands.png"),
        DEATHS_DOMAIN(22, "deaths_domain.png"),
        DJELIBEYBI(23, "djb.png"),
        DJB_WIZARDS(24, "djb_wizards.png"),
        EPHEBE(25, "ephebe.png"),
        EPHEBE_UNDERDOCKS(26, "ephebe_under.png"),
        GENUA(27, "genua.png"),
        GENUA_SEWERS(28, "genua_sewers.png"),
        GRFLX(29, "grflx.png"),
        HASHISHIM_CAVES(30, "hashishim_caves.png"),
        KLATCH_REGION(31, "klatch.png"),
        LANCRE_REGION(32, "lancre_castle.png"),
        MANO_ROSSA(33, "mano_rossa.png"),
        MONKS_OF_COOL(34, "monks_cool.png"),
        NETHERWORLD(35, "netherworld.png"),
        PUMPKIN_TOWN(37, "pumpkin_town.png"),
        RAMTOPS(38, "ramtops.png"),
        STO_LAT(39, "sl.png"),
        ACADEMY_OF_ARTIFICERS(40, "sl_aoa.png"),
        CABBAGE_WAREHOUSE(41, "sl_cabbages.png"),
        AOA_LIBRARY(42, "sl_library.png"),
        STO_LAT_SEWERS(43, "sl_sewers.png"),
        SPRITE_CAVES(44, "sprite_caves.png"),
        STO_PLAINS(45, "sto_plains.png"),
        UBERWALD(46, "uberwald.png"),
        UU_LIBRARY(47, "uu_library_full.png"),
        KLATCHIAN_FARMSTEADS(48, "farmsteads.png"),
        CTF_ARENA(49, "ctf_arena.png"),
        PK_ARENA(50, "pk_arena.png"),
        AM_POST_OFFICE(51, "am_postoffice.png"),
        NINJA_GUILD(52, "bp_ninjas.png"),
        TRAVELLING_SHOP(53, "tshop.png"),
        SLIPPERY_HOLLOW(54, "slippery_hollow.png"),
        HOUSE_OF_MAGIC_CREEL(55, "creel_guild.png"),
        SPECIAL_AREAS(56, "quow_specials.png"),
        SKUND_WOLF_TRAIL(57, "skund_wolftrails.png"),
        MEDINA(58, "medina.png"),
        COPPERHEAD(59, "copperhead.png"),
        EPHEBE_CITADEL(60, "ephebe_citadel.png"),
        AM_FOOLS_GUILD(61, "am_fools.png"),
        THURSDAY_ISLAND(62, "thursday.png"),
        SS_UNSINKABLE(63, "unsinkable.png"),
        PASSAGE_ROOMS(64, "passages.png"),
        SKUND_HEDGE_WIZZARDS(65, "sto_hedge.png"),
        FLYING_ROOM(66, "flyingroom.png", true),
        WHOLE_DISC(99, "discwhole.png");

        private static final Map<Integer, MapBackground> BY_ID = new HashMap<>();

        static {
            for (MapBackground background : values()) {
                BY_ID.put(background.mapId, background);
            }
        }

        private final int mapId;
        private final String filename;
        private final boolean staticBackground;

        MapBackground(int mapId, String filename) {
            this(mapId, filename, false);
        }

        MapBackground(int mapId, String filename, boolean staticBackground) {
            this.mapId = mapId;
            this.filename = filename;
            this.staticBackground = staticBackground;
        }

        private static Optional<MapBackground> forMapId(int mapId) {
            return Optional.ofNullable(BY_ID.get(mapId));
        }

        private static String displayNameFor(int mapId) {
            MapBackground background = BY_ID.get(mapId);
            if (background == null) {
                return "Map " + mapId;
            }
            return background.formatName();
        }

        private boolean isStaticBackground() {
            return staticBackground;
        }

        private String formatName() {
            String[] parts = name().toLowerCase().split("_");
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    builder.append(' ');
                }
                String part = parts[i];
                if (part.isBlank()) {
                    continue;
                }
                builder.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    builder.append(part.substring(1));
                }
            }
            return builder.toString();
        }
    }
}
