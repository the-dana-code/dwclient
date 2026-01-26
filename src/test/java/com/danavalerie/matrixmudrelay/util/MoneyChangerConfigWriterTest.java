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

package com.danavalerie.matrixmudrelay.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MoneyChangerConfigWriterTest {

    @TempDir
    Path tempDir;

    private Path originalConfigPath = Path.of("config.json");
    private Path tempConfigPath;

    @BeforeEach
    void setUp() throws IOException {
        BackgroundSaver.resetForTests();
        tempConfigPath = tempDir.resolve("config.json");
        if (Files.exists(originalConfigPath)) {
            Files.copy(originalConfigPath, tempConfigPath);
        } else {
            // Create a minimal config if it doesn't exist
            Files.writeString(tempConfigPath, "{\"mud\":{\"host\":\"localhost\",\"port\":4242},\"bookmarks\":[]}");
        }
        // Redirect config path in some way? 
        // MoneyChangerConfigWriter uses Path.of("config.json") hardcoded.
        // I should probably make it configurable or run it in a way that it sees the temp file.
    }

    @Test
    void testUpdateExistingBookmark() throws Exception {
        String exchangeName = "Existing Exchange";
        String nativeCurrency = "Native";
        String[] additionalCurrencies = {"Other"};
        String roomId1 = "room1";
        String roomId2 = "room2";

        // First add it
        MoneyChangerConfigWriter.updateConfig(tempConfigPath, exchangeName, nativeCurrency, additionalCurrencies, roomId1);
        
        // Then update it with different room ID
        MoneyChangerConfigWriter.updateConfig(tempConfigPath, exchangeName, nativeCurrency, additionalCurrencies, roomId2);

        ClientConfig config = ConfigLoader.load(tempConfigPath).clientConfig();
        
        long count = config.bookmarks.stream()
                .filter(b -> b.name.contains("Existing Exchange"))
                .count();
        assertEquals(4, count, "Should still only have 4 bookmarks for this exchange");
        
        assertBookmarkExists(config, "Money Changers/To/Native/From Other/Existing Exchange", roomId2);
    }

    @Test
    void testUpdateConfig() throws Exception {
        String exchangeName = "Test Exchange";
        String nativeCurrency = "Native";
        String[] additionalCurrencies = {"Other1", "Other2"};
        String roomId = "room123";

        MoneyChangerConfigWriter.updateConfig(tempConfigPath, exchangeName, nativeCurrency, additionalCurrencies, roomId);

        ClientConfig config = ConfigLoader.load(tempConfigPath).clientConfig();
        
        // 2 additional currencies * 4 variants each = 8 new bookmarks
        // But we might have started with some bookmarks if we copied original config.
        // Let's check for the specific bookmarks we expected.
        
        assertBookmarkExists(config, "Money Changers/To/Native/From Other1/Test Exchange", roomId);
        assertBookmarkExists(config, "Money Changers/To/Other1/From Native/Test Exchange", roomId);
        assertBookmarkExists(config, "Money Changers/From/Native/To Other1/Test Exchange", roomId);
        assertBookmarkExists(config, "Money Changers/From/Other1/To Native/Test Exchange", roomId);
        
        assertBookmarkExists(config, "Money Changers/To/Native/From Other2/Test Exchange", roomId);
        assertBookmarkExists(config, "Money Changers/To/Other2/From Native/Test Exchange", roomId);
        assertBookmarkExists(config, "Money Changers/From/Native/To Other2/Test Exchange", roomId);
        assertBookmarkExists(config, "Money Changers/From/Other2/To Native/Test Exchange", roomId);
    }

    private void assertBookmarkExists(ClientConfig config, String name, String roomId) {
        boolean found = false;
        for (ClientConfig.Bookmark b : config.bookmarks) {
            if (name.equals(b.name) && roomId.equals(b.roomId)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Bookmark not found: " + name);
    }
}
