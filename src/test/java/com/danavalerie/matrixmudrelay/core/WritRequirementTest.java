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
        WritTracker.WritRequirement req = new WritTracker.WritRequirement(1, "item", "npc", "loc", null);
        // locationDisplay already handles null locationSuffix
        assertEquals("loc", req.locationDisplay());
    }
}
