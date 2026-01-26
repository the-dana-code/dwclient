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
import com.danavalerie.matrixmudrelay.util.CaseInsensitiveLinkedHashMap;
import com.danavalerie.matrixmudrelay.util.GsonUtils;
import com.google.gson.Gson;
import com.google.gson.InstanceCreator;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConfigLoader {
    private static final Gson GSON = GsonUtils.getDefaultBuilder()
            .registerTypeAdapter(ClientConfig.TimerData.class, new TimerDataAdapter())
            .registerTypeAdapter(new TypeToken<Map<String, ClientConfig.CharacterConfig>>() {}.getType(),
                    (InstanceCreator<Map<String, ClientConfig.CharacterConfig>>) type -> new CaseInsensitiveLinkedHashMap<>())
            .create();

    private ConfigLoader() {}

    public static ClientConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Path examplePath = path.resolveSibling("config-example.json");
            if (Files.exists(examplePath)) {
                Files.copy(examplePath, path);
            }
        }
        String json = Files.readString(path);
        ClientConfig cfg = GSON.fromJson(json, ClientConfig.class);

        boolean migrated = false;
        if (cfg.teleports != null && !cfg.teleports.isEmpty()) {
            for (Map.Entry<String, ClientConfig.CharacterTeleports> entry : cfg.teleports.entrySet()) {
                ClientConfig.CharacterConfig charCfg = cfg.characters.computeIfAbsent(entry.getKey(), k -> new ClientConfig.CharacterConfig());
                charCfg.teleports = entry.getValue();
            }
            cfg.teleports = null;
            migrated = true;
        }

        if (cfg.bookmarks != null && !cfg.bookmarks.isEmpty()) {
            List<ClientConfig.Bookmark> flat = new ArrayList<>();
            if (migrateBookmarks(cfg.bookmarks, null, flat)) {
                cfg.bookmarks = flat;
                migrated = true;
            }
        }

        if (cfg.triggers == null || cfg.triggers.isEmpty()) {
            cfg.triggers = createDefaultTriggers();
            migrated = true;
        }

        validate(cfg);

        if (migrated) {
            save(path, cfg);
        }

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

    public static java.util.concurrent.Future<?> saveRoutes(Path path, DeliveryRouteMappings routes) {
        String json = GSON.toJson(routes.routes());
        return BackgroundSaver.save(path, json);
    }

    public static java.util.concurrent.Future<?> save(Path path, ClientConfig cfg) {
        String json = GSON.toJson(cfg);
        return BackgroundSaver.save(path, json);
    }

    public static boolean convertCoordinatesToRoomIds(ClientConfig cfg, com.danavalerie.matrixmudrelay.core.RoomMapService mapService) {
        boolean changed = false;
        try {
            if (cfg.bookmarks != null) {
                changed |= convertBookmarksToRoomIds(cfg.bookmarks, mapService);
            }
            if (cfg.characters != null) {
                for (ClientConfig.CharacterConfig cc : cfg.characters.values()) {
                    if (cc.teleports != null && cc.teleports.locations != null) {
                        for (ClientConfig.TeleportLocation tl : cc.teleports.locations) {
                            if (tl.roomId == null && tl.target != null && tl.target.length >= 3) {
                                String id = mapService.findRoomIdByCoordinates(tl.target[0], tl.target[1], tl.target[2]);
                                if (id != null) {
                                    tl.roomId = id;
                                    tl.target = null;
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error during config coordinate conversion: " + e.getMessage());
        }
        return changed;
    }

    private static boolean migrateBookmarks(List<ClientConfig.Bookmark> source, String prefix, List<ClientConfig.Bookmark> target) {
        boolean migrated = false;
        for (ClientConfig.Bookmark b : source) {
            String currentName = (b.name != null) ? b.name : "";
            String fullName = (prefix == null || prefix.isEmpty()) ? currentName : prefix + "/" + currentName;

            if (b.bookmarks != null && !b.bookmarks.isEmpty()) {
                migrated = true;
                migrateBookmarks(b.bookmarks, fullName, target);
            }

            if (b.roomId != null || b.target != null) {
                b.name = fullName;
                b.bookmarks = null;
                target.add(b);
            }
        }
        return migrated;
    }

    private static boolean convertBookmarksToRoomIds(List<ClientConfig.Bookmark> bookmarks, com.danavalerie.matrixmudrelay.core.RoomMapService mapService) {
        boolean changed = false;
        for (ClientConfig.Bookmark b : bookmarks) {
            if (b.roomId == null && b.target != null && b.target.length >= 3) {
                String id = mapService.findRoomIdByCoordinates(b.target[0], b.target[1], b.target[2]);
                if (id != null) {
                    b.roomId = id;
                    b.target = null;
                    changed = true;
                }
            }
        }
        return changed;
    }

    public static DeliveryRouteMappings convertRoutesToRoomIds(DeliveryRouteMappings routes, com.danavalerie.matrixmudrelay.core.RoomMapService mapService) {
        boolean changed = false;
        List<DeliveryRouteMappings.RouteEntry> newEntries = new ArrayList<>();
        try {
            if (routes.routes() != null) {
                for (DeliveryRouteMappings.RouteEntry entry : routes.routes()) {
                    if (entry.roomId() == null && entry.target() != null && entry.target().size() >= 3) {
                        String id = mapService.findRoomIdByCoordinates(entry.target().get(0), entry.target().get(1), entry.target().get(2));
                        if (id != null) {
                            newEntries.add(new DeliveryRouteMappings.RouteEntry(entry.npc(), entry.location(), id, null, entry.commands(), entry.npcOverride()));
                            changed = true;
                            continue;
                        }
                    }
                    newEntries.add(entry);
                }
            }
        } catch (Exception e) {
            System.err.println("Error during routes coordinate conversion: " + e.getMessage());
            return routes;
        }
        return changed ? new DeliveryRouteMappings(newEntries) : routes;
    }

    public static List<ClientConfig.Trigger> createDefaultTriggers() {
        List<ClientConfig.Trigger> triggers = new ArrayList<>();
        triggers.add(createTrigger("^Whoops!  You tried to carry too many things and fumbled .*$", "#FFFFFF", "#FF0000", true, null, true, false));
        triggers.add(createTrigger("^When you open the .* you think you can hear a faint rumbling sound from it\\.$", "#FFFFFF", "#FF0000", true, null, true, false));
        triggers.add(createTrigger("^Your divine protection is weakening\\.$", "#000000", "#FFFFFF", false, null, true, false));
        triggers.add(createTrigger("^Your divine protection expires\\.$", "#000000", "#FFFFFF", false, null, true, false));
        triggers.add(createTrigger("^\\([^)]*\\) .*", "#B478FF", null, false, null, false, true));
        triggers.add(createTrigger("^.+ asks you: .*$", "#FFEB50", null, false, null, true, true));
        triggers.add(createTrigger("^.+ tells you: .*$", "#FFEB50", null, false, null, true, true));
        triggers.add(createTrigger("^You .* tell .+: .*$", "#FFEB50", null, false, null, true, true));
        triggers.add(createTrigger("^You have been awarded .*$", "#FFFFFF", null, true, null, false, false));
        return triggers;
    }

    private static ClientConfig.Trigger createTrigger(String pattern, String fg, String bg, boolean bold, String soundFile, boolean beep, boolean chitchat) {
        ClientConfig.Trigger t = new ClientConfig.Trigger();
        t.pattern = pattern;
        t.foreground = fg;
        t.background = bg;
        t.bold = bold;
        t.soundFile = soundFile;
        t.systemBeep = beep;
        t.sendToChitchat = chitchat;
        return t;
    }

    private static void validate(ClientConfig cfg) {
        require(cfg.mud.host, "mud.host");
        if (cfg.mud.port <= 0 || cfg.mud.port > 65535) throw new IllegalArgumentException("mud.port invalid");
    }

    private static void require(String s, String name) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Missing required config field: " + name);
    }
}

