package jadx.emu.ext.unidbg

/** Runs one native method. The unidbg implementation is [UnidbgRunner]; tests use a fake. */
internal interface NativeRunner : AutoCloseable {
    /**
     * @param className internal name of the declaring class, as in `com/foo/Bar`
     * @return the result, or [Unit] for a method that returns void. A native that returns null gives null.
     * @throws Exception when the call can't be made or the native code fails
     */
    fun invoke(className: String, method: String, signature: String, args: List<Any?>, receiver: Any?): Any?
}
