package dev.glorioustr.mtzstudio.library

import dev.glorioustr.mtzstudio.composer.ComponentSelection
import dev.glorioustr.mtzstudio.composer.CompositionMetadata
import dev.glorioustr.mtzstudio.composer.CompositionRequest
import dev.glorioustr.mtzstudio.composer.CompositionSource
import dev.glorioustr.mtzstudio.composer.MtzComposer
import dev.glorioustr.mtzstudio.core.ComponentCategory
import dev.glorioustr.mtzstudio.core.UnsafeMtzException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ThemeLibraryTest {
    @Test
    fun `imports reloads composes and records provenance in private roots`() {
        val root = Files.createTempDirectory("mtz-library-test")
        val library = ThemeLibrary(root)
        val icons = library.importTheme(ByteArrayInputStream(zip("icons" to byteArrayOf(1))), "icons.mtz")
        val wallpaper = library.importTheme(
            ByteArrayInputStream(zip("wallpaper/home.jpg" to byteArrayOf(2))),
            "wallpaper.mtz",
        )

        val loaded = library.load()
        assertEquals(2, loaded.themes.size)
        assertTrue(loaded.warnings.isEmpty())

        val request = CompositionRequest(
            metadata = CompositionMetadata("Library test"),
            selections = listOf(
                selection(icons, ComponentCategory.ICONS),
                selection(wallpaper, ComponentCategory.WALLPAPER),
            ),
        )
        val result = MtzComposer().compose(request, library.newExportPath("Library test"))
        library.recordComposition(result)

        assertTrue(Files.isRegularFile(result.output))
        assertTrue(Files.list(root.resolve("mtz-history")).use { it.count() } == 1L)
        assertEquals(64, result.outputSha256.length)
    }

    @Test
    fun `oversized import is rejected and staging file is cleaned`() {
        val root = Files.createTempDirectory("mtz-library-limit-test")
        val library = ThemeLibrary(root, maxSourceBytes = 8)

        assertFailsWith<UnsafeMtzException> {
            library.importTheme(ByteArrayInputStream(ByteArray(9)), "large.mtz")
        }
        assertTrue(Files.list(root.resolve("mtz-library")).use { it.count() } == 0L)
    }

    @Test
    fun `import observer receives durable phase boundaries`() {
        val root = Files.createTempDirectory("mtz-library-diagnostics-test")
        val events = mutableListOf<ImportEvent>()
        val source = zip("icons" to ByteArray(32))

        ThemeLibrary(root).importTheme(
            ByteArrayInputStream(source),
            "observed.mtz",
            ImportObserver(events::add),
        )

        assertTrue(events.first() is ImportEvent.StagingCreated)
        assertTrue(events.any { it is ImportEvent.CopyCompleted && it.bytesCopied == source.size.toLong() })
        assertTrue(events.any { it is ImportEvent.ValidationCompleted })
        assertTrue(events.any { it is ImportEvent.CommitCompleted })
        assertTrue(events.last() is ImportEvent.StagingCleaned)
    }

    @Test
    fun `studio gallery marker survives reload`() {
        val root = Files.createTempDirectory("mtz-library-gallery-test")
        val library = ThemeLibrary(root)
        val imported = library.importTheme(
            input = ByteArrayInputStream(zip("icons" to byteArrayOf(1))),
            suggestedName = "My mix",
            includeInThemeGallery = true,
        )

        assertTrue(imported.includeInThemeGallery)
        assertTrue(library.load().themes.single().includeInThemeGallery)
    }

    private fun selection(theme: LibraryTheme, category: ComponentCategory): ComponentSelection {
        val component = theme.archive.components.single { it.category == category }
        return ComponentSelection(
            source = CompositionSource(theme.id, theme.displayName, theme.archive),
            category = category,
            rootPath = component.rootPath,
        )
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
