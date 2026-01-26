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

            System.out.println("Creating example config...");
            ClientConfig example = new ClientConfig();

            // Specifically pull out what we want to keep

            // MUD settings
            example.mud.host = config.mud.host;
            example.mud.port = config.mud.port;
            example.mud.charset = config.mud.charset;
            example.mud.connectTimeoutMs = config.mud.connectTimeoutMs;

            // UI settings - explicitly null to keep out of example
            example.ui = null;

            // Bookmarks
            example.bookmarks.addAll(config.bookmarks);

            // Triggers
            example.triggers.addAll(config.triggers);

            System.out.println("Saving to config-example.json...");
            ConfigLoader.save(Paths.get("config-example.json"), example);

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
