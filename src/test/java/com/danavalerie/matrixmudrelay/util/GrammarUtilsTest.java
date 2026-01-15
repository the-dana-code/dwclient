package com.danavalerie.matrixmudrelay.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GrammarUtilsTest {

    @Test
    void testSingularizePhrase() {
        assertEquals(List.of("pair of red socks"), GrammarUtils.singularizePhrase("pairs of red socks"));
        assertEquals(List.of("packet of tea"), GrammarUtils.singularizePhrase("packets of tea"));
        assertEquals(List.of("tube of glue"), GrammarUtils.singularizePhrase("tubes of glue"));
        assertEquals(List.of("game of chess"), GrammarUtils.singularizePhrase("games of chess"));
        assertEquals(List.of("red beach towel"), GrammarUtils.singularizePhrase("red beach towels"));
        assertEquals(List.of("steel xiphos"), GrammarUtils.singularizePhrase("steel xiphoi"));
        assertEquals(List.of("aulos"), GrammarUtils.singularizePhrase("auloi"));
        assertEquals(List.of("small blue flag with bolognas"), GrammarUtils.singularizePhrase("small blue flags with bolognas"));
        assertEquals(List.of("box of chocolates", "boxe of chocolates"), GrammarUtils.singularizePhrase("boxes of chocolates"));
        assertEquals(List.of("key to the door"), GrammarUtils.singularizePhrase("keys to the door"));
    }

    @Test
    void testSingularizeWord() {
        assertTrue(GrammarUtils.singularizeWord("flies").contains("fly"));
        assertTrue(GrammarUtils.singularizeWord("potatoes").contains("potato"));
        assertTrue(GrammarUtils.singularizeWord("leaves").contains("leaf"));
        assertTrue(GrammarUtils.singularizeWord("firemen").contains("fireman"));
        assertTrue(GrammarUtils.singularizeWord("auloi").contains("aulos"));
        assertTrue(GrammarUtils.singularizeWord("xiphoi").contains("xiphos"));
        
        // Case preservation
        assertTrue(GrammarUtils.singularizeWord("Xiphoi").contains("Xiphos"));
        assertTrue(GrammarUtils.singularizeWord("Flies").contains("Fly"));
    }
    
    @Test
    void testSpecialCases() {
        assertEquals(List.of("petit fours"), GrammarUtils.singularizePhrase("petits fours"));
    }
}
