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

package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.util.GrammarUtils;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MudCommandProcessorTest {

    @Test
    void testSingularizePhrase() {
        assertEquals(List.of("pair of red socks"), GrammarUtils.singularizePhrase("pairs of red socks"));
        assertEquals(List.of("packet of tea"), GrammarUtils.singularizePhrase("packets of tea"));
        assertEquals(List.of("tube of glue"), GrammarUtils.singularizePhrase("tubes of glue"));
        assertEquals(List.of("game of chess"), GrammarUtils.singularizePhrase("games of chess"));
        assertEquals(List.of("red beach towel"), GrammarUtils.singularizePhrase("red beach towels"));
    }

    @Test
    void testSingularizeWord() {
        // ies
        assertTrue(GrammarUtils.singularizeWord("flies").contains("fly"));
        assertTrue(GrammarUtils.singularizeWord("pies").contains("pie"));

        // oes
        assertTrue(GrammarUtils.singularizeWord("potatoes").contains("potato"));
        assertTrue(GrammarUtils.singularizeWord("shoes").contains("shoe"));

        // ves
        assertTrue(GrammarUtils.singularizeWord("leaves").contains("leaf"));
        assertTrue(GrammarUtils.singularizeWord("knives").contains("knife"));

        // men
        assertTrue(GrammarUtils.singularizeWord("firemen").contains("fireman"));

        // auloi
        assertTrue(GrammarUtils.singularizeWord("auloi").contains("aulos"));
    }
}

