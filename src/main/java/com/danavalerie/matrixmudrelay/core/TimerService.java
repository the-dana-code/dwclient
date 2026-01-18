package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class TimerService {
    private final BotConfig config;
    private final Path configPath;

    public TimerService(BotConfig config, Path configPath) {
        this.config = config;
        this.configPath = configPath;
    }

    public synchronized void setTimer(String characterName, String timerName, long durationMs) {
        if (characterName == null || characterName.isBlank()) {
            return;
        }

        BotConfig.CharacterConfig charConfig = config.characters.computeIfAbsent(characterName, k -> new BotConfig.CharacterConfig());
        if (charConfig.timers == null) {
            charConfig.timers = new LinkedHashMap<>();
        }
        long expiration = System.currentTimeMillis() + durationMs;
        charConfig.timers.put(timerName, expiration);
        saveConfig();
    }

    public synchronized Map<String, Long> getTimers(String characterName) {
        if (characterName == null || characterName.isBlank()) {
            return Collections.emptyMap();
        }

        BotConfig.CharacterConfig charConfig = config.characters.get(characterName);
        if (charConfig == null || charConfig.timers == null) {
            return Collections.emptyMap();
        }

        // Return a copy to avoid concurrent modification issues and keep the original for cleanup if needed
        return new LinkedHashMap<>(charConfig.timers);
    }

    private void saveConfig() {
        try {
            ConfigLoader.save(configPath, config);
        } catch (Exception e) {
            // Log error or handle it
            System.err.println("Failed to save config after updating timers: " + e.getMessage());
        }
    }

    public String formatRemainingTime(long remainingMs) {
        if (remainingMs <= 0) {
            return "0:00";
        }

        long totalSeconds = remainingMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}
