package dev.glorioustr.mtzstudio.tester

/** Select using read-only probes. Never replay a real operation after an uncertain failure. */
class VerifiedRootCommandRunner(
    private val service: () -> PrivilegedCommandRunner?,
    private val direct: PrivilegedCommandRunner,
) : PrivilegedCommandRunner {
    override fun run(command: String, timeoutSeconds: Long): PrivilegedCommandResult {
        val failures = mutableListOf<Exception>()
        val candidates = listOfNotNull(service(), direct)
        for (candidate in candidates) {
            val probe = try {
                candidate.run("id -u", 10)
            } catch (error: Exception) {
                if (error is InterruptedException || error is java.util.concurrent.CancellationException) throw error
                failures += error
                continue
            }
            if (probe.exitCode != 0 || probe.output.trim() != "0") {
                failures += IllegalStateException("Root probe did not confirm UID 0 (${probe.authorizationSource})")
                continue
            }
            return if (command == "id -u") probe else candidate.run(command, timeoutSeconds)
        }
        throw RootAccessUnavailableException().also { error -> failures.forEach(error::addSuppressed) }
    }
}

class RootAccessUnavailableException : Exception("No channel confirmed root access")
