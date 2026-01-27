package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RouteMenuPolicyTest {

    @Test
    public void testNoCharacterKeepsMenuOpen() {
        assertTrue(RouteMenuPolicy.shouldKeepRouteMenuOpen(null, new HashMap<>()));
        assertTrue(RouteMenuPolicy.shouldKeepRouteMenuOpen(" ", new HashMap<>()));
    }

    @Test
    public void testReliableTeleportsKeepsMenuOpen() {
        Map<String, ClientConfig.CharacterConfig> characters = new HashMap<>();
        ClientConfig.CharacterConfig config = new ClientConfig.CharacterConfig();
        config.teleports.reliable = true;
        characters.put("Lesa", config);

        assertTrue(RouteMenuPolicy.shouldKeepRouteMenuOpen("Lesa", characters));
    }

    @Test
    public void testUnreliableTeleportsAllowsMenuClose() {
        Map<String, ClientConfig.CharacterConfig> characters = new HashMap<>();
        ClientConfig.CharacterConfig config = new ClientConfig.CharacterConfig();
        config.teleports.reliable = false;
        characters.put("Lesa", config);

        assertFalse(RouteMenuPolicy.shouldKeepRouteMenuOpen("Lesa", characters));
    }

    @Test
    public void testMissingTeleportsKeepsMenuOpen() {
        Map<String, ClientConfig.CharacterConfig> characters = new HashMap<>();
        ClientConfig.CharacterConfig config = new ClientConfig.CharacterConfig();
        config.teleports = null;
        characters.put("Lesa", config);

        assertTrue(RouteMenuPolicy.shouldKeepRouteMenuOpen("Lesa", characters));
    }
}
