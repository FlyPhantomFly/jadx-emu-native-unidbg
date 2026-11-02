package jadx.emu.ext.unidbg

import jadx.emu.ext.unidbg.EmuTestDex.cls
import jadx.emu.ext.unidbg.EmuTestDex.staticMethod
import jadx.plugins.emu.api.Hooks
import jadx.plugins.emu.api.NativeRequest
import jadx.plugins.emu.exec.EngineContext
import jadx.plugins.emu.exec.ExecLimits
import jadx.plugins.emu.exec.HostBoundary
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The whole path: an emulated Dalvik app calls a native method, the engine hands it to the backend, unidbg runs real
 * ARM64 code, and the answer goes back into the emulated app, which carries on with it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndTest {
    private val native = "Ljnitest/Native;"

    private val source = EmuTestDex.assemble(
        cls(native, """
            .method public constructor <init>()V
                .registers 1
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method
            .method public static native triple(I)I
            .end method
            .method public static native isEven(I)Z
            .end method
            .method public static native name(I)Ljava/lang/String;
            .end method
            .method public static native length([B)I
            .end method
            .method public static native mulLong(JJ)J
            .end method
            .method public native bump(I)I
            .end method
        """),
        cls("LApp;",
            staticMethod("decode(I)Ljava/lang/String;", 2, """
                invoke-static {p0}, Ljnitest/Native;->name(I)Ljava/lang/String;
                move-result-object v0
                return-object v0
            """) + staticMethod("tripleThenAddOne(I)I", 2, """
                invoke-static {p0}, Ljnitest/Native;->triple(I)I
                move-result v0
                add-int/lit8 v0, v0, 1
                return v0
            """) + staticMethod("even(I)Z", 2, """
                invoke-static {p0}, Ljnitest/Native;->isEven(I)Z
                move-result v0
                return v0
            """) + staticMethod("greeting(I)Ljava/lang/String;", 3, """
                invoke-static {p0}, Ljnitest/Native;->name(I)Ljava/lang/String;
                move-result-object v0
                const-string v1, " world"
                invoke-virtual {v0, v1}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v0
                return-object v0
            """) + staticMethod("sizeOf()I", 3, """
                const/4 v0, 4
                new-array v0, v0, [B
                invoke-static {v0}, Ljnitest/Native;->length([B)I
                move-result v0
                return v0
            """) + staticMethod("big()J", 6, """
                const-wide v0, 3000000000L
                const-wide/16 v2, 2
                invoke-static {v0, v1, v2, v3}, Ljnitest/Native;->mulLong(JJ)J
                move-result-wide v0
                return-wide v0
            """) + staticMethod("notAllowed(I)I", 2, """
                new-instance v0, Ljnitest/Native;
                invoke-direct {v0}, Ljnitest/Native;-><init>()V
                invoke-virtual {v0, p0}, Ljnitest/Native;->bump(I)I
                move-result v0
                return v0
            """)),
    )

    private val backend = UnidbgBackend(LoggerFactory.getLogger("test"))
    private lateinit var apk: File
    private lateinit var engine: EngineContext

    @BeforeAll
    fun setUp() {
        apk = File.createTempFile("jnitest", ".apk")
        val so = javaClass.getResourceAsStream("/libjnitest.so")!!.readBytes()
        ZipOutputStream(apk.outputStream()).use { z -> z.putNextEntry(ZipEntry("lib/arm64-v8a/libjnitest.so")); z.write(so); z.closeEntry() }
        engine = EngineContext(source, ExecLimits(maxSteps = 200_000, maxMillis = 30_000, maxDepth = 32), HostBoundary(), Hooks())
    }

    @AfterAll
    fun tearDown() {
        backend.closeAll()
        apk.delete()
    }

    private fun run(method: String, methods: Set<String>, vararg args: Any?): Any? {
        val bridge = backend.open(NativeRequest(apk, methods, listOf("libjnitest.so")))
        val vm = engine.newVm(engine.limits, androidEnvUnknown = false, nativeBridge = bridge)
        return vm.invoke(EmuTestDex.method(source, "LApp;", method), args.toList())
    }

    private val some = setOf("triple", "isEven", "name", "length", "mulLong").map { "Ljnitest/Native;->$it" }.toSet()

    @Test
    fun aDecryptedStringComesBackIntoTheEmulatedApp() {
        assertEquals("alpha", run("decode(I)Ljava/lang/String;", some, 0))
        assertEquals("Hello, unidbg!", run("decode(I)Ljava/lang/String;", some, 2))
    }

    @Test
    fun theAppKeepsComputingWithWhatTheNativeReturned() {
        assertEquals(22, run("tripleThenAddOne(I)I", some, 7))
        assertEquals("bravo-key world", run("greeting(I)Ljava/lang/String;", some, 1))
    }

    @Test
    fun aBooleanNativeBecomesABooleanInTheApp() {
        assertEquals(true, run("even(I)Z", some, 10))
        assertEquals(false, run("even(I)Z", some, 7))
    }

    @Test
    fun anArrayCreatedByTheAppIsPassedToNativeCode() {
        assertEquals(4, run("sizeOf()I", some))
    }

    @Test
    fun wideLongArgumentsSurviveTheWholePath() {
        assertEquals(6_000_000_000L, run("big()J", some))
    }

    @Test
    fun aNativeThatWasNotRequestedIsLeftAsUnknownJustLikeWithNoBackend() {
        val with = run("notAllowed(I)I", some, 7)
        val vmWithout = engine.newVm(engine.limits, androidEnvUnknown = false)
        val without = vmWithout.invoke(EmuTestDex.method(source, "LApp;", "notAllowed(I)I"), listOf(7))
        assertEquals(without?.javaClass, with?.javaClass)
    }
}
