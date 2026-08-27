package dev.glorioustr.mtzstudio.library

sealed interface ImportEvent {
    data class StagingCreated(val fileName: String) : ImportEvent
    data object CopyStarted : ImportEvent
    data class CopyProgress(val bytesCopied: Long) : ImportEvent
    data class CopyCompleted(val bytesCopied: Long) : ImportEvent
    data object ValidationStarted : ImportEvent
    data class ValidationCompleted(val sha256: String, val entryCount: Int) : ImportEvent
    data class CommitStarted(val themeId: String) : ImportEvent
    data class CommitCompleted(val themeId: String, val sha256: String) : ImportEvent
    data class Failed(val stage: String, val message: String) : ImportEvent
    data class StagingCleaned(val removed: Boolean) : ImportEvent
}

fun interface ImportObserver {
    fun onEvent(event: ImportEvent)

    companion object {
        val NONE = ImportObserver { }
    }
}
