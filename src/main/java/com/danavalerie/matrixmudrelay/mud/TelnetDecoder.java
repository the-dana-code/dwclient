package com.danavalerie.matrixmudrelay.mud;

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

    private static final byte MXP  = (byte)91;

    private enum State { DATA, IAC, OPT, SB, SB_IAC }

    private State state = State.DATA;
    private byte lastCmd = 0;

    private final Supplier<OutputStream> outSupplier;

    public TelnetDecoder(Supplier<OutputStream> outSupplier) {
        this.outSupplier = outSupplier;
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
                if (b == IAC) { state = State.SB_IAC; }
                return new byte[0];
            }
            case SB_IAC -> {
                if (b == SE) {
                    state = State.DATA;
                } else if (b == IAC) {
                    state = State.SB;
                } else {
                    state = State.SB;
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
            if (opt == MXP) {
                send(out, WILL, opt);
            } else {
                send(out, WONT, opt);
            }
        } else if (cmd == WILL) {
            if (opt == MXP) {
                send(out, DO, opt);
            } else {
                send(out, DONT, opt);
            }
        } else {
            // DONT/WONT => ignore
        }
    }

    private static void send(OutputStream out, byte cmd, byte opt) throws IOException {
        out.write(IAC);
        out.write(cmd);
        out.write(opt);
        out.flush();
    }
}
