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
        ClientConfig example = new ClientConfig();

        // MUD settings
        example.mud.host = config.mud.host;
        example.mud.port = config.mud.port;
        example.mud.charset = config.mud.charset;
        example.mud.connectTimeoutMs = config.mud.connectTimeoutMs;

        // UI settings - explicitly null to keep out of example
        example.ui = null;

        // Bookmarks
        example.bookmarks.addAll(config.bookmarks);

        JsonElement actual = JsonParser.parseString(gson.toJson(example));
        JsonElement expected = JsonParser.parseString(Files.readString(examplePath));

        assertEquals(expected, actual, "config-example.json is out of date with ConfigExampleGenerator output");
    }
}
