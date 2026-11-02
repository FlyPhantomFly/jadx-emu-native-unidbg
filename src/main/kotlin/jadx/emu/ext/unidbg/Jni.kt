package jadx.emu.ext.unidbg

/** Helpers for JVM method signatures such as `(I[BLjava/lang/String;)J` and for allow-list entries. */
internal object Jni {
    private val METHOD_KEY = Regex("^L[^;()]+;->[^;()\\s]+$")

    /** The types of the arguments, one entry each: `I`, `[B`, `Ljava/lang/String;`. */
    fun argTypes(sig: String): List<String> {
        val open = sig.indexOf('(')
        val close = sig.indexOf(')')
        if (open < 0 || close < 0 || close < open) return emptyList()
        val s = sig.substring(open + 1, close)
        val out = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val start = i
            while (i < s.length && s[i] == '[') i++
            if (i >= s.length) break
            if (s[i] == 'L') {
                val semi = s.indexOf(';', i)
                if (semi < 0) break
                i = semi + 1
            } else {
                i++
            }
            out.add(s.substring(start, i))
        }
        return out
    }

    fun returnType(sig: String): String {
        val close = sig.indexOf(')')
        return if (close < 0 || close + 1 >= sig.length) "V" else sig.substring(close + 1)
    }

    /** The allow-list entry for a method the engine reports as class `com/foo/Bar`, method `decrypt`. */
    fun key(className: String, method: String): String = "L$className;->$method"

    /** Whether [s] looks like `Lcom/foo/Bar;->decrypt`. */
    fun isMethodKey(s: String): Boolean = METHOD_KEY.matches(s)
}
