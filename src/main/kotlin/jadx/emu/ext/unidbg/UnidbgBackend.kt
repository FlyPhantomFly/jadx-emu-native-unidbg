package jadx.emu.ext.unidbg

import jadx.plugins.emu.api.NativeBackend
import jadx.plugins.emu.api.NativeRequest
import jadx.plugins.emu.exec.NativeBridge
import org.slf4j.Logger
import java.nio.file.Files

/** The `unidbg` native backend. One emulator is created for each opened bridge, and only when its first native runs. */
internal class UnidbgBackend(private val log: Logger) : NativeBackend {
    override val id = "unidbg"

    private val runners = ArrayList<NativeRunner>()

    override fun open(request: NativeRequest): NativeBridge {
        val is64 = when (request.abi) {
            "arm64-v8a" -> true
            "armeabi-v7a" -> false
            else -> throw IllegalArgumentException("unsupported abi '${request.abi}'; use arm64-v8a or armeabi-v7a")
        }
        for (m in request.methods) {
            require(Jni.isMethodKey(m)) { "method '$m' should look like Lcom/foo/Bar;->decrypt" }
        }
        val options = BackendOptions.parse(request.options)
        val dir = Files.createTempDirectory("jadx-emu-unidbg").toFile()
        val libs = try {
            ApkLibs.extract(request.apk, request.abi, request.libs, dir)
        } catch (t: Throwable) {
            dir.deleteRecursively()
            throw t
        }
        log.info("unidbg: {} library(ies) from {} [{}], will run {} method(s)", libs.size, request.apk.name, request.abi, request.methods.size)
        val runner = UnidbgRunner(libs, is64, options, dir)
        synchronized(runners) { runners += runner }
        return UnidbgBridge(request.methods, runner, log)
    }

    fun closeAll() {
        val all = synchronized(runners) { runners.toList().also { runners.clear() } }
        all.forEach { runCatching { it.close() } }
    }
}
