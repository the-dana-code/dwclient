package com.danavalerie.matrixmudrelay.util;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;

import java.nio.file.Paths;

/**
 * Utility to recreate config-example.json based on config.json.
 */
public class ConfigExampleGenerator {
    public static void main(String[] args) {
        try {
            System.out.println("Loading config.json...");
            ClientConfig config = ConfigLoader.load(Paths.get("config.json"));

            System.out.println("Removing unnecessary data...");
            // Remove characters and associated data
            config.characters.clear();
            config.teleports = null;

            // Add a sample character to show the new character-specific settings
            ClientConfig.CharacterConfig sampleChar = new ClientConfig.CharacterConfig();
            sampleChar.useTeleports = true;
            config.characters.put("SampleCharacter", sampleChar);

            // Remove splitpane stored location info and other transient UI state
            if (config.ui != null) {
                config.ui.mudMapSplitRatio = null;
                config.ui.mapNotesSplitRatio = null;
                config.ui.chitchatTimerSplitRatio = null;
                config.ui.outputSplitRatio = null;
                config.ui.timerColumnWidths = null;
            }

            System.out.println("Saving to config-example.json...");
            ConfigLoader.save(Paths.get("config-example.json"), config);

            System.out.println("Waiting for background saves to complete...");
            BackgroundSaver.waitForIdle();
            BackgroundSaver.shutdown();

            System.out.println("config-example.json has been recreated successfully.");
        } catch (Exception e) {
            System.err.println("Failed to recreate config-example.json!");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
