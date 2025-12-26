package com.danavalerie.matrixmudrelay.matrix;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.util.Sanitizer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RetryingMatrixSender {
    private static final Logger log = LoggerFactory.getLogger(RetryingMatrixSender.class);
    private static final Gson GSON = new GsonBuilder().create();

    private final MatrixClient client;
    private final BotConfig.Retry retry;
    private final ExecutorService single;

    public RetryingMatrixSender(MatrixClient client, BotConfig.Retry retry) {
        this.client = client;
        this.retry = retry;
        this.single = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "matrix-send");
            t.setDaemon(true);
            return t;
        });
    }

    public void sendText(String roomId, String body, boolean notify) {
        single.submit(() -> sendWithRetry(roomId, body, "<pre>" + Sanitizer.escapeHtml(body) + "</pre>", body, notify));
    }

    public void sendHtml(String roomId, String body, String html, String originalText, boolean notify) {
        single.submit(() -> sendWithRetry(roomId, body, html, originalText, notify));
    }

    private void sendWithRetry(String roomId, String body, String html, String originalText, boolean notify) {
        long backoff = Math.max(0, retry.initialBackoffMs);
        int attempts = 0;

        while (true) {
            attempts++;
            try {
                client.sendTextMessage(roomId, body, html, originalText, notify);
                return;
            } catch (MatrixApiException e) {
                if (e.statusCode == 429) {
                    long ra = parseRetryAfterMs(e.responseBody);
                    long sleep = ra > 0 ? ra : backoff;
                    log.warn("matrix_send rate_limited status=429 sleepMs={} attempts={}", sleep, attempts);
                    sleepMs(sleep);
                } else if (e.statusCode / 100 == 5) {
                    log.warn("matrix_send server_error status={} attempts={} backoffMs={}", e.statusCode, attempts, backoff);
                    sleepMs(backoff);
                } else {
                    log.error("matrix_send fatal status={} body={}", e.statusCode, truncate(e.responseBody));
                    return;
                }
            } catch (IOException e) {
                log.warn("matrix_send io_error attempts={} backoffMs={} err={}", attempts, backoff, e.toString());
                sleepMs(backoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("matrix_send unexpected attempts={} backoffMs={} err={}", attempts, backoff, e.toString());
                sleepMs(backoff);
            }

            if (retry.maxAttempts > 0 && attempts >= retry.maxAttempts) {
                log.error("matrix_send giving_up attempts={} (maxAttempts={})", attempts, retry.maxAttempts);
                return;
            }

            backoff = Math.min(Math.max(backoff, 1), retry.maxBackoffMs);
            backoff = Math.min(retry.maxBackoffMs, (long) (backoff * 1.7) + 1);
        }
    }

    private static long parseRetryAfterMs(String body) {
        try {
            JsonObject n = GSON.fromJson(body, JsonObject.class);
            JsonElement ra = n.get("retry_after_ms");
            return ra == null ? -1 : ra.getAsLong();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static void sleepMs(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= 500) return s;
        return s.substring(0, 500) + "...";
    }

    public void shutdown() throws InterruptedException {
        single.shutdownNow();
        single.awaitTermination(5, TimeUnit.SECONDS);
    }
}
