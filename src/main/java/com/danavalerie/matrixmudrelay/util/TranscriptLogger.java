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

import com.danavalerie.matrixmudrelay.config.BotConfig;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;

public class TranscriptLogger implements AutoCloseable {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final boolean enabled;
    private final Path dir;
    private final long maxBytes;
    private final int maxFiles;

    private BufferedWriter writer;
    private Path currentFile;

    protected TranscriptLogger(boolean enabled, Path dir, long maxBytes, int maxFiles) {
        this.enabled = enabled;
        this.dir = dir;
        this.maxBytes = maxBytes;
        this.maxFiles = maxFiles;
    }

    public static TranscriptLogger create(BotConfig.Transcript cfg) throws IOException {
        TranscriptLogger t = new TranscriptLogger(cfg.enabled, Path.of(cfg.directory), cfg.maxBytes, cfg.maxFiles);
        if (t.enabled) {
            Files.createDirectories(t.dir);
            t.openIfNeeded();
        }
        return t;
    }

    public void logMudToClient(String line) { log("MUD->CLIENT", line); }
    public void logClientToMud(String line) { log("CLIENT->MUD", line); }
    public void logSystem(String line) { log("SYSTEM", line); }

    private synchronized void log(String dirTag, String line) {
        if (!enabled) return;
        try {
            openIfNeeded();
            rotateIfNeeded();

            String ts = Instant.now().toString();
            writer.write(ts + " " + dirTag + " " + (line == null ? "" : line));
            writer.newLine();
            writer.flush();
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private void openIfNeeded() throws IOException {
        if (!enabled) return;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Path file = dir.resolve("transcript-" + DAY.format(today) + ".log");
        if (writer == null || currentFile == null || !currentFile.equals(file)) {
            close();
            currentFile = file;
            writer = Files.newBufferedWriter(currentFile,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        }
    }

    private void rotateIfNeeded() throws IOException {
        if (currentFile == null) return;
        long size = Files.size(currentFile);
        if (size < maxBytes) return;

        close();

        String rotatedName = currentFile.getFileName().toString().replace(".log", "")
                + "-" + System.currentTimeMillis() + ".log";
        Path rotated = dir.resolve(rotatedName);
        Files.move(currentFile, rotated, StandardCopyOption.ATOMIC_MOVE);

        pruneOldFiles();

        writer = Files.newBufferedWriter(currentFile,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
    }

    private void pruneOldFiles() {
        if (maxFiles <= 0) return;
        try (Stream<Path> s = Files.list(dir)) {
            Path[] files = s
                    .filter(p -> p.getFileName().toString().startsWith("transcript-"))
                    .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .toArray(Path[]::new);

            int excess = files.length - maxFiles;
            for (int i = 0; i < excess; i++) {
                try { Files.deleteIfExists(files[i]); } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    @Override
    public synchronized void close() throws IOException {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }
    }
}

