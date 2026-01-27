package com.danavalerie.matrixmudrelay.core.data;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class RoomData {
    private String roomId;
    private int mapId;
    private int xpos;
    private int ypos;
    private String roomShort;
    private String roomType;
    private Map<String, String> exits = new TreeMap<>();
    private List<ShopItem> shopItems = new ArrayList<>();
    public static final String FLAG_NO_TELEPORT = "notp";
    @SerializedName(value = "flags", alternate = {"Flags"})
    @JsonAdapter(FlagsAdapter.class)
    private List<String> flags;

    public RoomData() {}

    public RoomData(String roomId, int mapId, int xpos, int ypos, String roomShort, String roomType) {
        this.roomId = roomId;
        this.mapId = mapId;
        this.xpos = xpos;
        this.ypos = ypos;
        this.roomShort = roomShort;
        this.roomType = roomType;
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public int getMapId() { return mapId; }
    public void setMapId(int mapId) { this.mapId = mapId; }

    public int getXpos() { return xpos; }
    public void setXpos(int xpos) { this.xpos = xpos; }

    public int getYpos() { return ypos; }
    public void setYpos(int ypos) { this.ypos = ypos; }

    public String getRoomShort() { return roomShort; }
    public void setRoomShort(String roomShort) { this.roomShort = roomShort; }

    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }

    public Map<String, String> getExits() { return exits; }
    public void setExits(Map<String, String> exits) { this.exits = exits; }

    public List<ShopItem> getShopItems() { return shopItems; }
    public void setShopItems(List<ShopItem> shopItems) { this.shopItems = shopItems; }

    public List<String> getFlags() { return flags; }
    public void setFlags(List<String> flags) { this.flags = normalizeFlags(flags); }

    public boolean hasFlag(String flag) {
        if (flags == null || flag == null || flag.isBlank()) {
            return false;
        }
        for (String entry : flags) {
            if (entry != null && entry.equalsIgnoreCase(flag)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> normalizeFlags(List<String> flags) {
        if (flags == null || flags.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        for (String flag : flags) {
            if (flag != null) {
                String trimmed = flag.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        return normalized.isEmpty() ? null : normalized;
    }

    static final class FlagsAdapter extends TypeAdapter<List<String>> {
        @Override
        public void write(JsonWriter out, List<String> value) throws IOException {
            List<String> normalized = normalizeFlags(value);
            if (normalized == null) {
                out.nullValue();
                return;
            }
            out.beginArray();
            for (String flag : normalized) {
                out.value(flag);
            }
            out.endArray();
        }

        @Override
        public List<String> read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            if (token == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            List<String> result = new ArrayList<>();
            if (token == JsonToken.STRING) {
                String flag = in.nextString();
                if (flag != null && !flag.isBlank()) {
                    result.add(flag.trim());
                }
                return normalizeFlags(result);
            }
            if (token == JsonToken.BEGIN_ARRAY) {
                in.beginArray();
                while (in.hasNext()) {
                    if (in.peek() == JsonToken.STRING) {
                        String flag = in.nextString();
                        if (flag != null && !flag.isBlank()) {
                            result.add(flag.trim());
                        }
                    } else {
                        in.skipValue();
                    }
                }
                in.endArray();
                return normalizeFlags(result);
            }
            in.skipValue();
            return null;
        }
    }
}
