package com.danavalerie.matrixmudrelay.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UULibraryMoveTest {
    private UULibraryService service;

    @BeforeEach
    void setUp() {
        service = UULibraryService.getInstance();
        service.reset();
        service.setRoomId("UULibrary");
    }

    @Test
    void testMoveBlockedByWallDoesNotReorient() {
        // At (2,1), exits are ["north", "south"]
        // Set orientation to NORTH
        service.setState(2, 1, UULibraryService.Orientation.NORTH);
        
        // Try to move left (WEST). There is no WEST exit at (2,1).
        service.processCommand("left");
        
        // Orientation should still be NORTH, position still (2,1)
        assertEquals(UULibraryService.Orientation.NORTH, service.getOrientation(), "Orientation should not change if move is blocked by wall");
        assertEquals(2, service.getCurRow());
        assertEquals(1, service.getCurCol());
        
        // Try to move right (EAST). There is no EAST exit at (2,1).
        service.processCommand("right");
        assertEquals(UULibraryService.Orientation.NORTH, service.getOrientation(), "Orientation should not change if move is blocked by wall");
        
        // Try to move backward (SOUTH). (2,1) HAS a SOUTH exit.
        // So this SHOULD work.
        service.processCommand("backward");
        assertEquals(UULibraryService.Orientation.SOUTH, service.getOrientation(), "Orientation should change if move is possible");
        assertEquals(1, service.getCurRow());
        assertEquals(1, service.getCurCol());
    }

    @Test
    void testMoveBlockedByBarrierDoesNotReorient() {
        // At (1,5), exits are ["east", "west"]
        service.setState(1, 5, UULibraryService.Orientation.NORTH);
        
        // Add a barrier to the WEST
        service.addBarrier(UULibraryService.Orientation.WEST);
        
        // Try to move left (WEST). There is an exit but it's blocked by a barrier.
        service.processCommand("left");
        
        // Orientation should still be NORTH
        assertEquals(UULibraryService.Orientation.NORTH, service.getOrientation(), "Orientation should not change if move is blocked by barrier");
        assertEquals(1, service.getCurRow());
        assertEquals(5, service.getCurCol());
        
        // Try to move right (EAST). No barrier there, should work.
        service.processCommand("right");
        assertEquals(UULibraryService.Orientation.EAST, service.getOrientation());
        assertEquals(1, service.getCurRow());
        assertEquals(6, service.getCurCol());
    }
}
