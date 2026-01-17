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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigLoader() {}

    public static BotConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Path examplePath = path.resolveSibling("config-example.json");
            if (Files.exists(examplePath)) {
                Files.copy(examplePath, path);
            }
        }
        String json = Files.readString(path);
        BotConfig cfg = GSON.fromJson(json, BotConfig.class);
        validate(cfg);
        return cfg;
    }

    public static DeliveryRouteMappings loadRoutes(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new DeliveryRouteMappings(List.of());
        }
        String json = Files.readString(path);
        Type type = new TypeToken<List<DeliveryRouteMappings.RouteEntry>>() {}.getType();
        List<DeliveryRouteMappings.RouteEntry> entries = GSON.fromJson(json, type);
        return new DeliveryRouteMappings(entries);
    }

    public static void saveRoutes(Path path, DeliveryRouteMappings routes) throws IOException {
        String json = GSON.toJson(routes.routes());
        Files.writeString(path, json);
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

