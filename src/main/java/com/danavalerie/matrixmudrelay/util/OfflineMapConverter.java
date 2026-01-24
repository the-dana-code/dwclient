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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Utility to batch convert all map backgrounds to dark theme offline.
 */
public class OfflineMapConverter {

    public static void main(String[] args) {
        Path mapsDir = Paths.get("map-backgrounds");
        if (!Files.exists(mapsDir)) {
            mapsDir = Paths.get("src", "main", "resources", "map-backgrounds");
        }
        if (!Files.exists(mapsDir)) {
            System.err.println("Directory not found: " + mapsDir.toAbsolutePath());
            return;
        }

        try (Stream<Path> paths = Files.list(mapsDir)) {
            paths.filter(p -> p.toString().endsWith(".png") && !p.toString().endsWith("_dark.png"))
                 .forEach(OfflineMapConverter::convertFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void convertFile(Path path) {
        String filename = path.getFileName().toString();
        String baseName = filename.substring(0, filename.lastIndexOf('.'));
        Path outputPath = path.getParent().resolve(baseName + "_dark.png");

        if (Files.exists(outputPath)) {
            System.out.println("Skipping (already exists): " + filename);
            return;
        }

        System.out.println("Converting: " + filename + " -> " + outputPath.getFileName());
        try {
            BufferedImage src = ImageIO.read(path.toFile());
            if (src == null) {
                System.err.println("Failed to load: " + filename);
                return;
            }
            BufferedImage dark = DarkThemeConverter.toDarkTheme(src);
            ImageIO.write(dark, "png", outputPath.toFile());
        } catch (IOException e) {
            System.err.println("Error converting " + filename + ": " + e.getMessage());
        }
    }
}

