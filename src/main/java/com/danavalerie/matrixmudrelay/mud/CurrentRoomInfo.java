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
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(
            new Snapshot(null, Map.of(), Map.of(), Map.of()));

    public Snapshot getSnapshot() {
        return snapshot.get();
    }

    public void clear() {
        snapshot.set(new Snapshot(null, Map.of(), Map.of(), Map.of()));
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

        Map<String, JsonElement> roomData = new LinkedHashMap<>(current.roomData);
        Map<String, JsonElement> charData = new LinkedHashMap<>(current.charData);
        Map<String, JsonElement> otherData = new LinkedHashMap<>(current.otherData);

        if (lower.startsWith("room.")) {
            roomData.put(lower, payload);
        } else if (lower.startsWith("char.")) {
            charData.put(lower, payload);
        } else {
            otherData.put(lower, payload);
        }

        snapshot.set(new Snapshot(nextRoomId, roomData, charData, otherData));
    }

    private static String extractRoomId(JsonObject obj) {
        String[] keys = {"identifier", "id", "num", "vnum", "roomid"};
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
        private final Map<String, JsonElement> roomData;
        private final Map<String, JsonElement> charData;
        private final Map<String, JsonElement> otherData;
        private final Map<String, JsonElement> data;

        private Snapshot(String roomId,
                         Map<String, JsonElement> roomData,
                         Map<String, JsonElement> charData,
                         Map<String, JsonElement> otherData) {
            this.roomId = roomId;
            this.roomData = Collections.unmodifiableMap(new LinkedHashMap<>(roomData));
            this.charData = Collections.unmodifiableMap(new LinkedHashMap<>(charData));
            this.otherData = Collections.unmodifiableMap(new LinkedHashMap<>(otherData));
            Map<String, JsonElement> combined = new LinkedHashMap<>();
            combined.putAll(this.roomData);
            combined.putAll(this.charData);
            combined.putAll(this.otherData);
            this.data = Collections.unmodifiableMap(combined);
        }

        public String roomId() {
            return roomId;
        }

        public String roomName() {
            JsonElement roomInfo = roomData.get("room.info");
            if (roomInfo != null && roomInfo.isJsonObject()) {
                JsonObject obj = roomInfo.getAsJsonObject();
                if (obj.has("short")) {
                    return obj.get("short").getAsString();
                }
            }
            return null;
        }

        public Map<String, JsonElement> data() {
            return data;
        }

        public String characterName() {
            return extractCapname(data.get("char.info"));
        }

        public boolean isEmpty() {
            return (roomId == null || roomId.isBlank()) && data.isEmpty();
        }

        public String formatForDisplay() {
            if (isEmpty()) {
                return "No room info available yet.";
            }
            StringBuilder sb = new StringBuilder();
            String capname = extractCapname(data.get("char.info"));
            if (capname != null && !capname.isBlank()) {
                sb.append("Character: ").append(capname).append("\n");
            }
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

        private static String extractCapname(JsonElement element) {
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonElement capname = element.getAsJsonObject().get("capname");
            if (capname == null || capname.isJsonNull() || !capname.isJsonPrimitive()) {
                return null;
            }
            return capname.getAsString();
        }
    }
}

