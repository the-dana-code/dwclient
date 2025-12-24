package com.danavalerie.matrixmudrelay.mud;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class TelnetDecoderTest {
    @Test
    void stripsIacNegotiation() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TelnetDecoder d = new TelnetDecoder(() -> out);

        // IAC DO 1 should produce IAC WONT 1 and no decoded data bytes.
        byte[] r1 = d.accept((byte)255);
        byte[] r2 = d.accept((byte)253);
        byte[] r3 = d.accept((byte)1);

        assertEquals(0, r1.length);
        assertEquals(0, r2.length);
        assertEquals(0, r3.length);

        byte[] resp = out.toByteArray();
        assertArrayEquals(new byte[]{(byte)255, (byte)252, (byte)1}, resp);
    }

    @Test
    void passesThroughData() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TelnetDecoder d = new TelnetDecoder(() -> out);

        byte[] r = d.accept((byte)'A');
        assertArrayEquals(new byte[]{(byte)'A'}, r);
    }
}
