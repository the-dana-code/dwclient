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

package com.danavalerie.matrixmudrelay.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.danavalerie.matrixmudrelay.config.BotConfig;

public final class TeleportRegistry {
    private static CharacterTeleports DEFAULT = new CharacterTeleports(true, List.of());
    private static Map<String, CharacterTeleports> BY_CHARACTER = Map.of();

    private TeleportRegistry() {
    }

    public static void initialize(Map<String, BotConfig.CharacterConfig> config) {
        Map<String, CharacterTeleports> map = new HashMap<>();
        if (config != null) {
            config.forEach((name, charConfig) -> {
                if (charConfig.teleports == null) return;
                List<TeleportLocation> locations = new ArrayList<>();
                if (charConfig.teleports.locations != null) {
                    for (BotConfig.TeleportLocation loc : charConfig.teleports.locations) {
                        locations.add(new TeleportLocation(loc.command, loc.roomId));
                    }
                }
                map.put(name.trim().toLowerCase(Locale.ROOT), new CharacterTeleports(charConfig.teleports.reliable, Collections.unmodifiableList(locations)));
            });
        }
        BY_CHARACTER = Collections.unmodifiableMap(map);
    }

    public static CharacterTeleports forCharacter(String characterName) {
        if (characterName == null || characterName.isBlank()) {
            return DEFAULT;
        }
        String key = characterName.trim().toLowerCase(Locale.ROOT);
        return BY_CHARACTER.getOrDefault(key, DEFAULT);
    }

    public record CharacterTeleports(boolean reliable, List<TeleportLocation> teleports) {
    }

    public record TeleportLocation(String command, String roomId) {
    }
}

