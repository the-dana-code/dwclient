package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import java.util.Map;

public final class RouteMenuPolicy {
    private RouteMenuPolicy() {}

    public static boolean shouldKeepRouteMenuOpen(String characterName, Map<String, ClientConfig.CharacterConfig> characters) {
        if (characterName == null || characterName.isBlank()) {
            return true;
        }
        if (characters == null) {
            return true;
        }
        ClientConfig.CharacterConfig charCfg = characters.get(characterName);
        if (charCfg == null || charCfg.teleports == null) {
            return true;
        }
        return charCfg.teleports.reliable;
    }
}
