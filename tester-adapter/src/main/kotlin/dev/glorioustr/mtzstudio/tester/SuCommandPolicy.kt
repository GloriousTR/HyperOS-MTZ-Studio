package dev.glorioustr.mtzstudio.tester

/** Select flags before dispatch; never replay a potentially destructive operation. */
object SuCommandPolicy {
    fun supportsGlobalMountNamespace(help: String): Boolean =
        Regex("(?m)^.*--mount-master(?:\\s|$).*$").containsMatchIn(help)

    fun arguments(command: String, globalMountNamespace: Boolean): List<String> =
        if (globalMountNamespace) listOf("su", "--mount-master", "-c", command)
        else listOf("su", "-c", command)
}
