package com.danavalerie.matrixmudrelay;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;
import com.danavalerie.matrixmudrelay.core.MatrixEventProcessor;
import com.danavalerie.matrixmudrelay.core.TellHighlightRenderer;
import com.danavalerie.matrixmudrelay.matrix.MatrixClient;
import com.danavalerie.matrixmudrelay.matrix.MatrixSyncLoop;
import com.danavalerie.matrixmudrelay.matrix.RetryingMatrixSender;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.util.Sanitizer;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public final class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -jar matrix-mud-relay-1.0.0-shaded.jar /path/to/config.json");
            System.exit(2);
        }

        BotConfig cfg = ConfigLoader.load(Path.of(args[0]));
        log.info("starting bot matrixUserId={} room={}", cfg.matrix.userId, cfg.matrix.room);

        MatrixClient matrix = new MatrixClient(cfg.matrix.homeserverUrl, cfg.matrix.accessToken, cfg.matrix.userId);

        String roomId = matrix.joinAndResolveRoom(cfg.matrix.room);
        log.info("matrix room resolved/joined roomId={}", roomId);

        // Create a server-side filter (spec-compliant) and reuse its filter_id for /sync.
        String filterId = matrix.createFilter(MatrixClient.buildRoomMessageFilter(roomId));
        log.info("matrix sync filter created filterId={}", filterId);

        TranscriptLogger transcript = TranscriptLogger.create(cfg.transcript);

        RetryingMatrixSender sender = new RetryingMatrixSender(matrix, cfg.retry);

        MudClient mud = new MudClient(
                cfg.mud,
                line -> {
                    transcript.logMudToMatrix(line);
                    String sanitized = Sanitizer.sanitizeMudOutput(line);
                    Sanitizer.MxpResult res = Sanitizer.processMxp(sanitized);
                    sender.sendHtml(roomId, res.plain, res.html, line, shouldNotify(line));
                    sendTellHighlight(sender, roomId, res.plain);
                },
                reason -> {
                    String msg = "* MUD disconnected: " + reason;
                    transcript.logSystem(msg);
                    sender.sendText(roomId, msg, true);
                },
                transcript
        );

        MatrixEventProcessor processor = new MatrixEventProcessor(cfg, roomId, sender, mud, transcript);

        String initialSince = null;
        if (cfg.matrix.ignoreInitialTimeline) {
            log.info("matrix performing initial sync to skip history...");
            // Retry initial sync a few times if it fails, but don't block forever if it keeps failing.
            for (int i = 0; i < 5; i++) {
                try {
                    MatrixClient.SyncResponse resp = matrix.sync(null, 0, filterId);
                    initialSince = resp.nextBatch();
                    if (initialSince != null) {
                        log.info("matrix initial sync complete, token={}", initialSince);
                        break;
                    }
                } catch (Exception e) {
                    log.warn("matrix initial sync attempt {} failed: {}", i + 1, e.toString());
                    Thread.sleep(1000);
                }
            }
        }

        MatrixSyncLoop syncLoop = new MatrixSyncLoop(
                matrix,
                roomId,
                filterId,
                cfg.matrix.syncTimeoutMs,
                cfg.matrix.ignoreInitialTimeline,
                processor,
                initialSince
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("shutdown requested");
            try { syncLoop.stop(); } catch (Exception ignored) {}
            try { mud.disconnect("shutdown", null); } catch (Exception ignored) {}
            try { sender.shutdown(); } catch (Exception ignored) {}
            try { transcript.close(); } catch (Exception ignored) {}
        }, "shutdown-hook"));

        syncLoop.start();

        new CountDownLatch(1).await();
    }

    private static boolean shouldNotify(String line) {
        System.out.println("notify = false, line: " + line);
        return false;
    }

    private static void sendTellHighlight(RetryingMatrixSender sender, String roomId, String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return;
        }
        String[] lines = plainText.split("\n", -1);
        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains("<send href='tell ") || lower.contains("<send href=\"tell ")) {
                try {
                    TellHighlightRenderer.HighlightImage image = TellHighlightRenderer.render(line);
                    sender.sendImage(roomId, image.body(), image.data(), "mud-tell.png", image.mimeType(),
                            image.width(), image.height(), false);
                } catch (Exception e) {
                    log.warn("tell highlight render failed err={}", e.toString());
                }
            }
        }
    }
}
