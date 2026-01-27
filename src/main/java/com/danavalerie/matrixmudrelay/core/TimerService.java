package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;
import com.danavalerie.matrixmudrelay.config.UiConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TimerService {
    private final ClientConfig config;
    private final UiConfig uiConfig;
    private final Path configPath;

    public TimerService(ClientConfig config, UiConfig uiConfig, Path configPath) {
        this.config = config;
        this.uiConfig = uiConfig;
        this.configPath = configPath;
    }

    public synchronized void setTimer(String characterName, String timerName, long durationMs) {
        if (characterName == null || characterName.isBlank()) {
            return;
        }

        UiConfig.CharacterUiData charConfig = uiConfig.characters.computeIfAbsent(characterName, k -> new UiConfig.CharacterUiData());
        if (charConfig.timers == null) {
            charConfig.timers = new LinkedHashMap<>();
        }
        long expiration = System.currentTimeMillis() + durationMs;
        charConfig.timers.put(timerName, new ClientConfig.TimerData(expiration, durationMs));
        saveUiConfig();
    }

    public synchronized void restartTimer(String characterName, String timerName) {
        if (characterName == null || characterName.isBlank() || timerName == null) {
            return;
        }
        UiConfig.CharacterUiData charConfig = uiConfig.characters.get(characterName);
        if (charConfig != null && charConfig.timers != null) {
            ClientConfig.TimerData data = charConfig.timers.get(timerName);
            if (data != null && data.durationMs > 0) {
                data.expirationTime = System.currentTimeMillis() + data.durationMs;
                saveUiConfig();
            }
        }
    }

    public synchronized void updateTimerDescription(String characterName, String oldDescription, String newDescription) {
        if (characterName == null || characterName.isBlank() || oldDescription == null || newDescription == null) {
            return;
        }
        UiConfig.CharacterUiData charConfig = uiConfig.characters.get(characterName);
        if (charConfig != null && charConfig.timers != null && charConfig.timers.containsKey(oldDescription)) {
            ClientConfig.TimerData data = charConfig.timers.remove(oldDescription);
            charConfig.timers.put(newDescription, data);
            saveUiConfig();
        }
    }

    public synchronized void removeTimer(String characterName, String timerName) {
        if (characterName == null || characterName.isBlank()) {
            return;
        }
        UiConfig.CharacterUiData charConfig = uiConfig.characters.get(characterName);
        if (charConfig != null && charConfig.timers != null) {
            charConfig.timers.remove(timerName);
            saveUiConfig();
        }
    }

    public synchronized void setTimerColumnWidths(java.util.List<Integer> widths) {
        uiConfig.timerColumnWidths = widths;
        saveUiConfig();
    }

    public synchronized java.util.List<Integer> getTimerColumnWidths() {
        return uiConfig.timerColumnWidths;
    }

    public synchronized Map<String, ClientConfig.TimerData> getTimers(String characterName) {
        if (characterName == null || characterName.isBlank()) {
            return Collections.emptyMap();
        }

        UiConfig.CharacterUiData charConfig = uiConfig.characters.get(characterName);
        if (charConfig == null || charConfig.timers == null) {
            return Collections.emptyMap();
        }

        // Return a copy to avoid concurrent modification issues and keep the original for cleanup if needed
        return new LinkedHashMap<>(charConfig.timers);
    }

    public synchronized Map<String, Map<String, ClientConfig.TimerData>> getAllTimers() {
        Map<String, Map<String, ClientConfig.TimerData>> allTimers = new LinkedHashMap<>();
        for (Map.Entry<String, UiConfig.CharacterUiData> entry : uiConfig.characters.entrySet()) {
            if (entry.getValue().timers != null && !entry.getValue().timers.isEmpty()) {
                allTimers.put(entry.getKey(), new LinkedHashMap<>(entry.getValue().timers));
            }
        }
        return allTimers;
    }

    public synchronized List<String> getKnownCharacters() {
        if (config.characters == null || config.characters.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(config.characters.keySet());
    }

    private void saveUiConfig() {
        ConfigLoader.saveUi(configPath.resolveSibling("ui.json"), uiConfig);
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
