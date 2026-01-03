package com.danavalerie.matrixmudrelay;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;
import com.danavalerie.matrixmudrelay.ui.DesktopClientFrame;
import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -jar mud-client-1.0.0-shaded.jar /path/to/config.json");
            System.exit(2);
        }

        BotConfig cfg = ConfigLoader.load(Path.of(args[0]));
        TranscriptLogger transcript = TranscriptLogger.create(cfg.transcript);
        DesktopClientFrame.launch(cfg, transcript);
    }
}
