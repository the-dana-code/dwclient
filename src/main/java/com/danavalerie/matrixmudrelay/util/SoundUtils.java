/*
 * Lesa's Discworld MUD client.
 * Copyright (C) 2026 Dana Reese
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.danavalerie.matrixmudrelay.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.awt.Toolkit;
import java.io.File;
import java.net.URL;

public final class SoundUtils {
    private static final Logger log = LoggerFactory.getLogger(SoundUtils.class);

    private SoundUtils() {}

    public static void playBeep() {
        Toolkit.getDefaultToolkit().beep();
    }

    public static void playSound(String soundFile) throws Exception {
        if (soundFile == null || soundFile.isBlank()) {
            return;
        }

        if (soundFile.startsWith("classpath:")) {
            String resourcePath = soundFile.substring("classpath:".length());
            URL url = SoundUtils.class.getClassLoader().getResource(resourcePath);
            if (url != null) {
                playSound(url);
            } else {
                throw new Exception("Classpath resource not found: " + resourcePath);
            }
        } else {
            File file = new File(soundFile);
            if (!file.exists()) {
                throw new Exception("Sound file not found: " + soundFile);
            }
            playSound(file);
        }
    }

    public static void playSound(File file) throws Exception {
        try (AudioInputStream audioIn = AudioSystem.getAudioInputStream(file)) {
            playAudioStream(audioIn);
        }
    }

    public static void playSound(URL url) throws Exception {
        try (AudioInputStream audioIn = AudioSystem.getAudioInputStream(url)) {
            playAudioStream(audioIn);
        }
    }

    private static void playAudioStream(AudioInputStream audioIn) throws Exception {
        Clip clip = AudioSystem.getClip();
        clip.open(audioIn);
        clip.start();
    }

    public static void playUULibraryReadySound() {
        // "Good sound" to prompt the user that the system is ready for the next command.
        // Disabled by request.
    }

    public static void playUULibraryAlertSound() {
        // "High alert sound" because the user needs to know to stop immediately.
        // Disabled by request.
    }
}
