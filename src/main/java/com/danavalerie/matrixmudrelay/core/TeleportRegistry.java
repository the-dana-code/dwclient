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

    public static void initialize(Map<String, BotConfig.CharacterTeleports> config) {
        Map<String, CharacterTeleports> map = new HashMap<>();
        if (config != null) {
            config.forEach((name, charConfig) -> {
                List<TeleportLocation> locations = new ArrayList<>();
                if (charConfig.locations != null) {
                    for (BotConfig.TeleportLocation loc : charConfig.locations) {
                        locations.add(new TeleportLocation(loc.command, loc.mapId, loc.x, loc.y));
                    }
                }
                map.put(name.trim().toLowerCase(Locale.ROOT), new CharacterTeleports(charConfig.reliable, Collections.unmodifiableList(locations)));
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

    public record TeleportLocation(String command, int mapId, int x, int y) {
    }
}
