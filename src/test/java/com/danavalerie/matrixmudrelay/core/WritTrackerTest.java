package com.danavalerie.matrixmudrelay.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WritTrackerTest {

    @Test
    void testParseWritWithPluralItem() {
        WritTracker tracker = new WritTracker();
        String writText = "You read the official employment writ:\n" +
                "[ ] two oil lamps to The Storekeeper at Sator Square\n" +
                "You have until tomorrow to complete this.";
        
        tracker.ingest(writText);
        List<WritTracker.WritRequirement> reqs = tracker.getRequirements();
        
        assertEquals(1, reqs.size());
        WritTracker.WritRequirement req = reqs.get(0);
        assertEquals(2, req.quantity());
        assertEquals("oil lamps", req.item());
        assertEquals("The Storekeeper", req.npc());
        assertEquals("Sator Square", req.locationName());
    }

    @Test
    void testParseWritWithSingularItem() {
        WritTracker tracker = new WritTracker();
        String writText = "You read the official employment writ:\n" +
                "[ ] a oil lamp to The Storekeeper at Sator Square\n" +
                "You have until tomorrow to complete this.";
        
        tracker.ingest(writText);
        List<WritTracker.WritRequirement> reqs = tracker.getRequirements();
        
        assertEquals(1, reqs.size());
        WritTracker.WritRequirement req = reqs.get(0);
        assertEquals(1, req.quantity());
        assertEquals("oil lamp", req.item());
    }

    @Test
    void testParseWritWithMultipleRequirements() {
        WritTracker tracker = new WritTracker();
        String writText = "You read the official employment writ:\n" +
                "[ ] 2 packets of tea to Mrs. Gamp at The Mended Drum\n" +
                "[x] one bottle of wine to Nobby at The Watch House\n" +
                "You have until next week to complete this.";
        
        tracker.ingest(writText);
        List<WritTracker.WritRequirement> reqs = tracker.getRequirements();
        
        assertEquals(2, reqs.size());
        assertEquals(2, reqs.get(0).quantity());
        assertEquals("packets of tea", reqs.get(0).item());
        assertEquals(1, reqs.get(1).quantity());
        assertEquals("bottle of wine", reqs.get(1).item());
    }
    @Test
    void testParseWritWithAlternativeStart() {
        WritTracker tracker = new WritTracker();
        String writText = "Written in carefully printed text:\n" +
                "[ ] a oil lamp to The Storekeeper at Sator Square\n" +
                "You have until tomorrow to complete this.";

        tracker.ingest(writText);
        List<WritTracker.WritRequirement> reqs = tracker.getRequirements();

        assertEquals(1, reqs.size());
        WritTracker.WritRequirement req = reqs.get(0);
        assertEquals(1, req.quantity());
        assertEquals("oil lamp", req.item());
    }
}
