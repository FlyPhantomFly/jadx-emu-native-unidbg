package jadx.emu.ext.unidbg

import jadx.plugins.emu.api.NativeRequest
import jadx.plugins.emu.exec.NativeBridge
import jadx.plugins.emu.exec.NotHandled
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Runs the real ARM64 test library (fixtures/jnitest.S) in unidbg, through the backend. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnidbgBackendTest {
    private val backend = UnidbgBackend(LoggerFactory.getLogger("test"))
    private lateinit var apk: File
    private lateinit var bridge: NativeBridge

    private val all = setOf("triple", "isEven", "mulLong", "name", "length", "same", "bump", "touch").map { "Ljnitest/Native;->$it" }.toSet()

    @BeforeAll
    fun setUp() {
        apk = File.createTempFile("jnitest", ".apk")
        val so = javaClass.getResourceAsStream("/libjnitest.so")!!.readBytes()
        ZipOutputStream(apk.outputStream()).use { z -> z.putNextEntry(ZipEntry("lib/arm64-v8a/libjnitest.so")); z.write(so); z.closeEntry() }
        bridge = backend.open(NativeRequest(apk, all, listOf("libjnitest.so")))
    }

    @AfterAll
    fun tearDown() {
        backend.closeAll()
        apk.delete()
    }

    private fun call(m: String, sig: String, vararg a: Any?, recv: Any? = null): Any? = bridge.call("jnitest/Native", m, sig, a.toList(), recv)

    @Test
    fun anIntegerMethodRuns() {
        assertEquals(21, call("triple", "(I)I", 7))
        assertEquals(-9, call("triple", "(I)I", -3))
    }

    @Test
    fun aBooleanResultComesBackAsABoolean() {
        assertEquals(true, call("isEven", "(I)Z", 10))
        assertEquals(false, call("isEven", "(I)Z", 7))
    }

    @Test
    fun longArgumentsAndResultsSurviveTheRoundTrip() {
        assertEquals(123456789000L, call("mulLong", "(JJ)J", 123456789L, 1000L))
        assertEquals(6_000_000_000L, call("mulLong", "(JJ)J", 3_000_000_000L, 2L))
    }

    @Test
    fun aNativeStringTableDecryptsAtRunTime() {
        assertEquals("alpha", call("name", "(I)Ljava/lang/String;", 0))
        assertEquals("bravo-key", call("name", "(I)Ljava/lang/String;", 1))
        assertEquals("Hello, unidbg!", call("name", "(I)Ljava/lang/String;", 2))
    }

    @Test
    fun aNativeThatReturnsNullGivesNullNotNotHandled() {
        assertNull(call("name", "(I)Ljava/lang/String;", 3))
    }

    @Test
    fun aByteArrayGoesInAndComesBackOut() {
        assertEquals(5, call("length", "([B)I", byteArrayOf(1, 2, 3, 4, 5)))
        assertArrayEquals(byteArrayOf(9, 8, 7), call("same", "([B)[B", byteArrayOf(9, 8, 7)) as ByteArray)
    }

    @Test
    fun anInstanceMethodRunsWithAReceiver() {
        assertEquals(105, call("bump", "(I)I", 5, recv = Any()))
    }

    @Test
    fun aVoidMethodReturnsUnitNotNull() {
        assertSame(Unit, call("touch", "()V"))
    }

    @Test
    fun theSameBridgeCanBeCalledManyTimesWithSteadyResults() {
        repeat(50) { i -> assertEquals(i * 3, call("triple", "(I)I", i)) }
    }

    @Test
    fun anArgumentTypeThatIsNotSupportedIsNotHandledNotGuessed() {
        assertSame(NotHandled, call("triple", "(F)I", 1.5f))
    }

    @Test
    fun aWrongArgumentTypeIsNotHandled() {
        assertSame(NotHandled, call("triple", "(I)I", "seven"))
    }

    @Test
    fun aMethodOutsideTheRequestIsNotHandledEvenThoughTheLibraryHasIt() {
        val narrow = backend.open(NativeRequest(apk, setOf("Ljnitest/Native;->triple"), listOf("libjnitest.so")))
        assertEquals(21, narrow.call("jnitest/Native", "triple", "(I)I", listOf(7), null))
        assertSame(NotHandled, narrow.call("jnitest/Native", "bump", "(I)I", listOf(7), Any()))
    }

    @Test
    fun aBridgeCanBeOpenedWithoutRunningJniOnLoad() {
        val b = backend.open(NativeRequest(apk, all, listOf("libjnitest.so"), options = mapOf("jni-onload" to "false")))
        assertEquals(21, b.call("jnitest/Native", "triple", "(I)I", listOf(7), null))
    }

    @Test
    fun anUnknownAbiIsRefusedBeforeAnythingIsExtracted() {
        val e = assertThrows(IllegalArgumentException::class.java) { backend.open(NativeRequest(apk, all, listOf("libjnitest.so"), abi = "mips")) }
        assertTrue(e.message!!.contains("mips"))
    }

    @Test
    fun aBadMethodNameIsRefusedAtOpen() {
        val e = assertThrows(IllegalArgumentException::class.java) { backend.open(NativeRequest(apk, setOf("jnitest.Native.triple"), listOf("libjnitest.so"))) }
        assertTrue(e.message!!.contains("jnitest.Native.triple"))
    }

    @Test
    fun aMissingLibraryIsRefusedAtOpenWithAHelpfulMessage() {
        val e = assertThrows(IllegalArgumentException::class.java) { backend.open(NativeRequest(apk, all, listOf("libnope.so"))) }
        assertTrue(e.message!!.contains("libnope.so") && e.message!!.contains("libjnitest.so"), e.message)
    }

    @Test
    fun anUnknownOptionIsRefusedAtOpen() {
        assertThrows(IllegalArgumentException::class.java) { backend.open(NativeRequest(apk, all, listOf("libjnitest.so"), options = mapOf("sdkk" to "23"))) }
    }

    private fun workDirs(): Int =
        File(System.getProperty("java.io.tmpdir")).list()!!.count { it.startsWith("jadx-emu-unidbg") }

    @Test
    fun closingABackendDeletesTheLibrariesItExtracted() {
        val before = workDirs()
        val b = UnidbgBackend(LoggerFactory.getLogger("test"))
        b.open(NativeRequest(apk, all, listOf("libjnitest.so")))
        assertEquals(before + 1, workDirs(), "opening extracts into a work folder")
        b.closeAll()
        assertEquals(before, workDirs(), "closing removes it")
        b.closeAll()                       // twice is harmless
        assertEquals(before, workDirs())
    }

    @Test
    fun aFailedOpenLeavesNoWorkFolderBehind() {
        val before = workDirs()
        assertThrows(IllegalArgumentException::class.java) { backend.open(NativeRequest(apk, all, listOf("libnope.so"))) }
        assertEquals(before, workDirs())
    }

    @Test
    fun theBackendIdIsUnidbg() {
        assertEquals("unidbg", backend.id)
    }
}
