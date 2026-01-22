package com.danavalerie.matrixmudrelay.core.data;

import java.util.Map;
import java.util.TreeMap;

public class NpcData {
    private String npcId;
    private int mapId;
    private String npcName;
    private String roomId;
    private Map<String, NpcItemData> items = new TreeMap<>();

    public NpcData() {}

    public String getNpcId() { return npcId; }
    public void setNpcId(String npcId) { this.npcId = npcId; }

    public int getMapId() { return mapId; }
    public void setMapId(int mapId) { this.mapId = mapId; }

    public String getNpcName() { return npcName; }
    public void setNpcName(String npcName) { this.npcName = npcName; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public Map<String, NpcItemData> getItems() { return items; }
    public void setItems(Map<String, NpcItemData> items) { this.items = items; }

    public static class NpcItemData {
        private String salePrice;
        private String specialNote;

        public NpcItemData() {}
        public NpcItemData(String salePrice, String specialNote) {
            this.salePrice = salePrice;
            this.specialNote = specialNote;
        }

        public String getSalePrice() { return salePrice; }
        public void setSalePrice(String salePrice) { this.salePrice = salePrice; }

        public String getSpecialNote() { return specialNote; }
        public void setSpecialNote(String specialNote) { this.specialNote = specialNote; }
    }
}
