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
    private final List<Runnable> listeners = new ArrayList<>();

    private static final UULibraryService INSTANCE = new UULibraryService();

    public static UULibraryService getInstance() {
        return INSTANCE;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
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
        if (active != wasActive) {
            notifyListeners();
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

    public String getNextStepCommand(int targetRow, int targetCol) {
        if (!active) return null;
        if (curRow == targetRow && curCol == targetCol) return null;

        List<Room> path = findPath(curRow, curCol, targetRow, targetCol);
        if (path == null || path.isEmpty()) return null;

        Room next = path.get(0);
        return determineCommand(curRow, curCol, orientation, next.row, next.col);
    }

    private List<Room> findPath(int startRow, int startCol, int targetRow, int targetCol) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        Map<String, Integer> gScores = new HashMap<>();

        Node startNode = new Node(startRow, startCol, 0, heuristic(startRow, startCol, targetRow, targetCol), null);
        open.add(startNode);
        gScores.put(startRow + "," + startCol, 0);

        while (!open.isEmpty()) {
            Node current = open.poll();

            if (current.row == targetRow && current.col == targetCol) {
                return reconstructPath(current);
            }

            if (current.gScore > gScores.getOrDefault(current.row + "," + current.col, Integer.MAX_VALUE)) {
                continue;
            }

            Room r = maze.get(current.row + "," + current.col);
            if (r == null || r.exits == null) continue;

            for (Orientation o : Orientation.values()) {
                if (r.exits.contains(o.name)) {
                    int nextRow = current.row + o.dRow;
                    int nextCol = current.col + o.dCol;
                    if (nextCol < 1) nextCol = 9;
                    if (nextCol > 9) nextCol = 1;

                    int tentativeG = current.gScore + 1;
                    String key = nextRow + "," + nextCol;
                    if (tentativeG < gScores.getOrDefault(key, Integer.MAX_VALUE)) {
                        gScores.put(key, tentativeG);
                        double fScore = tentativeG + heuristic(nextRow, nextCol, targetRow, targetCol);
                        open.add(new Node(nextRow, nextCol, tentativeG, fScore, current));
                    }
                }
            }
        }
        return null;
    }

    private List<Room> reconstructPath(Node targetNode) {
        LinkedList<Room> path = new LinkedList<>();
        Node curr = targetNode;
        while (curr != null && curr.parent != null) {
            path.addFirst(maze.get(curr.row + "," + curr.col));
            curr = curr.parent;
        }
        return path;
    }

    private double heuristic(int r1, int c1, int r2, int c2) {
        int dr = Math.abs(r1 - r2);
        int dc = Math.abs(c1 - c2);
        dc = Math.min(dc, 9 - dc);
        return dr + dc;
    }

    private String determineCommand(int r1, int c1, Orientation currentOri, int r2, int c2) {
        for (Orientation o : Orientation.values()) {
            int nextR = r1 + o.dRow;
            int nextC = c1 + o.dCol;
            if (nextC < 1) nextC = 9;
            if (nextC > 9) nextC = 1;

            if (nextR == r2 && nextC == c2) {
                if (o == currentOri) return "fw";
                if (o == currentOri.turn180()) return "bw";
                if (o == currentOri.turnRight()) return "rt";
                if (o == currentOri.turnLeft()) return "lt";
            }
        }
        return null;
    }

    private static class Node {
        int row, col;
        int gScore;
        double fScore;
        Node parent;

        Node(int row, int col, int gScore, double fScore, Node parent) {
            this.row = row;
            this.col = col;
            this.gScore = gScore;
            this.fScore = fScore;
            this.parent = parent;
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

    public int getCurRow() {
        return curRow;
    }

    public int getCurCol() {
        return curCol;
    }
}
