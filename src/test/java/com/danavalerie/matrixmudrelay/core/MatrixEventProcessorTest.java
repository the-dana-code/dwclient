package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.matrix.RetryingMatrixSender;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MatrixEventProcessorTest {
    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void controllerOnlyAndDisconnectedGating() throws Exception {
        BotConfig cfg = new BotConfig();
        cfg.matrix.userId = "@bot:hs";
        cfg.matrix.controllingUserId = "@dana:hs";
        cfg.aliases = Map.of("x", List.of("look"));

        List<String> sentToMatrix = new ArrayList<>();

        RetryingMatrixSender sender = new RetryingMatrixSender(null, cfg.retry) {
            @Override public void sendText(String roomId, String body) { sentToMatrix.add(body); }
            @Override public void shutdown() {}
        };

        MudClient mud = new MudClient(cfg.mud, l -> {}, r -> {}, TranscriptLogger.create(cfg.transcript)) {
            @Override public boolean isConnected() { return false; }
            @Override public void sendLinesFromController(List<String> lines) { fail("must not send while disconnected"); }
        };

        MatrixEventProcessor p = new MatrixEventProcessor(cfg, "!room", sender, mud, TranscriptLogger.create(cfg.transcript));

        p.onMatrixEvent(event("@dana:hs", "look"));
        assertTrue(sentToMatrix.get(0).contains("Disconnected"));

        sentToMatrix.clear();
        p.onMatrixEvent(event("@other:hs", "connect"));
        assertEquals(0, sentToMatrix.size());
    }

    private static ObjectNode event(String sender, String body) {
        ObjectNode ev = M.createObjectNode();
        ev.put("type", "m.room.message");
        ev.put("sender", sender);
        ObjectNode content = ev.putObject("content");
        content.put("msgtype", "m.text");
        content.put("body", body);
        return ev;
    }
}
