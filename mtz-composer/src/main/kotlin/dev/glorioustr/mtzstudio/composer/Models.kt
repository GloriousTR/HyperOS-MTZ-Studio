package dev.glorioustr.mtzstudio.composer

import dev.glorioustr.mtzstudio.core.ComponentCategory
import dev.glorioustr.mtzstudio.core.MtzArchive
import dev.glorioustr.mtzstudio.core.ThemeId
import java.nio.file.Path

data class CompositionSource(
    val themeId: ThemeId,
    val displayName: String,
    val archive: MtzArchive,
)

data class ComponentSelection(
    val source: CompositionSource,
    val category: ComponentCategory,
    val rootPath: String,
    val useDefault: Boolean = false,
)

data class CompositionMetadata(
    val name: String,
    val author: String? = null,
    val designer: String? = null,
    val version: String = "1.0",
    val description: String = "Composed locally with HyperOS MTZ Studio",
)

data class CompositionRequest(
    val metadata: CompositionMetadata,
    val selections: List<ComponentSelection>,
    val baseSource: CompositionSource? = null,
    val customHomeWallpaperBytes: ByteArray? = null,
    val customLockWallpaperBytes: ByteArray? = null,
    val generatedPreviewBytes: ByteArray? = null,
)

data class FontExportRequest(
    val metadata: CompositionMetadata,
    val fontFile: Path,
    val archiveFileName: String = "Roboto-Regular.ttf",
    val previewFile: Path? = null,
)

data class ProvenanceEntry(
    val sourceThemeId: ThemeId,
    val sourceDisplayName: String,
    val sourceSha256: String,
    val category: ComponentCategory,
    val rootPath: String,
    val useDefault: Boolean = false,
)

data class CompositionResult(
    val output: Path,
    val outputSha256: String,
    val verifiedArchive: MtzArchive,
    val provenance: List<ProvenanceEntry>,
)

class CompositionException(message: String, cause: Throwable? = null) : Exception(message, cause)
