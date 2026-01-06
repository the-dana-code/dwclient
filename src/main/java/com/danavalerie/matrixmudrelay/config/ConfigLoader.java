package com.danavalerie.matrixmudrelay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigLoader() {}

    public static BotConfig load(Path path) throws IOException {
        String json = Files.readString(path);
        BotConfig cfg = GSON.fromJson(json, BotConfig.class);
        validate(cfg);
        return cfg;
    }

    public static void save(Path path, BotConfig cfg) throws IOException {
        String json = GSON.toJson(cfg);
        Files.writeString(path, json);
    }

    private static void validate(BotConfig cfg) {
        require(cfg.mud.host, "mud.host");
        if (cfg.mud.port <= 0 || cfg.mud.port > 65535) throw new IllegalArgumentException("mud.port invalid");
    }

    private static void require(String s, String name) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Missing required config field: " + name);
    }
}
