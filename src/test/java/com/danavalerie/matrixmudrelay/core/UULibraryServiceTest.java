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
}
