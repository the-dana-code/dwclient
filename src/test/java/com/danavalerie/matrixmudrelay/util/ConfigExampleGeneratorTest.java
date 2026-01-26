package com.danavalerie.matrixmudrelay.util;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.config.TimerDataAdapter;
import com.google.gson.Gson;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigExampleGeneratorTest {
    @Test
    void configExampleMatchesGenerator() throws Exception {
        Gson gson = GsonUtils.getDefaultBuilder()
                .registerTypeAdapter(ClientConfig.TimerData.class, new TimerDataAdapter())
                .registerTypeAdapter(new TypeToken<Map<String, ClientConfig.CharacterConfig>>() {}.getType(),
                        (InstanceCreator<Map<String, ClientConfig.CharacterConfig>>) type -> new CaseInsensitiveLinkedHashMap<>())
                .create();

        Path repoRoot = Paths.get("").toAbsolutePath();
        Path configPath = repoRoot.resolve("config.json");
        Path examplePath = repoRoot.resolve("config-example.json");

        ClientConfig config = gson.fromJson(Files.readString(configPath), ClientConfig.class);
        config.characters.clear();

        // Add a sample character to show the new character-specific settings
        ClientConfig.CharacterConfig sampleChar = new ClientConfig.CharacterConfig();
        sampleChar.useTeleports = true;
        config.characters.put("SampleCharacter", sampleChar);

        if (config.ui != null) {
            config.ui.mudMapSplitRatio = null;
            config.ui.mapNotesSplitRatio = null;
            config.ui.chitchatTimerSplitRatio = null;
            config.ui.outputSplitRatio = null;
            config.ui.timerColumnWidths = null;
        }

        JsonElement actual = JsonParser.parseString(gson.toJson(config));
        JsonElement expected = JsonParser.parseString(Files.readString(examplePath));

        assertEquals(expected, actual, "config-example.json is out of date with ConfigExampleGenerator output");
    }
}
