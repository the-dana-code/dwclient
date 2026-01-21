package com.danavalerie.matrixmudrelay.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class BackgroundSaverTest {

    @Test
    void testSequentialSaving(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("test.txt");
        List<Future<?>> futures = new ArrayList<>();
        
        // Queue 100 saves
        for (int i = 0; i < 100; i++) {
            futures.add(BackgroundSaver.save(path, String.valueOf(i)));
        }
        
        // Wait for the last one
        futures.get(futures.size() - 1).get();
        
        // The final content should be "99" because they were processed in order
        assertEquals("99", Files.readString(path));
    }

    @Test
    void testAtomicWrite(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("atomic.txt");
        String content = "Hello World";
        
        Future<?> future = BackgroundSaver.save(path, content);
        future.get();
        
        assertTrue(Files.exists(path));
        assertEquals(content, Files.readString(path));
        
        // Verify no temp file is left behind
        Path tempPath = path.resolveSibling(path.getFileName().toString() + ".tmp");
        assertFalse(Files.exists(tempPath), "Temp file should be gone");
    }
}
