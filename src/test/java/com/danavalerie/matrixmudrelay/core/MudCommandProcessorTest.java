package com.danavalerie.matrixmudrelay.core;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MudCommandProcessorTest {

    @Test
    void testSingularizePhrase() throws Exception {
        Method method = MudCommandProcessor.class.getDeclaredMethod("singularizePhrase", String.class);
        method.setAccessible(true);

        assertEquals(List.of("pair of red socks"), method.invoke(null, "pairs of red socks"));
        assertEquals(List.of("packet of tea"), method.invoke(null, "packets of tea"));
        assertEquals(List.of("tube of glue"), method.invoke(null, "tubes of glue"));
        assertEquals(List.of("game of chess"), method.invoke(null, "games of chess"));
        assertEquals(List.of("red beach towel"), method.invoke(null, "red beach towels"));
    }

    @Test
    void testSingularizeWord() throws Exception {
        Method method = MudCommandProcessor.class.getDeclaredMethod("singularizeWord", String.class);
        method.setAccessible(true);

        // ies
        assertTrue(((List<String>) method.invoke(null, "flies")).contains("fly"));
        assertTrue(((List<String>) method.invoke(null, "pies")).contains("pie"));

        // oes
        assertTrue(((List<String>) method.invoke(null, "potatoes")).contains("potato"));
        assertTrue(((List<String>) method.invoke(null, "shoes")).contains("shoe"));

        // ves
        assertTrue(((List<String>) method.invoke(null, "leaves")).contains("leaf"));
        assertTrue(((List<String>) method.invoke(null, "knives")).contains("knife"));

        // men
        assertTrue(((List<String>) method.invoke(null, "firemen")).contains("fireman"));

        // auloi
        assertTrue(((List<String>) method.invoke(null, "auloi")).contains("aulos"));
    }
}
