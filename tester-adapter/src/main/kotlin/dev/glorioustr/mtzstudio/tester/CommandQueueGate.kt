package dev.glorioustr.mtzstudio.tester

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/** Queue time is bounded separately from execution time; an unstarted operation is never dispatched. */
class CommandQueueGate(private val waitMillis: Long = 3_000) {
    private val lock = ReentrantLock(true)

    fun <T> run(block: () -> T): T {
        if (!lock.tryLock(waitMillis, TimeUnit.MILLISECONDS)) throw CommandQueueBusyException()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}

class CommandQueueBusyException : Exception("Another privileged operation is still running. Please try again shortly.")
