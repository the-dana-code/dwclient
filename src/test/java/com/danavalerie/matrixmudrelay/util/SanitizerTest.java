package com.danavalerie.matrixmudrelay.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SanitizerTest {
    @Test
    void stripsControlChars() {
        String s = "hi\u0001there\u007F!";
        assertEquals("hithere!", Sanitizer.sanitizeMudOutput(s));
    }

    @Test
    void stripsNewlinesFromInput() {
        assertEquals("abc", Sanitizer.sanitizeMudInput("a\nb\rc"));
    }
}
