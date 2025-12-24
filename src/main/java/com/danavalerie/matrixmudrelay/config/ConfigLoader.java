package com.danavalerie.matrixmudrelay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {
    private static final Gson GSON = new GsonBuilder().create();

    private ConfigLoader() {}

    public static BotConfig load(Path path) throws IOException {
        String json = Files.readString(path);
        BotConfig cfg = GSON.fromJson(json, BotConfig.class);
        validate(cfg);
        return cfg;
    }

    private static void validate(BotConfig cfg) {
        require(cfg.matrix.homeserverUrl, "matrix.homeserverUrl");
        require(cfg.matrix.accessToken, "matrix.accessToken (SENSITIVE)");
        require(cfg.matrix.userId, "matrix.userId");
        require(cfg.matrix.room, "matrix.room");
        require(cfg.matrix.controllingUserId, "matrix.controllingUserId");

        require(cfg.mud.host, "mud.host");
        if (cfg.mud.port <= 0 || cfg.mud.port > 65535) throw new IllegalArgumentException("mud.port invalid");

        if (!cfg.matrix.userId.startsWith("@")) throw new IllegalArgumentException("matrix.userId must start with '@'");
        if (!cfg.matrix.controllingUserId.startsWith("@")) throw new IllegalArgumentException("matrix.controllingUserId must start with '@'");
        if (!(cfg.matrix.room.startsWith("!") || cfg.matrix.room.startsWith("#"))) {
            throw new IllegalArgumentException("matrix.room must be a roomId (!...) or alias (#...)");
        }

        if (cfg.matrix.syncTimeoutMs < 0 || cfg.matrix.syncTimeoutMs > 120000) {
            throw new IllegalArgumentException("matrix.syncTimeoutMs out of range");
        }
        if (cfg.retry.initialBackoffMs < 0 || cfg.retry.maxBackoffMs < cfg.retry.initialBackoffMs) {
            throw new IllegalArgumentException("retry backoff invalid");
        }
    }

    private static void require(String s, String name) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Missing required config field: " + name);
    }
}
