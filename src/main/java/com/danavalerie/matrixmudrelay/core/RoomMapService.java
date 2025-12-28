package com.danavalerie.matrixmudrelay.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

public class RoomMapService {
    private static final int IMAGE_SPAN = 250;
    private static final int IMAGE_HALF_SPAN = IMAGE_SPAN / 2;
    private static final int IMAGE_SCALE = 2;
    private static final int IMAGE_PIXEL_SPAN = IMAGE_SPAN * IMAGE_SCALE;
    private static final int ROOM_PIXEL_SIZE = 4 * IMAGE_SCALE;
    private final String dbPath;
    private final boolean driverAvailable;

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

            int minX = current.xpos - IMAGE_HALF_SPAN;
            int maxX = minX + IMAGE_SPAN - 1;
            int minY = current.ypos - IMAGE_HALF_SPAN;
            int maxY = minY + IMAGE_SPAN - 1;

            Map<String, RoomRecord> rooms = loadRoomsInArea(conn, current.mapId, minX, maxX, minY, maxY);

            BufferedImage image = new BufferedImage(IMAGE_PIXEL_SPAN, IMAGE_PIXEL_SPAN, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            g2.setColor(new Color(12, 12, 18));
            g2.fillRect(0, 0, IMAGE_PIXEL_SPAN, IMAGE_PIXEL_SPAN);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setStroke(new BasicStroke(IMAGE_SCALE));

            for (RoomRecord room : rooms.values()) {
                int px = (room.xpos - minX) * IMAGE_SCALE;
                int py = (room.ypos - minY) * IMAGE_SCALE;
                drawRoomSquare(g2, px, py, room.roomId.equals(currentRoomId));
            }
            drawConnectionsImage(conn, rooms, minX, minY, g2);

            g2.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            String body = "Map centered on " + current.roomId + " (" + current.xpos + ", " + current.ypos + ")";
            return new MapImage(out.toByteArray(), IMAGE_PIXEL_SPAN, IMAGE_PIXEL_SPAN, "image/png", body);
        }
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
                    drawConnectionLine(g2, fromX, fromY, toX, toY);
                }
            }
        }
    }

    private record RoomRecord(String roomId, int mapId, int xpos, int ypos, String roomShort, String roomType) {
    }

    private static void drawRoomSquare(Graphics2D g2, int px, int py, boolean isCurrent) {
        int half = ROOM_PIXEL_SIZE / 2;
        int topLeftX = px - half;
        int topLeftY = py - half;
        int startX = Math.max(0, topLeftX);
        int startY = Math.max(0, topLeftY);
        int endX = Math.min(IMAGE_PIXEL_SPAN, topLeftX + ROOM_PIXEL_SIZE);
        int endY = Math.min(IMAGE_PIXEL_SPAN, topLeftY + ROOM_PIXEL_SIZE);
        int width = endX - startX;
        int height = endY - startY;
        if (width <= 0 || height <= 0) {
            return;
        }
        g2.setColor(isCurrent ? new Color(96, 230, 118) : new Color(208, 212, 230));
        g2.fillRect(startX, startY, width, height);
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

    public record MapImage(byte[] data, int width, int height, String mimeType, String body) {
    }

    public static class MapLookupException extends Exception {
        public MapLookupException(String message) {
            super(message);
        }
    }
}
