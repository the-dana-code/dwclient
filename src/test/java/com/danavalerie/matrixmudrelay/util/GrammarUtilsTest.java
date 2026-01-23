/*
 * Lesa's Discworld MUD client.
 * Copyright (C) 2026 Dana Reese
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
        assertEquals(List.of("bluebird of happiness bathing suit"), GrammarUtils.singularizePhrase("bluebird of happiness bathing suits"));
        assertEquals(List.of("wooden axe"), GrammarUtils.singularizePhrase("wooden axes"));
        assertEquals(List.of("iron pickaxe"), GrammarUtils.singularizePhrase("iron pickaxes"));
        assertEquals(List.of("tax", "taxe"), GrammarUtils.singularizePhrase("taxes"));
        assertEquals(List.of("box", "boxe"), GrammarUtils.singularizePhrase("boxes"));
        assertEquals(List.of(), GrammarUtils.singularizePhrase("bronze sagaris"));
        
        // Ensure "set of" and "pair of" don't over-singularize the tail
        assertEquals(List.of(), GrammarUtils.singularizePhrase("set of shiny gold handcuffs"));
        assertEquals(List.of("set of shiny gold handcuffs"), GrammarUtils.singularizePhrase("sets of shiny gold handcuffs"));
        assertEquals(List.of(), GrammarUtils.singularizePhrase("pair of airy white cotton trousers"));
    }

    @Test
    void testSingularizeWord() {
        assertTrue(GrammarUtils.singularizeWord("flies").contains("fly"));
        assertTrue(GrammarUtils.singularizeWord("potatoes").contains("potato"));
        assertTrue(GrammarUtils.singularizeWord("leaves").contains("leaf"));
        assertTrue(GrammarUtils.singularizeWord("firemen").contains("fireman"));
        assertTrue(GrammarUtils.singularizeWord("auloi").contains("aulos"));
        assertTrue(GrammarUtils.singularizeWord("xiphoi").contains("xiphos"));
        assertFalse(GrammarUtils.singularizeWord("sagaris").contains("sagari"));
        
        // Case preservation
        assertTrue(GrammarUtils.singularizeWord("Xiphoi").contains("Xiphos"));
        assertTrue(GrammarUtils.singularizeWord("Flies").contains("Fly"));
    }
    
    @Test
    void testSpecialCases() {
        assertEquals(List.of("petit fours"), GrammarUtils.singularizePhrase("petits fours"));
        assertEquals(List.of("main gauche"), GrammarUtils.singularizePhrase("mains gauches"));
        assertEquals(List.of("blue fluffy blanket with fluffy bunnies on"), 
                GrammarUtils.singularizePhrase("blue fluffy blankets with fluffy bunnies on them"));
    }
}

