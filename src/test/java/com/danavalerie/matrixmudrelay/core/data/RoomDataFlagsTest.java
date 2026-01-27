package com.danavalerie.matrixmudrelay.core.data;

import com.danavalerie.matrixmudrelay.util.GsonUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomDataFlagsTest {
    @Test
    void omitsFlagsWhenEmpty() {
        RoomData room = new RoomData("A", 1, 0, 0, "Start", "outside");
        room.setFlags(List.of());

        String json = GsonUtils.getGson().toJson(room);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        assertFalse(obj.has("flags"), "Flags should be omitted when empty");
    }

    @Test
    void serializesFlagsAsArray() {
        RoomData room = new RoomData("A", 1, 0, 0, "Start", "outside");
        room.setFlags(List.of("notp"));

        String json = GsonUtils.getGson().toJson(room);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        assertTrue(obj.has("flags"));
        assertTrue(obj.get("flags").isJsonArray());
        assertEquals("notp", obj.get("flags").getAsJsonArray().get(0).getAsString());
    }

    @Test
    void readsFlagsFromStringValue() {
        String json = "{\"roomId\":\"A\",\"Flags\":\"notp\"}";
        RoomData room = GsonUtils.getGson().fromJson(json, RoomData.class);

        assertTrue(room.hasFlag(RoomData.FLAG_NO_TELEPORT));
    }
}
