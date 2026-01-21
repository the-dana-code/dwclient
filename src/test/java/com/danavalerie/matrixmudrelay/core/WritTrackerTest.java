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

    @Test
    void testIssueReproduction() {
        WritTracker tracker = new WritTracker();
        String writText = "You read the official employment writ:\n" +
                "Written in carefully printed text:\n" +
                "\n" +
                "You are required to deliver:\n" +
                "[ ] an embroidered black velvet cape to the Morporkian teacher in the Ephebian Embassy\n" +
                "[ ] a theatrical red haired wig to the small bell boy at the Ankh Post Office entrance foyer on The Soake\n" +
                "[ ] two yellow tunics to Sandra at the Dysk Playwrights Guild office\n" +
                "[ ] a small paperback book to Mrs Green at the second hand shop on Zephire Street\n" +
                "\n" +
                "You have until Wed Jan 21 15:46:30 2026 [Bangkok] to complete this job.";

        tracker.ingest(writText);
        List<WritTracker.WritRequirement> reqs = tracker.getRequirements();

        assertEquals(4, reqs.size(), "Should have parsed 4 requirements");
        
        assertEquals("embroidered black velvet cape", reqs.get(0).item());
        assertEquals("the Morporkian teacher", reqs.get(0).npc());
        assertEquals("the Ephebian Embassy", reqs.get(0).locationName());

        assertEquals("theatrical red haired wig", reqs.get(1).item());
        assertEquals("the small bell boy", reqs.get(1).npc());
        assertEquals("the Ankh Post Office entrance foyer", reqs.get(1).locationName());
        assertEquals("on The Soake", reqs.get(1).locationSuffix());

        assertEquals(2, reqs.get(2).quantity());
        assertEquals("yellow tunics", reqs.get(2).item());
        assertEquals("Sandra", reqs.get(2).npc());
        assertEquals("the Dysk Playwrights Guild office", reqs.get(2).locationName());

        assertEquals("small paperback book", reqs.get(3).item());
        assertEquals("Mrs Green", reqs.get(3).npc());
        assertEquals("the second hand shop", reqs.get(3).locationName());
        assertEquals("on Zephire Street", reqs.get(3).locationSuffix());
    }
}
