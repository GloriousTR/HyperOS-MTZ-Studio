package dev.glorioustr.mtzstudio.tester

/** Select using read-only probes. Never replay a real operation after an uncertain failure. */
class VerifiedRootCommandRunner(
    private val service: () -> PrivilegedCommandRunner?,
    private val direct: PrivilegedCommandRunner,
) : PrivilegedCommandRunner {
    override fun run(command: String, timeoutSeconds: Long): PrivilegedCommandResult {
        val failures = mutableListOf<Exception>()
        // Prefer the device's normal `su` channel. On some Vector/Shevery builds, starting a
        // root user service for every probe leaves orphan processes behind and can make the
        // entire device stutter. The compatible service remains a fallback for devices without
        // a working direct root manager.
        fun probeAndRun(candidate: PrivilegedCommandRunner): PrivilegedCommandResult? {
            val probe = try {
                candidate.run("id -u", 10)
            } catch (error: Exception) {
                if (error is InterruptedException || error is java.util.concurrent.CancellationException) throw error
                failures += error
                return null
            }
            if (probe.exitCode != 0 || probe.output.trim() != "0") {
                failures += IllegalStateException("Root probe did not confirm UID 0 (${probe.authorizationSource})")
                return null
            }
            return if (command == "id -u") probe else candidate.run(command, timeoutSeconds)
        }

        // Do not even construct/bind the root service while direct su works. Constructing all
        // candidates eagerly was enough to leave a service process behind on affected ROMs.
        probeAndRun(direct)?.let { return it }
        val compatibleService = try {
            service()
        } catch (error: Exception) {
            if (error is InterruptedException || error is java.util.concurrent.CancellationException) throw error
            failures += error
            null
        }
        if (compatibleService != null) probeAndRun(compatibleService)?.let { return it }
        throw RootAccessUnavailableException().also { error -> failures.forEach(error::addSuppressed) }
    }
}

class RootAccessUnavailableException : Exception("No channel confirmed root access")
