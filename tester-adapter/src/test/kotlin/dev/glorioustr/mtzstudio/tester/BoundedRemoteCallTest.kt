package dev.glorioustr.mtzstudio.tester

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BoundedRemoteCallTest {
    @Test fun `successful remote result is returned`() {
        assertEquals("0", BoundedRemoteCall.await(2_000) { "0" })
    }

    @Test fun `remote error is preserved without wrapping or retry`() {
        val error = IllegalStateException("binder failed")
        val calls = AtomicInteger()
        assertSame(error, assertFailsWith<IllegalStateException> {
            BoundedRemoteCall.await(2_000) { calls.incrementAndGet(); throw error }
        })
        assertEquals(1, calls.get())
    }

    @Test fun `stalled remote call returns timeout without replaying it`() {
        val calls = AtomicInteger()
        val finished = CountDownLatch(1)
        assertFailsWith<ThemeManagerUpdateException> {
            BoundedRemoteCall.await(200) {
                calls.incrementAndGet()
                try { CountDownLatch(1).await(5, TimeUnit.SECONDS) } finally { finished.countDown() }
            }
        }
        assertTrue(finished.await(2, TimeUnit.SECONDS))
        assertEquals(1, calls.get())
    }
}
