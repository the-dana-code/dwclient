package com.danavalerie.matrixmudrelay.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

import javax.imageio.ImageIO;

public class RoomMapService {
    private static final int TELEPORT_START_COST = 8;
    private static final int IMAGE_SCALE = 2;
    private static final int ROOM_PIXEL_SIZE = 5 * IMAGE_SCALE;
    private static final int ROOM_PIXEL_OFFSET_X = IMAGE_SCALE;
    private static final int ROOM_PIXEL_OFFSET_Y = IMAGE_SCALE;
    private static final int IMAGE_SPAN = 250;
    private static final int IMAGE_HALF_SPAN = IMAGE_SPAN / 2;
    private final String dbPath;
    private final boolean driverAvailable;
    private final Map<Integer, Optional<BufferedImage>> backgroundCache = new HashMap<>();
    private BaseImageCache baseImageCache;

    public RoomMapService(String dbPath) {
        this.dbPath = dbPath;
        boolean loaded = false;
        try {
            Class.forName("org.sqlite.JDBC");
            loaded = true;
        } catch (ClassNotFoundException e) {
            loaded = false;
        }
        this.driverAvailable = loaded;
    }

    public MapImage renderMapImage(String currentRoomId) throws SQLException, MapLookupException, IOException {
        if (currentRoomId == null || currentRoomId.isBlank()) {
            throw new MapLookupException("No room info available yet.");
        }
        if (!driverAvailable) {
            throw new MapLookupException("SQLite driver not available.");
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            RoomRecord current = loadRoom(conn, currentRoomId);
            if (current == null) {
                throw new MapLookupException("Current room not found in map database.");
            }
            if (baseImageCache != null && baseImageCache.mapId != current.mapId) {
                baseImageCache = null;
            }

            BufferedImage backgroundImage = loadMapBackground(current.mapId);
            int minX;
            int maxX;
            int minY;
            int maxY;
            int imageWidth;
            int imageHeight;
            if (backgroundImage == null) {
                BaseImageCache cached = baseImageCache;
                if (cached != null && cached.mapId == current.mapId && cached.containsRoom(current)) {
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
                    && cachedBase.matches(current.mapId, minX, maxX, minY, maxY, imageWidth, imageHeight);
            byte[] data = null;
            if (!reuseBase) {
                BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = image.createGraphics();
                g2.setColor(new Color(12, 12, 18));
                g2.fillRect(0, 0, imageWidth, imageHeight);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.setStroke(new BasicStroke(IMAGE_SCALE));

                if (backgroundImage != null) {
                    drawMapBackground(backgroundImage, g2);
                }

                g2.dispose();
                baseImageCache = new BaseImageCache(current.mapId, minX, maxX, minY, maxY, imageWidth, imageHeight,
                        image);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                data = out.toByteArray();
            }

            int currentX = (current.xpos - minX) * IMAGE_SCALE + ROOM_PIXEL_OFFSET_X;
            int currentY = (current.ypos - minY) * IMAGE_SCALE + ROOM_PIXEL_OFFSET_Y;

            String body = "Map centered on " + current.roomId + " (" + current.xpos + ", " + current.ypos + ")";
            return new MapImage(data, imageWidth, imageHeight, "image/png", body, currentX, currentY, reuseBase);
        }
    }

    public List<RoomSearchResult> searchRoomsByName(String term, int limit) throws SQLException, MapLookupException {
        if (term == null || term.isBlank()) {
            throw new MapLookupException("Search term cannot be blank.");
        }
        if (!driverAvailable) {
            throw new MapLookupException("SQLite driver not available.");
        }
        String trimmed = term.trim().toLowerCase();
        String sql = "select room_id, map_id, xpos, ypos, room_short, room_type " +
                "from rooms where lower(room_short) like ? order by room_short, map_id, room_id limit ?";
        List<RoomSearchResult> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + trimmed + "%");
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new RoomSearchResult(
                            rs.getString("room_id"),
                            rs.getInt("map_id"),
                            rs.getInt("xpos"),
                            rs.getInt("ypos"),
                            rs.getString("room_short"),
                            rs.getString("room_type"),
                            null
                    ));
                }
            }
        }
        return results;
    }

    public List<NpcSearchResult> searchNpcsByName(String term, int limit) throws SQLException, MapLookupException {
        if (term == null || term.isBlank()) {
            throw new MapLookupException("Search term cannot be blank.");
        }
        if (!driverAvailable) {
            throw new MapLookupException("SQLite driver not available.");
        }
        String trimmed = term.trim().toLowerCase();
        String sql = "select npc_info.npc_id, npc_info.npc_name, rooms.room_id, rooms.map_id, " +
                "rooms.xpos, rooms.ypos, rooms.room_short, rooms.room_type " +
                "from npc_info join rooms on npc_info.room_id = rooms.room_id " +
                "where lower(npc_info.npc_name) like ? " +
                "order by lower(npc_info.npc_name), rooms.map_id, rooms.room_id " +
                "limit ?";
        List<NpcSearchResult> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + trimmed + "%");
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new NpcSearchResult(
                            rs.getString("npc_id"),
                            rs.getString("npc_name"),
                            rs.getString("room_id"),
                            rs.getInt("map_id"),
                            rs.getInt("xpos"),
                            rs.getInt("ypos"),
                            rs.getString("room_short"),
                            rs.getString("room_type")
                    ));
                }
            }
        }
        return results;
    }

    public List<ItemSearchResult> searchItemsByName(String term, int limit) throws SQLException, MapLookupException {
        if (term == null || term.isBlank()) {
            throw new MapLookupException("Search term cannot be blank.");
        }
        if (!driverAvailable) {
            throw new MapLookupException("SQLite driver not available.");
        }
        String trimmed = term.trim().toLowerCase();
        String sql = "select item_name from items where lower(item_name) like ? order by lower(item_name) limit ?";
        List<ItemSearchResult> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + trimmed + "%");
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new ItemSearchResult(rs.getString("item_name")));
                }
            }
        }
        return results;
    }

    public List<RoomSearchResult> searchRoomsByItemName(String itemName, int limit) throws SQLException, MapLookupException {
        if (itemName == null || itemName.isBlank()) {
            throw new MapLookupException("Item name cannot be blank.");
        }
        if (!driverAvailable) {
            throw new MapLookupException("SQLite driver not available.");
        }
        String sql = "select rooms.room_id, rooms.map_id, rooms.xpos, rooms.ypos, rooms.room_short, rooms.room_type, refs.source_info " +
                "from rooms join ( " +
                "  select room_id, 'Shop' as source_info from shop_items where lower(item_name) = ? " +
                "  union " +
                "  select npc_info.room_id, 'NPC: ' || npc_info.npc_name as source_info from npc_items join npc_info on npc_items.npc_id = npc_info.npc_id " +
                "  where lower(npc_items.item_name) = ? " +
                "  union " +
                "  select special_find_note as room_id, 'Special' as source_info from items " +
                "  where lower(item_name) = ? and special_find_note <> ''" +
                ") refs on rooms.room_id = refs.room_id " +
                "order by (case " +
                "  when refs.source_info like 'NPC:%' then 0 " +
                "  when refs.source_info = 'Special' then 1 " +
                "  when refs.source_info = 'Shop' then 2 " +
                "  else 3 " +
                "end), rooms.map_id, rooms.room_short, rooms.room_id " +
                "limit ?";
        List<RoomSearchResult> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String lowered = itemName.trim().toLowerCase();
            stmt.setString(1, lowered);
            stmt.setString(2, lowered);
            stmt.setString(3, lowered);
            stmt.setInt(4, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new RoomSearchResult(
                            rs.getString("room_id"),
                            rs.getInt("map_id"),
                            rs.getInt("xpos"),
                            rs.getInt("ypos"),
                            rs.getString("room_short"),
                            rs.getString("room_type"),
                            rs.getString("source_info")
                    ));
                }
            }
        }
        return results;
    }

    public RouteResult findRoute(String startRoomId, String targetRoomId) throws SQLException, MapLookupException {
        return findRoute(startRoomId, targetRoomId, true);
    }

    public RouteResult findRoute(String startRoomId, String targetRoomId, boolean useTeleports)
            throws SQLException, MapLookupException {
        if (startRoomId == null || startRoomId.isBlank()) {
            throw new MapLookupException("Start room not available.");
        }
        if (targetRoomId == null || targetRoomId.isBlank()) {
            throw new MapLookupException("Target room not available.");
        }
        if (!driverAvailable) {
            throw new MapLookupException("SQLite driver not available.");
        }
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            RoomRecord start = loadRoom(conn, startRoomId);
            if (start == null) {
                throw new MapLookupException("Start room not found in map database.");
            }
            RoomRecord target = loadRoom(conn, targetRoomId);
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

            if (useTeleports) {
                List<ResolvedTeleport> teleports = resolveTeleports(conn, roomCache);
                for (ResolvedTeleport teleport : teleports) {
                    if (teleport.roomId.equals(start.roomId)) {
                        continue;
                    }
                    int tentativeScore = TELEPORT_START_COST;
                    Integer bestScore = gScore.get(teleport.roomId);
                    if (bestScore == null || tentativeScore < bestScore) {
                        cameFrom.put(teleport.roomId, new PreviousStep(start.roomId, teleport.command()));
                        gScore.put(teleport.roomId, tentativeScore);
                        double fScore = tentativeScore + estimateDistance(teleport.room, target);
                        open.add(new RouteNode(teleport.roomId, fScore));
                    }
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "select connect_id, exit from room_exits where room_id = ?")) {
                while (!open.isEmpty()) {
                    RouteNode current = open.poll();
                    if (current.roomId.equals(target.roomId)) {
                        return new RouteResult(reconstructRoute(cameFrom, target.roomId));
                    }
                    Integer currentScore = gScore.get(current.roomId);
                    if (currentScore == null) {
                        continue;
                    }
                    stmt.setString(1, current.roomId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String neighborId = rs.getString("connect_id");
                            String exit = rs.getString("exit");
                            RoomRecord neighbor = getRoomCached(conn, neighborId, roomCache);
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
                }
            }
        }
        throw new MapLookupException("No route found between rooms.");
    }

    public String getMapDisplayName(int mapId) {
        return MapBackground.displayNameFor(mapId);
    }

    private RoomRecord loadRoom(Connection conn, String roomId) throws SQLException {
        String sql = "select room_id, map_id, xpos, ypos, room_short, room_type from rooms where room_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, roomId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new RoomRecord(
                        rs.getString("room_id"),
                        rs.getInt("map_id"),
                        rs.getInt("xpos"),
                        rs.getInt("ypos"),
                        rs.getString("room_short"),
                        rs.getString("room_type")
                );
            }
        }
    }

    private Map<String, RoomRecord> loadRoomsInArea(Connection conn, int mapId, int minX, int maxX, int minY, int maxY)
            throws SQLException {
        String sql = "select room_id, map_id, xpos, ypos, room_short, room_type " +
                "from rooms where map_id = ? and xpos between ? and ? and ypos between ? and ?";
        Map<String, RoomRecord> rooms = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, mapId);
            stmt.setInt(2, minX);
            stmt.setInt(3, maxX);
            stmt.setInt(4, minY);
            stmt.setInt(5, maxY);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RoomRecord room = new RoomRecord(
                            rs.getString("room_id"),
                            rs.getInt("map_id"),
                            rs.getInt("xpos"),
                            rs.getInt("ypos"),
                            rs.getString("room_short"),
                            rs.getString("room_type")
                    );
                    rooms.put(room.roomId, room);
                }
            }
        }
        return rooms;
    }

    private RoomRecord getRoomCached(Connection conn, String roomId, Map<String, RoomRecord> cache) throws SQLException {
        RoomRecord cached = cache.get(roomId);
        if (cached != null) {
            return cached;
        }
        RoomRecord loaded = loadRoom(conn, roomId);
        if (loaded != null) {
            cache.put(roomId, loaded);
        }
        return loaded;
    }

    private List<ResolvedTeleport> resolveTeleports(Connection conn, Map<String, RoomRecord> cache) throws SQLException {
        List<ResolvedTeleport> teleports = new ArrayList<>();
        String sql = "select room_id from rooms where map_id = ? and xpos = ? and ypos = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (TeleportRegistry.TeleportLocation teleport : TeleportRegistry.TELEPORTS) {
                stmt.setInt(1, teleport.mapId());
                stmt.setInt(2, teleport.x());
                stmt.setInt(3, teleport.y());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        continue;
                    }
                    String roomId = rs.getString("room_id");
                    RoomRecord room = getRoomCached(conn, roomId, cache);
                    if (room == null) {
                        continue;
                    }
                    teleports.add(new ResolvedTeleport(teleport.name(), roomId, room));
                }
            }
        }
        return teleports;
    }

    private BufferedImage loadMapBackground(int mapId) throws IOException {
        Optional<BufferedImage> cached = backgroundCache.get(mapId);
        if (cached != null) {
            return cached.orElse(null);
        }
        Optional<MapBackground> background = MapBackground.forMapId(mapId);
        if (background.isEmpty()) {
            backgroundCache.put(mapId, Optional.empty());
            return null;
        }
        Path backgroundPath = Path.of("map-backgrounds", background.get().filename);
        BufferedImage loaded = ImageIO.read(backgroundPath.toFile());
        backgroundCache.put(mapId, Optional.ofNullable(loaded));
        return loaded;
    }

    private void drawMapBackground(BufferedImage source, Graphics2D g2) {
        if (source == null) {
            return;
        }
        int destRight = source.getWidth() * IMAGE_SCALE;
        int destBottom = source.getHeight() * IMAGE_SCALE;
        g2.drawImage(source, 0, 0, destRight, destBottom, 0, 0, source.getWidth(), source.getHeight(), null);
    }

    private void drawConnectionsImage(Connection conn, Map<String, RoomRecord> rooms, int minX, int minY, Graphics2D g2)
            throws SQLException {
        if (rooms.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>(rooms.keySet());
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) placeholders.append(',');
            placeholders.append('?');
        }
        String sql = "select room_id, connect_id from room_exits where room_id in (" + placeholders + ")";
        g2.setColor(new Color(80, 90, 120));
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                stmt.setString(i + 1, ids.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RoomRecord from = rooms.get(rs.getString("room_id"));
                    RoomRecord to = rooms.get(rs.getString("connect_id"));
                    if (from == null || to == null) {
                        continue;
                    }
                    int fromX = (from.xpos - minX) * IMAGE_SCALE;
                    int fromY = (from.ypos - minY) * IMAGE_SCALE;
                    int toX = (to.xpos - minX) * IMAGE_SCALE;
                    int toY = (to.ypos - minY) * IMAGE_SCALE;
                    drawConnectionLine(
                            g2,
                            fromX + ROOM_PIXEL_OFFSET_X,
                            fromY + ROOM_PIXEL_OFFSET_Y,
                            toX + ROOM_PIXEL_OFFSET_X,
                            toY + ROOM_PIXEL_OFFSET_Y
                    );
                }
            }
        }
    }

    private record RoomRecord(String roomId, int mapId, int xpos, int ypos, String roomShort, String roomType) {
    }

    public record RoomSearchResult(String roomId, int mapId, int xpos, int ypos, String roomShort, String roomType, String sourceInfo) {
    }

    public record NpcSearchResult(String npcId, String npcName, String roomId, int mapId, int xpos, int ypos,
                                  String roomShort, String roomType) {
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

    private static void drawRoomSquare(Graphics2D g2, int px, int py, boolean isCurrent, int imageWidth, int imageHeight) {
        if (isCurrent) {
            drawCurrentRoom(g2, px, py, imageWidth, imageHeight);
            return;
        }
        int half = ROOM_PIXEL_SIZE / 2;
        int topLeftX = px - half;
        int topLeftY = py - half;
        int startX = Math.max(0, topLeftX);
        int startY = Math.max(0, topLeftY);
        int endX = Math.min(imageWidth, topLeftX + ROOM_PIXEL_SIZE);
        int endY = Math.min(imageHeight, topLeftY + ROOM_PIXEL_SIZE);
        int width = endX - startX;
        int height = endY - startY;
        if (width <= 0 || height <= 0) {
            return;
        }
        g2.setColor(new Color(208, 212, 230));
        g2.fillRect(startX, startY, width, height);
    }

    private static void drawCurrentRoom(Graphics2D g2, int px, int py, int imageWidth, int imageHeight) {
        // Current room marker is drawn as an animated overlay in the UI.
    }

    private static void drawConnectionLine(Graphics2D g2, int fromX, int fromY, int toX, int toY) {
        int dx = toX - fromX;
        int dy = toY - fromY;
        if (dx == 0 && dy == 0) {
            return;
        }
        double length = Math.hypot(dx, dy);
        double ux = dx / length;
        double uy = dy / length;
        double offset = (ROOM_PIXEL_SIZE + IMAGE_SCALE) / 2.0;
        int startX = (int) Math.round(fromX + ux * offset);
        int startY = (int) Math.round(fromY + uy * offset);
        int endX = (int) Math.round(toX - ux * offset);
        int endY = (int) Math.round(toY - uy * offset);
        g2.drawLine(startX, startY, endX, endY);
    }

    public record MapImage(byte[] data, int width, int height, String mimeType, String body, int currentX, int currentY,
                           boolean baseImageReused) {
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

        private BaseImageCache(int mapId, int minX, int maxX, int minY, int maxY, int imageWidth, int imageHeight,
                               BufferedImage image) {
            this.mapId = mapId;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.image = image;
        }

        private boolean matches(int mapId, int minX, int maxX, int minY, int maxY, int imageWidth, int imageHeight) {
            return this.mapId == mapId
                    && this.minX == minX
                    && this.maxX == maxX
                    && this.minY == minY
                    && this.maxY == maxY
                    && this.imageWidth == imageWidth
                    && this.imageHeight == imageHeight;
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

    private record ResolvedTeleport(String name, String roomId, RoomRecord room) {
        private String command() {
            return "tp " + name;
        }
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
        return Math.abs(a.xpos - b.xpos) + Math.abs(a.ypos - b.ypos);
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
        WHOLE_DISC(99, "discwhole.png");

        private static final Map<Integer, MapBackground> BY_ID = new HashMap<>();

        static {
            for (MapBackground background : values()) {
                BY_ID.put(background.mapId, background);
            }
        }

        private final int mapId;
        private final String filename;

        MapBackground(int mapId, String filename) {
            this.mapId = mapId;
            this.filename = filename;
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
