package com.japanglify.app.domain.polyglos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptDetectorTest {
    @Test fun japanese() {
        val p = ScriptDetector.detect("\u65e5\u672c\u8a9e\u3067\u3059")
        assertEquals(ScriptKind.JAPANESE, p.dominant)
        assertFalse(p.mixed)
    }

    @Test fun greekPolytonic() {
        val p = ScriptDetector.detect("\u1f18\u03bd \u1f00\u03c1\u03c7\u1fc7")
        assertEquals(ScriptKind.GREEK, p.dominant)
    }

    @Test fun hebrew() {
        val p = ScriptDetector.detect("\u05d1\u05bc\u05b0\u05e8\u05b5\u05d0\u05e9\u05c1\u05b4\u05d9\u05ea")
        assertEquals(ScriptKind.HEBREW, p.dominant)
    }

    @Test fun latinOnly() {
        val p = ScriptDetector.detect("hello world")
        assertNull(p.dominant)
        assertTrue(p.isLatinOnly)
    }

    @Test fun mixedDominantJapanese() {
        // More Japanese units than Greek so JA wins dominance under 1.0 policy.
        val p = ScriptDetector.detect(
            "\u65e5\u672c\u8a9e\u306e\u6587\u7ae0 and \u03bb\u03cc\u03b3\u03bf\u03c2"
        )
        assertTrue(p.mixed)
        assertEquals(ScriptKind.JAPANESE, p.dominant)
    }
}