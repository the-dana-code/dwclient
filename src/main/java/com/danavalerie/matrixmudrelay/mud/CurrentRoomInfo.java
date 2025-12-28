package com.danavalerie.matrixmudrelay.mud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class CurrentRoomInfo {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(new Snapshot(null, Map.of()));

    public Snapshot getSnapshot() {
        return snapshot.get();
    }

    public void clear() {
        snapshot.set(new Snapshot(null, Map.of()));
    }

    public void update(String command, JsonElement payload) {
        if (command == null || payload == null) return;
        String normalized = command.trim();
        if (normalized.isEmpty()) return;

        String lower = normalized.toLowerCase();
        Snapshot current = snapshot.get();
        String nextRoomId = current.roomId;

        if ("room.info".equals(lower) && payload.isJsonObject()) {
            String extracted = extractRoomId(payload.getAsJsonObject());
            if (extracted != null && !extracted.isBlank()) {
                nextRoomId = extracted;
            }
        }

        Map<String, JsonElement> data;
        if (nextRoomId != null && (current.roomId == null || !current.roomId.equals(nextRoomId))) {
            data = new LinkedHashMap<>();
        } else {
            data = new LinkedHashMap<>(current.data);
        }
        data.put(lower, payload);
        snapshot.set(new Snapshot(nextRoomId, data));
    }

    private static String extractRoomId(JsonObject obj) {
        String[] keys = {"id", "num", "vnum", "roomid"};
        for (String key : keys) {
            JsonElement val = obj.get(key);
            if (val == null || val.isJsonNull()) continue;
            if (val.isJsonPrimitive()) {
                return val.getAsString();
            }
        }
        return null;
    }

    public static final class Snapshot {
        private final String roomId;
        private final Map<String, JsonElement> data;

        private Snapshot(String roomId, Map<String, JsonElement> data) {
            this.roomId = roomId;
            this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
        }

        public String roomId() {
            return roomId;
        }

        public Map<String, JsonElement> data() {
            return data;
        }

        public boolean isEmpty() {
            return (roomId == null || roomId.isBlank()) && data.isEmpty();
        }

        public String formatForDisplay() {
            if (isEmpty()) {
                return "No room info available yet.";
            }
            StringBuilder sb = new StringBuilder();
            if (roomId != null && !roomId.isBlank()) {
                sb.append("Room ID: ").append(roomId);
            } else {
                sb.append("Room ID: (unknown)");
            }
            if (!data.isEmpty()) {
                sb.append("\n\n");
            }
            int index = 0;
            for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
                String json = GSON.toJson(entry.getValue());
                sb.append(entry.getKey()).append(":");
                if (json.contains("\n")) {
                    sb.append("\n").append(json);
                } else {
                    sb.append(" ").append(json);
                }
                index++;
                if (index < data.size()) {
                    sb.append("\n\n");
                }
            }
            return sb.toString();
        }
    }
}
