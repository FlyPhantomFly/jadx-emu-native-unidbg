package jadx.emu.ext.unidbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JniTest {
    @Test
    fun argumentTypesAreSplitIncludingArraysAndObjects() {
        assertEquals(listOf("I", "[B", "Ljava/lang/String;", "[[J", "Z"), Jni.argTypes("(I[BLjava/lang/String;[[JZ)V"))
    }

    @Test
    fun aMethodWithNoArgumentsHasNone() {
        assertEquals(emptyList<String>(), Jni.argTypes("()V"))
    }

    @Test
    fun theReturnTypeIsWhateverFollowsTheParenthesis() {
        assertEquals("Ljava/lang/String;", Jni.returnType("(I)Ljava/lang/String;"))
        assertEquals("[B", Jni.returnType("([B)[B"))
        assertEquals("V", Jni.returnType("()V"))
    }

    @Test
    fun anAllowListEntryIsBuiltFromTheEnginesInternalClassName() {
        assertEquals("Lcom/foo/Bar;->decrypt", Jni.key("com/foo/Bar", "decrypt"))
    }

    @Test
    fun methodKeysMustLookLikeClassArrowName() {
        assertTrue(Jni.isMethodKey("Lcom/foo/Bar;->decrypt"))
        assertTrue(Jni.isMethodKey("Lelsm/at;->a"))
        assertFalse(Jni.isMethodKey("com.foo.Bar.decrypt"))
        assertFalse(Jni.isMethodKey("Lcom/foo/Bar;->decrypt(I)I"))
        assertFalse(Jni.isMethodKey("Lcom/foo/Bar;"))
        assertFalse(Jni.isMethodKey(""))
    }
}
