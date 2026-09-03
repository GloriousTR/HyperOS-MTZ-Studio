package dev.glorioustr.mtzstudio.tester

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import java.io.IOException

class VerifiedRootCommandRunnerTest {
    private class FakeRunner(private val action: (String) -> PrivilegedCommandResult) : PrivilegedCommandRunner {
        val calls = mutableListOf<String>()
        override fun run(command: String, timeoutSeconds: Long): PrivilegedCommandResult {
            calls += command
            return action(command)
        }
    }
    private fun ok(output: String = "0") = PrivilegedCommandResult(0, output, "test")

    @Test fun `absent compatible service uses direct root`() {
        val direct = FakeRunner { ok() }
        VerifiedRootCommandRunner({ null }, direct).run("operation", 30)
        assertEquals(listOf("id -u", "operation"), direct.calls)
    }

    @Test fun `root compatible service is used when direct su is unavailable`() {
        val service = FakeRunner { ok() }
        val direct = FakeRunner { throw IOException("su unavailable") }
        VerifiedRootCommandRunner({ service }, direct).run("operation", 30)
        assertEquals(listOf("id -u", "operation"), service.calls)
        assertEquals(listOf("id -u"), direct.calls)
    }

    @Test fun `working direct root avoids constructing compatible service`() {
        val service = FakeRunner { ok("2000") }
        val direct = FakeRunner { ok() }
        VerifiedRootCommandRunner({ service }, direct).run("operation", 30)
        assertEquals(emptyList(), service.calls)
        assertEquals(listOf("id -u", "operation"), direct.calls)
    }

    @Test fun `unavailable probes never run operation`() {
        val service = FakeRunner { throw IOException("binder unavailable") }
        val direct = FakeRunner { throw IOException("permission denied") }
        val failure = assertFailsWith<RootAccessUnavailableException> {
            VerifiedRootCommandRunner({ service }, direct).run("operation", 30)
        }
        assertEquals(2, failure.suppressed.size)
        assertEquals(listOf("id -u"), service.calls)
        assertEquals(listOf("id -u"), direct.calls)
    }

    @Test fun `failed root probe exit code does not run operation`() {
        val direct = FakeRunner { PrivilegedCommandResult(1, "0", "su") }
        assertFailsWith<RootAccessUnavailableException> {
            VerifiedRootCommandRunner({ null }, direct).run("operation", 30)
        }
        assertEquals(listOf("id -u"), direct.calls)
    }

    @Test fun `uncertain direct operation is not repeated via service`() {
        val failure = IOException("binder died after dispatch")
        val service = FakeRunner { ok() }
        val direct = FakeRunner { if (it == "id -u") ok() else throw failure }
        assertSame(failure, assertFailsWith<IOException> {
            VerifiedRootCommandRunner({ service }, direct).run("operation", 30)
        })
        assertEquals(emptyList(), service.calls)
    }

    @Test fun `nonzero command result is returned without fallback`() {
        val service = FakeRunner { ok() }
        val direct = FakeRunner { if (it == "id -u") ok() else PrivilegedCommandResult(42, "failure", "su") }
        assertEquals(42, VerifiedRootCommandRunner({ service }, direct).run("operation", 30).exitCode)
        assertEquals(emptyList(), service.calls)
    }

    @Test fun `preflight reuses probe result`() {
        val direct = FakeRunner { ok() }
        VerifiedRootCommandRunner({ null }, direct).run("id -u", 10)
        assertEquals(listOf("id -u"), direct.calls)
    }

    @Test fun `interrupted direct probe does not try another channel`() {
        val service = FakeRunner { ok() }
        val direct = FakeRunner { throw InterruptedException("cancelled") }
        assertFailsWith<InterruptedException> {
            VerifiedRootCommandRunner({ service }, direct).run("operation", 30)
        }
        assertEquals(emptyList(), service.calls)
    }
}
