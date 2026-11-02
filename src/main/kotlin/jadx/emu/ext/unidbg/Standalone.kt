package jadx.emu.ext.unidbg

import java.io.File

/**
 * Runs one native method of a .so outside jadx, to try a library quickly.
 *
 *   ./gradlew run --args="libfoo.so com/foo/Bar decrypt (I)Ljava/lang/String; i:7"
 *
 * Arguments after the signature are typed: `i:7` int, `j:7` long, `z:true` boolean, `s:text` String, `b:00ff` byte[].
 * Set UNIDBG_VERBOSE=1 for unidbg's own tracing. Add `--instance` first for a non-static method.
 */
fun main(argv: Array<String>) {
    val args = argv.toMutableList()
    val instance = args.remove("--instance")
    if (args.size < 4) {
        System.err.println("usage: [--instance] <lib.so> <class, as com/foo/Bar> <method> <signature, as (I)I> [i:7 j:7 z:true s:text b:00ff ...]")
        return
    }
    val so = File(args[0])
    if (!so.isFile) { System.err.println("no such file: $so"); return }
    val values = args.drop(4).map(::parseValue)
    val options = BackendOptions(verbose = System.getenv("UNIDBG_VERBOSE") == "1")
    UnidbgRunner(listOf(so), true, options).use { runner ->
        val receiver = if (instance) Any() else null
        when (val r = runner.invoke(args[1], args[2], args[3], values, receiver)) {
            is ByteArray -> println("result: byte[${r.size}] = ${r.joinToString("") { "%02x".format(it) }}")
            Unit -> println("result: void")
            null -> println("result: null")
            else -> println("result: $r")
        }
    }
}

private fun parseValue(s: String): Any {
    val kind = s.substringBefore(':', "")
    val v = s.substringAfter(':', "")
    return when (kind) {
        "i" -> v.toInt()
        "j" -> v.toLong()
        "z" -> v.toBooleanStrict()
        "s" -> v
        "b" -> { require(v.length % 2 == 0) { "hex input must have an even length" }; ByteArray(v.length / 2) { v.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }
        else -> throw IllegalArgumentException("argument '$s' needs a type prefix: i: j: z: s: or b:")
    }
}
