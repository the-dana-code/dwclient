package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class TimerService {
    private final ClientConfig config;
    private final Path configPath;

    public TimerService(ClientConfig config, Path configPath) {
        this.config = config;
        this.configPath = configPath;
    }

    public synchronized void setTimer(String characterName, String timerName, long durationMs) {
        if (characterName == null || characterName.isBlank()) {
            return;
        }

        ClientConfig.CharacterConfig charConfig = config.characters.computeIfAbsent(characterName, k -> new ClientConfig.CharacterConfig());
        if (charConfig.timers == null) {
            charConfig.timers = new LinkedHashMap<>();
        }
        long expiration = System.currentTimeMillis() + durationMs;
        charConfig.timers.put(timerName, new ClientConfig.TimerData(expiration, durationMs));
        saveConfig();
    }

    public synchronized void restartTimer(String characterName, String timerName) {
        if (characterName == null || characterName.isBlank() || timerName == null) {
            return;
        }
        ClientConfig.CharacterConfig charConfig = config.characters.get(characterName);
        if (charConfig != null && charConfig.timers != null) {
            ClientConfig.TimerData data = charConfig.timers.get(timerName);
            if (data != null && data.durationMs > 0) {
                data.expirationTime = System.currentTimeMillis() + data.durationMs;
                saveConfig();
            }
        }
    }

    public synchronized void updateTimerDescription(String characterName, String oldDescription, String newDescription) {
        if (characterName == null || characterName.isBlank() || oldDescription == null || newDescription == null) {
            return;
        }
        ClientConfig.CharacterConfig charConfig = config.characters.get(characterName);
        if (charConfig != null && charConfig.timers != null && charConfig.timers.containsKey(oldDescription)) {
            ClientConfig.TimerData data = charConfig.timers.remove(oldDescription);
            charConfig.timers.put(newDescription, data);
            saveConfig();
        }
    }

    public synchronized void removeTimer(String characterName, String timerName) {
        if (characterName == null || characterName.isBlank()) {
            return;
        }
        ClientConfig.CharacterConfig charConfig = config.characters.get(characterName);
        if (charConfig != null && charConfig.timers != null) {
            charConfig.timers.remove(timerName);
            saveConfig();
        }
    }

    public synchronized void setTimerColumnWidths(java.util.List<Integer> widths) {
        config.ui.timerColumnWidths = widths;
        saveConfig();
    }

    public synchronized java.util.List<Integer> getTimerColumnWidths() {
        return config.ui.timerColumnWidths;
    }

    public synchronized Map<String, ClientConfig.TimerData> getTimers(String characterName) {
        if (characterName == null || characterName.isBlank()) {
            return Collections.emptyMap();
        }

        ClientConfig.CharacterConfig charConfig = config.characters.get(characterName);
        if (charConfig == null || charConfig.timers == null) {
            return Collections.emptyMap();
        }

        // Return a copy to avoid concurrent modification issues and keep the original for cleanup if needed
        return new LinkedHashMap<>(charConfig.timers);
    }

    public synchronized Map<String, Map<String, ClientConfig.TimerData>> getAllTimers() {
        Map<String, Map<String, ClientConfig.TimerData>> allTimers = new LinkedHashMap<>();
        for (Map.Entry<String, ClientConfig.CharacterConfig> entry : config.characters.entrySet()) {
            if (entry.getValue().timers != null && !entry.getValue().timers.isEmpty()) {
                allTimers.put(entry.getKey(), new LinkedHashMap<>(entry.getValue().timers));
            }
        }
        return allTimers;
    }

    private void saveConfig() {
        ConfigLoader.save(configPath, config);
    }

    public String formatRemainingTime(long remainingMs) {
        if (remainingMs <= 0) {
            return "EXPIRED";
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
