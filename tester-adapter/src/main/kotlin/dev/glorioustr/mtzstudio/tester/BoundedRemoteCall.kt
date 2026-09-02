package dev.glorioustr.mtzstudio.tester

import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

object BoundedRemoteCall {
    /** A stalled Binder transaction must not keep the caller waiting indefinitely. No retry. */
    fun <T> await(timeoutMillis: Long, block: () -> T): T {
        val task = FutureTask(block)
        thread(name = "mtz-root-transaction", isDaemon = true) { task.run() }
        try {
            return task.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (error: ExecutionException) {
            throw (error.cause ?: error)
        } catch (error: TimeoutException) {
            task.cancel(true)
            throw ThemeManagerUpdateException("Root service did not respond in time. The operation was not retried.", error)
        } catch (error: InterruptedException) {
            task.cancel(true)
            Thread.currentThread().interrupt()
            throw error
        }
    }
}
