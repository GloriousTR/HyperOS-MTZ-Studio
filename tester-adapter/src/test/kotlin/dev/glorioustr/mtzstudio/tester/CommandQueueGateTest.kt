package dev.glorioustr.mtzstudio.tester

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandQueueGateTest {
    @Test fun `queued command expires without dispatching while first command owns gate`() {
        val gate = CommandQueueGate(100)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = thread {
            gate.run { entered.countDown(); release.await(5, TimeUnit.SECONDS) }
        }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            var dispatched = false
            assertFailsWith<CommandQueueBusyException> { gate.run { dispatched = true } }
            assertFalse(dispatched)
        } finally {
            release.countDown()
            worker.join(2_000)
        }
        assertEquals("next", gate.run { "next" })
    }

    @Test fun `throwing command releases gate for next request`() {
        val gate = CommandQueueGate(100)
        assertFailsWith<IllegalStateException> { gate.run { error("test") } }
        assertEquals(42, gate.run { 42 })
    }
}
