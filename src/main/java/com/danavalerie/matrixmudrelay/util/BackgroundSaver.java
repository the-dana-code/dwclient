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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility to handle file saving in a background thread with atomic writes.
 */
public final class BackgroundSaver {
    private static final Logger log = LoggerFactory.getLogger(BackgroundSaver.class);
    
    // Single thread executor ensures that saves happen in the order they were queued.
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "BackgroundSaver-Thread");
        thread.setDaemon(true);
        return thread;
    });

    private BackgroundSaver() {}

    /**
     * Queues a task to save content to a file and returns a Future.
     *
     * @param path    The path to the file.
     * @param content The content to write.
     * @return A Future representing pending completion of the save task.
     */
    public static java.util.concurrent.Future<?> save(Path path, String content) {
        if (EXECUTOR.isShutdown()) {
            log.warn("Saver is shut down, cannot save to {}", path);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return EXECUTOR.submit(() -> {
            try {
                atomicWrite(path, content);
            } catch (IOException e) {
                log.error("Failed to save file atomically: {}", path, e);
            }
        });
    }

    /**
     * Atomically writes content to a file by first writing to a temp file and then renaming it.
     *
     * @param path    The target path.
     * @param content The content to write.
     * @throws IOException If an I/O error occurs.
     */
    private static void atomicWrite(Path path, String content) throws IOException {
        Path tempFile = path.resolveSibling(path.getFileName().toString() + ".tmp");
        
        // Write to temp file
        Files.writeString(tempFile, content);
        
        // Atomically rename temp file to target path
        try {
            Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Fallback if atomic move is not supported
            log.warn("Atomic move failed, falling back to standard move for: {}", path);
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    /**
     * Shutdown the background saver and wait for pending tasks to complete.
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
