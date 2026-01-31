package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.util.GsonUtils;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WritRequirementTest {
    private final Gson gson = GsonUtils.getGson();

    @Test
    void testRestorationFromNullFields() {
        // This JSON simulates what happens when empty strings are converted to null
        String json = "{\"quantity\":1,\"item\":null,\"npc\":null,\"locationName\":null,\"locationSuffix\":null}";
        WritTracker.WritRequirement req = gson.fromJson(json, WritTracker.WritRequirement.class);

        assertNotNull(req, "Requirement should not be null");
        
        // Before fix, these will be null
        // We expect them to be non-null after fix
        assertNotNull(req.item(), "item should not be null");
        assertNotNull(req.npc(), "npc should not be null");
        assertNotNull(req.locationName(), "locationName should not be null");
        assertNotNull(req.locationSuffix(), "locationSuffix should not be null");
    }

    @Test
    void testLocationDisplayWithNulls() {
        WritTracker.WritRequirement req = new WritTracker.WritRequirement(1, "item", "npc", "loc", null, null, null);
        // locationDisplay already handles null locationSuffix
        assertEquals("loc", req.locationDisplay());
    }

    @Test
    void testTriggerOnlyAtTheEnd() {
        WritTracker tracker = new WritTracker();
        String text = "read writ\n" +
                "You read the official employment writ:\n" +
                "Written in carefully printed text:\n" +
                "\n" +
                "You are required to deliver:\n" +
                "[ ] a braided leather belt to Nathan at the Curio Shop on Artorollo Alley\n" +
                "[ ] a blue and gold snake anklet to Marvin at Marvin's Mantels on Street of Cunning Artificers\n" +
                "[ ] a strawberry cheese cake to Ulora Icta at the Laughing Falafel, All Nite Grocery\n" +
                "[ ] a beribboned hairpin to Mr Graves at the premises of Grangrid, Graves, and Descendants on Chrononhotonthologos Street\n" +
                "\n" +
                "You have until Sat Jan 31 20:37:03 2026 to complete this job.";

        String[] lines = text.split("\n");
        int updateCount = 0;
        for (String line : lines) {
            if (tracker.ingest(line)) {
                updateCount++;
            }
        }

        assertEquals(1, updateCount, "Should only trigger update once");
        var requirements = tracker.getRequirements();
        assertEquals(4, requirements.size(), "Should have 4 requirements");
        assertEquals(0, requirements.get(0).originalIndex());
        assertEquals(1, requirements.get(1).originalIndex());
        assertEquals(2, requirements.get(2).originalIndex());
        assertEquals(3, requirements.get(3).originalIndex());
    }
}
