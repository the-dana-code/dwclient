package com.danavalerie.matrixmudrelay.matrix;

import com.danavalerie.matrixmudrelay.core.MatrixEventProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MatrixSyncLoop {
    private static final Logger log = LoggerFactory.getLogger(MatrixSyncLoop.class);

    private final MatrixClient client;
    private final String roomId;
    private final String filterId;
    private final int timeoutMs;
    private final boolean ignoreInitialTimeline;
    private final MatrixEventProcessor processor;

    private volatile boolean running = false;
    private Thread thread;

    public MatrixSyncLoop(MatrixClient client,
                          String roomId,
                          String filterId,
                          int timeoutMs,
                          boolean ignoreInitialTimeline,
                          MatrixEventProcessor processor) {
        this.client = client;
        this.roomId = roomId;
        this.filterId = filterId;
        this.timeoutMs = timeoutMs;
        this.ignoreInitialTimeline = ignoreInitialTimeline;
        this.processor = processor;
    }

    public void start() {
        running = true;
        thread = new Thread(this::run, "matrix-sync");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    private void run() {
        String since = null;
        boolean first = true;

        while (running) {
            try {
                MatrixClient.SyncResponse resp = client.sync(since, timeoutMs, filterId);
                String next = resp.nextBatch();
                if (next != null) since = next;

                if (first && ignoreInitialTimeline) {
                    first = false;
                    continue;
                }
                first = false;

                JsonNode root = resp.root;
                JsonNode rooms = root.get("rooms");
                if (rooms == null) continue;
                JsonNode join = rooms.get("join");
                if (join == null) continue;

                JsonNode room = join.get(roomId);
                if (room == null) continue;

                JsonNode timeline = room.get("timeline");
                if (timeline == null) continue;

                JsonNode events = timeline.get("events");
                if (events == null || !events.isArray()) continue;

                for (JsonNode ev : events) {
                    processor.onMatrixEvent(ev);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (MatrixApiException e) {
                log.warn("sync api_error status={} body={}", e.statusCode, safe(e.responseBody));
                sleep(1000);
            } catch (Exception e) {
                log.warn("sync unexpected err={}", e.toString());
                sleep(1000);
            }
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String safe(String s) {
        if (s == null) return "";
        if (s.length() <= 300) return s;
        return s.substring(0, 300) + "...";
    }
}
