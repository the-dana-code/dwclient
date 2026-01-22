package com.danavalerie.matrixmudrelay.core.data;

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
    private Map<String, String> shopItems = new TreeMap<>();

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

    public Map<String, String> getShopItems() { return shopItems; }
    public void setShopItems(Map<String, String> shopItems) { this.shopItems = shopItems; }
}
