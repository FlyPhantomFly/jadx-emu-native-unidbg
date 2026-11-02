package jadx.emu.ext.unidbg

/** Settings a caller can put in `NativeRequest.options`. Unknown names are refused so typos don't go unnoticed. */
internal data class BackendOptions(
    val sdk: Int = 23,
    val verbose: Boolean = false,
    val jniOnLoad: Boolean = true,
) {
    companion object {
        val KNOWN = listOf("sdk", "verbose", "jni-onload")

        fun parse(raw: Map<String, String>): BackendOptions {
            val unknown = raw.keys - KNOWN.toSet()
            require(unknown.isEmpty()) { "unknown option(s) ${unknown.sorted()}; known options: ${KNOWN.joinToString()}" }
            val defaults = BackendOptions()
            return BackendOptions(
                sdk = raw["sdk"]?.let { it.trim().toIntOrNull() ?: throw IllegalArgumentException("option sdk must be a number, got '$it'") } ?: defaults.sdk,
                verbose = flag(raw, "verbose", defaults.verbose),
                jniOnLoad = flag(raw, "jni-onload", defaults.jniOnLoad),
            )
        }

        private fun flag(raw: Map<String, String>, key: String, default: Boolean): Boolean {
            val v = raw[key] ?: return default
            return when (v.trim().lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> throw IllegalArgumentException("option $key must be true or false, got '$v'")
            }
        }
    }
}
