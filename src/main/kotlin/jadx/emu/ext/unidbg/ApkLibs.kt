package jadx.emu.ext.unidbg

import java.io.File
import java.util.zip.ZipFile

/** Pulls the named native libraries for one ABI out of an apk. */
internal object ApkLibs {
    /**
     * @return the extracted files in the order of [names], so the primary library stays first
     * @throws IllegalArgumentException when the apk, the ABI folder or a library is missing; the message says what the apk does have
     */
    fun extract(apk: File, abi: String, names: List<String>, into: File): List<File> {
        require(apk.isFile) { "no such file: $apk" }
        for (n in names) require(n.isNotBlank() && '/' !in n && '\\' !in n && n != "." && n != "..") { "library must be a plain file name, got '$n'" }
        val prefix = "lib/$abi/"
        val out = ArrayList<File>()
        val zip = try {
            ZipFile(apk)
        } catch (e: java.io.IOException) {
            throw IllegalArgumentException("${apk.name} is not a readable zip/apk: ${e.message}", e)
        }
        zip.use { z ->
            val libs = z.entries().asSequence().map { it.name }.filter { it.startsWith("lib/") && it.endsWith(".so") }.toList()
            val inAbi = libs.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.filter { '/' !in it }
            if (inAbi.isEmpty()) {
                val abis = libs.map { it.removePrefix("lib/").substringBefore('/') }.distinct().sorted()
                throw IllegalArgumentException("${apk.name} has no libraries in $prefix; ABIs present: ${abis.ifEmpty { listOf("none") }.joinToString()}")
            }
            for (name in names) {
                val entry = z.getEntry(prefix + name)
                    ?: throw IllegalArgumentException("$name is not in $prefix of ${apk.name}; it has: ${inAbi.sorted().joinToString()}")
                val dst = File(into, name)
                z.getInputStream(entry).use { ins -> dst.outputStream().use { ins.copyTo(it) } }
                out.add(dst)
            }
        }
        return out
    }
}
