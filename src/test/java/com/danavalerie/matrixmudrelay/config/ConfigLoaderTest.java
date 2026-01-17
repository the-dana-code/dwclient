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

    @Test
    void loadConfigWithArrayTarget(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.json");
        String json = """
            {
              "mud": { "host": "localhost", "port": 4242 },
              "bookmarks": [
                { "name": "Mended Drum", "target": [1, 718, 802] }
              ],
              "teleports": {
                "lesa": {
                  "reliable": true,
                  "locations": [
                    { "command": "tp am-fiddleys", "target": [1, 774, 323] }
                  ]
                }
              }
            }
            """;
        Files.writeString(configPath, json);

        BotConfig cfg = ConfigLoader.load(configPath);
        assertNotNull(cfg);
        assertEquals(1, cfg.bookmarks.size());
        assertArrayEquals(new int[]{1, 718, 802}, cfg.bookmarks.get(0).target);
        
        var lesaTeleports = cfg.teleports.get("lesa");
        assertNotNull(lesaTeleports);
        assertEquals(1, lesaTeleports.locations.size());
        assertArrayEquals(new int[]{1, 774, 323}, lesaTeleports.locations.get(0).target);
    }

    @Test
    void loadConfigAutomaticallyCopiesExampleIfMissing(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.json");
        Path examplePath = tempDir.resolve("config-example.json");
        
        String exampleJson = """
            {
              "mud": { "host": "example.com", "port": 4242 }
            }
            """;
        Files.writeString(examplePath, exampleJson);
        
        // Ensure config.json does not exist
        assertFalse(Files.exists(configPath));
        
        // We need to tell ConfigLoader where the example is, 
        // OR ConfigLoader should look for it relative to the configPath.
        // The requirement says: "if it doesn't exist, then make a copy of config-example.json as config.json automatically"
        
        BotConfig cfg = ConfigLoader.load(configPath);
        
        assertTrue(Files.exists(configPath), "config.json should have been created");
        assertEquals("example.com", cfg.mud.host);
        assertEquals(exampleJson, Files.readString(configPath));
    }
}
