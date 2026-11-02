package jadx.emu.ext.unidbg

import jadx.plugins.emu.api.EmuExtension
import jadx.plugins.emu.api.EmuExtensionContext
import jadx.plugins.emu.api.EmuExtensionInfo
import jadx.plugins.emu.api.ExtensionMode

/** Registers the `unidbg` native backend. It does nothing else: other extensions ask for it when they need it. */
class UnidbgExtension : EmuExtension {
    private var backend: UnidbgBackend? = null

    override fun info() = EmuExtensionInfo(
        "native-unidbg",
        "Native backend (unidbg)",
        "Runs JNI methods of an app's native libraries in unidbg, for other extensions to use",
        ExtensionMode.ON_DEMAND,
    )

    override fun init(ctx: EmuExtensionContext) {
        val b = UnidbgBackend(ctx.log)
        backend = b
        ctx.registerNativeBackend(b)
    }

    override fun unload() {
        backend?.closeAll()
        backend = null
    }
}
