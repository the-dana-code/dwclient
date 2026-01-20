package com.danavalerie.matrixmudrelay.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;

public class UULibraryService {
    public enum Orientation {
        NORTH(0, -2, "north"),
        EAST(2, 0, "east"),
        SOUTH(0, 2, "south"),
        WEST(-2, 0, "west");

        public final int dn, dm;
        public final String name;

        Orientation(int dn, int dm, String name) {
            this.dn = dn;
            this.dm = dm;
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
        public int n, m, x, y;
        public boolean table;
        public int number = -1;
        public List<String> exits;
    }

    private final Map<String, Room> maze = new HashMap<>();
    private int curN, curM;
    private Orientation orientation = Orientation.NORTH;
    private boolean active = false;

    private static final UULibraryService INSTANCE = new UULibraryService();

    public static UULibraryService getInstance() {
        return INSTANCE;
    }

    private UULibraryService() {
        loadMap();
    }

    private void loadMap() {
        try (InputStream is = getClass().getResourceAsStream("/uu_library.json")) {
            if (is == null) {
                System.err.println("UULibrary map JSON not found!");
                return;
            }
            Gson gson = new Gson();
            Type listType = new TypeToken<List<Room>>() {}.getType();
            List<Room> rooms = gson.fromJson(new InputStreamReader(is), listType);
            for (Room r : rooms) {
                maze.put(r.n + "," + r.m, r);
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
            curN = 9;
            curM = 321;
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
        Room r = maze.get(curN + "," + curM);
        if (r != null && r.exits != null && r.exits.contains(orientation.name)) {
            curN += orientation.dn;
            curM += orientation.dm;
        }
        // Even if move fails, orientation remains changed
    }

    public int getX() {
        Room r = maze.get(curN + "," + curM);
        return r != null ? r.x : 150; // Default to Exit area
    }

    public int getY() {
        Room r = maze.get(curN + "," + curM);
        return r != null ? r.y : 4825;
    }

    public Orientation getOrientation() {
        return orientation;
    }
}
