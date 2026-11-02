package jadx.emu.ext.unidbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkLibsTest {
    @TempDir
    lateinit var dir: File

    private fun apk(vararg entries: Pair<String, String>): File {
        val f = File(dir, "app.apk")
        ZipOutputStream(f.outputStream()).use { z ->
            for ((name, text) in entries) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
        }
        return f
    }

    private fun out() = File(dir, "out").also { it.mkdirs() }

    @Test
    fun theNamedLibrariesAreExtractedInTheRequestedOrder() {
        val apk = apk("lib/arm64-v8a/libb.so" to "B", "lib/arm64-v8a/liba.so" to "A", "lib/x86/liba.so" to "X")
        val files = ApkLibs.extract(apk, "arm64-v8a", listOf("liba.so", "libb.so"), out())
        assertEquals(listOf("liba.so", "libb.so"), files.map { it.name })
        assertEquals("A", files[0].readText())      // the arm64 one, not the x86 one
        assertEquals("B", files[1].readText())
    }

    @Test
    fun onlyTheNamedLibrariesAreExtracted() {
        val apk = apk("lib/arm64-v8a/liba.so" to "A", "lib/arm64-v8a/libother.so" to "O")
        ApkLibs.extract(apk, "arm64-v8a", listOf("liba.so"), out())
        assertEquals(listOf("liba.so"), out().list()!!.toList())
    }

    @Test
    fun aMissingLibraryIsReportedTogetherWithWhatTheApkDoesHave() {
        val apk = apk("lib/arm64-v8a/liba.so" to "A", "lib/arm64-v8a/libb.so" to "B")
        val e = assertThrows(IllegalArgumentException::class.java) { ApkLibs.extract(apk, "arm64-v8a", listOf("libc.so"), out()) }
        assertTrue(e.message!!.contains("libc.so") && e.message!!.contains("liba.so") && e.message!!.contains("libb.so"), e.message)
    }

    @Test
    fun aMissingAbiNamesTheAbisThatArePresent() {
        val apk = apk("lib/x86/liba.so" to "A", "lib/armeabi-v7a/liba.so" to "A")
        val e = assertThrows(IllegalArgumentException::class.java) { ApkLibs.extract(apk, "arm64-v8a", listOf("liba.so"), out()) }
        assertTrue(e.message!!.contains("x86") && e.message!!.contains("armeabi-v7a"), e.message)
    }

    @Test
    fun anApkWithoutNativeLibrariesSaysSo() {
        val apk = apk("classes.dex" to "x")
        val e = assertThrows(IllegalArgumentException::class.java) { ApkLibs.extract(apk, "arm64-v8a", listOf("liba.so"), out()) }
        assertTrue(e.message!!.contains("none"), e.message)
    }

    @Test
    fun aLibraryNameWithAPathIsRefused() {
        val apk = apk("lib/arm64-v8a/liba.so" to "A")
        assertThrows(IllegalArgumentException::class.java) { ApkLibs.extract(apk, "arm64-v8a", listOf("../liba.so"), out()) }
        assertThrows(IllegalArgumentException::class.java) { ApkLibs.extract(apk, "arm64-v8a", listOf("sub/liba.so"), out()) }
    }

    @Test
    fun aCraftedEntryNamedWithDotDotCannotWriteOutsideTheWorkFolder() {
        // the entry really is called lib/arm64-v8a/../evil.so, so a lookup by that name would succeed
        val apk = apk("lib/arm64-v8a/liba.so" to "A", "lib/arm64-v8a/../evil.so" to "EVIL")
        val target = out()
        assertThrows(IllegalArgumentException::class.java) { ApkLibs.extract(apk, "arm64-v8a", listOf("../evil.so"), target) }
        assertTrue(!File(dir, "evil.so").exists(), "nothing may be written next to the work folder")
        assertTrue(target.list()!!.isEmpty())
    }

    @Test
    fun aFileThatIsNotAZipIsRefusedClearly() {
        val f = File(dir, "x.apk").also { it.writeText("not a zip") }
        assertThrows(IllegalArgumentException::class.java) { ApkLibs.extract(f, "arm64-v8a", listOf("liba.so"), out()) }
    }

    @Test
    fun aMissingFileIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { ApkLibs.extract(File(dir, "none.apk"), "arm64-v8a", listOf("liba.so"), out()) }
    }
}
