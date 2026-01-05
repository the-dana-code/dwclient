package com.danavalerie.matrixmudrelay.mud;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.util.Sanitizer;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
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
        DecodeState decodeState = new DecodeState(cs);
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

        try {
            InputStream currentIn = in.get();
            while (connected.get() && currentIn != null) {
                int b;
                try {
                    b = currentIn.read();
                } catch (java.net.SocketTimeoutException e) {
                    continue;
                }

                if (b == -1) {
                    disconnect("eof", null);
                    return;
                }

                byte[] decoded = decoder.accept((byte) b);
                String text = decodeState.append(decoded);
                if (!text.isEmpty()) {
                    lineListener.onLine(text);
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

    private static final int DECODE_BUFFER_SIZE = 8192;

    private static final class DecodeState {
        private final CharsetDecoder decoder;
        private ByteBuffer pending;
        private final CharBuffer chars;

        private DecodeState(Charset cs) {
            this.decoder = cs.newDecoder();
            this.pending = ByteBuffer.allocate(DECODE_BUFFER_SIZE);
            this.chars = CharBuffer.allocate(DECODE_BUFFER_SIZE);
        }

        private String append(byte[] decoded) {
            if (decoded.length == 0) {
                return "";
            }
            ensureCapacity(decoded.length);
            pending.put(decoded);
            pending.flip();

            StringBuilder out = new StringBuilder();
            while (true) {
                chars.clear();
                CoderResult result = decoder.decode(pending, chars, false);
                chars.flip();
                if (chars.hasRemaining()) {
                    out.append(chars);
                }
                if (result.isOverflow()) {
                    continue;
                }
                if (result.isUnderflow()) {
                    break;
                }
                if (result.isError()) {
                    throw new IllegalArgumentException("Failed to decode mud stream: " + result);
                }
            }
            pending.compact();
            return stripCarriageReturns(out);
        }

        private void ensureCapacity(int incoming) {
            if (pending.remaining() >= incoming) {
                return;
            }
            int required = pending.position() + incoming;
            int newSize = pending.capacity();
            while (newSize < required) {
                newSize *= 2;
            }
            ByteBuffer resized = ByteBuffer.allocate(newSize);
            pending.flip();
            resized.put(pending);
            pending = resized;
        }

        private static String stripCarriageReturns(CharSequence input) {
            int length = input.length();
            boolean hasCarriageReturn = false;
            for (int i = 0; i < length; i++) {
                if (input.charAt(i) == '\r') {
                    hasCarriageReturn = true;
                    break;
                }
            }
            if (!hasCarriageReturn) {
                return input.toString();
            }
            StringBuilder filtered = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                char c = input.charAt(i);
                if (c != '\r') {
                    filtered.append(c);
                }
            }
            return filtered.toString();
        }
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
