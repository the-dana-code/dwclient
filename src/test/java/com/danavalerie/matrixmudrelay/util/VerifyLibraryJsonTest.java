package com.danavalerie.matrixmudrelay.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class VerifyLibraryJsonTest {
    static class Room {
        int row, col;
        List<String> exits;
        boolean table;
        int number;
    }

    @Test
    public void testJsonContent() throws Exception {
        Gson gson = new Gson();
        List<Room> rooms = gson.fromJson(new InputStreamReader(Files.newInputStream(Paths.get("uu_library.json"))), new TypeToken<List<Room>>() {}.getType());
        
        Map<String, Room> map = new HashMap<>();
        for (Room r : rooms) {
            map.put(r.row + "," + r.col, r);
        }

        // (1,1) has exits west, north, east only, has no table, and no room number
        Room r11 = map.get("1,1");
        assertNotNull(r11, "Room (1,1) not found");
        assertTrue(r11.exits.contains("west"), "(1,1) should have west exit");
        assertTrue(r11.exits.contains("north"), "(1,1) should have north exit");
        assertTrue(r11.exits.contains("east"), "(1,1) should have east exit");
        assertFalse(r11.exits.contains("south"), "(1,1) should NOT have south exit");
        assertEquals(3, r11.exits.size(), "(1,1) should have exactly 3 exits");
        assertFalse(r11.table, "(1,1) should have no table");
        assertEquals(0, r11.number, "(1,1) should have no room number");

        // (2,6) has exits west, south, east only, has no table, and has room number "1"
        Room r26 = map.get("2,6");
        assertNotNull(r26, "Room (2,6) not found");
        assertTrue(r26.exits.contains("west"), "(2,6) should have west exit");
        assertTrue(r26.exits.contains("south"), "(2,6) should have south exit");
        assertTrue(r26.exits.contains("east"), "(2,6) should have east exit");
        assertFalse(r26.exits.contains("north"), "(2,6) should NOT have north exit");
        assertEquals(3, r26.exits.size(), "(2,6) should have exactly 3 exits");
        assertFalse(r26.table, "(2,6) should have no table");
        assertEquals(1, r26.number, "(2,6) should have room number 1");

        // (3,3) has exits west, south, east, north (i.e. all 4 exits), HAS a table, and has no room number
        Room r33 = map.get("3,3");
        assertNotNull(r33, "Room (3,3) not found");
        assertTrue(r33.exits.contains("west"), "(3,3) should have west exit");
        assertTrue(r33.exits.contains("south"), "(3,3) should have south exit");
        assertTrue(r33.exits.contains("east"), "(3,3) should have east exit");
        assertTrue(r33.exits.contains("north"), "(3,3) should have north exit");
        assertEquals(4, r33.exits.size());
        assertTrue(r33.table, "(3,3) should have a table");
        assertEquals(0, r33.number, "(3,3) should have no room number");

        // (1,3) has exits east and west only, no table, no special number
        Room r13 = map.get("1,3");
        assertNotNull(r13, "Room (1,3) not found");
        assertTrue(r13.exits.contains("east"), "(1,3) should have east exit");
        assertTrue(r13.exits.contains("west"), "(1,3) should have west exit");
        assertFalse(r13.exits.contains("north"), "(1,3) should NOT have north exit");
        assertFalse(r13.exits.contains("south"), "(1,3) should NOT have south exit");
        assertEquals(2, r13.exits.size(), "(1,3) should have exactly 2 exits");
        assertFalse(r13.table, "(1,3) should have no table");
        assertEquals(0, r13.number, "(1,3) should have no room number");
        
        // (1,5) exists (starting point)
        assertNotNull(map.get("1,5"), "Starting room (1,5) not found");
    }
}
