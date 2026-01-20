package com.danavalerie.matrixmudrelay.config;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CharacterConfigTest {

    @Test
    void testCaseInsensitiveAccess() {
        BotConfig cfg = new BotConfig();
        BotConfig.CharacterConfig lesaConfig = new BotConfig.CharacterConfig();
        cfg.characters.put("Lesa", lesaConfig);

        // Currently, this will likely fail because it's a LinkedHashMap
        assertNotNull(cfg.characters.get("lesa"), "Should be able to get Lesa with lowercase 'lesa'");
        assertNotNull(cfg.characters.get("LESA"), "Should be able to get Lesa with uppercase 'LESA'");
    }

    @Test
    void testCasePreservation() {
        BotConfig cfg = new BotConfig();
        BotConfig.CharacterConfig lesaConfig = new BotConfig.CharacterConfig();
        cfg.characters.put("Lesa", lesaConfig);

        // Try to "add" lesa
        cfg.characters.computeIfAbsent("lesa", k -> new BotConfig.CharacterConfig());
        
        assertEquals(1, cfg.characters.size(), "Should not have added a second entry for 'lesa'");
        assertTrue(cfg.characters.containsKey("Lesa"), "Should contain Lesa");
        assertTrue(cfg.characters.containsKey("lesa"), "Should also 'contain' lesa (case-insensitive)");
        
        // Verify key set only contains the original casing
        assertEquals(1, cfg.characters.keySet().size(), "Key set should have size 1");
        assertEquals("Lesa", cfg.characters.keySet().iterator().next(), "The single key should be 'Lesa'");
    }

    @Test
    void testJsonDeserialization() throws Exception {
        String json = "{\"characters\": {\"Lesa\": {\"gpRateSamples\": [1, 2, 3]}}}";
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .registerTypeAdapter(new com.google.gson.reflect.TypeToken<java.util.Map<String, BotConfig.CharacterConfig>>() {}.getType(),
                        (com.google.gson.InstanceCreator<java.util.Map<String, BotConfig.CharacterConfig>>) type -> new com.danavalerie.matrixmudrelay.util.CaseInsensitiveLinkedHashMap<>())
                .create();

        BotConfig cfg = gson.fromJson(json, BotConfig.class);
        assertNotNull(cfg.characters);
        assertTrue(cfg.characters instanceof com.danavalerie.matrixmudrelay.util.CaseInsensitiveLinkedHashMap);
        
        // Test case-insensitive access on deserialized map
        assertNotNull(cfg.characters.get("lesa"));
        assertEquals(3, cfg.characters.get("LESA").gpRateSamples.size());
        
        // Test case preservation
        assertEquals("Lesa", cfg.characters.keySet().iterator().next());
    }
}
