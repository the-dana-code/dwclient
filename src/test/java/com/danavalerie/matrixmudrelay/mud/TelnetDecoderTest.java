package com.danavalerie.matrixmudrelay.mud;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

public class TelnetDecoderTest {

    @Test
    public void testMXPNegotiation() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TelnetDecoder decoder = new TelnetDecoder(() -> out);

        // IAC DO MXP (255 253 91)
        decoder.accept((byte) 255);
        decoder.accept((byte) 253);
        byte[] result = decoder.accept((byte) 91);

        assertEquals(0, result.length);
        byte[] sent = out.toByteArray();
        // Should respond IAC WILL MXP (255 251 91)
        assertArrayEquals(new byte[]{(byte) 255, (byte) 251, (byte) 91}, sent);
    }

    @Test
    public void testOtherNegotiation() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TelnetDecoder decoder = new TelnetDecoder(() -> out);

        // IAC DO TTYPE (255 253 24)
        decoder.accept((byte) 255);
        decoder.accept((byte) 253);
        byte[] result = decoder.accept((byte) 24);

        assertEquals(0, result.length);
        byte[] sent = out.toByteArray();
        // Should respond IAC WONT TTYPE (255 252 24)
        assertArrayEquals(new byte[]{(byte) 255, (byte) 252, (byte) 24}, sent);
    }
}
