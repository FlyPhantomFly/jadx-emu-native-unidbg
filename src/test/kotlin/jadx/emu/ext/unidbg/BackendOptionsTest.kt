package jadx.emu.ext.unidbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackendOptionsTest {
    @Test
    fun noOptionsGivesTheDefaults() {
        assertEquals(BackendOptions(sdk = 23, verbose = false, jniOnLoad = true), BackendOptions.parse(emptyMap()))
    }

    @Test
    fun optionsAreRead() {
        val o = BackendOptions.parse(mapOf("sdk" to " 19 ", "verbose" to "yes", "jni-onload" to "off"))
        assertEquals(BackendOptions(sdk = 19, verbose = true, jniOnLoad = false), o)
    }

    @Test
    fun anUnknownOptionIsRefusedAndTheMessageListsTheKnownOnes() {
        val e = assertThrows(IllegalArgumentException::class.java) { BackendOptions.parse(mapOf("verbos" to "true")) }
        assertTrue(e.message!!.contains("verbos") && e.message!!.contains("jni-onload"), e.message)
    }

    @Test
    fun aNonNumericSdkIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { BackendOptions.parse(mapOf("sdk" to "latest")) }
    }

    @Test
    fun aFlagThatIsNeitherTrueNorFalseIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { BackendOptions.parse(mapOf("verbose" to "maybe")) }
    }
}
