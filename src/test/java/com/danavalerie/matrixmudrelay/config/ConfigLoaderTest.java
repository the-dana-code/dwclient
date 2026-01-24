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

package com.danavalerie.matrixmudrelay.config;

import com.danavalerie.matrixmudrelay.util.BackgroundSaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {
    @AfterEach
    void tearDown() {
        BackgroundSaver.waitForIdle();
    }

    @Test
    void loadRoutesWithRoomId(@TempDir Path tempDir) throws Exception {
        Path routesPath = tempDir.resolve("delivery-routes.json");
        String json = """
            [
              {
                "npc": "Mardi",
                "location": "Masqueparade on Phedre Road",
                "roomId": "mardi_room_id"
              }
            ]
            """;
        Files.writeString(routesPath, json);

        DeliveryRouteMappings mappings = ConfigLoader.loadRoutes(routesPath);
        assertNotNull(mappings);
        assertEquals(1, mappings.routes().size());
        
        var route = mappings.findRoutePlan("Mardi", "Masqueparade on Phedre Road");
        assertTrue(route.isPresent());
        assertEquals("mardi_room_id", route.get().target().roomId());
    }

    @Test
    void loadConfigWithRoomId(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.json");
        String json = """
            {
              "mud": { "host": "localhost", "port": 4242 },
              "bookmarks": [
                { "name": "Mended Drum", "roomId": "drum_id" }
              ],
              "teleports": {
                "lesa": {
                  "reliable": true,
                  "locations": [
                    { "command": "tp am-fiddleys", "roomId": "fiddleys_id" }
                  ]
                }
              }
            }
            """;
        Files.writeString(configPath, json);

        BotConfig cfg = ConfigLoader.load(configPath);
        assertNotNull(cfg);
        assertEquals(1, cfg.bookmarks.size());
        assertEquals("drum_id", cfg.bookmarks.get(0).roomId);

        // After migration, it should be in characters map
        var lesaConfig = cfg.characters.get("lesa");
        assertNotNull(lesaConfig);
        assertNotNull(lesaConfig.teleports);
        assertEquals(1, lesaConfig.teleports.locations.size());
        assertEquals("fiddleys_id", lesaConfig.teleports.locations.get(0).roomId);

        // Old field should be null
        assertNull(cfg.teleports);

        // Migration causes an asynchronous save. We need to wait for it.
        // We can't easily get the Future here without changing the load() API,
        // but we can submit a dummy task to the same single-threaded executor and wait for it.
        // Or better, we can expose a wait method in BackgroundSaver for testing.
        
        // For now, let's just try to wait a bit or use a more robust way if I add it to BackgroundSaver.
        BackgroundSaver.waitForIdle();

        // Verify it was saved back in the new format
        String savedJson = Files.readString(configPath);
        assertTrue(savedJson.contains("\"characters\":"));
        assertTrue(savedJson.contains("\"lesa\": {"));
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

    @Test
    void loadConfigWithNestedBookmarks(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.json");
        String json = """
            {
              "mud": { "host": "localhost", "port": 4242 },
              "bookmarks": [
                { "name": "Mended Drum", "roomId": "drum_id" },
                {
                  "name": "Witches",
                  "bookmarks": [
                    { "name": "Granny Weatherwax", "roomId": "weatherwax_id" }
                  ]
                }
              ]
            }
            """;
        Files.writeString(configPath, json);

        BotConfig cfg = ConfigLoader.load(configPath);
        assertNotNull(cfg);
        // Should be flattened to 2 bookmarks
        assertEquals(2, cfg.bookmarks.size());
        assertEquals("Mended Drum", cfg.bookmarks.get(0).name);
        assertEquals("drum_id", cfg.bookmarks.get(0).roomId);

        assertEquals("Witches/Granny Weatherwax", cfg.bookmarks.get(1).name);
        assertEquals("weatherwax_id", cfg.bookmarks.get(1).roomId);
        assertNull(cfg.bookmarks.get(1).bookmarks);
    }
}

