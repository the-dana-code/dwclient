package com.danavalerie.matrixmudrelay.matrix;

import com.danavalerie.matrixmudrelay.core.MatrixEventProcessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
                    log.info("sync: skipping initial timeline as requested (ignoreInitialTimeline=true)");
                    first = false;
                    continue;
                }
                first = false;

                JsonObject root = resp.root;
                log.debug("sync response: {}", root);
                JsonElement rooms = root.get("rooms");
                if (rooms == null || !rooms.isJsonObject()) {
                    log.debug("sync: no rooms in response");
                    continue;
                }
                JsonElement join = rooms.getAsJsonObject().get("join");
                if (join == null || !join.isJsonObject()) {
                    log.debug("sync: no join in rooms");
                    continue;
                }

                JsonElement room = join.getAsJsonObject().get(roomId);
                if (room == null || !room.isJsonObject()) {
                    log.debug("sync: room {} not found in join. available: {}", roomId, join.getAsJsonObject().keySet());
                    continue;
                }

                JsonElement timeline = room.getAsJsonObject().get("timeline");
                if (timeline == null || !timeline.isJsonObject()) {
                    log.debug("sync: no timeline in room {}", roomId);
                    continue;
                }

                JsonElement events = timeline.getAsJsonObject().get("events");
                if (events == null || !events.isJsonArray()) {
                    log.debug("sync: no events in timeline of room {}", roomId);
                    continue;
                }

                for (JsonElement ev : events.getAsJsonArray()) {
                    if (ev.isJsonObject()) {
                        processor.onMatrixEvent(ev.getAsJsonObject());
                    }
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
