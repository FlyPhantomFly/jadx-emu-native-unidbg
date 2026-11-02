package jadx.emu.ext.unidbg

import jadx.plugins.emu.exec.NativeBridge
import jadx.plugins.emu.exec.NotHandled
import jadx.plugins.emu.exec.runtime.UnknownVal
import org.slf4j.Logger

/**
 * The engine's view of the backend. Runs the methods in [allowed] and answers [NotHandled] for everything else,
 * so natives that weren't asked for behave exactly as they would with no backend.
 */
internal class UnidbgBridge(
    private val allowed: Set<String>,
    private val runner: NativeRunner,
    private val log: Logger,
) : NativeBridge {
    private val reported = HashSet<String>()

    override fun call(className: String, methodName: String, signature: String, args: List<Any?>, receiver: Any?): Any? {
        val key = Jni.key(className, methodName)
        if (key !in allowed) return NotHandled
        if (args.any { it is UnknownVal } || receiver is UnknownVal) {
            log.debug("unidbg: {}{} skipped, an argument is unknown", key, signature)
            return NotHandled
        }
        return try {
            val result = runner.invoke(className, methodName, signature, args, receiver)
            log.debug("unidbg: {}{} -> {}", key, signature, describe(result))
            result
        } catch (t: Throwable) {
            // say it loudly once per method; a decryptor called from thousands of places would otherwise flood the log
            if (reported.add(key + signature)) log.warn("unidbg: {}{} failed: {}", key, signature, t.toString())
            else log.debug("unidbg: {}{} failed again: {}", key, signature, t.toString())
            NotHandled
        }
    }

    private fun describe(r: Any?): String = when (r) {
        null -> "null"
        is Unit -> "void"
        is ByteArray -> "byte[${r.size}]"
        is String -> "\"${r.take(40)}\"" + if (r.length > 40) "..." else ""
        else -> r.toString()
    }
}
