/*
 * Lesa's Discworld MUD client.
 * Copyright (C) 2026 Dana Reese
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

    @Test
    void testPrepositionalPhraseMatching() {
        StoreInventoryTracker tracker = new StoreInventoryTracker();
        String inventory = "The following items are for sale:\n" +
                "   A: a small blue flag with bolognas for A$2 (five left).\n" +
                "   B: a bologna for A$1.50 (seventy left).\n";
        tracker.ingest(inventory);

        // This is what the writ has: "2 small blue flags with bolognas"
        String itemFromWrit = "small blue flags with bolognas";

        assertTrue(tracker.findMatch(itemFromWrit).isPresent(), "Should match plural form via variants");
        assertTrue(tracker.findMatch("small blue flag with bolognas").isPresent(), "Should match singular form");
        
        // Test "of"
        inventory = "The following items are for sale:\n" +
                "  A: box of chocolates for 5 gold\n";
        tracker.ingest(inventory);
        assertTrue(tracker.findMatch("boxes of chocolates").isPresent());
    }
}

