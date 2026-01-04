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
import java.util.ArrayList;
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

    public interface MudGmcpListener {
        void onGmcp(TelnetDecoder.GmcpMessage message);
    }

    public interface MudDisconnectListener {
        void onDisconnected(String reason);
    }

    private final BotConfig.Mud cfg;
    private final MudLineListener lineListener;
    private final MudDisconnectListener disconnectListener;
    private final TranscriptLogger transcript;
    private volatile MudGmcpListener gmcpListener;

    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final AtomicReference<Socket> socket = new AtomicReference<>();
    private final AtomicReference<InputStream> in = new AtomicReference<>();
    private final AtomicReference<OutputStream> out = new AtomicReference<>();
    private final CurrentRoomInfo currentRoomInfo = new CurrentRoomInfo();
    private Thread readerThread;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mud-write");
        t.setDaemon(true);
        return t;
    });
    ;

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

    public void setGmcpListener(MudGmcpListener gmcpListener) {
        this.gmcpListener = gmcpListener;
    }

    public CurrentRoomInfo.Snapshot getCurrentRoomSnapshot() {
        return currentRoomInfo.getSnapshot();
    }

    public void connect() throws IOException {
        if (!connected.compareAndSet(false, true)) {
            return;
        }

        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(cfg.host, cfg.port), cfg.connectTimeoutMs);
            s.setTcpNoDelay(true);
            s.setSoTimeout(250);

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
        TelnetDecoder decoder = new TelnetDecoder(out::get, (opt, data) -> {
            if (opt == (byte) 201) { // GMCP
                String msg = new String(data, cs);
                //log.debug("Received GMCP: {}", msg);
                TelnetDecoder.GmcpMessage parsed = TelnetDecoder.parseGmcpMessage(msg);
                if (parsed != null) {
                    currentRoomInfo.update(parsed.command(), parsed.payload());
                    MudGmcpListener listener = gmcpListener;
                    if (listener != null) {
                        listener.onGmcp(parsed);
                    }
                }
            }
        });

        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream(1024);
        List<String> messageBuffer = new ArrayList<>();
        long bufferStartTime = 0;

        try {
            InputStream currentIn = in.get();
            while (connected.get() && currentIn != null) {
                int b;
                try {
                    b = currentIn.read();
                } catch (java.net.SocketTimeoutException e) {
                    if (bufferStartTime > 0) {
                        long now = System.currentTimeMillis();
                        boolean maxTimeout = (now - bufferStartTime > 1000);
                        boolean atLineBreak = (lineBuf.size() == 0);
                        if (maxTimeout || atLineBreak) {
                            flushBuffer(messageBuffer, lineBuf, cs);
                            bufferStartTime = 0;
                        }
                    }
                    continue;
                }

                if (b == -1) {
                    disconnect("eof", null);
                    return;
                }

                byte[] decoded = decoder.accept((byte) b);
                for (byte db : decoded) {
                    if (bufferStartTime == 0) bufferStartTime = System.currentTimeMillis();
                    if (db == (byte) '\n') {
                        appendToMessageBuffer(messageBuffer, lineBuf, cs);
                    } else {
                        lineBuf.write(db);
                    }
                }

                if (bufferStartTime > 0 && System.currentTimeMillis() - bufferStartTime > 1000) {
                    flushBuffer(messageBuffer, lineBuf, cs);
                    bufferStartTime = 0;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            if (connected.get()) {
                disconnect("io_error", e);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (connected.get()) {
                disconnect("unexpected exception", e);
            }
        }
    }

    private void flushBuffer(List<String> messageBuffer, ByteArrayOutputStream lineBuf, Charset cs) {
        if (lineBuf.size() > 0) {
            appendToMessageBuffer(messageBuffer, lineBuf, cs);
        }
        if (!messageBuffer.isEmpty()) {
            String combined = String.join("\n", messageBuffer);
            lineListener.onLine(combined);
            messageBuffer.clear();
        }
    }

    private void appendToMessageBuffer(List<String> messageBuffer, ByteArrayOutputStream lineBuf, Charset cs) {
        String line = lineBuf.toString(cs);
        lineBuf.reset();
        line = stripTrailingCR(line);
        messageBuffer.add(line);
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

                log.info("Writing line: " + raw);
                transcript.logClientToMud(line);

                // MUD line-oriented input; CRLF for telnet compatibility
                currentOut.write(line.getBytes(Charset.forName(cfg.charset)));
//                currentOut.write('\r');
                currentOut.write('\n');
            }
            currentOut.flush();
        } catch (IOException e) {
            disconnect("write_error: " + e.getMessage(), e);
        }
    }

    public void disconnect(String reason, Exception e) {
        if (!connected.compareAndSet(true, false)) {
            return;
        }

        InputStream i = in.getAndSet(null);
        OutputStream o = out.getAndSet(null);
        Socket s = socket.getAndSet(null);

        closeQuietly(i);
        closeQuietly(o);
        closeQuietly(s);

        log.info("mud disconnected reason=" + reason, e);
        disconnectListener.onDisconnected(reason);
    }

    private static void closeQuietly(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (Exception ignored) {
        }
    }

    private static void closeQuietly(Socket s) {
        try {
            if (s != null) s.close();
        } catch (Exception ignored) {
        }
    }
}
