package com.danavalerie.matrixmudrelay.mud;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.util.Sanitizer;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MudClient {
    private static final Logger log = LoggerFactory.getLogger(MudClient.class);

    public interface MudLineListener {
        void onLine(String line);
    }

    public interface MudDisconnectListener {
        void onDisconnected(String reason);
    }

    private final BotConfig.Mud cfg;
    private final MudLineListener lineListener;
    private final MudDisconnectListener disconnectListener;
    private final TranscriptLogger transcript;

    private final Object lock = new Object();
    private volatile boolean connected = false;

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Thread readerThread;
    private ExecutorService writer;

    public MudClient(BotConfig.Mud cfg,
                     MudLineListener lineListener,
                     MudDisconnectListener disconnectListener,
                     TranscriptLogger transcript) {
        this.cfg = cfg;
        this.lineListener = lineListener;
        this.disconnectListener = disconnectListener;
        this.transcript = transcript;
    }

    public boolean isConnected() {
        return connected;
    }

    public void connect() throws IOException {
        synchronized (lock) {
            if (connected) return;

            socket = new Socket();
            socket.connect(new InetSocketAddress(cfg.host, cfg.port), cfg.connectTimeoutMs);
            socket.setTcpNoDelay(true);

            in = socket.getInputStream();
            out = socket.getOutputStream();

            connected = true;

            // Writer executor: commands only originate from controller message handling.
            writer = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "mud-write");
                t.setDaemon(true);
                return t;
            });

            startReader();
            log.info("mud connected host={} port={}", cfg.host, cfg.port);
        }
    }

    private void startReader() {
        readerThread = new Thread(this::readLoop, "mud-read");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        Charset cs = Charset.forName(cfg.charset);
        TelnetDecoder decoder = new TelnetDecoder(() -> {
            synchronized (lock) { return out; }
        });

        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream(1024);

        try {
            while (true) {
                int b;
                synchronized (lock) {
                    if (!connected || in == null) return;
                    b = in.read();
                }
                if (b == -1) {
                    disconnect("eof");
                    return;
                }

                byte[] decoded = decoder.accept((byte) b);
                for (byte db : decoded) {
                    if (db == (byte) '\n') {
                        String line = lineBuf.toString(cs);
                        lineBuf.reset();
                        line = stripTrailingCR(line);

                        // Sanitization removes control characters.
                        line = Sanitizer.sanitizeMudOutput(line);

                        // Send immediately (including empty lines).
                        lineListener.onLine(line);
                    } else {
                        lineBuf.write(db);
                    }
                }
            }
        } catch (IOException e) {
            disconnect("io_error: " + e.getMessage());
        } catch (Exception e) {
            disconnect("unexpected: " + e.getMessage());
        }
    }

    private static String stripTrailingCR(String s) {
        if (s.endsWith("\r")) return s.substring(0, s.length() - 1);
        return s;
    }

    public void sendLinesFromController(List<String> lines) {
        // Hard gate: never send unless connected at send time.
        synchronized (lock) {
            if (!connected || writer == null) return;
        }
        writer.submit(() -> doWrite(lines));
    }

    private void doWrite(List<String> lines) {
        synchronized (lock) {
            if (!connected || out == null) return;
            try {
                for (String raw : lines) {
                    String line = raw == null ? "" : raw;
                    line = Sanitizer.sanitizeMudInput(line);

                    // MUD line-oriented input; CRLF for telnet compatibility
                    out.write(line.getBytes(Charset.forName(cfg.charset)));
                    out.write('\r');
                    out.write('\n');

                    transcript.logMatrixToMud(line);
                }
                out.flush();
            } catch (IOException e) {
                disconnect("write_error: " + e.getMessage());
            }
        }
    }

    public void disconnect(String reason) {
        Socket s;
        ExecutorService w;
        synchronized (lock) {
            if (!connected) return;
            connected = false;

            s = socket;
            socket = null;

            closeQuietly(in); in = null;
            closeQuietly(out); out = null;

            w = writer;
            writer = null;
        }

        if (w != null) w.shutdownNow();
        if (s != null) closeQuietly(s);

        log.info("mud disconnected reason={}", reason);
        disconnectListener.onDisconnected(reason);
    }

    private static void closeQuietly(Closeable c) {
        try { if (c != null) c.close(); } catch (Exception ignored) {}
    }
    private static void closeQuietly(Socket s) {
        try { if (s != null) s.close(); } catch (Exception ignored) {}
    }
}
