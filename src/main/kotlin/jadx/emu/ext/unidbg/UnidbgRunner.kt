package jadx.emu.ext.unidbg

import com.github.unidbg.AndroidEmulator
import com.github.unidbg.arm.backend.BackendFactory
import com.github.unidbg.linux.android.AndroidEmulatorBuilder
import com.github.unidbg.linux.android.AndroidResolver
import com.github.unidbg.linux.android.dvm.DvmObject
import com.github.unidbg.linux.android.dvm.StringObject
import com.github.unidbg.linux.android.dvm.VM
import com.github.unidbg.linux.android.dvm.array.ByteArray as UnidbgByteArray
import java.io.File

/**
 * Builds a unidbg Android emulator, loads the given libraries and runs JNI methods in them.
 *
 * Nothing starts until the first [invoke]. Supported arguments are `int`-like types, `boolean`, `long`, `String`
 * and `byte[]`; results may also be void. Anything else is refused with an exception, never guessed.
 */
internal class UnidbgRunner(
    private val libs: List<File>,       // primary first; the rest are libraries it needs
    private val is64Bit: Boolean,
    private val options: BackendOptions,
    private val workDir: File? = null,  // deleted on close
) : NativeRunner {
    private var emulator: AndroidEmulator? = null
    private var vm: VM? = null
    private var loadError: Throwable? = null

    @Synchronized
    private fun ready(): Pair<AndroidEmulator, VM> {
        loadError?.let { throw IllegalStateException("libraries failed to load earlier: ${it.message}", it) }
        emulator?.let { return it to vm!! }
        try {
            val emu = builder().build()
            emu.memory.setLibraryResolver(AndroidResolver(options.sdk))
            val v = emu.createDalvikVM()
            v.setVerbose(options.verbose)
            emulator = emu
            vm = v
            for (lib in libs.asReversed()) {       // dependencies first, the primary library last
                try {
                    v.loadLibrary(lib, options.jniOnLoad)
                } catch (t: Throwable) {
                    throw IllegalStateException("could not load ${lib.name}: $t", t)
                }
            }
            return emu to v
        } catch (t: Throwable) {
            loadError = t
            runCatching { emulator?.close() }
            emulator = null
            throw t
        }
    }

    private fun builder(): AndroidEmulatorBuilder {
        val b = if (is64Bit) AndroidEmulatorBuilder.for64Bit() else AndroidEmulatorBuilder.for32Bit()
        unicorn2()?.let { b.addBackendFactory(it) }
        return b
    }

    /** unidbg 0.9.9 wants the unicorn2 CPU registered explicitly; 0.9.8 doesn't need it. Use it when it is on the classpath. */
    private fun unicorn2(): BackendFactory? = try {
        Class.forName("com.github.unidbg.arm.backend.Unicorn2Factory")
            .getConstructor(Boolean::class.javaPrimitiveType).newInstance(true) as BackendFactory
    } catch (_: ClassNotFoundException) {
        null
    }

    @Synchronized
    override fun invoke(className: String, method: String, signature: String, args: List<Any?>, receiver: Any?): Any? {
        val (emu, vm) = ready()
        val types = Jni.argTypes(signature)
        require(args.size == types.size) { "$method$signature takes ${types.size} argument(s), got ${args.size}" }
        val jniArgs = types.mapIndexed { i, t -> toNative(vm, args[i], t, i) }.toTypedArray()
        val cls = vm.resolveClass(className)
        val sig = method + signature          // unidbg wants "name(args)ret"
        val self: DvmObject<*>? = if (receiver != null) cls.newObject(null) else null
        return when (val ret = Jni.returnType(signature)) {
            "V" -> { if (self != null) self.callJniMethod(emu, sig, *jniArgs) else cls.callStaticJniMethod(emu, sig, *jniArgs); Unit }
            "I", "S", "B", "C" -> if (self != null) self.callJniMethodInt(emu, sig, *jniArgs) else cls.callStaticJniMethodInt(emu, sig, *jniArgs)
            "J" -> if (self != null) self.callJniMethodLong(emu, sig, *jniArgs) else cls.callStaticJniMethodLong(emu, sig, *jniArgs)
            "Z" -> if (self != null) self.callJniMethodBoolean(emu, sig, *jniArgs) else cls.callStaticJniMethodBoolean(emu, sig, *jniArgs)
            "Ljava/lang/String;", "[B" -> {
                val o: DvmObject<*>? = if (self != null) self.callJniMethodObject(emu, sig, *jniArgs) else cls.callStaticJniMethodObject(emu, sig, *jniArgs)
                fromNative(o, ret)
            }
            else -> throw UnsupportedOperationException("return type $ret is not supported")
        }
    }

    private fun toNative(vm: VM, v: Any?, type: String, index: Int): Any = when (type) {
        "I", "S", "B", "C" -> when (v) {
            is Int -> v
            is Short -> v.toInt()
            is Byte -> v.toInt()
            is Char -> v.code
            is Boolean -> if (v) 1 else 0
            else -> bad(index, type, v)
        }
        "Z" -> when (v) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            else -> bad(index, type, v)
        }
        "J" -> when (v) {
            is Long -> v
            is Int -> v.toLong()
            else -> bad(index, type, v)
        }
        "Ljava/lang/String;" -> if (v == null) 0 else StringObject(vm, v as? String ?: bad(index, type, v))
        "[B" -> if (v == null) 0 else UnidbgByteArray(vm, v as? ByteArray ?: bad(index, type, v))
        else -> throw UnsupportedOperationException("argument ${index + 1} has type $type, which is not supported")
    }

    private fun bad(index: Int, type: String, v: Any?): Nothing =
        throw IllegalArgumentException("argument ${index + 1} should be $type, got ${v?.javaClass?.simpleName ?: "null"}")

    private fun fromNative(o: DvmObject<*>?, type: String): Any? = when {
        o == null -> null
        type == "Ljava/lang/String;" -> o.value as? String
        type == "[B" -> o.value as? ByteArray
        else -> throw UnsupportedOperationException("return type $type is not supported")
    }

    @Synchronized
    override fun close() {
        runCatching { emulator?.close() }
        emulator = null
        vm = null
        workDir?.deleteRecursively()
    }
}
