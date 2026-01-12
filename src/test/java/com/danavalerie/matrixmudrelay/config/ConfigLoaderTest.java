package com.danavalerie.matrixmudrelay.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @Test
    void loadRoutesWithArrayTarget(@TempDir Path tempDir) throws Exception {
        Path routesPath = tempDir.resolve("delivery-routes.json");
        String json = """
            [
              {
                "npc": "Mardi",
                "location": "Masqueparade on Phedre Road",
                "target": [7, 328, 68]
              }
            ]
            """;
        Files.writeString(routesPath, json);

        DeliveryRouteMappings mappings = ConfigLoader.loadRoutes(routesPath);
        assertNotNull(mappings);
        assertEquals(1, mappings.routes().size());
        
        var route = mappings.findRoutePlan("Mardi", "Masqueparade on Phedre Road");
        assertTrue(route.isPresent());
        assertEquals(7, route.get().target().mapId());
        assertEquals(328, route.get().target().x());
        assertEquals(68, route.get().target().y());
    }
}
