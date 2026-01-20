package com.danavalerie.matrixmudrelay.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class UULibraryService {
    public enum Orientation {
        NORTH(1, 0, "north"),
        EAST(0, 1, "east"),
        SOUTH(-1, 0, "south"),
        WEST(0, -1, "west");

        public final int dRow, dCol;
        public final String name;

        Orientation(int dRow, int dCol, String name) {
            this.dRow = dRow;
            this.dCol = dCol;
            this.name = name;
        }

        public Orientation turnRight() {
            return values()[(ordinal() + 1) % 4];
        }

        public Orientation turnLeft() {
            return values()[(ordinal() + 3) % 4];
        }

        public Orientation turn180() {
            return values()[(ordinal() + 2) % 4];
        }
    }

    public static class Room {
        public int row, col;
        public boolean table;
        public int number = -1;
        public List<String> exits;
    }

    private final Map<String, Room> maze = new HashMap<>();
    private volatile int curRow, curCol;
    private volatile Orientation orientation = Orientation.NORTH;
    private volatile boolean active = false;

    private static final UULibraryService INSTANCE = new UULibraryService();

    public static UULibraryService getInstance() {
        return INSTANCE;
    }

    private UULibraryService() {
        loadMap();
    }

    private void loadMap() {
        try (InputStream is = Files.newInputStream(Paths.get("uu_library.json"))) {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<Room>>() {}.getType();
            List<Room> rooms = gson.fromJson(new InputStreamReader(is), listType);
            for (Room r : rooms) {
                maze.put(r.row + "," + r.col, r);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setRoomId(String roomId) {
        boolean wasActive = active;
        active = roomId != null && "UULibrary".equalsIgnoreCase(roomId.trim());
        if (active && !wasActive) {
            // Initial position when entering from South
            curRow = 1;
            curCol = 5;
            orientation = Orientation.NORTH;
        }
    }

    public boolean isActive() {
        return active;
    }

    public void processCommand(String cmd) {
        if (!active) return;
        cmd = cmd.toLowerCase().trim();
        switch (cmd) {
            case "forward":
            case "fw":
                move();
                break;
            case "backward":
            case "bw":
                orientation = orientation.turn180();
                move();
                break;
            case "right":
            case "rt":
                orientation = orientation.turnRight();
                move();
                break;
            case "left":
            case "lt":
                orientation = orientation.turnLeft();
                move();
                break;
        }
    }

    private void move() {
        Room r = maze.get(curRow + "," + curCol);
        if (r != null && r.exits != null && r.exits.contains(orientation.name)) {
            int nextRow = curRow + orientation.dRow;
            int nextCol = curCol + orientation.dCol;
            
            // Handle wrap-around
            if (nextCol < 1) nextCol = 9;
            if (nextCol > 9) nextCol = 1;
            
            curRow = nextRow;
            curCol = nextCol;
        }
    }

    public int getX() {
        // Inverse of translation logic in UULibraryMapGenerator: c = (x - 45) / 30 + 1
        return (curCol - 1) * 30 + 45;
    }

    public int getY() {
        // Inverse of translation logic in UULibraryMapGenerator: r = (4810 - y) / 30 + 1
        return 4810 - (curRow - 1) * 30;
    }

    public Orientation getOrientation() {
        return orientation;
    }
}
