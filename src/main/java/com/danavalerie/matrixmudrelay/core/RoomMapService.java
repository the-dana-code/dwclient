package com.danavalerie.matrixmudrelay.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoomMapService {
    private static final int MAP_SIZE = 20;
    private static final int HALF_SPAN = 9;
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

    public String renderMap(String currentRoomId) throws SQLException, MapLookupException {
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

            int minX = current.xpos - HALF_SPAN;
            int maxX = current.xpos + (MAP_SIZE - HALF_SPAN - 1);
            int minY = current.ypos - HALF_SPAN;
            int maxY = current.ypos + (MAP_SIZE - HALF_SPAN - 1);

            Map<String, RoomRecord> rooms = loadRoomsInArea(conn, current.mapId, minX, maxX, minY, maxY);
            int gridSize = MAP_SIZE * 2 - 1;
            char[][] grid = new char[gridSize][gridSize];
            fill(grid, ' ');

            for (RoomRecord room : rooms.values()) {
                int col = (room.xpos - minX) * 2;
                int row = (maxY - room.ypos) * 2;
                if (row < 0 || row >= gridSize || col < 0 || col >= gridSize) {
                    continue;
                }
                grid[row][col] = room.roomId.equals(currentRoomId) ? '@' : 'o';
            }

            drawConnections(conn, rooms, minX, maxY, grid);

            StringBuilder sb = new StringBuilder();
            sb.append("Map centered on ").append(current.roomId)
                    .append(" (").append(current.xpos).append(", ").append(current.ypos).append(")")
                    .append(" [x=").append(minX).append("..").append(maxX)
                    .append(", y=").append(minY).append("..").append(maxY).append("]")
                    .append("\n");
            sb.append("Legend: @=you o=room").append("\n");
            for (int row = 0; row < gridSize; row++) {
                sb.append(rtrim(grid[row]));
                if (row < gridSize - 1) {
                    sb.append("\n");
                }
            }
            return sb.toString();
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

    private void drawConnections(Connection conn, Map<String, RoomRecord> rooms, int minX, int maxY, char[][] grid)
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
                    int dx = to.xpos - from.xpos;
                    int dy = to.ypos - from.ypos;
                    if (Math.abs(dx) + Math.abs(dy) != 1) {
                        continue;
                    }
                    int col = (from.xpos - minX) * 2;
                    int row = (maxY - from.ypos) * 2;
                    if (dx == 1) {
                        grid[row][col + 1] = '-';
                    } else if (dx == -1) {
                        grid[row][col - 1] = '-';
                    } else if (dy == 1) {
                        grid[row - 1][col] = '|';
                    } else if (dy == -1) {
                        grid[row + 1][col] = '|';
                    }
                }
            }
        }
    }

    private static void fill(char[][] grid, char ch) {
        for (char[] row : grid) {
            for (int i = 0; i < row.length; i++) {
                row[i] = ch;
            }
        }
    }

    private static String rtrim(char[] row) {
        int end = row.length;
        while (end > 0 && row[end - 1] == ' ') {
            end--;
        }
        return new String(row, 0, end);
    }

    private record RoomRecord(String roomId, int mapId, int xpos, int ypos, String roomShort, String roomType) {
    }

    public static class MapLookupException extends Exception {
        public MapLookupException(String message) {
            super(message);
        }
    }
}
