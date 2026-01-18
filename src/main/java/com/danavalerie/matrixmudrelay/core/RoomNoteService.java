package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.core.data.RoomButton;
import com.danavalerie.matrixmudrelay.core.data.RoomNoteData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class RoomNoteService {
    private static final Logger logger = LoggerFactory.getLogger(RoomNoteService.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path storagePath;
    private final Map<String, RoomNoteData> roomButtonsMap = new TreeMap<>();

    public RoomNoteService(Path storagePath) {
        this.storagePath = storagePath;
        load();
    }

    private void load() {
        if (!Files.exists(storagePath)) {
            return;
        }
        try {
            String json = Files.readString(storagePath);
            JsonElement element = JsonParser.parseString(json);
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    JsonElement val = entry.getValue();
                    if (val.isJsonArray()) {
                        // Old format: List<RoomButton>
                        Type type = new TypeToken<List<RoomButton>>() {}.getType();
                        List<RoomButton> buttons = GSON.fromJson(val, type);
                        roomButtonsMap.put(entry.getKey(), new RoomNoteData("Unknown", buttons));
                    } else if (val.isJsonObject()) {
                        // New format: RoomNoteData
                        RoomNoteData rb = GSON.fromJson(val, RoomNoteData.class);
                        if (rb.getName() == null || rb.getName().isBlank()) {
                            rb.setName("Unknown");
                        }
                        roomButtonsMap.put(entry.getKey(), rb);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load room buttons from {}", storagePath, e);
        }
    }

    public synchronized void save() {
        try {
            // Map is already a TreeMap, so it will be sorted by roomId
            String json = GSON.toJson(roomButtonsMap);
            Files.writeString(storagePath, json);
        } catch (IOException e) {
            logger.error("Failed to save room buttons to {}", storagePath, e);
        }
    }

    public synchronized List<RoomButton> getButtonsForRoom(String roomId) {
        if (roomId == null) return Collections.emptyList();
        RoomNoteData rb = roomButtonsMap.get(roomId);
        return rb != null ? new ArrayList<>(rb.getButtons()) : Collections.emptyList();
    }

    public synchronized String getNotesForRoom(String roomId) {
        if (roomId == null) return "";
        RoomNoteData rb = roomButtonsMap.get(roomId);
        return (rb != null && rb.getNotes() != null) ? rb.getNotes() : "";
    }

    public synchronized void updateNotesForRoom(String roomId, String roomName, String notes) {
        if (roomId == null) return;
        String nameToUse = (roomName == null || roomName.isBlank()) ? "Unknown" : roomName;
        RoomNoteData rb = roomButtonsMap.computeIfAbsent(roomId, k -> new RoomNoteData(nameToUse, new ArrayList<>()));
        if (roomName != null && !roomName.isBlank()) {
            rb.setName(roomName);
        } else if (rb.getName() == null || rb.getName().isBlank()) {
            rb.setName("Unknown");
        }
        rb.setNotes(notes);
        if (rb.getNotes() == null && rb.getButtons().isEmpty()) {
            roomButtonsMap.remove(roomId);
        }
        save();
    }

    public synchronized void updateRoomName(String roomId, String roomName) {
        if (roomId == null) return;
        RoomNoteData rb = roomButtonsMap.get(roomId);
        if (rb != null) {
            String nameToUse = (roomName == null || roomName.isBlank()) ? "Unknown" : roomName;
            if (rb.getName() == null || rb.getName().isBlank() || "Unknown".equals(rb.getName()) ||
                    (roomName != null && !roomName.isBlank() && !roomName.equals(rb.getName()))) {

                if ("Unknown".equals(nameToUse) && rb.getName() != null && !rb.getName().isBlank() && !"Unknown".equals(rb.getName())) {
                    return;
                }

                rb.setName(nameToUse);
                save();
            }
        }
    }

    public synchronized void setButtonsForRoom(String roomId, String roomName, List<RoomButton> buttons) {
        if (roomId == null) return;
        if (buttons == null || buttons.isEmpty()) {
            roomButtonsMap.remove(roomId);
        } else {
            String nameToUse = (roomName == null || roomName.isBlank()) ? "Unknown" : roomName;
            roomButtonsMap.put(roomId, new RoomNoteData(nameToUse, buttons));
        }
        save();
    }

    public synchronized void addButton(String roomId, String roomName, RoomButton button) {
        if (roomId == null) return;
        String nameToUse = (roomName == null || roomName.isBlank()) ? "Unknown" : roomName;
        RoomNoteData rb = roomButtonsMap.computeIfAbsent(roomId, k -> new RoomNoteData(nameToUse, new ArrayList<>()));
        if (roomName != null && !roomName.isBlank()) {
            rb.setName(roomName);
        } else if (rb.getName() == null || rb.getName().isBlank()) {
            rb.setName("Unknown");
        }
        rb.getButtons().add(button);
        save();
    }

    public synchronized void updateButton(String roomId, String roomName, int index, RoomButton button) {
        RoomNoteData rb = roomButtonsMap.get(roomId);
        if (rb != null) {
            if (roomName != null && !roomName.isBlank()) {
                rb.setName(roomName);
            } else if (rb.getName() == null || rb.getName().isBlank()) {
                rb.setName("Unknown");
            }
            List<RoomButton> buttons = rb.getButtons();
            if (index >= 0 && index < buttons.size()) {
                buttons.set(index, button);
                save();
            }
        }
    }

    public synchronized void removeButton(String roomId, int index) {
        RoomNoteData rb = roomButtonsMap.get(roomId);
        if (rb != null) {
            List<RoomButton> buttons = rb.getButtons();
            if (index >= 0 && index < buttons.size()) {
                buttons.remove(index);
                if (buttons.isEmpty() && rb.getNotes() == null) {
                    roomButtonsMap.remove(roomId);
                }
                save();
            }
        }
    }

    public synchronized void moveButtonLeft(String roomId, int index) {
        RoomNoteData rb = roomButtonsMap.get(roomId);
        if (rb != null) {
            List<RoomButton> buttons = rb.getButtons();
            if (index > 0 && index < buttons.size()) {
                Collections.swap(buttons, index, index - 1);
                save();
            }
        }
    }

    public synchronized void moveButtonRight(String roomId, int index) {
        RoomNoteData rb = roomButtonsMap.get(roomId);
        if (rb != null) {
            List<RoomButton> buttons = rb.getButtons();
            if (index >= 0 && index < buttons.size() - 1) {
                Collections.swap(buttons, index, index + 1);
                save();
            }
        }
    }

    public synchronized void populateMissingNames(RoomMapService mapService) {
        boolean changed = false;
        for (Map.Entry<String, RoomNoteData> entry : roomButtonsMap.entrySet()) {
            RoomNoteData rb = entry.getValue();
            if (rb.getName() == null || rb.getName().isBlank() || "Unknown".equals(rb.getName())) {
                try {
                    RoomMapService.RoomLocation loc = mapService.lookupRoomLocation(entry.getKey());
                    if (loc != null) {
                        rb.setName(loc.roomShort());
                        changed = true;
                    } else if (rb.getName() == null || rb.getName().isBlank()) {
                        rb.setName("Unknown");
                        changed = true;
                    }
                } catch (Exception ignored) {
                    if (rb.getName() == null || rb.getName().isBlank()) {
                        rb.setName("Unknown");
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            save();
        }
    }
}
