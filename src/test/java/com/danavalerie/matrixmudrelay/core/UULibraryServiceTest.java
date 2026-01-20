package com.danavalerie.matrixmudrelay.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UULibraryServiceTest {

    @Test
    public void testNavigation() {
        UULibraryService service = UULibraryService.getInstance();
        
        // Initial state
        service.setRoomId("some other room");
        assertFalse(service.isActive());
        
        // Enter library
        service.setRoomId("UULibrary");
        assertTrue(service.isActive());
        assertEquals(UULibraryService.Orientation.NORTH, service.getOrientation());
        
        // Check initial coordinates (row 1, col 5)
        // x = (5 - 1) * 30 + 45 = 120 + 45 = 165
        // y = 4810 - (1 - 1) * 30 = 4810
        assertEquals(165, service.getX());
        assertEquals(4810, service.getY());
        
        // Move forward
        // Need to check if (9, 319) has a north exit.
        // If it does, m becomes 317.
        service.processCommand("fw");
        // We'll check if it moved or not based on our generated JSON.
        // Assuming (9, 319) has a north exit.
    }
    
    @Test
    public void testTurning() {
        UULibraryService service = UULibraryService.getInstance();
        service.setRoomId("UULibrary"); // Reset
        
        assertEquals(UULibraryService.Orientation.NORTH, service.getOrientation());
        
        service.processCommand("rt");
        assertEquals(UULibraryService.Orientation.EAST, service.getOrientation());
        
        service.processCommand("rt");
        assertEquals(UULibraryService.Orientation.SOUTH, service.getOrientation());
        
        service.processCommand("lt");
        assertEquals(UULibraryService.Orientation.EAST, service.getOrientation());
        
        service.processCommand("bw");
        assertEquals(UULibraryService.Orientation.WEST, service.getOrientation());
    }

    @Test
    public void testGetNextStepCommand() {
        UULibraryService service = UULibraryService.getInstance();
        service.setRoomId("None");
        service.setRoomId("UULibrary"); // Resets to row 1, col 5, North

        // Target 1,4. From 1,5 facing North, should be "lt"
        String cmd = service.getNextStepCommand(1, 4);
        assertEquals("lt", cmd);

        // Target 1,6. From 1,5 facing North, should be "rt"
        cmd = service.getNextStepCommand(1, 6);
        assertEquals("rt", cmd);

        // Move to 1,6 (facing East)
        service.processCommand("rt");
        assertEquals(1, service.getCurRow());
        assertEquals(6, service.getCurCol());
        assertEquals(UULibraryService.Orientation.EAST, service.getOrientation());

        // Target 1,7. From 1,6 facing East, should be "fw"
        cmd = service.getNextStepCommand(1, 7);
        assertEquals("fw", cmd);

        // Target 1,5. From 1,6 facing East, should be "bw"
        cmd = service.getNextStepCommand(1, 5);
        assertEquals("bw", cmd);

        // Target 2,6. From 1,6 facing East, should be "lt"
        cmd = service.getNextStepCommand(2, 6);
        assertEquals("lt", cmd);
    }

    @Test
    public void testWrapAroundNavigation() throws Exception {
        UULibraryService service = UULibraryService.getInstance();
        service.setRoomId("UULibrary");

        // Set position to (12, 1) facing NORTH using reflection
        java.lang.reflect.Field curRowField = UULibraryService.class.getDeclaredField("curRow");
        java.lang.reflect.Field curColField = UULibraryService.class.getDeclaredField("curCol");
        java.lang.reflect.Field orientationField = UULibraryService.class.getDeclaredField("orientation");
        curRowField.setAccessible(true);
        curColField.setAccessible(true);
        orientationField.setAccessible(true);

        curRowField.set(service, 12);
        curColField.set(service, 1);
        orientationField.set(service, UULibraryService.Orientation.NORTH);

        // Target Room 8 at (10, 8)
        // If 8-column wrap works, it should go WEST.
        // Facing NORTH, WEST is a left turn: "lt"
        String cmd = service.getNextStepCommand(10, 8);
        assertEquals("lt", cmd, "From (12, 1) to (10, 8) should be WEST (lt) because of wrap-around");
    }

    @Test
    public void testRow10Col1NoWestExit() throws Exception {
        UULibraryService service = UULibraryService.getInstance();
        
        java.lang.reflect.Field mazeField = UULibraryService.class.getDeclaredField("maze");
        mazeField.setAccessible(true);
        java.util.Map<String, UULibraryService.Room> maze = (java.util.Map<String, UULibraryService.Room>) mazeField.get(service);
        
        UULibraryService.Room room10_1 = maze.get("10,1");
        assertNotNull(room10_1, "Room (10, 1) should exist");
        assertFalse(room10_1.exits.contains("west"), "Room (10, 1) should NOT have a west exit");
        assertTrue(room10_1.exits.contains("north"), "Room (10, 1) should have north exit");
        assertTrue(room10_1.exits.contains("east"), "Room (10, 1) should have east exit");
        assertTrue(room10_1.exits.contains("south"), "Room (10, 1) should have south exit");
    }
}
