package com.danavalerie.matrixmudrelay.mud;
  
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Supplier;

/**
 * Minimal Telnet negotiation tolerance:
 * - Strip IAC sequences from output.
 * - Reply WONT to DO, reply DONT to WILL (unsupported options).
 * - Ignore subnegotiation blocks.
 */
public final class TelnetDecoder {
    private static final byte IAC  = (byte)255;
    private static final byte DONT = (byte)254;
    private static final byte DO   = (byte)253;
    private static final byte WONT = (byte)252;
    private static final byte WILL = (byte)251;
    private static final byte SB   = (byte)250;
    private static final byte SE   = (byte)240;

    private static final byte TTYPE = (byte)24;
    private static final byte MXP   = (byte)91;
    private static final byte GMCP  = (byte)201;

    private static final byte IS   = (byte)0;
    private static final byte SEND = (byte)1;

    private enum State { DATA, IAC, OPT, SB, SB_DATA, SB_IAC }

    private State state = State.DATA;
    private byte lastCmd = 0;
    private byte sbOpt = 0;
    private final ByteArrayOutputStream sbBuf = new ByteArrayOutputStream();

    private final Supplier<OutputStream> outSupplier;
    private final SubnegotiationListener subnegotiationListener;

    public interface SubnegotiationListener {
        void onSubnegotiation(byte opt, byte[] data);
    }

    public TelnetDecoder(Supplier<OutputStream> outSupplier, SubnegotiationListener subnegotiationListener) {
        this.outSupplier = outSupplier;
        this.subnegotiationListener = subnegotiationListener;
    }

    public byte[] accept(byte b) throws IOException {
        switch (state) {
            case DATA -> {
                if (b == IAC) { state = State.IAC; return new byte[0]; }
                return new byte[]{ b };
            }
            case IAC -> {
                if (b == IAC) { // escaped 255
                    state = State.DATA;
                    return new byte[]{ IAC };
                }
                if (b == DO || b == DONT || b == WILL || b == WONT) {
                    lastCmd = b;
                    state = State.OPT;
                    return new byte[0];
                }
                if (b == SB) {
                    state = State.SB;
                    return new byte[0];
                }
                // Other telnet commands: ignore
                state = State.DATA;
                return new byte[0];
            }
            case OPT -> {
                byte opt = b;
                handleNegotiation(lastCmd, opt);
                state = State.DATA;
                return new byte[0];
            }
            case SB -> {
                sbOpt = b;
                sbBuf.reset();
                state = State.SB_DATA;
                return new byte[0];
            }
            case SB_DATA -> {
                if (b == IAC) { state = State.SB_IAC; }
                else { sbBuf.write(b); }
                return new byte[0];
            }
            case SB_IAC -> {
                if (b == SE) {
                    handleSubnegotiation(sbOpt, sbBuf.toByteArray());
                    state = State.DATA;
                } else if (b == IAC) {
                    sbBuf.write(IAC);
                    state = State.SB_DATA;
                } else {
                    // unexpected, but try to recover
                    state = State.SB_DATA;
                }
                return new byte[0];
            }
        }
        state = State.DATA;
        return new byte[0];
    }

    private void handleNegotiation(byte cmd, byte opt) throws IOException {
        OutputStream out = outSupplier.get();
        if (out == null) return;

        if (cmd == DO) {
            if (opt == MXP || opt == TTYPE || opt == GMCP) {
                send(out, WILL, opt);
            } else {
                send(out, WONT, opt);
            }
        } else if (cmd == WILL) {
            if (opt == MXP || opt == GMCP) {
                send(out, DO, opt);
                if (opt == GMCP) {
                    sendGmcpHandshake(out);
                }
            } else {
                send(out, DONT, opt);
            }
        }
    }

    private void handleSubnegotiation(byte opt, byte[] data) throws IOException {
        OutputStream out = outSupplier.get();
        if (out == null) return;

        if (opt == TTYPE && data.length > 0 && data[0] == SEND) {
            out.write(IAC);
            out.write(SB);
            out.write(TTYPE);
            out.write(IS);
            out.write("ANSI".getBytes()); // Could be "MTTS 9" if we want to be fancy
            out.write(IAC);
            out.write(SE);
            out.flush();
        } else if (opt == GMCP) {
            if (subnegotiationListener != null) {
                subnegotiationListener.onSubnegotiation(opt, data);
            }
            String msg = new String(data);
            System.out.println("[DEBUG_LOG] GMCP: " + msg);
        }
    }

    private void sendGmcpHandshake(OutputStream out) throws IOException {
        // Send Core.Hello and Core.Supports.Set
        sendGmcp(out, "Core.Hello { \"client\": \"dwclient-bot\", \"version\": \"1.0.0\" }");
        sendGmcp(out, "Core.Supports.Set [ \"room.info 1\", \"room.map 1\", \"char.vitals 1\" ]");
    }

    private void sendGmcp(OutputStream out, String json) throws IOException {
        out.write(IAC);
        out.write(SB);
        out.write(GMCP);
        out.write(json.getBytes());
        out.write(IAC);
        out.write(SE);
        out.flush();
    }

    private static void send(OutputStream out, byte cmd, byte opt) throws IOException {
        out.write(IAC);
        out.write(cmd);
        out.write(opt);
        out.flush();
    }
}
