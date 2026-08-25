package dev.glorioustr.mtzstudio.core

internal object SafeArchivePath {
    private val drivePrefix = Regex("^[A-Za-z]:")

    fun normalize(raw: String, directory: Boolean): String {
        if (raw.isBlank() || raw.indexOf('\u0000') >= 0) unsafe(raw)
        if ('\\' in raw) unsafe(raw)
        val slashPath = raw
        if (slashPath.startsWith('/') || drivePrefix.containsMatchIn(slashPath)) unsafe(raw)

        val comparable = if (directory) slashPath.removeSuffix("/") else slashPath
        if (comparable.isBlank()) unsafe(raw)
        val segments = comparable.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) unsafe(raw)
        return if (directory) "$comparable/" else comparable
    }

    private fun unsafe(path: String): Nothing = throw UnsafeMtzException(
        UnsafeMtzException.Reason.UNSAFE_PATH,
        "Unsafe archive path: ${path.take(160)}",
    )
}
