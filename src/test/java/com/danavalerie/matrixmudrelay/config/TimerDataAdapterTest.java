package com.danavalerie.matrixmudrelay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TimerDataAdapterTest {

    @Test
    public void testMigrationFromLong() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ClientConfig.TimerData.class, new TimerDataAdapter())
                .create();

        String json = "{\"timer1\": 1234567890}";
        // Map<String, TimerData> type
        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, ClientConfig.TimerData>>(){}.getType();
        Map<String, ClientConfig.TimerData> timers = gson.fromJson(json, type);

        assertNotNull(timers);
        assertTrue(timers.containsKey("timer1"));
        assertEquals(1234567890L, timers.get("timer1").expirationTime);
        assertEquals(0L, timers.get("timer1").durationMs);
    }

    @Test
    public void testFullData() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ClientConfig.TimerData.class, new TimerDataAdapter())
                .create();

        String json = "{\"timer1\": {\"expirationTime\": 1234567890, \"durationMs\": 60000}}";
        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, ClientConfig.TimerData>>(){}.getType();
        Map<String, ClientConfig.TimerData> timers = gson.fromJson(json, type);

        assertNotNull(timers);
        assertTrue(timers.containsKey("timer1"));
        assertEquals(1234567890L, timers.get("timer1").expirationTime);
        assertEquals(60000L, timers.get("timer1").durationMs);
    }
}
