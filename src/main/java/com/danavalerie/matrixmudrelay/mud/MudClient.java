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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final AtomicReference<Socket> socket = new AtomicReference<>();
    private final AtomicReference<InputStream> in = new AtomicReference<>();
    private final AtomicReference<OutputStream> out = new AtomicReference<>();
    private Thread readerThread;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mud-write");
        t.setDaemon(true);
        return t;
    });;

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
        return connected.get();
    }

    public void connect() throws IOException {
        if (!connected.compareAndSet(false, true)) {
            return;
        }

        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(cfg.host, cfg.port), cfg.connectTimeoutMs);
            s.setTcpNoDelay(true);

            socket.set(s);
            in.set(s.getInputStream());
            out.set(s.getOutputStream());

            startReader();
            log.info("mud connected host={} port={}", cfg.host, cfg.port);
        } catch (IOException e) {
            connected.set(false);
            throw e;
        }
    }

    private void startReader() {
        readerThread = new Thread(this::readLoop, "mud-read");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        Charset cs = Charset.forName(cfg.charset);
        TelnetDecoder decoder = new TelnetDecoder(out::get);

        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream(1024);

        try {
            InputStream currentIn = in.get();
            while (connected.get() && currentIn != null) {
                int b = currentIn.read();
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
            if (connected.get()) {
                disconnect("io_error: " + e.getMessage());
            }
        } catch (Exception e) {
            if (connected.get()) {
                disconnect("unexpected: " + e.getMessage());
            }
        }
    }

    private static String stripTrailingCR(String s) {
        if (s.endsWith("\r")) return s.substring(0, s.length() - 1);
        return s;
    }

    public void sendLinesFromController(List<String> lines) {
        // Hard gate: never send unless connected at send time.
        if (!connected.get()) {
            throw new IllegalStateException("Not connected to MUD");
        }
        ExecutorService w = writer;
        if (w == null) {
            throw new IllegalStateException("Writer not initialized");
        }
        w.submit(() -> doWrite(lines));
    }

    private void doWrite(List<String> lines) {
        OutputStream currentOut = out.get();
        if (!connected.get() || currentOut == null) return;
        try {
            for (String raw : lines) {
                String line = raw == null ? "" : raw;
                line = Sanitizer.sanitizeMudInput(line);

                // MUD line-oriented input; CRLF for telnet compatibility
                currentOut.write(line.getBytes(Charset.forName(cfg.charset)));
                currentOut.write('\r');
                currentOut.write('\n');

                transcript.logMatrixToMud(line);
            }
            currentOut.flush();
        } catch (IOException e) {
            disconnect("write_error: " + e.getMessage());
        }
    }

    public void disconnect(String reason) {
        if (!connected.compareAndSet(true, false)) {
            return;
        }

        InputStream i = in.getAndSet(null);
        OutputStream o = out.getAndSet(null);
        Socket s = socket.getAndSet(null);

        closeQuietly(i);
        closeQuietly(o);
        closeQuietly(s);

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
