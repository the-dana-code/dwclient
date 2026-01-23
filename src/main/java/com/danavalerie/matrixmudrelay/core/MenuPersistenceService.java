package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.util.BackgroundSaver;
import com.danavalerie.matrixmudrelay.util.GsonUtils;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class MenuPersistenceService {
    private static final Logger logger = LoggerFactory.getLogger(MenuPersistenceService.class);
    private static final Gson GSON = GsonUtils.getGson();
    private final Path storagePath;

    public record SavedMenus(List<WritTracker.WritRequirement> writRequirements,
                            String writCharacterName,
                            Map<Integer, EnumSet<WritMenuAction>> writMenuVisits) {}

    public MenuPersistenceService(Path storagePath) {
        this.storagePath = storagePath;
    }

    public synchronized void save(List<WritTracker.WritRequirement> writRequirements,
                                 String writCharacterName,
                                 Map<Integer, EnumSet<WritMenuAction>> writMenuVisits) {
        SavedMenus savedMenus = new SavedMenus(writRequirements, writCharacterName, writMenuVisits);
        String json = GSON.toJson(savedMenus);
        BackgroundSaver.save(storagePath, json);
    }

    public SavedMenus load() {
        if (!Files.exists(storagePath)) {
            return null;
        }
        try {
            String json = Files.readString(storagePath);
            return GSON.fromJson(json, SavedMenus.class);
        } catch (IOException e) {
            logger.error("Failed to load menus from {}", storagePath, e);
            return null;
        }
    }
}
