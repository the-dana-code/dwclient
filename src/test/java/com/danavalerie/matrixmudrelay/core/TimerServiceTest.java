package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.util.BackgroundSaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TimerServiceTest {
    private ClientConfig config;
    private TimerService timerService;
    private Path configPath;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        BackgroundSaver.resetForTests();
        config = new ClientConfig();
        configPath = tempDir.resolve("config.json");
        timerService = new TimerService(config, configPath);
    }
    
    @AfterEach
    void tearDown() {
        BackgroundSaver.waitForIdle();
    }

    @Test
    void testAddAndRemoveTimer() {
        timerService.setTimer("Char1", "Timer1", 1000);
        Map<String, ClientConfig.TimerData> timers = timerService.getTimers("Char1");
        assertEquals(1, timers.size());
        assertTrue(timers.containsKey("Timer1"));
        assertEquals(1000, timers.get("Timer1").durationMs);

        timerService.removeTimer("Char1", "Timer1");
        timers = timerService.getTimers("Char1");
        assertTrue(timers.isEmpty());
    }

    @Test
    void testGetAllTimers() {
        timerService.setTimer("Char1", "Timer1", 1000);
        timerService.setTimer("Char2", "Timer2", 2000);

        Map<String, Map<String, ClientConfig.TimerData>> allTimers = timerService.getAllTimers();
        assertEquals(2, allTimers.size());
        assertTrue(allTimers.containsKey("Char1"));
        assertTrue(allTimers.containsKey("Char2"));
        assertTrue(allTimers.get("Char1").containsKey("Timer1"));
        assertTrue(allTimers.get("Char2").containsKey("Timer2"));
    }

    @Test
    void testUpdateTimerDescription() {
        timerService.setTimer("Char1", "OldDesc", 1000);
        ClientConfig.TimerData data = config.characters.get("Char1").timers.get("OldDesc");
        long expiration = data.expirationTime;

        timerService.updateTimerDescription("Char1", "OldDesc", "NewDesc");
        
        Map<String, ClientConfig.TimerData> timers = timerService.getTimers("Char1");
        assertFalse(timers.containsKey("OldDesc"));
        assertTrue(timers.containsKey("NewDesc"));
        assertEquals(expiration, timers.get("NewDesc").expirationTime);
    }

    @Test
    void testRestartTimer() {
        timerService.setTimer("Char1", "Timer1", 10000);
        ClientConfig.TimerData data = config.characters.get("Char1").timers.get("Timer1");
        long originalExpiration = data.expirationTime;

        // Manually age the timer
        data.expirationTime -= 5000;

        timerService.restartTimer("Char1", "Timer1");
        assertTrue(data.expirationTime >= originalExpiration);
        assertEquals(10000, data.durationMs);
    }

    @Test
    void testFormatRemainingTime() {
        assertEquals("EXPIRED", timerService.formatRemainingTime(0));
        assertEquals("EXPIRED", timerService.formatRemainingTime(-1000));
        assertEquals("0:05", timerService.formatRemainingTime(5000));
        assertEquals("1:00", timerService.formatRemainingTime(60000));
        assertEquals("1:00:00", timerService.formatRemainingTime(3600000));
    }
}
