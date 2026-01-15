package com.danavalerie.matrixmudrelay.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;

class StoreInventoryTrackerTest {

    @Test
    void testNormalizeItemNameViaFindMatch() {
        StoreInventoryTracker tracker = new StoreInventoryTracker();
        String inventory = "The following items are for sale:\n" +
                "  a: a sword for 10 gold\n" +
                "  b: an apple for 1 gold\n" +
                "  c: our very last shield for 100 gold\n" +
                "  d: pair of boots for 20 gold\n" +
                "  e: pairs of gloves for 15 gold\n" +
                "  f: one ring for 50 gold\n";
        
        tracker.ingest(inventory);
        
        // Basic match
        assertTrue(tracker.findMatch("a sword").isPresent());
        assertEquals("a sword", tracker.findMatch("a sword").get().name());
        
        // Stripping "a "
        assertTrue(tracker.findMatch("sword").isPresent());
        
        // Stripping "an "
        assertTrue(tracker.findMatch("apple").isPresent());
        
        // Stripping "our very last "
        assertTrue(tracker.findMatch("shield").isPresent());
        
        // Stripping "pair of "
        assertTrue(tracker.findMatch("boots").isPresent());
        
        // Stripping "pairs of "
        assertTrue(tracker.findMatch("gloves").isPresent());
        
        // Stripping "one "
        assertTrue(tracker.findMatch("ring").isPresent());
        
        // Combined stripping: "a pair of boots" -> "boots"
        // Wait, the inventory has "pair of boots". If I search for "a pair of boots":
        // normalize("a pair of boots") -> "boots"
        // normalize("pair of boots") -> "boots"
        // They should match.
        assertTrue(tracker.findMatch("a pair of boots").isPresent());
        
        // Case insensitivity
        assertTrue(tracker.findMatch("A SWORD").isPresent());
        
        // Extra whitespace
        assertTrue(tracker.findMatch("  a   sword  ").isPresent());
    }

    @Test
    void testSingularization() {
        StoreInventoryTracker tracker = new StoreInventoryTracker();
        String inventory = "The following items are for sale:\n" +
                "  a: sword for 10 gold\n" +
                "  b: potato for 1 gold\n" +
                "  c: steel xiphos for 50 gold\n";
        tracker.ingest(inventory);
        
        // "swords" singularized is "sword", matches "sword" in inventory
        assertTrue(tracker.findMatch("swords").isPresent());
        
        // "potatoes" singularized is "potato"
        assertTrue(tracker.findMatch("potatoes").isPresent());

        // "steel xiphoi" singularized is "steel xiphos"
        assertTrue(tracker.findMatch("steel xiphoi").isPresent());
    }
}
