package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.core.data.ItemData;
import com.danavalerie.matrixmudrelay.core.data.NpcData;
import com.danavalerie.matrixmudrelay.core.data.RoomData;
import com.danavalerie.matrixmudrelay.core.data.ShopItem;
import com.danavalerie.matrixmudrelay.util.BackgroundSaver;
import com.danavalerie.matrixmudrelay.util.GsonUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class MapDataService {
    private static final Logger logger = LoggerFactory.getLogger(MapDataService.class);
    private static final Gson GSON = GsonUtils.getGson();

    private Map<String, RoomData> rooms = new TreeMap<>();
    private Map<String, ItemData> items = new TreeMap<>();
    private Map<String, NpcData> npcs = new TreeMap<>();

    public MapDataService() {
        loadAll();
    }

    private void loadAll() {
        rooms = loadJson("rooms.json", new TypeToken<TreeMap<String, RoomData>>() {}.getType());
        npcs = loadJson("npcs.json", new TypeToken<TreeMap<String, NpcData>>() {}.getType());

        deriveItems();
    }

    private void deriveItems() {
        items = new TreeMap<>();
        if (rooms != null) {
            rooms.values().forEach(r -> {
                if (r.getShopItems() != null) {
                    r.getShopItems().forEach(si -> addItemIfAbsent(si.getName()));
                }
            });
        }
        if (npcs != null) {
            npcs.values().forEach(n -> {
                if (n.getItems() != null) {
                    n.getItems().keySet().forEach(this::addItemIfAbsent);
                }
            });
        }
    }

    private void addItemIfAbsent(String itemName) {
        items.computeIfAbsent(itemName, k -> {
            ItemData id = new ItemData();
            id.setItemName(k);
            return id;
        });
    }

    private <T> T loadJson(String filename, Type type) {
        Path path = Path.of(filename);
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                T data = GSON.fromJson(json, type);
                if (data != null) return data;
            } catch (IOException e) {
                logger.error("Failed to load {}", filename, e);
            }
        }
        try {
            return (T) ((Class<?>) ((java.lang.reflect.ParameterizedType) type).getRawType()).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return null; // Should not happen with TreeMap
        }
    }

    public synchronized void saveAll() {
        saveJson("rooms.json", rooms);
        saveJson("npcs.json", npcs);
    }

    private void saveJson(String filename, Object data) {
        String json = GSON.toJson(data);
        BackgroundSaver.save(Path.of(filename), json);
    }

    public Map<String, RoomData> getRooms() { return rooms; }
    public Map<String, ItemData> getItems() { return items; }
    public Map<String, NpcData> getNpcs() { return npcs; }

    public RoomData getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public NpcData getNpc(String npcId) {
        return npcs.get(npcId);
    }

    public ItemData getItem(String itemName) {
        return items.get(itemName);
    }
}
