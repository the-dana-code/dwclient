package com.danavalerie.matrixmudrelay.mud;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class MudClientIntegrationTest {

    @Test
    void connectsReadsLineThenDisconnectsOnEof() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            int port = ss.getLocalPort();

            BotConfig.Mud mudCfg = new BotConfig.Mud();
            mudCfg.host = "127.0.0.1";
            mudCfg.port = port;
            mudCfg.charset = "UTF-8";
            mudCfg.connectTimeoutMs = 2000;

            List<String> lines = new CopyOnWriteArrayList<>();
            List<String> disconnects = new CopyOnWriteArrayList<>();

            MudClient client = new MudClient(
                    mudCfg,
                    lines::add,
                    disconnects::add,
                    TranscriptLogger.create(new BotConfig.Transcript())
            );

            Thread serverThread = new Thread(() -> {
                try (Socket s = ss.accept()) {
                    OutputStream out = s.getOutputStream();
                    out.write("welcome\r\n".getBytes());
                    out.flush();
                } catch (Exception ignored) {}
            });
            serverThread.start();

            client.connect();

            Thread.sleep(200);
            assertTrue(lines.contains("welcome"));

            Thread.sleep(200);
            assertFalse(client.isConnected());
            assertTrue(disconnects.size() >= 1);
        }
    }
}
