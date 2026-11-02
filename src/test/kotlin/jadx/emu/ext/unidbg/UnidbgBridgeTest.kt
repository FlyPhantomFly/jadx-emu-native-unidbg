package jadx.emu.ext.unidbg

import jadx.plugins.emu.exec.NotHandled
import jadx.plugins.emu.exec.runtime.UnknownVal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class UnidbgBridgeTest {
    private class Fake(var result: Any? = 42, var failure: Throwable? = null) : NativeRunner {
        val calls = ArrayList<List<Any?>>()
        override fun invoke(className: String, method: String, signature: String, args: List<Any?>, receiver: Any?): Any? {
            calls += listOf(className, method, signature, args, receiver)
            failure?.let { throw it }
            return result
        }
        override fun close() {}
    }

    private val log = LoggerFactory.getLogger("test")
    private val allowed = setOf("Lcom/foo/Bar;->decrypt")

    private fun bridge(f: Fake) = UnidbgBridge(allowed, f, log)

    @Test
    fun anAllowedMethodRunsAndItsResultIsReturned() {
        val f = Fake(result = "plain")
        assertEquals("plain", bridge(f).call("com/foo/Bar", "decrypt", "(I)Ljava/lang/String;", listOf(7), null))
        assertEquals(1, f.calls.size)
    }

    @Test
    fun theRunnerSeesExactlyWhatTheEngineSent() {
        val f = Fake()
        val recv = Any()
        bridge(f).call("com/foo/Bar", "decrypt", "(I[B)I", listOf(7, byteArrayOf(1)), recv)
        val c = f.calls.single()
        assertEquals("com/foo/Bar", c[0]); assertEquals("decrypt", c[1]); assertEquals("(I[B)I", c[2])
        assertEquals(7, (c[3] as List<*>)[0]); assertSame(recv, c[4])
    }

    @Test
    fun aMethodThatIsNotOnTheListIsLeftAloneAndNeverReachesTheRunner() {
        val f = Fake()
        assertSame(NotHandled, bridge(f).call("com/foo/Bar", "other", "(I)I", listOf(1), null))
        assertSame(NotHandled, bridge(f).call("com/foo/Baz", "decrypt", "(I)I", listOf(1), null))
        assertTrue(f.calls.isEmpty())
    }

    @Test
    fun anUnknownArgumentIsNeverGuessedAt() {
        val f = Fake()
        assertSame(NotHandled, bridge(f).call("com/foo/Bar", "decrypt", "(I)I", listOf(UnknownVal("I")), null))
        assertTrue(f.calls.isEmpty())
    }

    @Test
    fun anUnknownReceiverIsNeverGuessedAtEither() {
        val f = Fake()
        assertSame(NotHandled, bridge(f).call("com/foo/Bar", "decrypt", "(I)I", listOf(1), UnknownVal("Lcom/foo/Bar;")))
        assertTrue(f.calls.isEmpty())
    }

    @Test
    fun aFailureInsideTheRunnerBecomesNotHandledEveryTime() {
        val f = Fake(failure = IllegalStateException("boom"))
        val b = bridge(f)
        repeat(3) { assertSame(NotHandled, b.call("com/foo/Bar", "decrypt", "(I)I", listOf(1), null)) }
        assertEquals(3, f.calls.size)
    }

    @Test
    fun aNativeThatReturnsNullGivesNullNotNotHandled() {
        assertNull(bridge(Fake(result = null)).call("com/foo/Bar", "decrypt", "(I)Ljava/lang/String;", listOf(1), null))
    }

    @Test
    fun aVoidResultStaysAVoidResult() {
        assertSame(Unit, bridge(Fake(result = Unit)).call("com/foo/Bar", "decrypt", "()V", emptyList(), null))
    }
}
